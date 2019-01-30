(ns telegram-export-parser.core
  (:require [clojure.string :as str]
            [clojure.java.jdbc :refer :all]
            [pl.danieljanus.tagsoup :as tagsoup]))


(defn create-db-specs [filename]
  {:classname   "org.sqlite.JDBC"
   :subprotocol "sqlite"
   :subname     filename})

(defn create-db
  "create db and table"
  [db]
  (try (db-do-commands db (create-table-ddl :chatmessages [[:message :text]]))

       (catch Exception e
         (println (.getMessage e)))))


(defn parse-block [block]
  {:tag (first block) :text (second block) :children (drop 2 block)})

(defn meta-to-keywords [{:keys [class]}]
  (map keyword (str/split class #" ")))

(defn children->map [[root-tag root-meta & root-children]]
  (reduce
    (fn [result [tag meta & children :as child]]
      (case tag
        :div (reduce #(assoc %1 %2 child) {} (meta-to-keywords meta))
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

(defn parse-message [message]
  (let [[tag class & text] (navigate message [:body :text])]
    (if (some? text)
      (str/trim (replace-blocks-with-original-text text))
      nil)))

(defn get-text-of-messages [root]
  (let [[_ _ & messages] (navigate root [:body :page_wrap :page_body :history])]
    (->> messages
         (map parse-message)
         (filter some?))))

(defn insert-into-db [db message]
  (insert! db :chatmessages {:message message}))


(defn parse-file [file db]
  (let [contents (slurp file)
        html (tagsoup/parse-string contents)]
    (doall (map #(insert-into-db db %) (get-text-of-messages html)))))

(defn parse-directory [directory db-specs]
  (let [files (filter
                #(.isFile %)
                (file-seq (clojure.java.io/file directory)))]
    (doall (map #(parse-file %1 db-specs) files))))



(def usage
  (->> ["Parser for telegram HTML logs"
        ""
        "Usage: parser directory dbfilename"
        ""]
       (str/join \newline)))

(defn -main [& args]
  (if (= (count args) 2)
    (let [db-specs (create-db-specs (second args))]
      (create-db db-specs)
      (parse-directory (first args) db-specs))
    (print usage)))
