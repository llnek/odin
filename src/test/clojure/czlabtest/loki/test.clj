;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.
;;
;; Copyright (c) 2013-2016, Kenneth Leung. All rights reserved.

(ns

  czlabtest.loki.test

  (:require [clojure.java.io :as io]
            [clojure.string :as cs])

  (:use [czlab.loki.event.core]
        [czlab.loki.game.core]
        [czlab.loki.game.player]
        [czlab.loki.game.room]
        [czlab.loki.game.reqs]
        [czlab.loki.game.session]
        [czlab.xlib.format]
        [czlab.xlib.core]
        [czlab.xlib.str]
        [clojure.test])

  (:import [czlab.wabbit.mock.test MockContainer MockIOService]
           [io.netty.handler.codec.http.websocketx
            WebSocketFrame
            TextWebSocketFrame]
           [czlab.loki.core Engine]
           [czlab.loki.event Events]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def
  ^:private
  evt-json
  "{\"type\" : 100, \"status\": 200, \"code\":111, \"body\": { \"a\" : 911 }}")
(def ^:private evt-body {:a 911})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def
  ^:private
  games-meta
  {:game-1
   {:layout "portrait",
    :name  "Test",
    :description "Fun!",
    :keywords "",
    :height  480,
    :width  320
    :pubdate #inst "2016-01-01"
    :author "llnek"
    :network {
      :enabled true
      :minp 2
      :maxp 2
      :engine  "czlabtest.loki.test/testEngine"}
    :status true
    :uri "/arena/test"
    :image "ui/catalog.png"}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn testEngine
  ""
  ^Engine
  [a b] nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftest czlabtestloki-test

  (is (do->true
        (initGameRegistry! games-meta)))

  (is (let [evt (eventObj<> 100 111 evt-body {:x 7})]
        (and (== 100 (:type evt))
             (== Events/OK (:status evt))
             (== 111 (:code evt))
             (map? (:body evt))
             (== 7 (:x evt)))))

  (is (let [evt (errorObj<> 100 111 evt-body {:x 7})]
        (and (== 100 (:type evt))
             (== Events/ERROR (:status evt))
             (== 111 (:code evt))
             (map? (:body evt))
             (== 7 (:x evt)))))

  (is (let [^TextWebSocketFrame
            evt (-> (eventObj<> 100 111 evt-body)
                    (encodeEvent ))
            s (.text evt)]
        (and (> (.indexOf s "100") 0)
             (> (.indexOf s "200") 0)
             (> (.indexOf s "111") 0))))

  (is (let [^TextWebSocketFrame
            evt (-> (eventObj<> 100 111)
                    (encodeEvent))
            s (.text evt)]
        (and (> (.indexOf s "100") 0)
             (> (.indexOf s "200") 0)
             (> (.indexOf s "111") 0))))

  (is (let [evt (decodeEvent evt-json {:x 3})]
        (and (== 100 (:type evt))
             (== 200 (:status evt))
             (== 111 (:code evt))
             (== 911 (get-in evt [:body :a])))))

  (is (let [g (lookupGame "game-1")]
        (some? g)))

  (is (let [c1 (lookupPlayer "u1" "p1")
            c2 (lookupPlayer "u1")
            c3 (removePlayer "u1")
            c4 (lookupPlayer "u1")]
        (and (some? c1)
             (identical? c1 c2)
             (some? c3)
             (nil? c4))))

  (is (let [c1 (lookupPlayer "u1" "p1")
            _ (.setEmailId c1 "e")
            _ (.setName c1 "n")
            e (.emailId c1)
            n (.name c1)
            id (.id c1)
            cs (.countSessions c1)
            _ (.logout c1)
            c4 (lookupPlayer "u1")]
        (and (some? c1)
             (= e "e")
             (= n "n")
             (== 0 cs)
             (= id "u1")
             (nil? c4))))

  (is (let [s (doPlayReq {:source (MockIOService.)
                          :body {:gameid "game-1"
                                 :userid  "u1"
                                 :password "p1"}})
            r (some-> s (.room ))]
        (and (some? r)
             (not (.canActivate r)))))



  (is (string? "That's all folks!")))

;;(clojure.test/run-tests 'czlabtest.loki.czlabtestloki-test)

