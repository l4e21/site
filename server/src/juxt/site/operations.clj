; Copyright © 2022, JUXT LTD.

(ns juxt.site.operations
  (:require
   [juxt.grab.alpha.parser :as graphql.parser]
   [juxt.grab.alpha.schema :as graphql.schema]
   [clojure.tools.logging :as log]
   [clojure.string :as str]
   [crypto.password.bcrypt :as bcrypt]
   [java-http-clj.core :as hc]
   [jsonista.core :as json]
   [juxt.site.http-authentication :as http-authn]
   [juxt.site.openid-connect :as openid-connect]
   [juxt.site.util :refer [make-nonce as-b64-str sha] :as util]
   [juxt.site.conditional :as conditional]
   [malli.core :as malli]
   [malli.error :as malli.error]
   [ring.util.codec :as codec]
   [sci.core :as sci]
   [xtdb.api :as xt]
   juxt.site.schema
   [juxt.site.jwt :as jwt]))

(defn operation->rules
  "Determine rules for the given operation id. A rule is bound to the
  given operation."
  [db operation]
  (mapv
   #(conj (second %) ['operation :xt/id (first %)])
   (xt/q db '{:find [e rules]
              :where [[e :xt/id operation]
                      [e :juxt.site/rules rules]]
              :in [operation]}
         operation)))

