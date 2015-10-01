(defproject sample-app "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [compojure "1.4.0"]
                 [com.hendrick/avenue "0.1.0"]
                 [hiccup "1.0.5"]
                 [ring/ring-defaults "0.1.2"]]
  :ring {:handler sample-app.core/app}
  :plugins [[lein-ring "0.9.7"]])
