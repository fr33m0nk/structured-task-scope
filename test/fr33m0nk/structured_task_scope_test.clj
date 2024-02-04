(ns fr33m0nk.structured-task-scope-test
  (:require [clojure.test :refer :all]
            [fr33m0nk.structured-task-scope :as sts])
  (:import [java.time Instant]
           [java.util.concurrent TimeoutException]))

(deftest shutdown-on-failure-test
  (testing "returns exception if :throw-on-failure? is set in options"
    (is (instance? Exception (sts/with-shutdown-on-failure
                               scope
                               [turtle (sts/fork-task scope (Thread/sleep 5000) :turtle-wins)
                                hare (sts/fork-task scope (throw (ex-info "boom" {})))]
                               {}
                               (.exception hare)))))

  (testing "throws exception if :throw-on-failure? is set in options"
    (is (thrown? Exception (sts/with-shutdown-on-failure
                             scope
                             [turtle (sts/fork-task scope (Thread/sleep 5000) :turtle-wins)
                              hare (sts/fork-task scope (throw (ex-info "boom" {})))]
                             {:throw-on-failure? true}
                             (map #(name (.get %)) [turtle hare])))))

  (testing "throws exception if :deadline-instant is set in options and it elapses"
    (is (thrown? TimeoutException (sts/with-shutdown-on-failure
                                    scope
                                    [turtle (sts/fork-task scope (Thread/sleep 5000) :turtle-wins)
                                     hare (sts/fork-task scope (Thread/sleep 5000) :hare-wins)]
                                    {:deadline-instant (.plusMillis (Instant/now) 2000)}
                                    (map #(name (.get %)) [turtle hare])))))

  (testing "forks subtasks mentioned in forked bindings that can be used in body"
    (is (= ["turtle-wins" "hare-wins"]
           (sts/with-shutdown-on-failure
             scope
             [turtle (sts/fork-task scope (Thread/sleep 5000) :turtle-wins)
              hare (sts/fork-task scope (Thread/sleep 5000) :hare-wins)]
             {:throw-on-failure? true
              :deadline-instant (.plusMillis (Instant/now) 7000)}
             (map #(name (.get %)) [turtle hare]))))))

(deftest shutdown-on-success-test
  (testing "shuts down the scope and returns the result when any of the forked tasks finish successfully.
  By its design StructuredTaskScope$ShutdownOnSuccess ignores any exception in forked tasks"
    (is (= :mouse-wins
           (sts/with-shutdown-on-success
             scope
             {}
             (sts/fork-task scope (Thread/sleep 5000) :turtle-wins)
             (sts/fork-task scope (Thread/sleep 5000) :hare-wins)
             (sts/fork-task scope (Thread/sleep 2000) :mouse-wins)))))

  (testing "throws exception if :deadline-instant is set in options and it elapses"
    (is (thrown? TimeoutException
                 (sts/with-shutdown-on-success
                   scope
                   {:deadline-instant (.plusMillis (Instant/now) 2000)}
                   (sts/fork-task scope (Thread/sleep 5000) :turtle-wins)
                   (sts/fork-task scope (Thread/sleep 5000) :hare-wins))))))
