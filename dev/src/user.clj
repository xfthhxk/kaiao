(ns user)


;; ------------------------------------------------------------------------
;; WARNING         WARNING         WARNING         WARNING         WARNING
;;
;;  This namespace is always loaded even in CI so can't put any code in here
;;  to auto start system
;;
;; WARNING         WARNING         WARNING         WARNING         WARNING
;; ----------------------------------------------------------------------

(defn dev
  "Load and switch to the 'dev' namespace."
  []
  (require 'dev)
  (in-ns 'dev)
  :loaded)
