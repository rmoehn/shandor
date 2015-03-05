(ns user
  "Tools for interactive development with the REPL. This file should
  not be included in a production build of the application."
  (:require [clojure.java.io :as io]
            [clojure.java.javadoc :refer [javadoc]]
            [clojure.pprint :refer [pprint]]
            [clojure.reflect :refer [reflect]]
            [clojure.repl :refer [apropos dir doc find-doc pst source]]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.test :as test]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [net.n01se.clojure-jna :as jna]
            [shandor])
  (:import [com.sun.jna.ptr PointerByReference]
           [com.sun.jna Pointer]
           ))

(jna/to-ns nm notmuch [Integer notmuch_database_create,
                       Integer notmuch_database_open,
                       Integer notmuch_database_close,
                       String notmuch_database_get_path,
                       Integer notmuch_database_get_version,
                       Integer notmuch_database_find_message,
                       String notmuch_message_get_filename,
                       Pointer notmuch_message_get_tags,
                       Boolean notmuch_tags_valid,
                       Void notmuch_tags_move_to_next,
                       String notmuch_tags_get,
                       Void notmuch_tags_destroy
                       ])

(def mode {:read-only 0})

(defn tags-from-msg [msg]
  (let [tags-obj (nm/notmuch_message_get_tags msg)
        res (loop [tags-vec []]
              (if-some [tag (nm/notmuch_tags_get tags-obj)]
                       (do (nm/notmuch_tags_move_to_next tags-obj)
                         (recur (conj tags-vec tag)))
                       tags-vec))]
    (nm/notmuch_tags_destroy tags-obj)
    res))

(comment

  (nm/notmuch_database_create "/home/erle/mail/kjellberg" db-pointer)

  (def db-pointer (PointerByReference.))
  (nm/notmuch_database_open "/home/erle/mail" (mode :read-only) db-pointer)
  (def db (.getValue db-pointer))

  (def msg-pointer (PointerByReference.))
  (nm/notmuch_database_find_message db "20150305075606.GB2120@localhost.zedat.fu-berlin.de" msg-pointer)
  (def msg (.getValue msg-pointer))

  (nm/notmuch_message_get_filename msg)

  (tags-from-msg msg)

  (nm/notmuch_database_get_path (.getValue db-pointer))

  (nm/notmuch_database_get_version (.getValue db-pointer))

  (nm/notmuch_database_close (.getValue db-pointer))

  )
