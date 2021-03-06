/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.progress;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;

import java.util.LinkedList;
import java.util.Queue;

public abstract class AbstractTaskQueue<T> {
  private final static Logger LOG = Logger.getInstance("#com.intellij.openapi.progress.AbstractTaskQueue");

  private final Object myLock;
  private final Queue<T> myQueue;
  private boolean myActive;
  protected final Runnable myQueueWorker;

  public AbstractTaskQueue() {
    myLock = new Object();
    myQueue = new LinkedList<T>();
    myActive = false;
    myQueueWorker = new MyWorker();
  }

  /**
   * !!! done under lock! (to allow single failures when putting into the execution queue)
   * Should run {@link #myQueueWorker}
   */
  protected abstract void runMe();
  protected abstract void runStuff(T stuff);

  public void run(final T stuff) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      runStuff(stuff);
      return;
    }
    boolean impulseGiven = false;
    synchronized (myLock) {
      try {
        myQueue.add(stuff);
        if (! myActive) {
          runMe();
          impulseGiven = true;
        }
      } catch (Throwable t) {
        LOG.info(t);
        throw t instanceof RuntimeException ? ((RuntimeException) t) : new RuntimeException(t);
      } finally {
        if ((! myActive) && impulseGiven) {
          myActive = true;
        }
      }
    }
  }

  private class MyWorker implements Runnable {
    public void run() {
      while (true) {
        try {
          final T stuff;
          synchronized (myLock) {
            stuff = myQueue.poll();
          }
          // each task is executed only once, once it has been taken from the queue..
          runStuff(stuff);
        } catch (Throwable t) {
          LOG.info(t);
        } finally {
          synchronized (myLock) {
            if (myQueue.isEmpty()) {
              myActive = false;
              return;
            }
          }
        }
      }
    }
  }
}
