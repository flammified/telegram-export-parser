(defproject telegram-export-parser "0.1.0-SNAPSHOT"
  :description "CLI parser for telegram HTML chat exports"
  :main telegram-export-parser.core
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/java.jdbc "0.7.8"]
                 [clj-tagsoup/clj-tagsoup "0.3.0"]
                 [org.xerial/sqlite-jdbc "3.7.2"]
                 [org.clojure/tools.cli "0.4.1"]
                 [progrock "0.1.2"]])
