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

package com.intellij.openapi.command.impl;

import com.intellij.history.core.LocalVcs;
import com.intellij.history.core.changes.Change;
import com.intellij.history.integration.IdeaGateway;

import java.io.IOException;

public class ChangeRange {
  private final IdeaGateway myGateway;
  private final LocalVcs myVcs;
  private final Change myFromChange;
  private final Change myToChange;

  public ChangeRange(IdeaGateway gw, LocalVcs vcs, Change change) {
    this(gw, vcs, change, change);
  }

  public ChangeRange(IdeaGateway gw, LocalVcs vcs, Change from, Change to) {
    myGateway = gw;
    myVcs = vcs;
    myFromChange = from;
    myToChange = to;
  }

  public ChangeRange revert(ChangeRange reverse) throws IOException {
    final Change[] first = {null};
    final Change[] last = {null};
    LocalVcs.Listener l = new LocalVcs.Listener() {
      public void onChange(Change c) {
        if (first[0] == null) first[0] = c;
        last[0] = c;
      }
    };
    myVcs.addListener(l);
    try {
      myVcs.acceptWrite(new ChangeRangeRevertionVisitor(myGateway, myToChange, myFromChange));
    }
    finally {
      myVcs.removeListener(l);
    }
    
    if (reverse != null) {
      if (first[0] == null) first[0] = reverse.myFromChange;
      if (last[0] == null) last[0] = reverse.myToChange;
    }
    return new ChangeRange(myGateway, myVcs, first[0], last[0]);
  }
}
