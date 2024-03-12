(ns fr33m0nk.collections
  (:require [fr33m0nk.scoped-value :as sv])
  (:import (clojure.lang LongRange)
           (java.util.concurrent Semaphore StructuredTaskScope$ShutdownOnFailure)))

(def ^:private parallelism (sv/->scoped-value))

(defn- empty-coll
  [coll]
  (if (instance? LongRange coll)
    []
    (empty coll)))

(defn keep-parallel
  ([predicate mapper coll]
   (with-open [scope (StructuredTaskScope$ShutdownOnFailure.)]
     (let [subtasks (into [] (map (fn [item] (.fork scope (fn [] (when (predicate item) (mapper item)))))) coll)
           init-coll (empty-coll coll)]
       (.join scope)
       (.throwIfFailed scope)
       (into init-coll (keep (fn [subtask] (when-let [result (.get subtask)] result))) subtasks))))
  ([predicate mapper coll parallel-limit]
   (sv/apply-where parallelism (Semaphore. parallel-limit)
                   (fn []
                     (with-open [scope (StructuredTaskScope$ShutdownOnFailure.)]
                       (let [^Semaphore semaphore (sv/deref parallelism)
                             subtasks (into []
                                            (map (fn [item]
                                                   (.fork scope (fn []
                                                                  (try
                                                                    (.acquire semaphore)
                                                                    (when (predicate item) (mapper item))
                                                                    (finally
                                                                      (.release semaphore)))))))
                                            coll)
                             init-coll (empty-coll coll)]
                         (.join scope)
                         (.throwIfFailed scope)
                         (into init-coll (keep (fn [subtask] (when-let [result (.get subtask)] result))) subtasks)))))))

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
   (sv/apply-where parallelism (Semaphore. parallel-limit)
                   (fn []
                     (with-open [scope (StructuredTaskScope$ShutdownOnFailure.)]
                       (let [^Semaphore semaphore (sv/deref parallelism)]
                         (run! (fn [item] (.fork scope (fn []
                                                         (try
                                                           (.acquire semaphore)
                                                           (f item)
                                                           (finally
                                                             (.release semaphore)))))) coll)
                         (.join scope)
                         (.throwIfFailed scope)))))))
