/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright (c) 2013-2016, Kenneth Leung. All rights reserved. */

package czlab.loki.core;

import czlab.xlib.Disposable;
import czlab.xlib.Initable;

/**
 * @author kenl
 */
public interface Engine extends Initable, Disposable {

  //life cycle of engine
  //1. initialize
  //2. ready
  //3. start/restart
  public Object ready(Room room);

  /**
   */
  public Object restart(Object arg);

  /**
   */
  public Object start(Object arg);

  /**
   */
  public void startRound(Object arg);

  /**
   */
  public void endRound(Object any);

  /**
   */
  public void stop();

  /**
   */
  public void update(Object event);

  /**
   */
  public Object state();

  /**
   */
  public Object server();

}


