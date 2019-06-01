(ns telegram-export-parser.core
  (:require [clojure.string :as str]
            [clojure.java.jdbc :refer :all]
            [pl.danieljanus.tagsoup :as tagsoup]
            [clojure.tools.cli :refer [parse-opts]])
  (:gen-class))

(defn create-db-specs [filename]
  {:classname   "org.sqlite.JDBC"
   :subprotocol "sqlite"
   :subname     filename})

(defn create-db!
  [db]
  (try
    (db-do-commands db (create-table-ddl :chatmessages [[:message :text] [:time :datetime]]))
    (catch Exception e
      (println (.getMessage e)))))

(defn meta-to-keywords [{:keys [class]}]
    (map keyword (str/split class #" ")))

(defn children->map [[_ _ & root-children]]
  (reduce
    (fn [result [tag meta & children :as child]]
      (case tag
        :div (reduce #(assoc %1 %2 child) result (meta-to-keywords meta))
        (assoc result tag child)))
    {}
    root-children))

(defn navigate [dom path]
  (if (empty? path)
    dom
    (let [[next & rest] path
          children-map (children->map dom)]
      (recur (get children-map next) rest))))

(defn replace-blocks-with-original-text [text]
  (reduce
    (fn [string item]
      (if (vector? item)
        (str string (str/join " " (drop 2 item)))
        (str string item)))
    ""
    text))

(defn remove-blocks [text]
  (reduce
    (fn [string item]
      (if (vector? item)
        string
        (str string item)))
    ""
    text))

(defn username-in-list? [usernames username]
  (some? ((set usernames) (str/trim username))))

(defn parse-message [opts message]
  (let [[_ _ & text] (navigate message [:body :text])
        [_ {datetime :title} & _] (navigate message [:body :details])
        [_ _ & username] (navigate message [:body :from_name])]
    (if (and (some? text) (some? datetime) (not (username-in-list? (:filter opts) (str/join "" username))))
      {:message (-> text
                    ((fn [item] (if (:filter-html opts) item (remove-blocks item))))
                    ((fn [item] (if (:extract-link-text opts) (replace-blocks-with-original-text item) item)))
                    (str/trim)
                    (str/lower-case))
       :time datetime}
      nil)))

(defn get-text-of-messages [root opts]
  (let [[_ _ & messages] (navigate root [:body :page_wrap :page_body :history])]
    (->> messages
         (map (partial parse-message opts))
         (filter some?)
         (filter #(not (str/blank? (:message %)))))))

(defn insert-into-db! [db {:keys [message time]}]
  (insert! db :chatmessages {:message message :time time}))

(defn parse-file! [file db opts]
  (let [contents (slurp file)
        html (tagsoup/parse-string contents)]
    (doall (map (partial insert-into-db! db) (get-text-of-messages html opts)))))

(defn parse-directory! [directory db-specs opts]
  (let [files (filter
                #(.isFile %)
                (file-seq (clojure.java.io/file directory)))]
    (doall (map #(parse-file! %1 db-specs opts) files))))

(def usage
  (->> ["Parser for telegram HTML logs"
        ""
        "Usage: ./parser <directory> <dbfilename> <options>"
        "E.g. ./parser logs messages.db"
        ""
        "Options: "
        ""]
       (str/join \newline)))

(def cli-options
  [["-u" "--filter-html" "Remove html blocks. Defaults to false."]
   ["-e" "--extract-link-text" "Extract text from hyperlinks. Defaults to false."]
   ["-f" "--filter" "Filters a specific username. Can be used multiple times."
    :default []
    :assoc-fn (fn [m k username] (update-in m k conj username))]])

(defn run-program! [directory filename opts]
  (let [db (create-db-specs filename)]
    (create-db! db)
    (parse-directory! directory db opts)))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (if errors
      (println (str/join " " errors))
      (if (>= (count arguments) 2)
        (if (> (count options) 1)
          (println (str usage summary \newline \newline "You can only choose one option."))
          (run-program! (first args) (second args) options))
        (println (str usage summary))))))
