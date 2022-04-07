; (use 'wordle-solver.core :reload) ;; LAZY RELOAD

(ns wordle-solver.core
  (:gen-class))

(require '[clojure.string :as str])
(require '[clojure.set])

;; LOAD 
(def answers-filename "wordle-answers.txt")
(def allowed-guesses-filename "wordle-allowed-guesses.txt")

(def dict-answers (str/split (slurp answers-filename) #"\n"))
(def dict-allowed-guesses (str/split (slurp allowed-guesses-filename) #"\n"))


;; MASKS
(def BLACK 0)
(def YELLOW 1)
(def GREEN 2)

(def mask-list (list BLACK YELLOW GREEN YELLOW BLACK))

; returns () or (\a \d \d) etc.
(defn apply-mask-to-word [mask-list seq-word val]
  (filter identity (map #(if (= val %1) %2) mask-list seq-word)))

;; TODO This is gross and not general but who cares
(defn generate-all-mask-lists [n]
  (if (= 1 n) '((0) (1) (2))
      (let [children (generate-all-mask-lists (dec n))
            result
            (reduce concat (list 
             (map #(conj % BLACK) children)
             (map #(conj % YELLOW) children)
             (map #(conj % GREEN) children)))]
        result)))

(def all-mask-lists (generate-all-mask-lists 5))

(defn _gen-includer-filter [letter]
  (partial filter #(str/includes? % letter)))

; accepts sequences of chars
(defn gen-includer-filters [yellow-list]
  (reduce comp
          (vec
           (map _gen-includer-filter
                (map str yellow-list)))))

(defn _generate-regex-entry [black-list mask-elt word-elt]
  (cond (= mask-elt GREEN) word-elt
        (= mask-elt YELLOW) (apply str "[^" black-list word-elt "]")
        :else   (apply str "[^" black-list "]")))

(defn filter-fn-from-mask-list-and-word [w-word mask-list]
  (let [seq-word (seq w-word)
        black-list (apply-mask-to-word mask-list seq-word BLACK)
        yellow-list (apply-mask-to-word mask-list seq-word  YELLOW)
        green-list (apply-mask-to-word mask-list seq-word GREEN)
        regex-str (apply str
                         (map (partial _generate-regex-entry (apply str black-list))
                              mask-list w-word))
        filter-regex (partial filter #(re-matches (re-pattern regex-str) %))
        filter-includes (gen-includer-filters yellow-list)
        ]
        (comp filter-regex filter-includes)
        ))
        


(def fn-d (partial filter #(str/includes? % "D")))
(def fn-reg1 #(filter (fn [w] (re-matches #"[^ER][^ER]A[^S]E" w)) %))

(defn calculate-entropy-numerator [result-set]
  (let [c (count result-set)]
    (cond (= 0 c) 0
          (= 1 c) (- c)
          :else  (/ (* (Math/log c) c) (Math/log 2)))))

(defn evaluate-move [all-mask-lists dict-answers w-word]
  (let [matching-words (map
                        #((filter-fn-from-mask-list-and-word w-word %)
                          dict-answers) all-mask-lists)
        ;; Klugey fix here.  Total words should always be dict-answers
        ;; but some patterns overlap so words are doubled.
        ;; This at least fixes the denominator
        total-words (apply + (map count matching-words))
        n-entropy-numerator (apply +
                                   (map calculate-entropy-numerator
                                        matching-words))
        n-entropy (/ n-entropy-numerator total-words)
        ]
    {:entropy n-entropy
     :matches (zipmap all-mask-lists matching-words)}))

;; sorted list of:
;; ["aahed"
;;  {:entropy 7.78318320736531353,
;;   :MATCHES
;;   {(2 0 2 2 0) ("ashen"),
;;     (2 0 2 0 0) ("abhor"),
;;    (1 2 0 2 1) ("cadet" "laden"),
(defn evaluate-all-moves [dict-answers dict-allowed-guesses]
  (let [all-mask-lists (generate-all-mask-lists 5)
        results (zipmap
                 dict-allowed-guesses
                 (map (partial evaluate-move all-mask-lists dict-answers)
                      dict-allowed-guesses))
        sorted-results (sort
                        #(< (:entropy (second %1)) (:entropy (second %2)))
                        results)]
    sorted-results))

;; returns dict-answers
(defn extract-row-from-results [r str-word]
  (filter #(= str-word (first %)) r))

(defn just-words-and-entropy [evaluations]
  (map #(list (first %) (first (second %))) evaluations))

(defn play-move [dict-answers dict-allowed-guesses r-evals str-word l-mask]
  (let [entry (extract-row-from-results r-evals str-word)] 
    (if (nil? entry) nil
        (-> entry first second :matches (get l-mask)))))


(defn viable-answer-words [l-answers r-evals] (filter #(get (set l-answers) (first %))
                                    (just-words-and-entropy r-evals)))

(defn intersect-blocks [a b]
   (into '()  (clojure.set/intersection (set a) (set b))))

(defn drop-nth-from-seq [n seq-w]
  (concat
    (take n seq-w)
    (drop (inc n) seq-w)))


(defn select-from-word [indices w]
  (let [s (seq w)]
  (map #(nth s %) indices)))


(defn select-similar-block [l-greens min-ct l-answers] 
 (mapcat 
   second
   (filter
     (fn [[k v]] (>= (count v) min-ct))
       (group-by (partial select-from-word l-greens)
     dict-answers))))

(defn select-anagrams [l-answers] 
 (mapcat 
   second
   (filter
     (fn [[k v]] (>= (count v) 2))
       (group-by (comp seq sort)
                 dict-answers))))

;; USAGE

#_(do
	;; DO ONCE ON INIT
		(def l-answers dict-answers)
		(def l-allowed-guesses dict-allowed-guesses)
		(def r-top   (evaluate-all-moves l-answers l-allowed-guesses)) ;; first run takes 10-15 minutes.
		(def r-evals r-top)

		;; FOR ANY GIVEN STEP
  (evaluate-all-moves l-answers l-allowed-guesses)
  (def r-evals *1)
  (pprint (take 10 (just-words-and-entropy r-evals)))

  (pprint (take 10 (viable-answer-words l-answers r-evals)))

  ;; MAKE YOUR CHOICE
	  (def w-word "cleat")
	  (def response-mask '(0 0 2 2 0))
	  (play-move l-answers l-allowed-guesses r-evals w-word response-mask)
	  (def l-answers *1)
	  
)
