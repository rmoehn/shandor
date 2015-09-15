(ns shandor.core
  (:require [clojure.java.io :as io]
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
(defn remove-message! [msg]
  (doseq [fnm (get-filenames msg)]
    (io/delete-file (io/file fnm))))

;;;; Mapping back and forth between tags and maps

(def ^:private period-constr {"d" jt/days
                              "w" jt/weeks
                              "m" jt/months
                              "y" jt/years})

(defn tag->map [t]
  (cond-let
    [[_ cmd date] (re-matches #"(REM|EXP)_(.*)" t)]
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

(defn new-tags-map [today tags]
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
    {}))

;;;; Going through all messages and doing what needs to be done

(defn treat-message [msg]
  (let [tags (tags->map (get-tags msg))
        nt (new-tags-map (jt/local-date) tags)]
    (when (or (keyword? nt) (seq nt))
      (println ">>>" (nm.msg/get-header msg "Subject"))
      (println tags)
      (println nt))
    (if (= nt :remove)
      (remove-message! msg)
      (add-tags! msg (map->tags nt)))))

(defn treat-messages [query db]
  (let [query-obj (nm.query/create db query)
        msgs-obj (nm.query/search-messages query-obj)]
    (loop []
      (when (nm.msgs/valid msgs-obj)
        (let [msg (nm.msgs/get msgs-obj)]
          (treat-message msg))
        (nm.msgs/move-to-next msgs-obj)
        (recur)))
    (nm.query/destroy query-obj)))


;;;; Entry point

(defn -main [& [db-path]]
  (let [db-pointer (PointerByReference.)
        _ (nm/notmuch_database_open db-path
                                    (mode :read-write)
                                    db-pointer)
        db (.getValue db-pointer)]
    (treat-messages "*" db)
    (nm/notmuch_database_close (.getValue db-pointer))))
