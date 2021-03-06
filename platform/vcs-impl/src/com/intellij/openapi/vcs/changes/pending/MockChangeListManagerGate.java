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
package com.intellij.openapi.vcs.changes.pending;

import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerGate;
import com.intellij.openapi.vcs.changes.LocalChangeList;

public class MockChangeListManagerGate implements ChangeListManagerGate {
  private final ChangeListManager myManager;

  public MockChangeListManagerGate(final ChangeListManager manager) {
    myManager = manager;
  }

  public LocalChangeList findChangeList(final String name) {
    return myManager.findChangeList(name);
  }

  public LocalChangeList addChangeList(final String name, final String comment) {
    return myManager.addChangeList(name, comment);
  }

  public LocalChangeList findOrCreateList(final String name, final String comment) {
    LocalChangeList changeList = myManager.findChangeList(name);
    if (changeList == null) {
      changeList = myManager.addChangeList(name, comment);
    }
    return changeList;
  }

  public void editComment(final String name, final String comment) {
    myManager.editComment(name, comment);
  }
}
