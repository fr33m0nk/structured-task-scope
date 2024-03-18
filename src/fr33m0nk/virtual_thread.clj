(ns fr33m0nk.virtual-thread)

(defn ->virtual-thread
  ^Thread
  ([^Runnable f]
   (-> (Thread/ofVirtual)
       (.unstarted f)))
  ([^Runnable f name]
   (-> (Thread/ofVirtual)
       (.name name)
       (.unstarted f))))

(defn start
  ^Thread
  [^Thread t]
  (.start t)
  t)

(defmacro run-virtual-thread!
  "Creates and starts a new virtual-thread.

  - executes supplied body in the virtual-thread.
  - returns nil
  Options:
  :name str     - virtual-thread's name
  "
  {:arglists '([name & body])}
  [name & body]
  `(let [f# (fn [] ~@body)
         thread# (->virtual-thread f# ~name)]
     (start thread#)))

(defmacro call-virtual-thread
  "Creates and starts a new virtual-thread.

  - executes supplied body in the virtual-thread.
  - returns a promise on which the value of body would be delivered
  Options:
  :name str     - virtual-thread's name
  "
  {:arglists '([name & body])}
  [name & body]
  `(let [p# (promise)
         f# (fn [] (deliver p# (try
                                 ~@body
                                 (catch Throwable t#
                                   t#))))
         thread# (->virtual-thread f# ~name)]
     (start thread#)
     p#))
