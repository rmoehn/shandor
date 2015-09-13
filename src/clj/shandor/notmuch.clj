(ns shandor.notmuch
  (:require [clojure.string :as str]
            [net.n01se.clojure-jna :as jna])
  (:import [com.sun.jna.ptr PointerByReference]
           [com.sun.jna Pointer]))

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
                       Pointer notmuch_message_get_filenames,
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
                       String notmuch_filenames_get
                       Void notmuch_filenames_move_to_next
                       Void notmuch_filenames_destroy
                       ])

(ns-comfort 'nm
            (fn [s] (-> s
                        (str/replace #"_" "-")
                        (str/replace-first #"notmuch-" "")))
            {"database-" 'nm.db
             "filenames-" 'nm.filenames
             "message-" 'nm.msg
             "messages-" 'nm.msgs
             "tags-" 'nm.tags
             "query-" 'nm.query})

