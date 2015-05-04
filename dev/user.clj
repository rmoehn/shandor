(ns user
  "Tools for interactive development with the REPL. This file should
  not be included in a production build of the application."
  (:require [clojure.java.io :as io]
            [clojure.java.javadoc :refer [javadoc]]
            [clojure.pprint :refer [pprint]]
            [clojure.reflect :refer [reflect]]
            [clojure.repl :refer [apropos dir doc find-doc pst source]]
;            [clojure.set :as set]
            [clojure.string :as str]
;            [clojure.test :as test]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [clojure.core.logic :as cl]
            [clojure.instant :as inst]
            [clojure.walk :as walk]
            [joda-time :as jt]
            [net.n01se.clojure-jna :as jna]
            [shandor])
  (:import [com.sun.jna.ptr PointerByReference]
           [com.sun.jna Pointer]
           ))

;;; Note: We won't be threadsafe, although notmuch makes that possible.

(jna/to-ns nm notmuch [Integer notmuch_database_create,
                       Integer notmuch_database_open,
                       Integer notmuch_database_close,
                       String notmuch_database_get_path,
                       Integer notmuch_database_get_version,
                       Integer notmuch_database_find_message,
                       String notmuch_message_get_filename,
                       String notmuch_message_get_header,
                       Pointer notmuch_message_get_tags,
                       Integer notmuch_message_add_tag,
                       Boolean notmuch_tags_valid,
                       Void notmuch_tags_move_to_next,
                       String notmuch_tags_get,
                       Void notmuch_tags_destroy
                       Pointer notmuch_query_create
                       Pointer notmuch_query_search_messages
                       Void notmuch_query_destroy
                       Boolean notmuch_messages_valid
                       Pointer notmuch_messages_get
                       Void notmuch_messages_move_to_next
                       ])

