# Avenue

Avenue is small set of macros for web apps that use Compojure. Primarily, it
provides a simple mechanism for authorization.

In a web app, most requests fall into one of a few categories. You have pages...

```Clojure
  (page
    "/admin/user-permissions"
    [:user.role/superAdmin]
    user-permissions-view/show)
```

You've got Ajax requests...
```Clojure
  (xhr
    POST "/admin/update-account"
    [:user.role/superAdmin]
    actions/update-account)
```

And you might have some form posts...
```Clojure
  (form-post
    "/admin/create-account"
    [:user.role/superAdmin]
    actions/create-account)
```

All Avenue does is provide a little shorthand for these common cases. You can
mix and match Avenue-style routes with regular ring routes.

## Usage / Arguments

Each of the `page`, `xhr`, and `form-post` macros require the same arguments,
with the exception that for `xhr` calls, you must specify the request's HTTP
method.

A call looks like this
```Clojure
(page|xhr|form-post
  http-method ;; xhr only
  url
  auth-data
  actions) ;; variable
```

The http-method and url arguments are just forwarded to Compojure/ring.  `auth-data` is
any data structure that identifies who is allowed to access this route.  The
contents of `auth-data` are not used by Avenue. Avenue will pass the contents
of `auth-data` to your `:auth-fn`

For example, you might have several routes that are only accessible by admin
users, you can indicate this with a keyword.

```Clojure
(page
  "/admin/do-secret-stuff"
  :adminOnly
  view/show-secret-page)
```

You might have some other routes that are accessible to everyone.

```Clojure
(page
  "/welcome"
  :allowEveryone
  view/welcome-page)
```

The keywords you choose are actually opaque to avenue, you specify their meaning in an `:auth-fn`.

```Clojure
(defn -main [& {:as args}]
  (let [app (-> routes
                (av/wrap-auth {:auth-fn (fn [allowed-roles req]
                                          (condp = allowed-roles
                                            :allowEveryone true
                                            :adminOnly (:is-admin (db/find-user (:user-id (:session req))))
                                            false))
                               :authorized-page-fn show-page
                               :unauthorized-page-fn redirect-to-welcome-page})
                (handler/site {:session {:store (cookie/cookie-store {:key "this is a secret"})}}))]
    (web/run app args)))
```

If you find keywords aren't sufficient to express your auth requirements, you
can use any arbitrarily complex Clojure data structure. It's up to you to find
the `:auth-fn` and route data-structure that best suits your needs.

For a working example, check out the [sample app](./sample-app/src/sample_app/core.clj).

## Configuration

To use Avenue, you must include `wrap-auth` in your middleware stack. It takes
a few options:

`:auth-fn` is a function that returns a truthy or falsey value indicating
whether a user is allowed to issue a request.  As parameters, we'll pass the data
structure that's specified along with the route, and the request itself.

The data structure that gets passed to the auth-fn is exactly what's specified
in the route definition. It is the second argument of the `page` or `form-post`
macro, and the third argument of the `xhr` macro. Avenue does nothing with this
data except to pass it to the auth-fn.

`:authorized-page-fn` is a function that the `page` macro will call with the
result of the route's actions. It's ignored by the `xhr` and `form-post`
macros.

`:unauthorized-page-fn` is a function that any macro will call when the
`:auth-fn` returns a falsey value.

If you don't care much about auth, you can omit `:auth-fn` and
`:unauthorized-page-fn` from the configuration that's passed to
`wrap-auth`, and also drop the auth data structures from your routes.

## License

Copyright 2015 Hendrick Automotive Group

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
