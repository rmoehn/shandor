(ns user
  "Tools for interactive development with the REPL. This file should
  not be included in a production build of the application."
  (:require [clojure.java.io :as io]
            [clojure.java.javadoc :refer [javadoc]]
            [clojure.pprint :refer [pprint]]
            [clojure.repl :refer [apropos dir doc find-doc pst source]]
            [clojure.string :as str]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [clojure.instant :as inst]
            [joda-time :as jt]
            [net.n01se.clojure-jna :as jna]
            [shandor])
  (:import [com.sun.jna.ptr PointerByReference]
           [com.sun.jna Pointer]))

;;; Note: We won't be threadsafe, although notmuch enables thread-safety.

;;;; My first macro

;; Credits: http://clj-me.cgrand.net/2011/06/17/a-flatter-cond/
(defmacro cond-let [& clauses]
  (when-let [[t e & cs] (seq clauses)]
    (if (vector? t)
      `(if-let ~t ~e (cond-let ~@cs))
      `(if ~t ~e (cond-let ~@cs)))))

;;;; Function for making namespace provided by clojure-jna easier to use

(defn ns-comfort [nmsp common-fn pref->ns-sym]
  (doseq [s (vals pref->ns-sym)]
    (remove-ns s)
    (create-ns s))
  (doseq [[raw-s v] (ns-interns nmsp)]
    (let [s (common-fn (str raw-s))
          pref (some #(re-find (re-pattern %) s) (keys pref->ns-sym))
          new-sym (symbol (str/replace-first s (re-pattern pref) ""))]
      (intern (pref->ns-sym pref) new-sym v)))
  (for [s (vals pref->ns-sym)]
    [s (ns-interns s)]))

;;;; Interfacing with notmuch C library

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

(ns-comfort 'nm
            (fn [s] (-> s
                        (str/replace #"_" "-")
                        (str/replace-first #"notmuch-" "")))
            {"database-" 'nm-db
             "message-" 'nm-msg
             "messages-" 'nm-msgs
             "tags-" 'nm-t
             "query-" 'nm-query})

;;;; More Clojure-y wrappers for notmuch functions

(def mode {:read-only 0
           :read-write 1})

(defn get-tags [msg]
  (let [tags-obj (nm/notmuch_message_get_tags msg)
        res (loop [tags-vec []]
              (if-some [tag (nm/notmuch_tags_get tags-obj)]
                       (do (nm/notmuch_tags_move_to_next tags-obj)
                         (recur (conj tags-vec tag)))
                       tags-vec))]
    (nm/notmuch_tags_destroy tags-obj)
    res))

(defn add-tags! [msg tags]
  (doseq [tag tags]
    (nm-msg/add-tag msg tag)))

;(defn add-tags! [msg tags]
  ;(println "Would add " tags))

(defn remove-message! [msg]
  (println "Would remove msg" msg))

;;;; Mapping back and forth between tags and maps

(def ^:private period-constr {"d" jt/days
                              "w" jt/weeks
                              "m" jt/months
                              "y" jt/years})

(defn tag->map [t]
  (cond-let
    [[_ cmd date] (re-matches #"(REM|DEL)_(.*)" t)]
    [(keyword (str/lower-case cmd)) (jt/local-date date)]

    [[_ cnt unit] (re-matches #"(\d+)(\w)" t)]
    [:ttl ((period-constr unit) (read-string cnt))]

    (= "deleted" t)
    [:deleted :deleted]

    :else
    [t t]))

(defn tags->map [tags]
 (into {} (map tag->map (set tags))))

(defn map-entry->tag [t]
  (case (key t)
    (:rem :exp) (str (-> t key name str/upper-case) "_" (jt/print (val t)))
    :deleted "deleted"
    nil))

(defn map->tags [ts]
  (filter some? (map map-entry->tag ts)))

;;;; Rules for generating new tags map from tags map

(defn new-tags-map [tags]
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

;;;; Going through all messages and doing what needs to be done

(defn treat-message [msg]
  (let [tags (tags->map (get-tags msg))
        nt (new-tags-map tags)]
    (if (= nt :remove)
      (remove-message! msg)
      (add-tags! msg (map->tags nt)))))

(defn treat-messages [query db]
  (let [query-obj (nm-query/create db query)
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
  (nm/notmuch_database_open "/home/erle/mail" (mode :read-write) db-pointer)
  (def db (.getValue db-pointer))
  (treat-messages "Videos in den Medienwissenschaften" db)
  (nm/notmuch_database_close (.getValue db-pointer))

  (def msg-pointer (PointerByReference.))
  (nm/notmuch_database_find_message db "20150305075606.GB2120@localhost.zedat.fu-berlin.de" msg-pointer)
  (def msg (.getValue msg-pointer))

  (def query (notmuch_query_create db "tag:deleted"))

  (nm/notmuch_message_get_filename msg)

  (nm/notmuch_message_get_header msg "From")

  (get-tags msg)

  (nm/notmuch_database_get_path (.getValue db-pointer))

  (nm/notmuch_database_get_version (.getValue db-pointer))


  )
