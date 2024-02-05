(ns fr33m0nk.scoped-value-test
  (:require [clojure.test :refer :all]
            [fr33m0nk.scoped-value :as sv]))

(deftest run-where!-test
  (testing "binds value to scoped-value instance and maintains thread level isolation"
    (let [scoped-value (sv/->scoped-value)
          result (atom [])
          executing-fn (fn []
                         (let [processed-string (str (sv/deref scoped-value) " Ok")]
                           (swap! result conj processed-string)
                           (println processed-string)))
          futures (doall (pmap #(future
                                  (sv/run-where! scoped-value (str "Hello - " %) executing-fn)) (range 3)))]

      (is (every? nil? (map deref futures)))
      (is (every? #{"Hello - 1 Ok" "Hello - 0 Ok" "Hello - 2 Ok"} @result))
      (is (false? (sv/bound? scoped-value))))))

(deftest apply-where-test
  (testing "binds value to scoped-value instance and maintains thread level isolation"
    (testing "binds value to scoped-value instance and maintains thread level isolation"
      (let [scoped-value (sv/->scoped-value)
            executing-fn (fn [] (str (sv/deref scoped-value) " Ok"))
            futures (doall (pmap #(future
                                    (sv/apply-where scoped-value (str "Hello - " %) executing-fn)) (range 3)))]
        (is (every? #{"Hello - 1 Ok" "Hello - 0 Ok" "Hello - 2 Ok"} (map deref futures)))
        (is (false? (sv/bound? scoped-value)))))))
