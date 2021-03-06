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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 21.11.2006
 * Time: 18:38:44
 */
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsHistorySession;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.intellij.vcsUtil.VcsRunnable;
import com.intellij.vcsUtil.VcsUtil;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefaultPatchBaseVersionProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.patch.DefaultPatchBaseVersionProvider");

  private final Project myProject;
  private final VirtualFile myFile;
  private final String myVersionId;
  private final Pattern myRevisionPattern;

  private final AbstractVcs myVcs;

  public DefaultPatchBaseVersionProvider(final Project project, final VirtualFile file, final String versionId) {
    myProject = project;
    myFile = file;
    myVersionId = versionId;
    myVcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(myFile);
    if (myVcs != null) {
      final String vcsPattern = myVcs.getRevisionPattern();
      if (vcsPattern != null) {
        myRevisionPattern = Pattern.compile("\\(revision (" + vcsPattern + ")\\)");
        return;
      }
    }
    myRevisionPattern = null;
  }

  public void getBaseVersionContent(final FilePath filePath, Processor<CharSequence> processor) throws VcsException {
    if (myVcs == null) {
      return;
    }
    final VcsHistoryProvider historyProvider = myVcs.getVcsHistoryProvider();
    if (historyProvider == null) return;

    VcsRevisionNumber revision = null;
    if (myRevisionPattern != null) {
      final Matcher matcher = myRevisionPattern.matcher(myVersionId);
      if (matcher.find()) {
        revision = myVcs.parseRevisionNumber(matcher.group(1), filePath);
      }
    }

    Date versionDate = null;
    if (revision == null) {
      try {
        versionDate = new Date(myVersionId);
      }
      catch (IllegalArgumentException ex) {
        return;
      }
    }
    try {
      final Ref<VcsHistorySession> ref = new Ref<VcsHistorySession>();
      VcsUtil.runVcsProcessWithProgress(new VcsRunnable() {
        public void run() throws VcsException {
          ref.set(historyProvider.createSessionFor(filePath));
        }
      }, VcsBundle.message("loading.file.history.progress"), true, myProject);
      if (ref.isNull()) return;
      final VcsHistorySession session = ref.get();
      final List<VcsFileRevision> list = session.getRevisionList();
      if (list == null) return;
      for (VcsFileRevision fileRevision : list) {
        boolean found;
        if (revision != null) {
          found = fileRevision.getRevisionNumber().compareTo(revision) <= 0;
        }
        else {
          final Date date = fileRevision.getRevisionDate();
          found = (date != null) && date.before(versionDate);
        }

        if (found) {
          fileRevision.loadContent();
          processor.process(LoadTextUtil.getTextByBinaryPresentation(fileRevision.getContent(), myFile, false));
          // TODO: try to download more than one version
          break;
        }
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public boolean canProvideContent() {
    if (myVcs == null) {
      return false;
    }
    if ((myRevisionPattern != null) && myRevisionPattern.matcher(myVersionId).matches()) {
      return true;
    }
    try {
      Date.parse(myVersionId);
    }
    catch (IllegalArgumentException ex) {
      return false;
    }
    return true;
  }
}
