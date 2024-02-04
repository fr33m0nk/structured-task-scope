# fr33m0nk/structured-task-scope

- This is an experimentation with JDK-21 preview feature `Structured-Task-Scope`
- The purpose is to arrive upon most usable abstraction in Clojure
- May be released as a library if the usability is established.

## Usage

In `deps.edn`, add following:
```clojure
fr33m0nk/structured-task-scope {:git/url "https://github.com/fr33m0nk/structured-task-scope"
                                :sha "f03e56ee46a306feeaaf64148ce3e256f9bb9cfd"}
```

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
