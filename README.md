# avenue

Avenue is small set of macros for web apps that use Compojure. Primarily, we provide a simple mechanism for authorization.

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
    "/cash-position/create"
    [:user.role/superAdmin :user.role/dataEntry]
    actions/edit-or-create-cash-position)
```

All avenue does is provide a little shorthand for these common cases. You can mix and match avenue-style routes with regular ring routes.

## Configuration

Avenue routes are depending on some middleware. Make sure to include `wrap-auth` in your middleware stack.

```Clojure
(defn -main [& {:as args}]
  (let [app (-> routes
                (av/wrap-auth {:auth-fn (fn [allowed-roles req]
                                          (let [user (db/find-user (:user-id (:session req)))]
                                            (some (partial = (:role user)) allowed-roles)))
                               :authorized-page-fn show-page
                               :unauthorized-page-fn redirect-to-welcome-page})
                (handler/site {:session {:store (cookie/cookie-store {:key "this is a secret"})}}))]
    (web/run app args)))
```

`wrap-auth` requires that you pass it a hash with some options.

`:auth-fn` is a function that returns a truthy or falsey value indicating whether a user is allowed to issue a request. As parameters, we'll pass a data structure that's specified along with the route, and the request itself.

The data structure that gets passed to the auth-fn is exactly what's specified in the route definition. It is the second argument of the `page` or `form-post` macro, and the third argument of the `xhr` macro. Avenue does nothing with this data except to pass it to the auth-fn.

`:authorized-page-fn` is a function that the `page` macro will call with the result of the route's actions.

`:unauthorized-page-fn` is a function that any macro will call when the `:auth-fn` returns a falsey value.

## Macro usage in detail

## License

Copyright © 2015 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
