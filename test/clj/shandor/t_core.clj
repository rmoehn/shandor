(ns shandor.t-core
  (:require [clojure.test :refer [deftest is are]]
            [joda-time :as jt]
            [shandor.core :as nut]))

;;; Ideas for generative tests:
;;;
;;;  - The obvious things.
;;;
;;;  - (= {}
;;;       (new-tag-map (jt/plus adate (jt/days x))
;;;                    (merge atagmap (new-tag-map adate atagmap))))
;;;    I.e., applying new-tag-map again, even on a later date, shouldn't make a
;;;    difference.

(deftest assoc-nc
  (are [m k v expected] (= expected (@#'nut/assoc-nc m k v))
       {} :x 5 {:x 5}
       {:m "klj"} :m "klj" {:m "klj"}
       {:kj 4} "lkj" "43" {"lkj" "43" :kj 4})

  (is (= {:a 1 :b 2 :c 3} (@#'nut/assoc-nc {:a 1} :b 2 :c 3)))
  (is (= {:a 1 :b 2 :c 3} (@#'nut/assoc-nc {} :a 1 :b 2 :c 3)))

  (is (thrown? IllegalArgumentException (@#'nut/assoc-nc {:a 4} :a 5)))
  (is (thrown? IllegalArgumentException (@#'nut/assoc-nc {:a 4} :b 5 :b 6))))


(deftest new-tag-map
  (are [tags-in expected] (= expected (nut/new-tags-map
                                        (jt/local-date "2015-06-12")
                                        tags-in))
       {} {}
       {:atag :atag} {}

       {:ttl (jt/years 1)
        :exp (jt/local-date "2015-06-11")}
       {:deleted :deleted
        :rem (jt/local-date "2015-06-26")}

       {:ttl (jt/years 1)
        :exp (jt/local-date "2015-06-11")
        :deleted :deleted
        :rem (jt/local-date "2015-06-26")}
       {}

       {:deleted :deleted}
       {:rem (jt/local-date "2015-06-26")}

       {:deleted :deleted :rem (jt/local-date "2015-06-26")}
       {}

       {:ttl (jt/weeks 2)} {:exp (jt/local-date "2015-06-26")}

       {:ttj (jt/months 6)} {:jdy (jt/local-date "2015-12-12")}

       {:ttj (jt/days 7)
        :jdy (jt/local-date "2015-05-01")}
       {:judge :judge}

       {:ttj (jt/days 7)
        :jdy (jt/local-date "2015-05-01")
        :judge :judge}
       {}))


(deftest action
  (are [tags-in expected] (= expected (nut/action
                                        (jt/local-date "2015-06-12")
                                        tags-in))

       {} [:nop]

       {:ttj (jt/days 7)
        :jdy (jt/local-date "2015-05-01")
        :judge :judge}
       [:nop]

       {"O43" "O43"} [:nop]

       {:ttl (jt/years 1)
        :exp (jt/local-date "2015-06-11")}
       [:add-tags {:deleted :deleted
                   :rem (jt/local-date "2015-06-26")}]

       {:ttl (jt/years 1)
        :exp (jt/local-date "2015-06-11")
        :deleted :deleted
        :rem (jt/local-date "2015-06-26")}
       [:nop]

       {:ttl (jt/years 1)
        :exp (jt/local-date "2015-05-11")
        :deleted :deleted
        :rem (jt/local-date "2015-05-26")}
       [:remove]

       {:deleted :deleted
        :rem (jt/local-date "2015-05-26")}
       [:remove]))
