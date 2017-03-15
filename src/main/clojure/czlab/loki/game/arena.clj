;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.loki.game.arena

  (:require [czlab.basal.logging :as log]
            [clojure.java.io :as io])

  (:use [czlab.loki.net.core]
        [czlab.basal.core]
        [czlab.basal.str])

  (:import [czlab.loki.game GameImpl GameMeta Arena]
           [czlab.loki.core Session]
           [czlab.loki.net Events]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- dummy2 [a b] nil)
(defn- dummy1 [a] nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn arena<>
  "" ^Arena [^GameImpl impl]

  (let [state (atom {})]
    (reify Arena
      (init [_ sessions]
        (swap! state
               assoc
               :sids (preduce<map> #(assoc! %1
                                            (.id ^Session %2) %2) sessions)
               :pids (preduce<map>
                       #(let [^Session s %2
                              pid (.. s player id)]
                          (assoc! %1
                                  pid [(. s number) pid])) sessions)
               :players sessions))
      (ready [this room]
        (log/debug "engine#ready() called")
        (swap! state assoc :room room)
        (->> (:pids @state)
             (eventObj<> Events/PUBLIC
                         Events/START)
             (.send (.container this))))

      (restart [this arg]
        (log/debug "engine#restart() called")
        (->> (:pids @state)
             (eventObj<> Events/PUBLIC
                         Events/RESTART)
             (.send (.container this))))
      (restart [_] (.restart _ nil))

      (start [_ arg]
        (log/info "engine#start called")
        (.start impl arg))
      (start [_] (.start _ nil))

      (stop [this]
        (->> (eventObj<> Events/PUBLIC
                         Events/STOP nil)
             (.send (.container this))))

      (update [this evt]
        (.onEvent impl
                  ^Session
                  (:context evt)
                  (dissoc evt :context)))

      (dispose [_])
      (state [_] @state)
      (container [_] (:room @state)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

