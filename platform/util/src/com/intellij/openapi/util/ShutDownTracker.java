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
package com.intellij.openapi.util;

import com.intellij.openapi.diagnostic.Logger;

import java.util.ArrayList;
import java.util.List;

public class ShutDownTracker implements Runnable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.ShutDownTracker");
  private static ShutDownTracker ourInstance;
  private final List<Thread> myThreads = new ArrayList<Thread>();
  private final List<Thread> myShutdownThreads = new ArrayList<Thread>();
  private final List<Runnable> myShutdownTasks = new ArrayList<Runnable>();
  private volatile boolean myIsShutdownHookRunning = false;

  private ShutDownTracker() {
    //noinspection HardCodedStringLiteral
    Runtime.getRuntime().addShutdownHook(new Thread(this, "Shutdown tracker"));
  }

  public static synchronized ShutDownTracker getInstance() {
    if (ourInstance == null) {
      ourInstance = new ShutDownTracker();
    }
    return ourInstance;
  }

  public static boolean isShutdownHookRunning() {
    return getInstance().myIsShutdownHookRunning;
  }

  public void run() {
    myIsShutdownHookRunning = true;
    Thread[] threads = getStopperThreads();
    while (threads.length > 0) {
      Thread thread = threads[0];
      if (!thread.isAlive()) {
        if (isRegistered(thread)) {
          LOG.error("Thread '" + thread.getName() + "' did not unregister itself from ShutDownTracker.");
          unregisterStopperThread(thread);
        }
      }
      else {
        try {
          thread.join(100);
        }
        catch (InterruptedException e) {
        }
      }
      threads = getStopperThreads();
    }

    while (!myShutdownTasks.isEmpty()) {
      //  task can change myShutdownTasks
      final Runnable task = myShutdownTasks.remove(myShutdownTasks.size()-1);
      try {
        task.run();
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    for (int idx = myShutdownThreads.size() - 1; idx >= 0; idx--) {
      final Thread thread = myShutdownThreads.remove(idx);
      thread.start();
      try {
        thread.join();
      }
      catch (InterruptedException e) { }
    }
  }

  private synchronized boolean isRegistered(Thread thread) {
    return myThreads.contains(thread);
  }

  private synchronized Thread[] getStopperThreads() {
    return myThreads.toArray(new Thread[myThreads.size()]);
  }

  public synchronized void registerStopperThread(Thread thread) {
    myThreads.add(thread);
  }

  public synchronized void unregisterStopperThread(Thread thread) {
    myThreads.remove(thread);
  }

  public void registerShutdownThread(final Thread thread) {
    myShutdownThreads.add(thread);
  }

  public void registerShutdownThread(int index, final Thread thread) {
    myShutdownThreads.add(index, thread);
  }

  public void registerShutdownTask(Runnable task) {
    myShutdownTasks.add(task);
  }

  public void unregisterShutdownTask(Runnable task) {
    myShutdownTasks.remove(task);
  }
}
