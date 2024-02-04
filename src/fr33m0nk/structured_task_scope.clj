(ns fr33m0nk.structured-task-scope
  (:import
    [java.time Instant]
    [java.util.concurrent
     StructuredTaskScope$Subtask StructuredTaskScope$Subtask$State
     StructuredTaskScope StructuredTaskScope$ShutdownOnSuccess
     StructuredTaskScope$ShutdownOnFailure]))


(defmacro forker
  [structured-task-scope & body]
  `(.fork ~structured-task-scope (fn [] ~@body)))


(defmacro with-shutdown-on-success
  {:arglists '([scope opts & body])}
  [scope opts & body]
  (let [{:keys [deadline-instant]} opts]
    `(with-open [scope# (StructuredTaskScope$ShutdownOnSuccess.)]
       (let [~scope scope#]
         ~@body
         (if ~deadline-instant
           (.joinUntil scope# ~deadline-instant)
           (.join scope#))
         (.result scope#)))))


(defmacro with-shutdown-on-failure
  {:arglists '([scope fork-task-bindings opts & body])}
  [scope fork-task-bindings opts & body]
  (let [{:keys [throw-on-failure? deadline-instant]} opts]
    `(with-open [scope# (StructuredTaskScope$ShutdownOnFailure.)]
       (let [~scope scope#
             ~@fork-task-bindings]
         (if ~deadline-instant
           (.joinUntil scope# ~deadline-instant)
           (.join scope#))
         (when ~throw-on-failure?
           (.throwIfFailed scope#))
         ~@body))))


(defn ->structured-scope
  ^StructuredTaskScope
  [success-handler error-handler]
  (proxy [StructuredTaskScope IFn] []
    (handleComplete [^StructuredTaskScope$Subtask subtask]
      (condp = (.state subtask)
        StructuredTaskScope$Subtask$State/UNAVAILABLE (throw (IllegalArgumentException. "SubTask is unavailable"))
        StructuredTaskScope$Subtask$State/FAILED (error-handler (.exception subtask))
        StructuredTaskScope$Subtask$State/SUCCESS (success-handler (.get subtask))))))
