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
 * @author max
 */
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.codeInsight.daemon.LineMarkerProviders;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightLevelUtil;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

public class LineMarkersPass extends ProgressableTextEditorHighlightingPass implements LineMarkersProcessor, DumbAware {
  private volatile Collection<LineMarkerInfo> myMarkers = Collections.emptyList();

  private final int myStartOffset;
  private final int myEndOffset;
  private final boolean myUpdateAll;

  public LineMarkersPass(@NotNull Project project,
                         @NotNull PsiFile file,
                         @NotNull Document document,
                         int startOffset,
                         int endOffset,
                         boolean updateAll) {
    super(project, document, GeneralHighlightingPass.IN_PROGRESS_ICON, GeneralHighlightingPass.PRESENTABLE_NAME, file,
          false);
    myStartOffset = startOffset;
    myEndOffset = endOffset;
    myUpdateAll = updateAll;
  }

  protected void applyInformationWithProgress() {
    try {
      UpdateHighlightersUtil.setLineMarkersToEditor(myProject, myDocument, myStartOffset, myEndOffset, myMarkers, Pass.UPDATE_ALL);
    }
    catch (IndexNotReadyException ignored) {
    }
  }

  protected void collectInformationWithProgress(final ProgressIndicator progress) {
    final List<LineMarkerInfo> lineMarkers = new ArrayList<LineMarkerInfo>();
    final FileViewProvider viewProvider = myFile.getViewProvider();
    final Set<Language> relevantLanguages = viewProvider.getLanguages();
    for (Language language : relevantLanguages) {
      PsiElement psiRoot = viewProvider.getPsi(language);
      if (!HighlightLevelUtil.shouldHighlight(psiRoot)) continue;
      //long time = System.currentTimeMillis();
      List<PsiElement> elements = CollectHighlightsUtil.getElementsInRange(psiRoot, myStartOffset, myEndOffset);
      if (elements.isEmpty()) {
        elements = Collections.singletonList(psiRoot);
      }
      final List<LineMarkerProvider> providers = getMarkerProviders(language, myProject);
      addLineMarkers(elements, providers, lineMarkers);
      collectLineMarkersForInjected(lineMarkers, elements, this, myFile);
    }

    myMarkers = lineMarkers;
  }

  public static List<LineMarkerProvider> getMarkerProviders(Language language, Project project) {
    return DumbService.getInstance(project).filterByDumbAwareness(LineMarkerProviders.INSTANCE.allForLanguage(language));
  }

  public void addLineMarkers(List<PsiElement> elements, final List<LineMarkerProvider> providers, final List<LineMarkerInfo> result) throws ProcessCanceledException {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    for (PsiElement element : elements) {
      ProgressManager.getInstance().checkCanceled();

      for (LineMarkerProvider provider: providers) {
        LineMarkerInfo info = provider.getLineMarkerInfo(element);
        if (info != null) {
          result.add(info);
        }
      }
    }
  }

  public static void collectLineMarkersForInjected(final List<LineMarkerInfo> result, List<PsiElement> elements,
                                                   final LineMarkersProcessor processor,
                                                   PsiFile file) {
    final InjectedLanguageManager manager = InjectedLanguageManager.getInstance(file.getProject());
    final List<LineMarkerInfo> injectedMarkers = new ArrayList<LineMarkerInfo>();

    final boolean dumb = DumbService.getInstance(file.getProject()).isDumb();

    for (PsiElement element : elements) {
      InjectedLanguageUtil.enumerate(element, file, new PsiLanguageInjectionHost.InjectedPsiVisitor() {
        public void visit(@NotNull final PsiFile injectedPsi, @NotNull List<PsiLanguageInjectionHost.Shred> places) {
          final Project project = injectedPsi.getProject();
          Document document = PsiDocumentManager.getInstance(project).getCachedDocument(injectedPsi);
          if (!(document instanceof DocumentWindow)) return;
          List<PsiElement> injElements = CollectHighlightsUtil.getElementsInRange(injectedPsi, 0, injectedPsi.getTextLength());
          final List<LineMarkerProvider> providers = getMarkerProviders(injectedPsi.getLanguage(), project);
          processor.addLineMarkers(injElements, providers, injectedMarkers);
          for (final LineMarkerInfo injectedMarker : injectedMarkers) {
            GutterIconRenderer gutterRenderer = injectedMarker.createGutterRenderer();
            TextRange injectedRange = new TextRange(injectedMarker.startOffset, injectedMarker.endOffset);
            List<TextRange> editables = manager.intersectWithAllEditableFragments(injectedPsi, injectedRange);
            for (TextRange editable : editables) {
              TextRange hostRange = manager.injectedToHost(injectedPsi, editable);
              Icon icon = gutterRenderer == null ? null : gutterRenderer.getIcon();
              LineMarkerInfo converted =
                  new LineMarkerInfo<PsiElement>(injectedMarker.getElement(), hostRange, icon, injectedMarker.updatePass,
                                     new Function<PsiElement, String>() {
                                       public String fun(PsiElement element) {
                                         return injectedMarker.getLineMarkerTooltip();
                                       }
                                     }, injectedMarker.getNavigationHandler(), GutterIconRenderer.Alignment.RIGHT);
              converted.textAttributesKey = injectedMarker.textAttributesKey;
              result.add(converted);
            }
          }
          injectedMarkers.clear();
        }
      }, false);
    }
  }

  public Collection<LineMarkerInfo> queryLineMarkers() {
    if (myFile.getNode() == null) {
      // binary file? see IDEADEV-2809
      return Collections.emptyList();
    }
    collectInformationWithProgress(null);
    return myMarkers;
  }

  public double getProgress() {
    // do not show progress of visible highlighters update
    return myUpdateAll ? super.getProgress() : -1;
  }
}