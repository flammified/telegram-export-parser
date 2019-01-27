(ns telegram-export-parser.core
  (:require [clojure.string :as string]
            [clojure.java.jdbc :refer :all]
            [pl.danieljanus.tagsoup :as tagsoup]))


(defn create-db-specs [filename]
  {:classname   "org.sqlite.JDBC"
   :subprotocol "sqlite"
   :subname     filename})

(defn create-db
  "create db and table"
  [db]
  (try (db-do-commands db
                       (create-table-ddl :chatmessages
                                         [[:timestamp :datetime]
                                          [:message :text]
                                          [:username :text]]))
       (catch Exception e
         (println (.getMessage e)))))


(defn parse-block [block]
  {:tag (first block) :text (second block) :children (drop 2 block)})


(defn is-chat [block]
  (println "START" block)
  (let [[tag meta] block
        class (:class meta)]

    (if (contains? (set (string/split class #" ")) "service")
      false
      (let [{tag :tag children :children} (parse-block block)
            [userpic body] children
            {children-body :children} (parse-block body)
            [time-block from-block text-block] children-body
            _ (println (parse-block text-block))
            {:keys [tag text children]} (parse-block text-block)
            _2 (println text)]

        (if (contains? (set (string/split (:class text) #" ")) "text")
          true
          false)))))


(defn parse-message [message]
  (let [{:keys [children]} (parse-block message)
        [userpic body] children
        {children-body :children} (parse-block body)
        [time from text] children-body]
    {:title (:title (second time)) :from (nth from 2) :text (nth text 2)}))



(defn get-messages [root]
  (let [{tag :tag children :children} (parse-block root)
        [_ body] children
        {children-body :children} (parse-block body)
        [page-wrap] children-body
        {children-page-wrap :children} (parse-block page-wrap)
        [page-header page-body] children-page-wrap
        {children-page-body :children} (parse-block page-body)
        [history] children-page-body
        {history-children :children} (parse-block history)
        [link & messages] history-children
        all-messages (drop-last messages)] ;; Drop final link

    (->> all-messages
      (filter is-chat)
      (map parse-message))))





(defn parse-file [file db]
  (let [contents (slurp file)
        html (tagsoup/parse-string contents)]
    (println (get-messages html))))


(defn parse-directory [directory db-specs]
  (let [files (filter
                #(.isFile %)
                (file-seq (clojure.java.io/file directory)))]
    (doall (map #(parse-file %1 db-specs) files))))



(def usage
  (->> ["Parser for telegram HTML logs"
        ""
        "Usage: parser directory"
        ""]
       (string/join \newline)))

(defn -main [& args]
  (if (= (count args) 2)
    (let [db-specs (create-db-specs (second args))]
      (parse-directory (first args) db-specs))
    (print usage)))
