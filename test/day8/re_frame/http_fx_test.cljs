(ns day8.re-frame.http-fx-test
  (:require
    [ajax.core     :as ajax]
    [cljs.test     :refer-macros [is deftest async use-fixtures]]
    [cljs.spec     :as s]
    [re-frame.core :as re-frame]
    [day8.re-frame.http-fx]))

;; Spec as per https://developer.github.com/v3/rate_limit/
(s/def ::limit int?)
(s/def ::remaining int?)
(s/def ::reset int?)
(s/def ::resource   (s/keys :req-un [::limit ::remaining ::reset]))
(s/def ::core ::resource )
(s/def ::search ::resource )
(s/def ::resources  (s/keys :req-un [::core ::search]))
(s/def ::api-result (s/keys :req-un [::resources]))

;; ---- FIXTURES ---------------------------------------------------------------
;; This fixture uses the re-frame.core/make-restore-fn to checkpoint and reset
;; to cleanup any dynamically registered handlers from our tests.
(defn fixture-re-frame
  []
  (let [restore-re-frame (atom nil)]
    {:before #(reset! restore-re-frame (re-frame.core/make-restore-fn))
     :after  #(@restore-re-frame)}))

(use-fixtures :each (fixture-re-frame))

;; ---- TESTS ------------------------------------------------------------------

(deftest xhrio-get
  ;; Setup effects handler with :http-xhrio specifying an ajax-request.
  ;; Note we specify optional :response-format to make sure our json result
  ;; has keywords for keys, and :timeout see the ajax-request API
  ;; for more details https://github.com/JulianBirch/cljs-ajax#ajax-request
  ;; We specify an :on-failure for completeness but we don't expect the request
  ;; to fail unless there is something wrong with your internet or github.
  (re-frame/reg-event-fx
    ::http-test
    (fn [_world _event-v]
      {:http-xhrio {:method          :get
                    :uri             "https://api.github.com/rate_limit"
                    :timeout         5000
                    :response-format (ajax/json-response-format {:keywords? true})
                    :on-success      [::good-http-result "test-token1"]
                    :on-failure      [::bad-http-result "test-token1"]}}))
    (async done

      ;; setup success handler
      (re-frame/reg-event-db
        ::good-http-result
        (fn [db [_ token result]]
          (is (= "test-token1" token) "expected: token passed through")
          ;; check shape of result using loose Spec
          (is (not= (s/conform ::api-result result) :cljs.spec/invalid)
              (s/explain-str ::api-result result))
          (is (every? keyword? (keys result)) "keys should be keywords")
          (done)
          db))

      ;; setup failure handler
      (re-frame/reg-event-db
        ::bad-http-result
        (fn [db [_ token error]]
          (is (= "test-token1" token) "expected: token passed through")
          (cljs.test/do-report
            {:type     :fail
             :message  "Unexpected HTTP error, something wrong with your internet?"
             :expected ::good-http-result
             :actual   error})
          (done)
          db))

      ;; kick off main handler
      (re-frame/dispatch [::http-test])))
