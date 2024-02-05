(ns fr33m0nk.scoped-value
  (:import [java.lang ScopedValue])
  (:refer-clojure :exclude [deref]))

(defn ->scoped-value
  []
  (ScopedValue/newInstance))

(defn deref
  [^ScopedValue scoped-value]
  (.get scoped-value))

(defn bound?
  [^ScopedValue scoped-value]
  (.isBound scoped-value))

(defn run-where!
  "Params:
  - scoped-value
  - value : this is the value that will be bound to the scoped-value during the execution
  returns value after application of `f`
  - f : f is a function that takes no argument and uses scoped-value via `(deref scoped-value)` in its body

  Return: nil"
  [^ScopedValue scoped-value value f]
  (ScopedValue/runWhere scoped-value value f))


(defn apply-where
  "Params:
  - scoped-value
  - value : this is the value that will be bound to the scoped-value during the execution
  returns value after application of `f`
  - f : f is a function that takes no argument and uses scoped-value via `(deref scoped-value)` in its body

  Return: value after applying f"
  [^ScopedValue scoped-value value f]
  (ScopedValue/callWhere scoped-value value f))