(defn ns-comfort [nmsp common-fn pref->ns-sym]
  (do
    (doseq [s (vals pref->ns-sym)]
      (remove-ns s)
      (create-ns s))
    (doseq [[raw-s v] (ns-interns nmsp)]
      (let [s (common-fn (str raw-s))
            pref (some #(re-find (re-pattern %) s) (keys pref->ns-sym))
            new-sym (symbol (str/replace-first s (re-pattern pref) ""))]
        (intern (pref->ns-sym pref) new-sym v)))
    (for [s (vals pref->ns-sym)]
      [s (ns-interns s)])))

(ns-comfort 'nm
            (fn [s] (-> s
                        (str/replace #"_" "-")
                        (str/replace-first #"notmuch-" "")))
            {"database-" 'nm-db
             "message-" 'nm-msg
             "messages-" 'nm-msgs
             "tags-" 'nm-t
             "query-" 'nm-query})

(def mode {:read-only 0})

(defn get-tags [msg]
  (let [tags-obj (nm/notmuch_message_get_tags msg)
        res (loop [tags-vec []]
              (if-some [tag (nm/notmuch_tags_get tags-obj)]
                       (do (nm/notmuch_tags_move_to_next tags-obj)
                         (recur (conj tags-vec tag)))
                       tags-vec))]
    (nm/notmuch_tags_destroy tags-obj)
    res))

;(defn add-tags! [msg tags]
  ;(doseq [tag tags]
    ;(nm-msg/add-tag tag)))

(defn add-tags! [msg tags]
  (println "Would add " tags))

;; What do we want? Apply an operation to all messages returned from a query.
;;  - TD is the date of today.
;;  - Remove all messages two weeks after they were marked as deleted.
;;      - There is no way to find out when a message was marked as deleted.
;;      - Therefore we have to run the program soon after messages are marked as
;;        deleted and give those that were newly marked a removal date.
;;  - Give all messages with tag "deleted" and without an removal date the
;;    removal date TD+2w.
;;  - Removal dates are Tags of the format REM_2015-06-07.
;;  - Give all messages with Tag 2w and without an expiration date the
;;  expiration date TD+2w.
;;  - Remove all messages whose removal date is before TD from disk.
;;  - Expiry dates are dates of the format EXP_2015-05-03.
;;  - Mark all messages whose expiration date is before date as deleted.

;; - How do we want to do it?
;; - Premise: Our logic engine is purely logical. It can't modify anything.
;; - What modifications do we have to make?
;;    - Remove message from disk.
;;    - Change tags of message.
;;    - Set maildir flags.
;;    - (Set a flag.)
;;    - (Freeze a message.)
;;    - (Thaw a message.)
;; - Possibilities:
;;  A. Calculate tags for each message and then go through tags.
;;      - Run calculate-tags on the message.
;;      - Set tags for that message.
;;      - If message has tag REMOVE, remove it.
;;     But that would mean communication between parts of our program through
;;     the tags of the message. Complecting tags and commands. Not good.
;;  B. Calculate a list of commands.
;;      - Run calculate-actions on the message.
;;      - Execute each action in the list returned. (SET-TAGS, REMOVE,
;;        SET-FLAGS(, FREEZE, THAW))
;;  C. Have the fact database mirror the notmuch database.
;;      - Put facts in the fact database according to current state of mail
;;        database.
;;      - Have the logic engine operate on the fact database.
;;      - After the logic engine finishes, update the mail database to be in
;;        accord with the fact database.
;;     That's overkill, I'd say.
;;  D. Any other?
;;      - We could have logic stuff that modifies the outside, I guess, but that
;;        wouldn't be very nice.

(def ^:private period-constr {"d" jt/days
                              "w" jt/weeks
                              "m" jt/months
                              "y" jt/years})

; http://clj-me.cgrand.net/2011/06/17/a-flatter-cond/

(defmacro cond-let [& clauses]
  (when-let [[t e & cs] (seq clauses)]
    (if (vector? t)
      `(if-let ~t ~e (cond-let ~@cs))
      `(if ~t ~e (cond-let ~@cs)))))

(defn transform-tag [t]
  (cond-let
    [[_ cmd date] (re-matches #"(REM|DEL)_(.*)" t)]
    [(keyword (str/lower-case cmd)) (jt/local-date date)]

    [[_ cnt unit] (re-matches #"(\d+)(\w)" t)]
    [:ttl ((period-constr unit) (read-string cnt))]

    (= "deleted" t)
    [:deleted :deleted]

    :else
    [t t]))

(defn transform-back-1 [t]
  (case (key t)
    (:rem :exp) (str (str/upper-case (key t)) "_" (jt/print (val t)))
    :deleted "deleted"
    nil))

(defn transform-back [ts]
  (map transform-back-1 ts))

(defn new-tags [tags]
  (let [today (jt/local-date)]
    (cond
      (and (contains? tags :deleted) (not (contains? tags :rem)))
      {:rem (jt/plus today (jt/weeks 2))}

      (and (contains? tags :rem) (jt/before? (tags :rem) today))
      :remove

      (and (contains? tags :ttl) (not (contains? tags :exp)))
      {:exp (jt/plus today (tags :ttl))}

      (and (not (contains? tags :deleted))
           (contains? tags :exp) (jt/before? (tags :exp) today))
      {:deleted :deleted
       :rem (jt/plus today (jt/weeks 2))}

      :else
      [])))

(transform-tag "deleted")

(defn transform-tags [tags]
 (into {} (map transform-tag (set tags))))

;(cond-let
  ;[rem-date (:rem tags)]
  ;(if (before? rem-date today)
    ;(assoc tags :remove true)
    ;tags
    ;))

; Message cycle:
; no special tags -> deleted -> remove+2w -> remove
;      ↓
;     ttl
;      ↓
;   exp…
;      ↓
;   deleted, remove+2w
;      ↓
;   remove

(defn remove-message! [msg]
  (println "Would remove msg" msg))


(defn treat-message [msg]
  (let [tags (transform-tags (get-tags msg))
        nt (new-tags tags)]
    (if (= nt :remove)
      (remove-message! msg)
      (add-tags! msg (transform-back nt)))))



(comment

  (defn before [a b]
    (cl/project [a b]
      (cl/== true (jt/before? (jt/local-date a) (jt/local-date b)))))

  (defn rem-date [tags date]
    (cl/project [tags]
      (cl/== date (.substring (first (filter #(.startsWith % "REM_") tags)) 4))))

  (defn add-rem-cmdo [today ttl cmds]
    (cl/fresh [rm-date rm-cmd]
      (cl/project [today ttl]
        (== rm-date (jt/plus today (str->period ttl))))
      (cl/== rm-cmd '(:set-tag ))
      (cl/membero rm-cmd cmds)))

  (defn commandso [tags today cs]
    (cl/all
      (cl/fresh [rd]
        (rem-date tags rd)
        (before rd today)
        (cl/membero :remove cs))
      (cl/all
        (cl/membero "deleted" tags)
        (rem-date tags nil)
        (add-rem-cmdo today "2w" cs))
      (cl/fresh [et]
        (exp-tag tags et)
        (cl/!= et nil)
        (add-exp-cmdo today et cs))
      (cl/resh [ed]
               (exp-date tags ed)
               (before ed today)
               (cl/membero [:set-tag "deleted"] tags))))

  (defn determine-action [tags]
    (let [rem-date])
    )

  (cl/run 1 [q]
          (commandso ["REM_2015-05-09"] "2015-05-08" q)
          ))

  ;
  ;(run* [tags-new]
  ;      (beforeo (removal-date m) (today))
  ;      (conso "REMOVE" (tags-from-msg m) tags-new))
  ;
  ;(run* [tags-new]
  ;      (beforeo (expiry-date m) (today))
  ;      (conso "")
  ;      )

  (defn extract [fields s]
    (filter #(re-find (re-pattern (str/join "|" fields)) %) (str/split-lines s)))

  (defn msgs-from-query [query db]
    (let [query-obj (nm/notmuch_query_create db query)
          msgs-obj (nm-query/search-messages query-obj)]
      (loop []
        (when (nm-msgs/valid msgs-obj)
          (let [msg (nm-msgs/get msgs-obj)]
            (println (nm-msg/get-header msg "Subject"))
            (treat-message msg))
          (nm-msgs/move-to-next msgs-obj)
          (recur)))
      (nm-query/destroy query-obj)))

(comment

  (nm/notmuch_database_create "/home/erle/mail/kjellberg" db-pointer)

  (def db-pointer (PointerByReference.))
  (nm/notmuch_database_open "/home/erle/mail" (mode :read-only) db-pointer)
  (def db (.getValue db-pointer))

  (msgs-from-query "tag:2w" db)

  (def msg-pointer (PointerByReference.))
  (nm/notmuch_database_find_message db "20150305075606.GB2120@localhost.zedat.fu-berlin.de" msg-pointer)
  (def msg (.getValue msg-pointer))

  (def query (notmuch_query_create db "tag:deleted"))

  (nm/notmuch_message_get_filename msg)

  (nm/notmuch_message_get_header msg "From")

  (get-tags msg)

  (nm/notmuch_database_get_path (.getValue db-pointer))

  (nm/notmuch_database_get_version (.getValue db-pointer))

  (nm/notmuch_database_close (.getValue db-pointer))

  )
