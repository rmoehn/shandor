(ns shandor.core
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [joda-time :as jt]
            [plumbing.fnk.schema :refer [assert-iae]]
            [shandor.notmuch])
  (:import [com.sun.jna.ptr PointerByReference])
  (:gen-class))

;;; Note: We won't be threadsafe, although notmuch enables thread-safety.

;;;; My first macro

;; Credits: http://clj-me.cgrand.net/2011/06/17/a-flatter-cond/
(defmacro cond-let [& clauses]
  (when-let [[t e & cs] (seq clauses)]
    (if (vector? t)
      `(if-let ~t ~e (cond-let ~@cs))
      `(if ~t ~e (cond-let ~@cs)))))


;;;; Generally helpful functions

(defn- assoc-nc [a-map & kvs]
  (letfn [(assoc-nc-one [m [k v]]
            (if (and (contains? m k) (not= v (m k)))
              (throw (IllegalArgumentException.
                       (str "Won't assoc " k " with " v ", because map already"
                            " maps that key to a different value.")))
              (assoc m k v)))]
    (reduce assoc-nc-one a-map (partition 2 kvs))))


;;;; More Clojure-y wrappers for notmuch functions

(def mode {:read-only 0
           :read-write 1})

(defn iterator-converter
  [get-iterator destroy-iterator get-item move-iterator]
  (fn iterator-converter-fn [obj]
   (let [iterator (get-iterator obj)
         res (loop [items-vec []]
               (if-some [item (get-item iterator)]
                 (do
                   (move-iterator iterator)
                   (recur (conj items-vec item)))
                 items-vec))]
     (destroy-iterator iterator)
     res)))

;; Note: This and the following are a bit silly, but because of AOT compilation,
;;       a simple (def get-tags (iterator-convert …)) doesn't work. I don't
;;       understand fully, why this is the case. Especially not, why
;;       clojure.jna/to-ns works, but shandor.notmuch/ns-comfort doesn't. Yes,
;;       the former is a macro and the latter a fn. But the work done at compile
;;       time and at runtime is the same, as far as I see it. Anyway, this isn't
;;       important enough to investigate deeply.
;; TODO: Correct the problem the above Note describes. (RM 2015-09-15)
(defn get-tags [msg]
  ((iterator-converter nm.msg/get-tags
                       nm.tags/destroy
                       nm.tags/get
                       nm.tags/move-to-next)
   msg))

(defn get-filenames [msg]
  ((iterator-converter nm.msg/get-filenames
                      nm.filenames/destroy
                      nm.filenames/get
                      nm.filenames/move-to-next)
   msg))

(defn add-tags! [msg tags]
  (doseq [tag tags]
    (nm.msg/add-tag msg tag)))

;; Removing from the database doesn't work for some reason. Therefore we rely on
;; notmuch new to delete the entries for messages deleted from disk.
;; TODO: Figure out what is wrong with removing. (RM 2015-09-15)
(defn remove-message! [msg]
  (doseq [fnm (get-filenames msg)]
    (io/delete-file (io/file fnm))))


;;;; Logging

(defn log-begin [premap]
  (print (str "X-Shandor-Begin: " (jt/local-date-time) \newline
              (if (seq premap)
                (str "X-Shandor-Premap: " premap \newline)
                ""))))

(defn log-end []
  (print (str "X-Shandor-End: " (jt/local-date-time) \newline \newline)))

(defn- get-and-format-header [msg hdr-k]
  (let [hdr-v (nm.msg/get-header msg hdr-k)]
    (if (empty? hdr-v)
      ""
      (str hdr-k ": " hdr-v \newline))))

(defn- format-msg [msg]
  (str
    (str/join (map #(get-and-format-header msg %)
                   ["Message-ID" "Date" "From" "To" "Cc" "Bcc" "Subject"]))
    "X-Notmuch-Tags: " (get-tags msg) \newline))

(defn log-msg [msg [action _ :as act-all]]
  (when-not (= :nop action)
    (print (str (format-msg msg)
                "X-Shandor-Action: " act-all \newline
                \newline))))


;;;; Mapping back and forth between tags and maps

