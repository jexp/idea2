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
package com.intellij.xdebugger.impl.frame;

import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionAdapter;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.openapi.Disposable;

/**
 * @author nik
 */
public abstract class XDebugViewBase implements Disposable {
  protected enum SessionEvent {PAUSED, BEFORE_RESUME, RESUMED, STOPPED, FRAME_CHANGED, SETTINGS_CHANGED}
  protected final XDebugSession mySession;
  private final MyDebugSessionListener mySessionListener;

  public XDebugViewBase(final XDebugSession session, Disposable parentDisposable) {
    mySession = session;
    mySessionListener = new MyDebugSessionListener();
    mySession.addSessionListener(mySessionListener);
  }

  public void rebuildView() {
    onSessionEvent(SessionEvent.SETTINGS_CHANGED);
  }

  private void onSessionEvent(final SessionEvent event) {
    DebuggerUIUtil.invokeOnEventDispatch(new Runnable() {
      public void run() {
        rebuildView(event);
      }
    });
  }

  protected abstract void rebuildView(final SessionEvent event);

  public void dispose() {
    mySession.removeSessionListener(mySessionListener);
  }

  private class MyDebugSessionListener extends XDebugSessionAdapter {
    public void sessionPaused() {
      onSessionEvent(SessionEvent.PAUSED);
    }

    public void sessionResumed() {
      onSessionEvent(SessionEvent.RESUMED);
    }

    public void sessionStopped() {
      onSessionEvent(SessionEvent.STOPPED);
    }

    public void stackFrameChanged() {
      onSessionEvent(SessionEvent.FRAME_CHANGED);
    }

    public void beforeSessionResume() {
      onSessionEvent(SessionEvent.BEFORE_RESUME);
    }
  }
}
