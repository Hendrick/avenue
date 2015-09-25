(ns avenue.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [compojure.core :refer [GET POST]]
            [avenue.core :refer [page xhr form-post check-auth]]))

(defn create-ring-request [uri middleware-params]
  (merge {:request-method :get
          :uri uri
          :params {}}
         middleware-params))

(def ring-params-that-deny-access {:avenue.core/auth-fn (fn [& args] false)})
(def ring-params-that-allow-access {:avenue.core/auth-fn (fn [& args] true)})

(defn fn-that-should-never-be-called [& _args]
  (is false "This function was called, but it should not have been called."))

(deftest check-auth-when-auth-fn-exists
  (testing "auth fn is called with correct args"
    (let [called? (atom false)]
      (check-auth {:avenue.core/auth-fn (fn [args req]
                                                   (reset! called? true)
                                                   (is (= args {:arbitrary "data structure"}))
                                                   (is (map? req)) ;; FIXME: this check could probably be stronger
                                                   true)}
                  (fn [& args] true)
                  fn-that-should-never-be-called
                  [{:arbitrary "data structure"}])
      (is (= @called? true))))

  (testing "when auth-fn returns true fail-fn is not called, but success-fn is called"
    (let [called? (atom false)]
      (check-auth {:avenue.core/auth-fn (fn [& args] true)}
                  (fn [& args] (reset! called? true))
                  fn-that-should-never-be-called
                  ["dont care what this is"])
      (is (= @called? true))))

  (testing "when auth-fn returns false fail-fn is called, but success-fn is not called"
    (let [called? (atom false)]
      (check-auth {:avenue.core/auth-fn (fn [& args] false)}
                  fn-that-should-never-be-called
                  (fn [& args] (reset! called? true))
                  ["dont care what this is"])
      (is (= @called? true))))

  (testing "request is passed through to fail-fn"
    (let [req {:avenue.core/auth-fn (fn [& args] false)}]
      (check-auth req
                  (fn [& args] nil)
                  (fn [req-arg] (is (= req-arg req)))
                  ["dont care what this is"])))

  (testing "success-fn is called with the correct args"
    (let [req {:avenue.core/auth-fn (fn [& args] true)}
          action1 (fn [req-arg]
                    (is (= req-arg req))
                    :arg1)
          action2 (fn [req-arg]
                    (is (= req-arg :arg1))
                    :arg2)]
      (check-auth req
                  (fn [req-arg action-result] (is (= req-arg req))
                    (is (= action-result :arg2)))
                  (fn [& args] nil)
                  ("dont care what this is"
                         action1
                         action2)))))

(deftest check-auth-when-auth-fn-does-not-exist
  (testing "success-fn is called with the correct args"
    (let [req {}
          called1? (atom false)
          called2? (atom false)
          action1 (fn [req-arg]
                    (is (= req-arg req))
                    (reset! called1? true)
                    :arg1)
          action2 (fn [req-arg]
                    (is (= req-arg :arg1))
                    (reset! called2? true)
                    :arg2)]
      (check-auth req
                  (fn [req-arg action-result] (is (= req-arg req))
                    (is (= action-result :arg2)))
                  fn-that-should-never-be-called
                  (action1 action2))
      (is (= true @called1? @called2?)))))

(deftest page-tests
  (defn page-response-fn [_req _action] "yay! we called the right function!")

  (let [boring-route-fn (page "/foo" (fn [_req] nil))]
    (testing "page macro -- authorized-page-fn is called when there is no auth-fn"
      (let [ring-req (create-ring-request "/foo"
                                          {:avenue.core/authorized-page-fn page-response-fn})]
        (is (= (:body (boring-route-fn ring-req)) "yay! we called the right function!"))))

    (testing "page macro -- unauthorized-page-fn is called when auth-fn denies entry"
      (let [ring-req (create-ring-request "/foo"
                                          (merge
                                            {:avenue.core/authorized-page-fn nil
                                             :avenue.core/unauthorized-page-fn (fn [_req] "auth failed")}
                                            ring-params-that-deny-access))]
        (is (= (:body (boring-route-fn ring-req)) "auth failed")))))

  (testing "page macro -- authorized-page-fn is called when auth-fn allows entry"
    (let [action (fn [_req] nil)
          route-fn-with-auth (page "/foo"
                                   [:fake :routes]
                                   action)
          ring-req (create-ring-request "/foo"
                                        (merge
                                          {:avenue.core/authorized-page-fn page-response-fn}
                                          ring-params-that-allow-access))]
      (is (= (:body (route-fn-with-auth ring-req)) "yay! we called the right function!")))))

(deftest xhr-tests
  (let [ring-req (create-ring-request "/foobar"
                                      {:request-method :post})]

    (testing "xhr macro -- it converts the response into edn"
      (let [action (fn [_req] [{:some "arbitrary" :thing 42}])
            route-fn (xhr POST "/foobar" action)]
        (is (= (:body (route-fn ring-req)) "[{:some \"arbitrary\", :thing 42}]"))))

    (testing "xhr macro -- actions that print don't pollute the response"
      (let [action (fn [_req]
                     ;;can we find a way to test this without printing to stdout?
                     (map (fn [x]
                            (prn "printing things should not cause them to appear in an xhr response")
                            x)
                          [1]))
            route-fn (xhr POST "/foobar" action)]
        (is (= (:body (route-fn ring-req)) (pr-str '(1))))))

    (testing "xhr macro -- 403s when auth is denied"
      (let [action (fn [_req] "dont care")
            route-fn (xhr POST "/foobar" action)
            ring-req (merge ring-req ring-params-that-deny-access)]
        (is (= ((juxt :body :status) (route-fn ring-req))
               ["Forbidden" 403]))))))

(deftest form-post-tests
  (let [ring-req (create-ring-request "/foobar"
                                      {:request-method :post})]
    (testing "form-post macro -- redirects to the path returned by the action"
      (let [action (fn [_req] "/foo")
            route-fn (form-post "/foobar" action)]
        (is (= ((juxt :status (comp #(get % "Location") :headers)) (route-fn ring-req)))
            [303 "/foo"])))

    (testing "form-post macro -- 403s when auth is denied"
      (let [ring-req (merge ring-req ring-params-that-deny-access)
            action (fn [_req] "dont care")
            route-fn (form-post "/foobar" action)]
        (is (= (:status (route-fn ring-req)))
            403)))))
