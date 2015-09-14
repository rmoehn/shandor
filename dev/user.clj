(ns user
  (:require [clojure.java.javadoc :refer [javadoc]]
            [clojure.pprint :refer [pprint]]
            [clojure.repl :refer [apropos dir doc find-doc pst source]]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [shandor.core])
  (:import [com.sun.jna.ptr PointerByReference]))

(comment

  (shandor.core/-main "/home/erle/mail")

  )
