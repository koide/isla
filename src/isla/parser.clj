(ns isla.parser
  (:use [clojure.pprint])
  (:use [isla.lexer])
  (:require [clojure.string :as str]))

(declare parse nnode
         alternatives is-type pattern-sequence pattern
         pattern-sequence-selector one-token-pattern
         -root -block -expression -slot-assignment -type-assignment -assignment -invocation
         -nl -integer -is-a -is -string -assignee -value -identifier)

(defn parse [code]
  (-root (lex code)))

(defn -root [tokens]
  (nnode :root [(-block tokens [])]))

(defn -block [tokens collected]
  (if-let [{node :node left-tokens :left-tokens}
           (-expression tokens [])]
    (-block left-tokens (conj collected node)) ;; add expr, continue collecting more
    (nnode :block collected))) ;; no more exprs, return block

;; expressions

(defn -expression [tokens collected]
  (pattern-sequence-selector tokens [-slot-assignment -type-assignment
                                     -assignment -invocation]
                             :expression))

(defn -slot-assignment [tokens]
  (if-let [{nodes :nodes left-tokens :left-tokens}
           (pattern-sequence tokens [-assignee -identifier -is -value -nl] [])]
    {:node (nnode :slot-assignment (take 4 nodes)) :left-tokens left-tokens}
    nil))

(defn -type-assignment [tokens]
  (if-let [{nodes :nodes left-tokens :left-tokens}
           (pattern-sequence tokens [-assignee -is-a -identifier -nl] [])]
    {:node (nnode :type-assignment (take 3 nodes)) :left-tokens left-tokens}
    nil))

(defn -assignment [tokens]
  (if-let [{nodes :nodes left-tokens :left-tokens}
           (pattern-sequence tokens [-assignee -is -value -nl] [])]
    {:node (nnode :assignment (take 3 nodes)) :left-tokens left-tokens}
    nil))

(defn -invocation [tokens]
  (if-let [{nodes :nodes left-tokens :left-tokens}
           (pattern-sequence tokens [-identifier -value -nl] [])]
    {:node (nnode :invocation (take 2 nodes)) :left-tokens left-tokens}
    nil))

;; atoms

(defn -is-a [tokens]
  (let [one (first tokens) two (second tokens)]
    (if (and (string? one) (string? two))
      (if (is-type #"is a" (str one " " two))
        {:node (nnode :is-a [:is-a]) :left-tokens (nthrest tokens 2)}))))

(defn -nl [tokens] (one-token-pattern tokens :nl :nl))

(defn -is [tokens]
  (one-token-pattern tokens #"is" :is (fn [x] [:is])))

;; values

(defn -value [tokens]
  (pattern-sequence-selector tokens [-string -integer -identifier] :value))

(defn -assignee [tokens] (one-token-pattern tokens #"[A-Za-z]+" :assignee))

(defn -identifier [tokens] (one-token-pattern tokens #"(?!^is$)[A-Za-z]+" :identifier))

(defn -integer [tokens]
  (one-token-pattern tokens #"[0-9]+" :integer
                     (fn [x] [(Integer/parseInt (first tokens))])))

(defn -string [tokens]
  (one-token-pattern tokens #"'[A-Za-z0-9 ]+'" :string
                     (fn [x] [(str/replace (first tokens) "'" "")])))

;; helpers

(defmulti is-type (fn [matcher tokens] (class matcher)))

(defmethod is-type clojure.lang.Keyword [symbol token]
  (= symbol token))

(defmethod is-type java.util.regex.Pattern [re token]
  (if (keyword? token) ;; looking for string but potential is kw so not a match
    false
    (if (not= nil (re-matches re token))
      true
      false)))

(defn alternatives [input types]
  (vec (map (fn [t] (t input)) ;; get nodes for each matching type
            (filter (fn [t] (not= nil (t input))) ;; get matching types
                    types))))

(defn pattern-sequence-selector [tokens pattern-sequences tag]
  (def alts (alternatives tokens pattern-sequences))
  (if (> (count alts) 0)
    (let [{node :node left-tokens :left-tokens} (first alts)]
      {:node (nnode tag [node]) :left-tokens left-tokens}) ;; return alternative
    nil)) ;; no alternatives match - return

(defn pattern-sequence [tokens patterns collected]
  (if-let [pattern (first patterns)]
    (if-let [{node :node left-tokens :left-tokens} (pattern tokens)] ;; matched token
      (pattern-sequence left-tokens (rest patterns) (conj collected node)) ;; round again
      nil) ;; pattern sequence element match failure - return
    {:nodes collected :left-tokens tokens})) ;; pattern matched - return

(defn one-token-pattern [tokens matcher tag & args]
  (if-let [token (first tokens)]
    (if (is-type matcher token)
      (let [output (if (not= nil args)
                     ((first args) token)
                     [token])]
        {:node (nnode tag output) :left-tokens (rest tokens)})
      nil)
    nil))

(defn nnode [tag data]
  {:tag tag :content data})
