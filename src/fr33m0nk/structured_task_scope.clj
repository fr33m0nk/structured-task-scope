(ns fr33m0nk.structured-task-scope
  (:import
    [java.time Instant]
    [java.util.concurrent
     StructuredTaskScope$Subtask StructuredTaskScope$Subtask$State
     StructuredTaskScope StructuredTaskScope$ShutdownOnSuccess
     StructuredTaskScope$ShutdownOnFailure]))


(defmacro fork-task
  [structured-task-scope & body]
  `(.fork ~structured-task-scope (fn [] ~@body)))


(defmacro with-shutdown-on-success
  "Parameters:
  scope: Structured task scope is created and bound to this symbol by the macro
  opts: a map of following options
    :deadline-instant -> expects an instance of java.time.Instant
  "
  {:arglists '([scope {:keys [deadline-instant]} & body])}
  [scope {:keys [deadline-instant]} & body]
  `(with-open [scope# (StructuredTaskScope$ShutdownOnSuccess.)]
     (let [~scope scope#]
       ~@body
       (if ~deadline-instant
         (.joinUntil scope# ~deadline-instant)
         (.join scope#))
       (.result scope#))))


(defmacro with-shutdown-on-failure
  "Parameters:
  scope: Structured task scope is created and bound to this symbol by the macro
  fork-task-bindings: These bindings should only be of the forked task as currently the macro isn't very intelligent
  opts: a map of following options
    :throw-on-failure? -> expects a boolean
    :deadline-instant -> expects an instance of java.time.Instant
  "
  {:arglists '([scope fork-task-bindings {:keys [throw-on-failure? deadline-instant]} & body])}
  [scope fork-task-bindings {:keys [throw-on-failure? deadline-instant]} & body]
  `(with-open [scope# (StructuredTaskScope$ShutdownOnFailure.)]
     (let [~scope scope#
           ~@fork-task-bindings]
       (if ~deadline-instant
         (.joinUntil scope# ~deadline-instant)
         (.join scope#))
       (when ~throw-on-failure?
         (.throwIfFailed scope#))
       ~@body)))


(defn ->structured-scope
  ^StructuredTaskScope
  [success-handler error-handler]
  (proxy [StructuredTaskScope] []
    (handleComplete [^StructuredTaskScope$Subtask subtask]
      (condp = (.state subtask)
        StructuredTaskScope$Subtask$State/UNAVAILABLE (throw (IllegalArgumentException. "SubTask is unavailable"))
        StructuredTaskScope$Subtask$State/FAILED (error-handler (.exception subtask))
        StructuredTaskScope$Subtask$State/SUCCESS (success-handler (.get subtask))))))
