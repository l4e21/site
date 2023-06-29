(ns juxt.site.conditional-test
  (:require
   [clojure.test :refer [deftest is are use-fixtures testing]]
   [juxt.site.test-helpers.oauth :as oauth :refer [RESOURCE_SERVER]]
   [juxt.site.test-helpers.local-files-util :refer [install-installer-groups! converge!]]
   [juxt.site.test-helpers.xt :refer [system-xt-fixture]]
   [juxt.site.test-helpers.handler :refer [*handler* handler-fixture]]
   [juxt.site.test-helpers.login :as login :refer [login-with-form!]]
   [juxt.site.test-helpers.fixture :refer [with-fixtures]]
   [juxt.site.repl :as repl]
   [jsonista.core :as json]
   [clojure.java.io :as io]))


(defn bootstrap []
  (install-installer-groups!
   ["juxt/site/bootstrap"
    "juxt/site/api-operations"
    "juxt/site/system-api"
    "juxt/site/openapi"
    "juxt/site/system-api-openapi"]
   RESOURCE_SERVER {})

  ;; Need some test users and a way for them to authenticate
  (install-installer-groups!
   ["juxt/site/login-form"
    "juxt/site/example-users"]
   RESOURCE_SERVER
   {"session-scope" "https://auth.example.test/session-scopes/form-login-session"})

  ;; Install a private-key for signing
  (converge!
   [{:juxt.site/base-uri "https://auth.example.test"
     :juxt.site/installer-path "/keypairs/{{kid}}"
     :juxt.site/parameters {"kid" "test-kid"}}]
   RESOURCE_SERVER
   {})

  ;; Install an authorization server
  (install-installer-groups!
   ["juxt/site/oauth-authorization-endpoint"
    "juxt/site/oauth-token-endpoint"]
   RESOURCE_SERVER
   {"session-scope" "https://auth.example.test/session-scopes/form-login-session"
    "kid" "test-kid"
    "authorization-code-length" 12
    "jti-length" 12})

  ;; Alice has the System role which confers access to put-user
  (converge!
   [{:juxt.site/base-uri "https://data.example.test"
     :juxt.site/installer-path "/_site/role-assignments/{{username}}-{{rolename}}"
     :juxt.site/parameters
     {"username" "alice"
      "rolename" "System"}}]
   RESOURCE_SERVER
   {})

  (converge!
   ;; TODO: Add these resources to a group
   [{:juxt.site/base-uri "https://auth.example.test" :juxt.site/installer-path "/applications/global-scope-app"}
    {:juxt.site/base-uri "https://auth.example.test" :juxt.site/installer-path "/applications/read-only-app"}
    {:juxt.site/base-uri "https://auth.example.test" :juxt.site/installer-path "/applications/read-write-app"}
    {:juxt.site/base-uri "https://auth.example.test" :juxt.site/installer-path "/applications/site-cli"}]
   RESOURCE_SERVER
   {})

  (converge!
   [{:juxt.site/base-uri "https://data.example.test"
     :juxt.site/installer-path "/_site/role-assignments/{{clientid}}-{{rolename}}"
     :juxt.site/parameters
     {"clientid" "site-cli"
      "rolename" "System"}}]
   RESOURCE_SERVER
   {}))

(defn bootstrap-fixture [f]
  (bootstrap)
  (f))

(use-fixtures :once system-xt-fixture handler-fixture bootstrap-fixture)

(with-fixtures
  (let [session-token (login-with-form! "alice" "garden")
        {access-token "access_token"}
        (oauth/acquire-access-token!
         (cond-> {:grant-type "authorization_code"
                  :authorization-uri "https://auth.example.test/oauth/authorize"
                  :token-uri "https://auth.example.test/oauth/token"
                  :session-token session-token
                  :client (str "https://auth.example.test/applications/global-scope-app")}))]
    (oauth/with-bearer-token access-token
      (let [payload (json/write-value-as-bytes {"xt/id" "https://data.example.test/_site/users/hannah"
                                                "fullname" "Hannah"
                                                "username" "hannah"})
            request {:juxt.site/uri "https://data.example.test/_site/users"
                     :ring.request/method :post
                     :ring.request/headers
                     {"content-type" "application/json"
                      "content-length" (str (count payload))
                      "if-match" "\"example\""}
                     :ring.request/body (io/input-stream payload)}
            response (*handler* request)]
        response))))


