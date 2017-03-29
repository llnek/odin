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
        [czlab.loki.net.disp]
        [czlab.basal.format]
        [czlab.basal.core]
        [czlab.basal.io]
        [czlab.basal.str]
        [czlab.loki.sys.session])

  (:import [czlab.jasal Identifiable Sendable Dispatchable]
           [java.util.concurrent.atomic AtomicInteger]
           [czlab.loki.game Game Info Arena]
           [czlab.loki.sys Session]
           [czlab.wabbit.ctl Pluglet]
           [czlab.loki.net Events Subr PubSub]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(def ^:private _latch_mutex_ (Object.))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype Subr [data]
  Object
  (toString [me] (.id me))
  Identifiable
  (id [_] (:id data))
  Receivable
  (receive [me evt]
    (when (= (:type (.state me))
             (:type evt))
      (log/debug "[%s]: recv'ed msg: %s" me evt)
      (send! (:session (.state me)) evt)))
  Stateful
  (state [_] data))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro defsubr "" [session]
  (let [id (str "subr#" (seqint2))]
    `(Subr. (atom {:type Events/PUBLIC
                   :session ~session
                   :id ~id}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn createSubr "" [session]
  (defsubr session))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fmtStartBody [^Game impl sessions]
  (preduce<map>
    #(let [^Session s %2
           sn (.number s)
           y (.player s)
           yid (.id y)
           g (.playerGist impl yid)]
       (assoc! %1
               (keyword yid)
               (merge {:pnum sn} g))) sessions))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype Room [data]
  Identifiable
  (id [_] (:id @data))
  IDeref
  (deref [_] @data)
  Stateful
  (state [_] data))

(defmacro defarena "" [gameInfo finzer]
  (let [rid (str "room#" (seqint2))]
    `(Room. (atom {:numctr (AtomicInteger.)
                   :shutting? false
                   :opened? false
                   :active? false
                   :id ~rid}))))

(defn connect "" [room player arg]
  (let [{:keys [conns numctr]}
        @room
        n (. ^AtomicInteger
             numctr
             incrementAndGet)
        s (defconn room
                   player
                   (merge arg
                          {:number n}))]
    (swap! conns assoc (.id s) s)
    (addTo @player s)
    s))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn arena<>
  "" ^Arena [^Info gameObj finzer {:keys [source]}]

  (let [state (atom {:shutting? false
                     :opened? false
                     :enabled? false})
        ctr (.server ^Pluglet source)
        pcount (AtomicInteger.)
        crt (.cljrt ctr)
        sessions (atom {})
        disp (dispatcher<>)
        created (now<>)
        rid (uid<>)
        latch (atom nil)]

    (reify Arena

      (countPlayers [_] (count @sessions))

      (disconnect [_ ps]
        (let [py (.player ps)]
          (swap! sessions dissoc (.id ps))
          (.removeSession py ps)
          (.unsubscribeIfSession disp ps)))

      (isShuttingDown [_] (bool! (:shutting? @state)))

      (canOpen [this]
        (and (not (:opened? @state))
             (>= (.countPlayers this)
                 (.minPlayers gameObj))))

      (game [_] gameObj)
      (id [_] rid)

      (close [_]
        (log/debug "closing room(arena) [%s]" rid)
        (doseq [^Session v (vals @sessions)]
          (-> (.player v)
              (.removeSession v))
          (closeQ v))
        (finzer rid))

      (open [this]
        (let [sss (sort-by #(.number ^Session %)
                           (vals @sessions))
              ^Game
              g (.callEx crt
                         (strKW (.implClass gameObj))
                         (vargs* Object this sss))]
          (log/debug "activating room %s" rid)
          (swap! state assoc :opened? true :impl g)
          (doseq [s sss]
            (.addHandler this (localSubr<> s)))
          (reset! latch @sessions)
          (.init g nil)
          (->> (fmtStartBody g sss)
               (publicEvent<> Events/START)
               (.broadcast this))))

      (removeHandler [_ h] (.unsubscribe disp h))
      (addHandler [_ h] (.subscribe disp h))

      (broadcast [_ evt] (.publish disp evt))

      (send [this msg]
        (cond
          (isPrivate? msg) (some-> ^Sendable
                                   (:context msg) (.send msg))
          (isPublic? msg) (.broadcast this msg)))

      (isActive [_] (bool! (:enabled? @state)))

      (restart [this arg]
        (log/debug "arena#restart() called")
        (->> (-> (:impl @state)
                 (fmtStartBody (vals @sessions)))
             (publicEvent<> Events/RESTART)
             (.broadcast this)))
      (restart [_] (.restart _ nil))

      (start [_ arg]
        (log/info "arena#start called")
        (swap! state assoc :enabled? true)
        (.start ^Game (:impl @state) arg))
      (start [_] (.start _ nil))

      (stop [_]
        (swap! state assoc :enabled? false))

      (receive [this evt]
        (when (:opened? @state)
          (log/debug "room got an event %s" evt)
          (cond
            (isPublic? evt)
            (.broadcast this evt)

            (isPrivate? evt)
            (let [{:keys [context body]}
                  evt
                  ss (cast? Session context)]
              (assert (some? ss))
              (cond
                (and (not (.isActive this))
                     (isCode? Events/REPLAY evt))
                (locking _latch_mutex_
                  (when (empty? @latch)
                    (reset! latch @sessions)
                    (.restart this)))
                (and (not (.isActive this))
                     (isCode? Events/STARTED evt))
                (if (in? @latch (.id ss))
                  (locking _latch_mutex_
                    (log/debug "latch: take-off: %s" (.id ss))
                    (swap! latch dissoc (.id ss))
                    (if (empty? @latch)
                      (.start this (readJsonStrKW body)))))
                (and (.isActive this)
                     (some? @latch)
                     (empty? @latch))
                (let [rc (.onEvent ^Game
                                   (:impl @state) evt)]
                  (when (and (isQuit? evt)
                             (= rc Events/TEAR_DOWN))
                    (->> (publicEvent<> Events/PLAY_SCRUBBED
                                        {:pnum (.number ss)})
                         (.broadcast this))
                    (pause 1000)
                    (.close this))))))))

      Object

      (hashCode [this] (.hashCode rid))

      (equals [this obj]
        (if (nil? obj)
          false
          (or (identical? this obj)
              (and (= (.getClass this)
                      (.getClass obj))
                   (= (.id ^Identifiable obj)
                      (.id this)))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


