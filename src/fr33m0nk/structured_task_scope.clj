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

(defn- should-be [p msg form]
  (when-not p
    (let [line (:line (meta form))
          msg (format "%s requires %s in %s:%s" (first form) msg *ns* line)]
      (throw (IllegalArgumentException. msg)))))

(defn- let-fork-subtask-macro-helper
  [^StructuredTaskScope scope & body]
  `(.fork ^StructuredTaskScope ~scope (fn [] ~@body)))

(defn- let-fork-value-from-subtask-macro-helper
  [^StructuredTaskScope$Subtask subtask]
  `(.get ^StructuredTaskScope$Subtask ~subtask))

(defmacro let-fork
  "Evaluates bindings in parallel and returns the result of
  evaluating body in the context of those bindings. Bindings
  have to be independent of each other."
  [bindings & body]
  (should-be (vector? bindings) "a vector for its bindings" &form)
  (should-be (even? (count bindings)) "an even number of forms in bindings" &form)
  (let [binding-syms (take-nth 2 bindings)
        expressions (take-nth 2 (rest bindings))
        binding-gensyms (take (count binding-syms) (repeatedly gensym))
        task-scope (gensym)]
    `(with-open [~task-scope (StructuredTaskScope$ShutdownOnFailure.)]
       (let ~(vec (interleave binding-gensyms (map #(let-fork-subtask-macro-helper task-scope %) expressions)))
         (.join ~task-scope)
         (.throwIfFailed ~task-scope)
         (let ~(vec (interleave binding-syms (map #(let-fork-value-from-subtask-macro-helper %) binding-gensyms)))
           ~@body)))))