(deftest if-match-test
  (let [session-token (login-with-form! "alice" "garden")
        {access-token "access_token"}
        (oauth/acquire-access-token!
         (cond-> {:grant-type "authorization_code"
                  :authorization-uri "https://auth.example.test/oauth/authorize"
                  :token-uri "https://auth.example.test/oauth/token"
                  :session-token session-token
                  :client (str "https://auth.example.test/applications/global-scope-app")}))]
    (testing "* wildcard"
      (oauth/with-bearer-token access-token
        (let [payload (json/write-value-as-bytes {"xt/id" "https://data.example.test/_site/users/hannah"
                                                  "fullname" "Hannah"
                                                  "username" "hannah"})
              request {:juxt.site/uri "https://data.example.test/_site/users"
                       :ring.request/method :post
                       :ring.request/headers
                       {"content-type" "application/json"
                        "content-length" (str (count payload))
                        "if-match" "*"}
                       :ring.request/body (io/input-stream payload)}
              response (*handler* request)]
          (is (= 200 (:ring.response/status response))))))
    (testing "matching keys"
      (oauth/with-bearer-token access-token
        (let [payload (json/write-value-as-bytes {"xt/id" "https://data.example.test/_site/users/hannah"
                                                  "fullname" "Hannah"
                                                  "username" "hannah"})
              request {:juxt.site/uri "https://data.example.test/_site/users"
                       :ring.request/method :post
                       :ring.request/headers
                       {"content-type" "application/json"
                        "content-length" (str (count payload))
                        "if-match" "\"example\""}
                       :ring.request/body (io/input-stream payload)}
              response (*handler* request)]
          (is (= 200 (:ring.response/status response))))))
    #_(testing "mismatched keys"
      (oauth/with-bearer-token access-token
        (let [payload (json/write-value-as-bytes {"xt/id" "https://data.example.test/_site/users/hannah"
                                                  "fullname" "Hannah"
                                                  "username" "hannah"})
              request {:juxt.site/uri "https://data.example.test/_site/users"
                       :ring.request/method :post
                       :ring.request/headers
                       {"content-type" "application/json"
                        "content-length" (str (count payload))
                        "if-match" "\"invalid\""}
                       :ring.request/body (io/input-stream payload)}
              response (*handler* request)]
          (is (= 500 (:juxt.http/status response))))))))

#_(deftest if-not-match-test
  (let [session-token (login-with-form! "alice" "garden")
        {access-token "access_token"}
        (oauth/acquire-access-token!
         (cond-> {:grant-type "authorization_code"
                  :authorization-uri "https://auth.example.test/oauth/authorize"
                  :token-uri "https://auth.example.test/oauth/token"
                  :session-token session-token
                  :client (str "https://auth.example.test/applications/global-scope-app")}))]
    (testing "* wildcard"
      (oauth/with-bearer-token access-token
        (let [request {:juxt.site/uri "https://data.example.test/_site/users"
                       :ring.request/method :get
                       :ring.request/headers
                       {"if-none-match" "*"}}
              response (*handler* request)]
          (is (= 500 (:juxt.http/status response))))))
    (testing "matching keys"
      (oauth/with-bearer-token access-token
        (let [payload (json/write-value-as-bytes {"xt/id" "https://data.example.test/_site/users/hannah"
                                                  "fullname" "Hannah"
                                                  "username" "hannah"})
              request {:juxt.site/uri "https://data.example.test/_site/users"
                       :ring.request/method :post
                       :ring.request/headers
                       {"content-type" "application/json"
                        "content-length" (str (count payload))
                        "if-none-match" "\"example\""}
                       :ring.request/body (io/input-stream payload)}
              response (*handler* request)]
          (is (= 500 (:juxt.http/status response))))))
    (testing "mismatched keys"
      (oauth/with-bearer-token access-token
        (let [payload (json/write-value-as-bytes {"xt/id" "https://data.example.test/_site/users/hannah"
                                                  "fullname" "Hannah"
                                                  "username" "hannah"})
              request {:juxt.site/uri "https://data.example.test/_site/users"
                       :ring.request/method :post
                       :ring.request/headers
                       {"content-type" "application/json"
                        "content-length" (str (count payload))
                        "if-none-match" "\"invalid\""}
                       :ring.request/body (io/input-stream payload)}
              response (*handler* request)]
          (is (= "No weak matches between if-match and current representations"
                 (:juxt.http/status response))))))))

