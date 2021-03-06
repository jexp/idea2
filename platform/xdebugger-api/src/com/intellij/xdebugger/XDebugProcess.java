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

package com.intellij.xdebugger;

import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class XDebugProcess {
  private final XDebugSession mySession;
  private ProcessHandler myProcessHandler;

  protected XDebugProcess(@NotNull XDebugSession session) {
    mySession = session;
  }

  public final XDebugSession getSession() {
    return mySession;
  }

  /**
   * @return breakpoint handlers which will be used to set/clear breakpoints in the underlying debugging process
   */
  public XBreakpointHandler<?>[] getBreakpointHandlers() {
    return XBreakpointHandler.EMPTY_ARRAY;
  }

  /**
   * @return editor provider which will be used to produce editors for "Evaluate" and "Set Value" actions
   */
  @Nullable
  public XDebuggerEditorsProvider getEditorsProvider() {
    return null;
  }

  /**
   * Called when {@link XDebugSession} is initialized and breakpoints are registered in
   * {@link com.intellij.xdebugger.breakpoints.XBreakpointHandler}
   */
  public void sessionInitialized() {
  }

  /**
   * Interrupt debugging process and call {@link XDebugSession#positionReached(com.intellij.xdebugger.frame.XSuspendContext)}
   * when next line in current method/function is reached 
   */
  public void startPausing() {
  }

  /**
   * Resume execution and call {@link XDebugSession#positionReached(com.intellij.xdebugger.frame.XSuspendContext)}
   * when next line in current method/function is reached  
   */
  public abstract void startStepOver();

  /**
   * Resume execution and call {@link XDebugSession#positionReached(com.intellij.xdebugger.frame.XSuspendContext)}
   * when next line is reached
   */
  public abstract void startStepInto();

  /**
   * Resume execution and call {@link XDebugSession#positionReached(com.intellij.xdebugger.frame.XSuspendContext)}
   * after returning from current method/function
   */
  public abstract void startStepOut();

  /**
   * Implement {@link com.intellij.xdebugger.stepping.XSmartStepIntoHandler} and return its instance from this method to enable Smart Step Into action
   * @return {@link com.intellij.xdebugger.stepping.XSmartStepIntoHandler} instance
   */
  @Nullable
  public XSmartStepIntoHandler<?> getSmartStepIntoHandler() {
    return null;
  }

  /**
   * Stop debugging and dispose resources
   */
  public abstract void stop();

  /**
   * Resume execution
   */
  public abstract void resume();

  /**
   * Resume execution and call {@link XDebugSession#positionReached(com.intellij.xdebugger.frame.XSuspendContext)}
   * when <code>position</code> is reached
   * @param position position in source code
   */
  public abstract void runToPosition(@NotNull XSourcePosition position);

  @Nullable
  protected ProcessHandler doGetProcessHandler() {
    return null;
  }

  @NotNull
  public final ProcessHandler getProcessHandler() {
    if (myProcessHandler == null) {
      myProcessHandler = doGetProcessHandler();
      if (myProcessHandler == null) {
        myProcessHandler = new DefaultDebugProcessHandler();
      }
    }
    return myProcessHandler;
  }

  @NotNull
  public ExecutionConsole createConsole() {
    final TextConsoleBuilder consoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(getSession().getProject());
    return consoleBuilder.getConsole();
  }

  /**
   * @return message to show in Variables View when debugger isn't paused
   */
  public String getCurrentStateMessage() {
    return mySession.isStopped() ? XDebuggerBundle.message("debugger.state.message.disconnected")
           : XDebuggerBundle.message("debugger.state.message.connected");
  }

}
