# fr33m0nk/structured-task-scope

- This is an experimentation `Structued concurrency` with JDK-21 preview feature `Structured Task Scope`
- The purpose is to arrive upon most usable abstraction for `Structured Task Scope` in Clojure
- May be released as a library if the usability is established.

## Usage

### JDK-21+ is mandatory to be able to use this wrapper/library

In `deps.edn`, add following:
```clojure
fr33m0nk/structured-task-scope {:git/url "https://github.com/fr33m0nk/structured-task-scope"
                                :sha "0af6062d15228f13a0ba0aa22e696da195dd81aa"}
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
   hare (sts/fork-task scope (Thread/sleep 5000) :hare-wins)
   ;; forked task bindings can be used inside another forked task
   ;; using bindings `boomer (.get hare)` here will cause `IllegalStateException`
   ;; However, such bindings are fine in body of the macro as the body executes after `(.join scope)` 
   zoomba (sts/fork-task scope (->> [turtle hare]
                                (map #(name (.get %)))))]
  ;; Extra options
  {:throw-on-failure? true
   :deadline-instant (.plusMillis (Instant/now) 7000)}
  ;; do something with bindings after calling `.get` method on subtasks
  (.get zoomba))
```
result
```clojure
("turtle-wins" "hare-wins")
```

### Additional functions/macros
`let-fork`
Evaluates bindings in parallel and returns the result of
evaluating body in the context of those bindings. 
Bindings have to be independent of each other
```clojure
(sts/let-fork [a (let [sleep-ms 5000] (println "Sleep 1 ") (Thread/sleep sleep-ms) (println "Awake 1") :a)
               b (let [sleep-ms 2000] (println "Sleep 2 ") (Thread/sleep sleep-ms) (println "Awake 2") 10)]
              {a b})
```
result
```clojure
Sleep 1
Sleep 2
Awake 2
Awake 1

{:a 10}
```
## License

Copyright © 2024 Prashant Sinha

Distributed under the Eclipse Public License version 1.0.
