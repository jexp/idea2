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

import com.intellij.openapi.application.ModalityState;

public interface ProgressIndicator {
  void start();

  void stop();

  boolean isRunning();

  void cancel();

  boolean isCanceled();

  void setText(String text);

  String getText();

  void setText2(String text);

  String getText2();

  double getFraction();

  void setFraction(double fraction);

  void pushState();

  void popState();

  void startNonCancelableSection();

  void finishNonCancelableSection();

  boolean isModal();

  ModalityState getModalityState();

  void setModalityProgress(ProgressIndicator modalityProgress);

  boolean isIndeterminate();

  void setIndeterminate(boolean indeterminate);

  void checkCanceled() throws ProcessCanceledException;
}