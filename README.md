# fr33m0nk/structured-task-scope

- This is an experimentation `Structued concurrency` with JDK-21 preview feature `Structured Task Scope`
- The purpose is to arrive upon most usable abstraction for `Structured Task Scope` in Clojure
- May be released as a library if the usability is established.

## Usage

### JDK-21+ is mandatory to be able to use this wrapper/library

In `deps.edn`, add following:
```clojure
fr33m0nk/structured-task-scope {:git/url "https://github.com/fr33m0nk/structured-task-scope"
                                :sha "71058b3ab587c91fd6bce4e82079531b48c3ad29"}
```
## ScopedValues
- ScopedValues are introduced to create differing local bindings for Threads (Virtual or Platform)
- They differ from ThreadLocals as the extent of binding is limited to function invocation and the binding is immutable
- This is also a good alternate to `^:dynamic` Clojure vars
- [JEP Café #16: Java 20 - From ThreadLocal to ScopedValue](https://www.youtube.com/watch?v=fjvGzBFmyhM) is a brilliant reference

In REPL, require `fr33m0nk.scoped-value` 
```clojure
(require '[fr33m0nk.scoped-value :as sv])

(def a-scoped-value (sv/->scoped-value))
```
### Using `a-scoped-value` for side effect
```clojure
(defn function-using-scope-value
  []
  (let [processed-string (str (sv/deref a-scoped-value) " Ok")]
    (println processed-string)))

(->> (range 3)
     (pmap #(future
             (sv/run-where! a-scoped-value (str "Hello - " %) function-using-scope-value)))
     (run! deref))
```
result
```clojure
;;Obvious jumbling due to multithreading while printing to screen
Hello - 2 OkHello - 0 OkHello - 1 Ok 
```
### Using `a-scoped-value` for functional transformation
```clojure
(defn function-using-scope-value
  []
  (str (sv/deref a-scoped-value) " Ok"))

(->> (range 3)
     (pmap #(future
              (sv/apply-where a-scoped-value (str "Hello - " %) function-using-scope-value)))
     (mapv deref))
```
result
```clojure
["Hello - 0 Ok" "Hello - 1 Ok" "Hello - 2 Ok"]
```

## StructuredTaskScope

In REPL, require `fr33m0nk.structured-task-scope` namespace
```clojure
(require '[fr33m0nk.structured-task-scope :as sts])
```

### StructuredTaskScope
```clojure
(let [success (atom [])
      error (atom [])
      success-handler #(swap! success conj %)
      failure-handler #(swap! error conj %)
      scope (sts/->structured-scope success-handler failure-handler)]
  (sts/fork-task scope (throw (ex-info "boom" {})))
  (sts/fork-task scope (Thread/sleep 5000) :turtle-wins)
  (sts/fork-task scope (Thread/sleep 5000) :hare-wins)
  (.join scope)
  {:success @success
   :failure @error})
```

result
```clojure
{:success [:hare-wins :turtle-wins],
 :failure [#error{:cause "boom",
                  :data {},
                  :via [{:type clojure.lang.ExceptionInfo,
                         :message "boom",
                         :data {},
                         :at [user$eval2546$fn__2551 invoke "form-init3588532553786133725.clj" 6]}],
                  :trace [[user$eval2546$fn__2551 invoke "form-init3588532553786133725.clj" 6]
                          [clojure.lang.AFn call "AFn.java" 18]
                          [java.util.concurrent.StructuredTaskScope$SubtaskImpl run "StructuredTaskScope.java" 889]
                          [java.lang.VirtualThread run "VirtualThread.java" 309]]}]}
```

JDK-21 currently also supports following refinements of `StructuredTaskScope`:

### ShutdownOnSuccess
```clojure
(import '(java.time Instant))

(sts/with-shutdown-on-success
  scope 
  {:deadline-instant (.plusMillis (Instant/now) 1500)} 
  (sts/fork-task scope (Thread/sleep 1000) :turtle-wins)
  (sts/fork-task scope (Thread/sleep 5000) :hare-wins))
```
result
```clojure
:turtle-wins
```

### ShutdownOnFailure
```clojure
(import '(java.time Instant))

(sts/with-shutdown-on-failure
  scope
  ;; Below are bindings to the tasks once forked
  ;; These bindings should only be of the forked task as
  ;; currently the macro isn't very intelligent
  [turtle (sts/fork-task scope (Thread/sleep 5000) :turtle-wins)
   hare (sts/fork-task scope (Thread/sleep 5000) :hare-wins)]
  ;; Extra options
  {:throw-on-failure? true
   :deadline-instant (.plusMillis (Instant/now) 7000)}
  ;; do something with bindings after calling `.get` method on subtasks
  (map #(name (.get %)) [turtle hare]))
```
result
```clojure
("turtle-wins" "hare-wins")
```

## License

Copyright © 2024 Prashant Sinha

Distributed under the Eclipse Public License version 1.0.