(def ^:private period-constr {"d" jt/days
                              "w" jt/weeks
                              "m" jt/months
                              "y" jt/years})

(defn tag->map [t]
  (cond-let
    [[_ cmd date] (re-matches #"(REM|EXP|JDY)_(.*)" t)]
    [(keyword (str/lower-case cmd)) (jt/local-date date)]

    [[_ cnt unit] (re-matches #"(\d+)(\w)" t)]
    [:ttl ((period-constr unit) (read-string cnt))]

    [[_ cnt unit] (re-matches #"j(\d+)(\w)" t)]
    [:ttj ((period-constr unit) (read-string cnt))]

    (contains? #{"deleted" "judge"} t)
    [(keyword t) (keyword t)]

    :else
    [t t]))

(defn tags->map [premap tags]
 (into {} (map tag->map
               (map #(get premap % %) tags))))

(defn map-entry->tag [t]
  (case (key t)
    (:rem :exp :jdy) (str (-> t key name str/upper-case) "_" (jt/print (val t)))
    (:deleted :judge) (name (key t))
    nil))

(defn map->tags [ts]
  (filter some? (map map-entry->tag ts)))


;;;; Rules for what to do with a message according to its tags

(defn new-tags-map [today tags]
  (cond-> {}
    (and (not (contains? tags :rem)) (contains? tags :deleted))
    (assoc-nc :rem (jt/plus today (jt/weeks 2)))

    (and (not (contains? tags :exp)) (contains? tags :ttl))
    (assoc-nc :exp (jt/plus today (tags :ttl)))

    (and (contains? tags :exp)
         (not (contains? tags :rem))
         (jt/before? (tags :exp) today))
    (assoc-nc :deleted :deleted
              :rem (jt/plus today (jt/weeks 2)))

    (and (not (contains? tags :jdy)) (contains? tags :ttj))
    (assoc-nc :jdy (jt/plus today (tags :ttj)))

    (and (contains? tags :jdy)
         (not (contains? tags :judge))
         (jt/before? (tags :jdy) today))
    (assoc-nc :judge :judge)))

(defn action
  "

  The action is either :add-tags or :remove."
  [today tags]
  (if (and (contains? tags :rem) (jt/before? (tags :rem) today))
    [:remove]
    (let [ntm (new-tags-map today tags)]
      (if (seq ntm)
        [:add-tags ntm]
        [:nop]))))


;;;; Going through all messages and doing what needs to be done

(defn treat-message [premap msg]
  (let [tags (tags->map premap (get-tags msg))
        [the-action act-args :as act-all] (action (jt/local-date) tags)]
    (log-msg msg act-all)
    (case the-action
      :remove
      (remove-message! msg)

      ; I couldn't find out exactly, but I'm pretty sure that
      ; notmuch_message_add_tags is idempotent (in the procedural sense).
      ; Therefore, we don't have to care whether a message already has a tag or
      ; not.
      :add-tags
      (add-tags! msg (map->tags act-args))

      :nop
      nil)))

(defn treat-messages [premap query db]
  (let [query-obj (nm.query/create db query)
        msgs-obj (nm.query/search-messages query-obj)]
    (loop []
      (when (nm.msgs/valid msgs-obj)
        (let [msg (nm.msgs/get msgs-obj)]
          (treat-message premap msg))
        (nm.msgs/move-to-next msgs-obj)
        (recur)))
    (nm.query/destroy query-obj))) ; Also destroys msgs-obj.


;;;; Entry point

(defn -main
  "

  Note that you should run notmuch new after every execution of this procedure."
  [& [db-path premap-fnm]]
  (assert db-path "You have to provide the path to the notmuch database.")
  (let [premap (if premap-fnm
                 (edn/read-string (slurp premap-fnm))
                 {})
        db-pointer (PointerByReference.)
        _ (nm.db/open db-path (mode :read-write) db-pointer)
        db (.getValue db-pointer)]
    (log-begin premap)
    (treat-messages premap "*" db)
    (log-end) ; Here, because I don't want errors closing the DB to interfere.
    (nm.db/close (.getValue db-pointer))))