(defn operations->rules
  "Determine rules for the given operation ids. Each rule is bound to the given
  operation."
  [db operations]
  (mapv
   #(conj (second %) ['operation :xt/id (first %)])
   (xt/q db {:find ['e 'rules]
             :where [['e :xt/id (set operations)]
                     ['e :juxt.site/rules 'rules]]})))

;; This is broken out into its own function to assist debugging when
;; authorization is denied and we don't know why. A better authorization
;; debugger is definitely required.

(defn ^{:private true} query-permissions
  [{:keys [db rules subject operation resource scope purpose]}]
  (assert (or (nil? subject) (string? subject)))
  (assert (or (nil? resource) (string? resource)))
  (assert (or (nil? scope) (set? scope)))

  (if scope
    (let [query {:find '[(pull permission [*]) ]
                 :where
                 '[
                   [operation :juxt.site/type "https://meta.juxt.site/types/operation"]

                   ;; Only consider a permitted operation
                   [permission :juxt.site/type "https://meta.juxt.site/types/permission"]
                   [permission :juxt.site/operation operation]
                   (allowed? subject operation resource permission)

                   ;; Only permissions that match our purpose
                   [permission :juxt.site/purpose purpose]

                   ;; When scope limits operations, restrict operations that have the scope
                   [operation :juxt.site/scope s]
                   [(contains? scope s)]]

                 :rules rules

                 :in '[subject operation resource scope purpose]}]
      (try
        (map first (xt/q db query subject operation resource scope purpose))
        (catch Exception e
          (throw (ex-info "Failed to query permissions" {:query query} e)))))

    ;; No scope involved. Ignore it.
    (let [query {:find '[(pull permission [*]) ]
                 :where
                 '[
                   [operation :juxt.site/type "https://meta.juxt.site/types/operation"]

                   ;; Only consider a permitted operation
                   [permission :juxt.site/type "https://meta.juxt.site/types/permission"]
                   [permission :juxt.site/operation operation]
                   (allowed? subject operation resource permission)

                   ;; Only permissions that match our purpose
                   [permission :juxt.site/purpose purpose]]

                 :rules rules

                 :in '[subject operation resource purpose]}]
      (try
        (map first (xt/q db query subject operation resource purpose))
        (catch Exception e
          (throw (ex-info "Failed to query permissions" {:query query} e)))))))

(defn check-permissions
  "Given a subject, an operation and resource, return all permissions."
  [db operation
   {subject :juxt.site/subject
    resource :juxt.site/resource
    scope :juxt.site/scope
    purpose :juxt.site/purpose
    :as options}]

  (when (= (find options :juxt.site/subject) [:juxt.site/subject nil])
    (throw (ex-info "Nil subject passed!" {})))

  ;; TODO: These asserts have been replaced by Malli schema instrumentation
  (assert (or (nil? subject) (map? subject)) "Subject expected to be a map, or null")
  (assert (or (nil? resource) (map? resource)) "Resource expected to be a map, or null")

  (let [rules (operation->rules db operation)]
    (when (seq rules)
      (query-permissions
       {:db db
        :rules rules
        :subject (:xt/id subject)
        :operation operation
        :resource (:xt/id resource)
        :scope scope
        :purpose purpose}))))

(malli/=>
 check-permissions
 [:=> [:cat
       :any
       :string
       [:map
        [:juxt.site/subject {:optional true}]
        [:juxt.site/resource {:optional true}]
        [:juxt.site/purpose {:optional true}]]]
  :any])

(defn allowed-resources
  "Given a set of possible operations, and possibly a subject and purpose, which
  resources are allowed?"
  [db operation {:juxt.site/keys [subject purpose]}]
  (let [rules (operation->rules db operation)
        query {:find '[resource]
               :where
               '[
                 [operation :juxt.site/type "https://meta.juxt.site/types/operation"]

                 ;; Only consider a permitted operation
                 [permission :juxt.site/type "https://meta.juxt.site/types/permission"]
                 [permission :juxt.site/operation operation]
                 (allowed? subject operation resource permission)

                 ;; Only permissions that match our purpose
                 [permission :juxt.site/purpose purpose]]

               :rules rules

               :in '[subject operation purpose]}]

    (try
      (xt/q db query (:xt/id subject) operation purpose)
      (catch Exception cause
        (throw
         (ex-info
          "Operations query failed"
          {:query query
           :rules rules
           :subject subject
           :operation operation
           :operation-entity (xt/entity db operation)
           :purpose purpose}
          cause))))))

(malli/=>
 allowed-resources
 [:=> [:cat
       :any
       :string
       [:map
        [:juxt.site/subject {:optional true}]
        [:juxt.site/purpose {:optional true}]]]
  :any])

;; TODO: How is this call protected from unauthorized use? Must call this with
;; access-token to verify subject.
(defn allowed-subjects
  "Given a resource and an operation, which subjects can access?"
  [db resource operation {:keys [purpose]}]
  (let [rules (operation->rules db operation)]
    (->> (xt/q
          db
          {:find '[subject]
           :keys '[subject]
           :where
           '[
             [operation :juxt.site/type "https://meta.juxt.site/types/operation"]

             ;; Only consider a permitted operation
             [permission :juxt.site/type "https://meta.juxt.site/types/permission"]
             [permission :juxt.site/operation operation]
             (allowed? subject operation resource permission)

             ;; Only permissions that match our purpose
             [permission :juxt.site/purpose purpose]

             #_[access-token :juxt.site/subject subject]]

           :rules rules

           :in '[resource operation purpose]}

          resource operation purpose))))

(defn pull-allowed-resource
  "Given a subject, an operation and a resource, pull the allowed
  attributes."
  [db operation resource ctx]
  (let [check-result
        (check-permissions
         db
         operation
         (assoc ctx :juxt.site/resource resource))

        pull-expr (vec (mapcat
                        (fn [{:juxt.site/keys [operation]}]
                          (:juxt.site/pull operation))
                        check-result))]
    (xt/pull db pull-expr (:xt/id resource))))

(malli/=>
 pull-allowed-resource
 [:=> [:cat
       :any
       :string
       :juxt.site/resource
       [:map
        [:juxt.site/subject {:optional true}]
        [:juxt.site/purpose {:optional true}]]]
  :any])

(defn pull-allowed-resources
  "Given a subject and an operation, which resources are allowed, and
  get me the documents. If resources-in-scope is given, only consider
  resources in that set."
  [db operation {:juxt.site/keys [subject purpose include-rules resources-in-scope]}]
  (assert (string? operation))
  (let [rules (operation->rules db operation)
        _ (when-not (seq rules)
            (throw (ex-info "No rules found for operation" {:operation operation})))
        results
        (xt/q
         db
         {:find '[resource (pull operation [:xt/id :juxt.site/pull]) purpose permission]
          :keys '[resource operation purpose permission]
          :where
          (cond-> '[
                    ;; Only consider given operations
                    [operation :juxt.site/type "https://meta.juxt.site/types/operation"]

                    ;; Only consider allowed permssions
                    [permission :juxt.site/type "https://meta.juxt.site/types/permission"]
                    [permission :juxt.site/operation operation]
                    (allowed? subject operation resource permission)

                    ;; Only permissions that match our purpose
                    [permission :juxt.site/purpose purpose]]

            include-rules
            (conj '(include? subject operation resource))

            resources-in-scope
            (conj '[(contains? resources-in-scope resource)]))

          :rules (vec (concat rules include-rules))

          :in '[subject operation purpose resources-in-scope]}

         (:xt/id subject) operation purpose (or resources-in-scope #{}))]

    ;; TODO: Too complex, extract this and unit test. The purpose here it to
    ;; apply the pull of each relevant operation to each result, and merge the
    ;; results into a single map.
    (doall
     (for [[resource resource-group] (group-by :resource results)]
       (apply merge
              (for [{:keys [operation]}
                    ;; TODO: Purpose and permission are useful metadata, how do
                    ;; we retain in the result? with-meta?
                    resource-group]
                (xt/pull db (:juxt.site/pull operation '[*]) resource)))))))

(malli/=>
 pull-allowed-resources
 [:=> [:cat
        :any
        :string
        [:map
         [:juxt.site/subject {:optional true}]
         [:juxt.site/purpose {:optional true}]]]
   :any])

(defn join-with-pull-allowed-resources
  "Join collection on given join-key with another pull of allowed-resources with
  given operations and options."
  [db coll join-key operations options]
  (let [idx (->>
             (assoc options :juxt.site/resources-in-scope (set (map join-key coll)))
             (pull-allowed-resources db operations)
             (group-by :xt/id))]
    (map #(update % join-key (comp first idx)) coll)))

(defn allowed-operations
  "Return all the operations that a subject is allowed to perform, along
  with the permissions that permit them."
  [db {:juxt.site/keys [subject purpose]}]
  (let [
        ;; Start with a list of all operations
        operations
        (set
         (map first
              (xt/q db '{:find [e]
                         :where [[e :juxt.site/type "https://meta.juxt.site/types/operation"]]})))

        rules (operations->rules db operations)]

    (->>
     (xt/q
      db
      {:find '[(pull operation [*]) permission]
       :keys '[juxt.site/operation juxt.site/permission]
       :where
       '[
         [operation :juxt.site/type "https://meta.juxt.site/types/operation"]

         ;; Only consider given operations
         ;; TODO: Is this necessary
         [(contains? operations operation)]

         ;; Only consider a permitted operation
         [permission :juxt.site/type "https://meta.juxt.site/types/permission"]
         [permission :juxt.site/operation operation]
         (allowed? subject operation resource permission)

         ;; Only permissions that match our purpose
         [permission :juxt.site/purpose purpose]]

       :rules rules

       :in '[subject operations resource purpose]}

      (:xt/id subject)
      operations
      nil
      purpose)

     ;; We want to list unique operations but associated with the
     ;; permissions that let the subject have access.
     (group-by :juxt.site/operation)
     (reduce-kv
      (fn [acc operation permissions]
        (conj acc (assoc operation :juxt.site/permitted-by (mapv :juxt.site/permission permissions))))
      []))))

(defn common-sci-namespaces [operation-doc]
  {
   'com.auth0.jwt.JWT
   {'decode (fn decode [x] (com.auth0.jwt.JWT/decode x))}

   'crypto.password.bcrypt {'encrypt bcrypt/encrypt}

   'java-http-clj.core
   {'send hc/send}

   'jsonista.core
   {'write-value-as-string (fn [x] (json/write-value-as-string x (json/object-mapper {:pretty true})))
    'write-value-as-bytes (fn [x] (json/write-value-as-bytes x (json/object-mapper {:pretty true})))
    'read-value json/read-value
    'read-value-with-keywords (fn [x] (json/read-value x (json/object-mapper {:decode-key-fn true})))}

   'juxt.site
   {'decode-id-token juxt.site.openid-connect/decode-id-token
    'verify-authorization-code
    (fn [{:keys [code-verifier code-challenge code-challenge-method]}]
      (assert code-verifier)
      (assert code-challenge)
      (assert code-challenge-method)
      (case code-challenge-method
        "S256" (let [new-code-challenge (util/code-challenge code-verifier)]
                 {:verified? (= code-challenge new-code-challenge)
                  :code-challenge code-challenge
                  :code-verifier code-verifier
                  :new-code-challenge new-code-challenge})))}

   'juxt.site.malli
   {'validate (fn validate [schema value] (malli/validate schema value))
    'explain-input (fn explain [input]
                     (->
                      (malli/explain (get-in operation-doc [:juxt.site.malli/input-schema]) input)
                      (malli.error/humanize)))
    'validate-input
    (fn validate-input [input]
      (let [schema (get-in operation-doc [:juxt.site.malli/input-schema])
            valid? (malli/validate schema input)]
        (when-not valid?
          (throw
           (ex-info
            "Validation failed"
            {:error :validation-failed
             :input input
             :schema schema})))
        input))}

   'log
   {'trace (fn [message] (log/trace message))
    'debug (fn [message] (log/debug message))
    'info (fn [message] (log/info message))
    'warn (fn [message] (log/warn message))
    'error (fn [message] (log/error message))}

   'grab
   {'parse graphql.parser/parse
    'compile-schema graphql.schema/compile-schema*}

   'ring.util.codec
   {'form-encode codec/form-encode
    'form-decode codec/form-decode}})

(defn generate-etag [ctx]
  (update ctx :juxt.site/current-representations
          #(mapv
            (fn [rep]
              (let [generator (get-in rep [:juxt.site/etag-generate :juxt.site.sci/program])]
                (assoc rep :juxt.http/etag
                       (if generator
                         ;; Validation here?
                         (sci/binding [sci/out *out*]
                           (sci/eval-string
                            generator
                            {:namespaces
                             (merge
                              {'user {'*resource* (:juxt.site/resource ctx)
                                      ;; '*tx* (xt/indexing-tx tx-ctx)
                                      'log (fn [& message]
                                             (eval `(log/info ~(str/join " " message))))
                                      'logf (fn [& args]
                                              (eval `(log/infof ~@args)))}})

                             :classes
                             {'java.util.Date java.util.Date
                              'java.time.Instant java.time.Instant
                              'java.time.Duration java.time.Duration}}))
                         ;; Should we throw an error? 
                         nil))))
            %)))

(defn do-operation-in-tx-fn
  "This function is applied within a transoperation function. It should be fast, but
  at least doesn't have to worry about the database being stale!"
  [xt-ctx
   {subject :juxt.site/subject
    operation :juxt.site/operation
    resource :juxt.site/resource
    purpose :juxt.site/purpose
    prepare :juxt.site/prepare
    evaluate-preconditions :juxt.site/evaluate-preconditions
    :as ctx}]

  (when evaluate-preconditions
    (conditional/evaluate-preconditions! (generate-etag ctx)))
  
  (let [db (xt/db xt-ctx)
        tx (xt/indexing-tx xt-ctx)
        operation-doc (xt/entity db operation)
        _ (when-not operation-doc
            (throw
             (ex-info
              (format "Operation '%s' not found in db" operation)
              {:operation operation})))]
    (try
      (assert (or (nil? subject) (map? subject)) "Subject to do-operation-in-tx-fn expected to be a string, or null")
      (assert (or (nil? resource) (map? resource)) "Resource to do-operation-in-tx-fn expected to be a string, or null")

      ;; Check that we /can/ call the operation
      (let [check-permissions-result
            (check-permissions db operation ctx)]

        (when-not (seq check-permissions-result)
          (throw (ex-info "Operation denied" ctx)))

        (let [fx
              (cond
                ;; Official: sci
                (-> operation-doc :juxt.site/transact :juxt.site.sci/program)
                (try
                  (sci/eval-string
                   (-> operation-doc :juxt.site/transact :juxt.site.sci/program)
                   {:namespaces
                    (merge-with
                     merge
                     {'user
                      {'*operation* operation-doc
                       '*resource* resource
                       '*prepare* prepare
                       '*ctx* ctx}
                      ;; Allowed to access the database
                      'xt
                      {'entity (fn [id] (xt/entity db id))
                       'q (fn [& args] (apply xt/q db args))}

                      'juxt.site
                      {'match-identity
                       (fn [m]
                         (log/infof "Matching identity: %s" m)
                         (let [q {:find ['id]
                                  :where (into
                                          [['id :juxt.site/type "https://meta.juxt.site/types/user-identity"]
                                           `(~'or
                                             [~'id :juxt.site.jwt.claims/sub ~(:juxt.site.jwt.claims/sub m)]
                                             [~'id :juxt.site.jwt.claims/nickname ~(:juxt.site.jwt.claims/nickname m)])])}]
                           (log/infof "Query used: %s" (pr-str q))
                           (let [result (ffirst (xt/q db q))]
                             (log/infof "Result: %s" result)
                             result)))

                       ;; TODO: Rather than password check in the
                       ;; transaction function (requiring the password
                       ;; to be stored in the transaction-log), this
                       ;; should be moved to the prepare step.
                       'match-identity-with-password
                       (fn [m password password-hash-key]
                         (ffirst
                          (xt/q db {:find ['id]
                                    :where (into
                                            [['id :juxt.site/type "https://meta.juxt.site/types/user-identity"]
                                             ['id password-hash-key 'password-hash]
                                             ['(crypto.password.bcrypt/check password password-hash)]
                                             ]
                                            (for [[k v] m] ['id k v]))
                                    :in ['password]} password)))

                       'lookup-applications
                       (fn [client-id]
                         (seq
                          (map first
                               (try
                                 (xt/q
                                  db
                                  '{:find [(pull e [*])]
                                    :where [[e :juxt.site/type "https://meta.juxt.site/types/application"]
                                            [e :juxt.site/client-id client-id]]
                                    :in [client-id]} client-id)
                                 (catch Exception cause
                                   (throw
                                    (ex-info
                                     (format "Failed to lookup client: %s" client-id)
                                     {:client-id client-id} cause)))))))

                       'lookup-scope
                       (fn [scope]
                         (let [results (xt/q
                                        db
                                        '{:find [(pull e [*])]
                                          :where [[e :juxt.site/type "https://meta.juxt.site/types/oauth-scope"]]})]

                           (if (= 1 (count results))
                             (ffirst results)
                             (if (seq results)
                               (throw
                                (ex-info
                                 (format "Multiple documents for scope: %s" scope)
                                 {:scope scope
                                  :documents (map :xt/id results)}))
                               (throw
                                (ex-info
                                 (format "No such scope: %s" scope)
                                 {:error "invalid_scope"}))))))

                       'lookup-authorization-code
                       (fn [code]
                         (first
                          (map first
                               (xt/q db '{:find [(pull e [*])]
                                          :where [[e :juxt.site/code code]
                                                  [e :juxt.site/type "https://meta.juxt.site/types/authorization-code"]]
                                          :in [code]}
                                     code))))

                       'lookup-access-token
                       (fn [token]
                         (first
                          (map first
                               (xt/q db '{:find [(pull e [*])]
                                          :where [[e :juxt.site/token token]
                                                  [e :juxt.site/type "https://meta.juxt.site/types/access-token"]]
                                          :in [token]}
                                     token))))

                       'lookup-refresh-token
                       (fn [token]
                         (first
                          (map first
                               (xt/q db '{:find [(pull e [*])]
                                          :where [[e :juxt.site/token token]
                                                  [e :juxt.site/type "https://meta.juxt.site/types/refresh-token"]]
                                          :in [token]}
                                     token))))

                       ;; TODO: Rename to make it clear this is a JWT
                       ;; access token. Other access tokens might be
                       ;; possible.
                       'make-access-token
                       (fn [claims keypair-id]
                         (let [keypair (xt/entity db keypair-id)]
                           (when-not keypair
                             (throw (ex-info (format "Keypair not found: %s" keypair-id) {:keypair-id keypair-id})))
                           (try
                             (jwt/new-access-token claims keypair)
                             (catch Exception cause
                               (throw
                                (ex-info
                                 "Failed to make access token"
                                 {:claims claims
                                  :keypair-id keypair-id}
                                 cause))))))

                       'decode-access-token
                       (fn [access-token]
                         (let [kid (jwt/get-kid access-token)
                               _ (when-not kid
                                   (throw (ex-info "No key id in access-token, should try all possible keypairs" {})))
                               keypair (jwt/lookup-keypair db kid)]
                           (when-not keypair
                             (throw (ex-info "Keypair not found" {:kid kid})))
                           (jwt/verify-jwt access-token keypair)))}

                      'grab
                      {'parsed-types
                       (fn parsed-types [schema-id]
                         (map :juxt.grab/type-definition
                              (map first
                                   (xt/q db '{:find [(pull e [:juxt.grab/type-definition])]
                                              :where [[e :juxt.site/type "https://meta.juxt.site/types/graphql-type"]
                                                      [e :juxt.site/graphql-schema schema-id]]
                                              :in [schema-id]}
                                         schema-id))))}}

                     (common-sci-namespaces operation-doc))

                    :classes
                    {'java.util.Date java.util.Date
                     'java.time.Instant java.time.Instant
                     'java.time.Duration java.time.Duration
                     'java.time.temporal.ChronoUnit java.time.temporal.ChronoUnit}

                    ;; We can't allow random numbers to be computed as they
                    ;; won't be the same on each node. If this is a problem, we
                    ;; can replace with a (non-secure) PRNG seeded from the
                    ;; tx-instant of the tx. Note that secure random numbers
                    ;; should not be generated this way anyway, since then it
                    ;; would then be possible to mount an attack based on
                    ;; knowledge of the current time. Instead, secure random
                    ;; numbers should be generated in the operation's 'prepare'
                    ;; step.
                    :deny `[loop recur rand rand-int]})

                  (catch clojure.lang.ExceptionInfo e
                    ;; The sci.impl/callstack contains a volatile which isn't freezable.
                    ;; Also, we want to unwrap the original cause exception.
                    ;; Possibly, in future, we should get the callstack
                    (throw (or (.getCause e) e))))

                ;; There might be other strategies in the future (although the
                ;; fewer the better really)
                :else
                (throw
                 (ex-info
                  "Submitted operations should have a valid juxt.site/transact entry"
                  {:operation operation-doc})))

              _ (log/debugf "FX are %s" (pr-str fx))

              ;; Validate
              _ (doseq [effect fx]
                  (when-not (and (vector? effect)
                                 (keyword? (first effect))
                                 (if (= :xtdb.api/put (first effect))
                                   (map? (second effect))
                                   true))
                    (throw (ex-info "Invalid effect" {:juxt.site/operation operation :effect effect}))))

              xtdb-ops (filter (fn [[effect]] (= (namespace effect) "xtdb.api")) fx)

              ;; Deprecated
              apply-to-request-context-fx (filter (fn [[effect]] (= effect :juxt.site/apply-to-request-context)) fx)
              ;; Decisions we've made which don't update the database but should
              ;; be record and reflected in the response.
              other-response-fx
              (remove
               (fn [[kw]]
                 (or
                  (= (namespace kw) "xtdb.api")
                  (= kw :juxt.site/apply-to-request-context)))
               fx)

              result-fx
              (conj
               xtdb-ops
               ;; Add an operation log entry for this transaction
               [:xtdb.api/put
                (into
                 (cond->
                     {:xt/id (str (:juxt.site/events-base-uri operation-doc) (::xt/tx-id tx))
                      :juxt.site/type "https://meta.juxt.site/types/event"
                      :juxt.site/subject-uri (:xt/id subject)
                      :juxt.site/operation operation
                      :juxt.site/purpose purpose
                      :juxt.site/puts (vec
                                       (keep
                                        (fn [[tx-op {id :xt/id}]]
                                          (when (= tx-op ::xt/put) id))
                                        xtdb-ops))
                      :juxt.site/deletes (vec
                                          (keep
                                           (fn [[tx-op {id :xt/id}]]
                                             (when (= tx-op ::xt/delete) id))
                                           xtdb-ops))}
                     tx (into tx)

                     ;; Any quotations that we want to apply to the request context?
                     ;; (deprecated)
                     (seq apply-to-request-context-fx)
                     (assoc :juxt.site/apply-to-request-context-ops apply-to-request-context-fx)

                     (seq other-response-fx)
                     (assoc :juxt.site/response-fx other-response-fx)

                     ))])]

          ;; This isn't the best debugger :( - need a better one!
          ;;(log/debugf "XXXX Result is: %s" result-ops)

          result-fx))

      (catch Throwable e
        (let [event-id (str (:juxt.site/events-base-uri operation-doc) (::xt/tx-id tx))
              create-error-structure
              (fn create-error-structure [error]
                (let [cause (.getCause error)]
                  (cond-> {:juxt.site/message (.getMessage error)
                           :juxt.site/ex-data (ex-data error)}
                    cause (assoc :juxt.site/cause (create-error-structure cause)))))

              error-record
              {:xt/id event-id
               :juxt.site/type "https://meta.juxt.site/types/event"
               :juxt.site/subject subject
               :juxt.site/operation operation
               :juxt.site/resource resource
               :juxt.site/purpose purpose
               :juxt.site/error (create-error-structure e)}
              ]

          (log/errorf e "Error when performing operation: %s %s" operation event-id)

          #_(log/errorf "Debugging error: %s" (pr-str error-record))

          [[::xt/put error-record]])))))


;; Remove anything in the ctx that will upset nippy. However, in the future
;; we'll definitely want to record all inputs to operations, so this is an
;; opportunity to decide which entries form the input 'record' and which are
;; only transitory for the purposes of responnding to the request.

(defn sanitize-ctx [ctx]
  (-> ctx
      (dissoc :juxt.site/xt-node :juxt.site/db)
      (update :juxt.site/operation :xt/id)))

(defn apply-response-fx [ctx fx]
  (reduce
   (fn [ctx [op & args]]
     (case op
       :ring.response/status (assoc ctx :ring.response/status (first args))
       :ring.response/headers (update ctx :ring.response/headers (fnil into {}) (first args))
       :ring.response/body (assoc ctx :ring.response/body (first args))
       (throw
        (ex-info
         (format "Op not recognized: %s" op)
         {:op op :args args}))))
   ctx fx))

(defn do-prepare [{:juxt.site/keys [db resource] :as ctx} operation-doc]
  (when-let [prepare-program (some-> operation-doc :juxt.site/prepare :juxt.site.sci/program)]
    (try
      (sci/eval-string
       prepare-program
       {:namespaces
        (merge-with
         merge
         {'user {'*operation* operation-doc
                 '*resource* resource
                 '*ctx* (sanitize-ctx ctx)
                 'logf (fn [fmt & fmt-args]
                         (log/infof (apply format fmt fmt-args)))
                 'log (fn [message]
                        (log/info message))}

          'xt
          { ;; Unsafe due to violation of strict serializability, hence marked as
           ;; entity*
           'entity*
           (fn [id] (xt/entity db id))}

          'juxt.site.util
          {'make-nonce make-nonce}

          'juxt.site
          {'generate-key-pair
           (fn [algo]
             (.generateKeyPair (java.security.KeyPairGenerator/getInstance algo)))
           'get-public-key (fn [kp] (.getPublic kp))
           'get-private-key (fn [kp] (.getPrivate kp))
           'get-encoded (fn [k] (as-b64-str (.getEncoded k)))
           'get-modulus (fn [k] (.getModulus k))
           'get-public-exponent (fn [k] (.getPublicExponent k))
           'get-key-format (fn [k] (.getFormat k))}}

         (common-sci-namespaces operation-doc))

        :classes
        {'java.util.Date java.util.Date
         'java.time.Instant java.time.Instant
         'java.time.Duration java.time.Duration
         'java.time.temporal.ChronoUnit java.time.temporal.ChronoUnit
         'java.security.KeyPairGenerator java.security.KeyPairGenerator
         }})
      (catch clojure.lang.ExceptionInfo e
        (throw
         (ex-info
          (format "Prepare failed for operation %s" (:xt/id operation-doc))
          ;; The point of this is that we want to allow the
          ;; thrower to set status, headers.
          ;;
          ;; TODO: We should take the latest ctx (given as a
          ;; parameter), and return it as the
          ;; juxt.site/request-context entry in the ex-info but
          ;; with the status, headers, and body merged into
          ;; it. This is a common idiom so there should be a
          ;; convenience function to help do this.
          (merge (ex-data e) (ex-data (.getCause e)))
          e))))))

(defn do-operation!
  [{:juxt.site/keys [xt-node db resource subject operation evaluate-preconditions] :as ctx}]
  (assert operation)

  (assert (:juxt.site/xt-node ctx) "xt-node must be present")
  (assert (:juxt.site/db ctx) "db must be present")

  #_(when evaluate-preconditions
    (conditional/evaluate-preconditions! (generate-etag ctx)))
  
  (when-not (map? operation)
    (throw
     (ex-info
      "Operation must be a map"
      {:juxt.site/request-context ctx :operation operation})))

  (when-not (or (nil? subject) (map? subject))
    (throw
     (ex-info
      "Subject to do-operation expected to be a map, or null"
      {:juxt.site/request-context ctx :subject subject})))

  (when-not (or (nil? resource) (map? resource))
    (throw
     (ex-info
      "Resource to do-operation expected to be a map, or null"
      {:juxt.site/request-context ctx :resource resource})))

  ;; Etag generator and stuff TODO
  
  ;; Evaluate preconditions

  ;; Prepare the transaction - this work happens prior to the transaction, one a
  ;; single node, and may be wasted work if the transaction ultimately
  ;; fails. However, it is a good place to compute any secure random numbers
  ;; which can't be done in the transaction.

  ;; The :juxt.site/subject can be nil, if this operation is being performed
  ;; by an anonymous user.
  (let [prepare (do-prepare ctx operation)
        tx-fn (:juxt.site/do-operation-tx-fn operation)
        _ (when-not (xt/entity db tx-fn)
            (throw (ex-info (format "do-operation must exist in database: %s" tx-fn)
                            {:operation operation})))
        tx-ctx (cond-> (sanitize-ctx ctx)
                 prepare (assoc :juxt.site/prepare prepare))
        tx (xt/submit-tx xt-node [[::xt/fn tx-fn tx-ctx]])
        {::xt/keys [tx-id] :as tx} (xt/await-tx xt-node tx)
        ctx (assoc ctx :juxt.site/db (xt/db xt-node tx))
        events-base-uri (:juxt.site/events-base-uri operation)]

    (when-not (xt/tx-committed? xt-node tx)
      (throw
       (ex-info
        (format "Transaction failed to be committed for operation %s" (:xt/id operation))
        {::xt/tx-id tx-id
         :juxt.site/operation (:xt/id operation)
         :juxt.site/request-context ctx})))

    (let [result
          (xt/entity
           (xt/db xt-node)
           (str events-base-uri tx-id))]

      (log/debugf "Result from operation %s: %s" (:xt/id operation) result)

      (if-let [{:juxt.site/keys [message ex-data]
                :as error} (:juxt.site/error result)]

        ;; This might just be an exit, which we can recover from.  The
        ;; transact is allowed to influence ring.response/status,
        ;; ring.response/headers and ring.response/body.
        (do
          (log/infof "Error during transaction: %s" (pr-str error))

          (when-let [status (:ring.response/status ex-data)]
            (when (>= status 500)
              (log/errorf "Transaction error: %s" error)))

          (throw
           (ex-info message (into {:juxt.site/request-context ctx} ex-data))))

        (cond-> ctx
          result (assoc :juxt.site/operation-result result)

          (seq (:juxt.site/response-fx result))
          (apply-response-fx (:juxt.site/response-fx result)))))))

;; TODO: Since it is possible that a permission is in the queue which might
;; grant or revoke an operation, it is necessary to run this check 'head-of-line'
;; and submit a transaction function. This will avoid any non-determinism caused
;; by a race-condition and retain proper serialization of transactions.
;;
;; For a fuller discussion on determinism and its benefits, see
;; https://www.cs.umd.edu/~abadi/papers/abadi-cacm2018.pdf
(defn wrap-authorize-with-operation [h]
  (fn [{:juxt.site/keys [db resource uri subject scope]
        :ring.request/keys [method]
        :as req}]

    (assert (or (nil? subject) (map? subject)))
    (assert (or (nil? resource) (map? resource)))

    (let [method (if (= method :head) :get method)
          operation-id (get-in resource [:juxt.site/methods method :juxt.site/operation])

          _ (when-not operation-id
              ;; Options doesn't cause this to fail, it will just
              ;; result in no permissions. But if an operation is
              ;; registered against an OPTIONS operation, we want to
              ;; respect it.
              (when (not= method :options)
                (throw
                 (ex-info
                  (format "No :juxt.site/operation for method %s: %s" method (pr-str resource) #_(pr-str (get-in resource [:juxt.site/methods method])))
                  {:juxt.site/request-context req
                   :resource resource
                   :method method}))))

          operation (when operation-id
                      (xt/entity db operation-id))
          _ (when-not operation
              (when (not= method :options)
                (throw
                 (ex-info
                  (format "No such operation: %s" operation-id)
                  {:juxt.site/request-context req
                   :missing-operation operation-id
                   :resource resource
                   :method method}))))

          permissions
          (when operation-id
            (check-permissions
             db
             operation-id
             (cond-> {}
               ;; When the resource is in the database, we can add it to the
               ;; permission checking in case there's a specific permission for
               ;; this resource.
               subject (assoc :juxt.site/subject subject)
               resource (assoc :juxt.site/resource resource)
               scope (assoc :juxt.site/scope scope))))]

      (cond
        (seq permissions)
        (h (assoc req :juxt.site/operation operation :juxt.site/permissions permissions))

        (= method :options) (h req)

        subject
        (throw
         (ex-info
          (format "No permission for this operation (%s) with subject (%s) and scope (%s)"
                  operation-id (:xt/id subject) scope)
          {:ring.response/status 403
           :ring.response/headers {"access-control-allow-origin" "*"}
           :operation operation-id
           :subject subject
           :scope scope
           :juxt.site/request-context req}))

        ;; We are in a protection space, so this is HTTP Authentication (401
        ;; + WWW-Authenticate header)
        (:juxt.site/protection-spaces resource)
        (let [protection-spaces (:juxt.site/protection-spaces resource)]
          (throw
           (ex-info
            (format "No anonymous permission for operation (try authenticating!): %s" (pr-str operation))
            {:ring.response/status 401
             :ring.response/headers
             {"www-authenticate" (http-authn/www-authenticate-header db protection-spaces)
              "access-control-allow-origin" "*"}
             :juxt.site/request-context req})))

        (:juxt.site/session-scope req)
        ;; We are inside a session-scope. Therefore, we can
        ;; respond with a redirect to a page that will establish (immediately
        ;; or eventually), the cookie.
        (let [session-scope (:juxt.site/session-scope req)
              login-uri (:juxt.site/login-uri session-scope)
              redirect (str
                        login-uri
                        "?return-to="
                        (codec/url-encode
                         (cond-> uri
                           (not (str/blank? (:ring.request/query req)))
                           (str "?" (:ring.request/query req)))))]
          ;; If we are in a session-scope that contains a login-uri, let's redirect to that
          ;;                (def req req)
          (throw
           (ex-info
            (format "No anonymous permission for operation (try logging in!): %s" (pr-str operation))
            {:ring.response/status 302
             :ring.response/headers {"location" redirect}
             :juxt.site/request-context req})))

        :else
        ;; We are outside a protection space, there is nothing we can do
        ;; except return a 403 status.

        ;; We MUST NOT return a 401 UNLESS we can
        ;; set a WWW-Authenticate header (which we can't, as there is no
        ;; protection space). 403 is the only option afforded by RFC 7231: "If
        ;; authentication credentials were provided in the request ... the
        ;; client MAY repeat the request with new or different credentials. "
        ;; -- Section 6.5.3, RFC 7231
        (throw
         (ex-info
          (format "No anonymous permission for operation: %s" (pr-str operation))
          {:ring.response/status 403
           :ring.response/headers {"access-control-allow-origin" "*"}
           :juxt.site/request-context req}))))))

(comment
  (sci/eval-string
   "(+ (clojure.core/rand) 10)"
   {:namespaces {'clojure.core {'rand (constantly 0.5)}}
    ;;:deny '[+]
    }
   ))
