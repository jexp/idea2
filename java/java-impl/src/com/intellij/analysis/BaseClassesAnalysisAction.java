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
package com.intellij.analysis;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author mike
 */
public abstract class BaseClassesAnalysisAction extends BaseAnalysisAction {
  protected BaseClassesAnalysisAction(String title, String analysisNoon) {
    super(title, analysisNoon);
  }

  protected abstract void analyzeClasses(final Project project, final AnalysisScope scope, ProgressIndicator indicator);

  protected void analyze(@NotNull final Project project, final AnalysisScope scope) {
    FileDocumentManager.getInstance().saveAllDocuments();

    ProgressManager.getInstance().run(new Task.Backgroundable(project, AnalysisScopeBundle.message("analyzing.project"), true) {
      public void run(@NotNull final ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
        indicator.setText(AnalysisScopeBundle.message("checking.class.files"));

        final CompilerManager compilerManager = CompilerManager.getInstance(myProject);
        final boolean upToDate = compilerManager.isUpToDate(compilerManager.createProjectCompileScope(myProject));

        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            if (!upToDate) {
              final int i = Messages.showYesNoCancelDialog(getProject(), AnalysisScopeBundle.message("recompile.confirmation.message"),
                                                           AnalysisScopeBundle.message("project.is.out.of.date"), Messages.getWarningIcon());

              if (i == 2) return;

              if (i == 0) {
                compileAndAnalyze(project, scope);
              }
              else {
                doAnalyze(project, scope);
              }
            }
            else {
              doAnalyze(project, scope);
            }
          }
        });
      }
    });
  }

  private void doAnalyze(final Project project, final AnalysisScope scope) {
    ProgressManager.getInstance().run(new Task.Backgroundable(project, AnalysisScopeBundle.message("analyzing.project"), true) {
      @Nullable
      public NotificationInfo getNotificationInfo() {
        return new NotificationInfo("Analysis",  "\"" + getTitle() + "\" Analysis Finished", "");
      }

      public void run(@NotNull final ProgressIndicator indicator) {
        analyzeClasses(project, scope, indicator);
      }
    });
  }

  private void compileAndAnalyze(final Project project, final AnalysisScope scope) {
    final CompilerManager compilerManager = CompilerManager.getInstance(project);
    compilerManager.make(compilerManager.createProjectCompileScope(project), new CompileStatusNotification() {
      public void finished(final boolean aborted, final int errors, final int warnings, final CompileContext compileContext) {
        if (aborted || errors != 0) return;
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            doAnalyze(project, scope);
          }
        });
    }});
  }
}
