(ns user
  (:require [clojure.java.javadoc :refer [javadoc]]
            [clojure.pprint :refer [pprint]]
            [clojure.repl :refer [apropos dir doc find-doc pst source]]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [clojure.test :refer [run-tests]]
            [shandor.t-core]
            [shandor.core])
  (:import [com.sun.jna.ptr PointerByReference]))

(comment

  (refresh)
  (shandor.core/-main "/home/erle/mail" "/home/erle/mail/shandor-premap.edn"  )

  (refresh)
  (run-tests 'shandor.t-core)

  )
