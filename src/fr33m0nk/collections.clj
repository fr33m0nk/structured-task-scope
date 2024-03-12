(ns fr33m0nk.collections
  (:import (clojure.lang LongRange)
           (java.util.concurrent StructuredTaskScope$ShutdownOnFailure)))

(defn- empty-coll
  [coll]
  (if (instance? LongRange coll)
    []
    (empty coll)))

(defn keep-parallel
  ([predicate? mapper coll]
   (with-open [scope (StructuredTaskScope$ShutdownOnFailure.)]
     (let [subtasks (into [] (map (fn [item] (.fork scope (fn [] (when (predicate? item) (mapper item)))))) coll)]
       (.join scope)
       (.throwIfFailed scope)
       (into (empty-coll coll)
             (keep (fn [subtask] (when-let [result (.get subtask)] result)))
             subtasks))))
  ([predicate? mapper coll parallel-limit]
   (with-open [scope (StructuredTaskScope$ShutdownOnFailure.)]
     (into (empty-coll coll)
           (comp
             (partition-all parallel-limit)
             (map (fn [batch]
                    (let [subtasks (into []
                                      (map (fn [item]
                                                 (.fork scope (fn [] (when (predicate? item) (mapper item))))))
                                      batch)]
                      (.join scope)
                      (.throwIfFailed scope)
                      (into (empty-coll batch) (keep (fn [subtask] (when-let [result (.get subtask)] result))) subtasks))))
             cat)
           coll))))

(defn map-parallel
  ([mapper coll]
   (keep-parallel (constantly true) mapper coll))
  ([mapper coll parallel-limit]
   (keep-parallel (constantly true) mapper coll parallel-limit)))

(defn filter-parallel
  ([predicate coll]
   (keep-parallel predicate identity coll))
  ([predicate coll parallel-limit]
   (keep-parallel predicate identity coll parallel-limit)))

(defn run-parallel!
  ([f coll]
   (with-open [scope (StructuredTaskScope$ShutdownOnFailure.)]
     (run! (fn [item] (.fork scope (fn [] (f item)))) coll)
     (.join scope)
     (.throwIfFailed scope)))
  ([f coll parallel-limit]
   (with-open [scope (StructuredTaskScope$ShutdownOnFailure.)]
     (->> coll
          (partition-all parallel-limit)
          (run! (fn [batch]
                  (run! (fn [item] (.fork scope (fn [] (f item)))) batch)
                  (.join scope)
                  (.throwIfFailed scope)))))))
