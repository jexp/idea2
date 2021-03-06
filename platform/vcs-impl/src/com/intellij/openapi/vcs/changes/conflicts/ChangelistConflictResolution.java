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
package com.intellij.openapi.vcs.changes.conflicts;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesCommitExecutor;
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public enum ChangelistConflictResolution {

  SHELVE {
    @Override
    public boolean resolveConflict(Project project, Collection<Change> changes) {
      LocalChangeList changeList = getManager(project).getChangeList(changes.iterator().next());
      return CommitChangeListDialog.commitChanges(project, changes, changeList, new ShelveChangesCommitExecutor(project), null);
    }},

  MOVE {
    @Override
    public boolean resolveConflict(Project project, Collection<Change> changes) {
      ChangeListManagerImpl manager = getManager(project);
      Set<ChangeList> changeLists = new HashSet<ChangeList>();
      for (Change change : changes) {
        changeLists.add(manager.getChangeList(change));
      }
      MoveChangesDialog dialog = new MoveChangesDialog(project, changes, changeLists, "Move Changes");
      dialog.show();
      if (dialog.isOK()) {
        manager.moveChangesTo(manager.getDefaultChangeList(), changes.toArray(new Change[changes.size()]));
        return true;
      }
      return false;
    }},

  SWITCH{
    @Override
    public boolean resolveConflict(Project project, Collection<Change> changes) {
      LocalChangeList changeList = getManager(project).getChangeList(changes.iterator().next());
      assert changeList != null;
      getManager(project).setDefaultChangeList(changeList);
      return true;
    }},

  IGNORE {
    @Override
    public boolean resolveConflict(Project project, Collection<Change> changes) {
      ChangeListManagerImpl manager = getManager(project);
      for (Change change : changes) {
        VirtualFile file = change.getVirtualFile();
        if (file != null) {
          manager.getConflictTracker().ignoreConflict(file, true);
        }
      }
      return true;
    }};

  public abstract boolean resolveConflict(Project project, Collection<Change> changes);

  private static ChangeListManagerImpl getManager(Project project) {
    return (ChangeListManagerImpl)ChangeListManager.getInstance(project);
  }
}
