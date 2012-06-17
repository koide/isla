(ns isla.test.interpreter
  (:use [isla.interpreter])
  (:use [isla.parser])
  (:use [clojure.test])
  (:use [clojure.pprint]))


(deftest integer-assignment
  (let [result (interpret (parse "isla is 1"))]
    (is (= (get (:ctx result) "isla")
           1))))

(deftest test-single-invoke-returns-return-value
  (let [result (interpret (parse "write 2"))]
    (is (= (:ret result) 2))))

(deftest test-next-expression-overwrites-ret-of-previous
  (let [result (interpret (parse "write 2\nwrite 3"))]
    (is (= (:ret result) 3))))

(deftest test-second-not-returning-expression-removes-ret-value-of-prev
  (let [result (interpret (parse "write 2"))]
    (is (= (:ret result) 2)) ;; check first would have ret val

    ;; run test
    (let [result (interpret (parse "write 2\nage is 1"))]
    (is (nil? (:ret result))))))

(deftest test-write-assigned-value
  (let [result (interpret (parse "name is 'mary'\nwrite name"))]
    (is (= (:ret result) "mary"))))
