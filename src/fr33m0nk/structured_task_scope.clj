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

(defmacro shutdown-on-success
  {:arglists '([scope & body] [scope deadline-instant & body])}
  [scope x & body]
  `(with-open [scope# (StructuredTaskScope$ShutdownOnSuccess.)]
     (let [~scope scope#]
       (if (instance? Instant ~x)
         (do
           ~@body
           (.joinUntil scope# ~x))
         (do
           ~x
           ~@body
           (.join scope#)))
       (.result scope#))))

(defmacro shutdown-on-failure
  {:arglists '([scope fork-task-bindings opts & body])}
  [scope fork-task-bindings opts & body]
  (let [{:keys [throw-on-failure? deadline-instant]} opts]
    `(with-open [scope# (StructuredTaskScope$ShutdownOnFailure.)]
       (let [~scope scope#
             ~@fork-task-bindings]
         (if ~deadline-instant
           (do
             (.joinUntil scope# ~deadline-instant)
             (when ~throw-on-failure?
               (.throwIfFailed scope#))
             ~@body)
           (do
             (.join scope#)
             (when ~throw-on-failure?
               (.throwIfFailed scope#))
             ~@body))))))

(defn ->structured-scope
  ^StructuredTaskScope
  [success-handler error-handler]
  (proxy [StructuredTaskScope IFn] []
    (handleComplete [^StructuredTaskScope$Subtask subtask]
      (condp = (.state subtask)
        StructuredTaskScope$Subtask$State/UNAVAILABLE (throw (IllegalArgumentException. "SubTask is unavailable"))
        StructuredTaskScope$Subtask$State/FAILED (error-handler (.exception subtask))
        StructuredTaskScope$Subtask$State/SUCCESS (success-handler (.get subtask))))))