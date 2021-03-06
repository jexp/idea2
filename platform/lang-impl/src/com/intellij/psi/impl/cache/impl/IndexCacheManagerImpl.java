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

package com.intellij.psi.impl.cache.impl;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.impl.cache.impl.id.IdIndex;
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry;
import com.intellij.psi.impl.cache.impl.todo.TodoIndex;
import com.intellij.psi.impl.cache.impl.todo.TodoIndexEntry;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.search.IndexPatternProvider;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FileBasedIndex;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 16, 2008
 */
public class IndexCacheManagerImpl implements CacheManager{
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.cache.impl.IndexCacheManagerImpl");
  private final Project myProject;
  private final PsiManager myPsiManager;

  public IndexCacheManagerImpl(PsiManager psiManager) {
    myPsiManager = psiManager;
    myProject = psiManager.getProject();
  }

  public void initialize() {
  }

  public void dispose() {
  }

  @NotNull
  public CacheUpdater[] getCacheUpdaters() {
    return new CacheUpdater[0]; // do not expose own updaters
  }

  @NotNull
  public PsiFile[] getFilesWithWord(@NotNull final String word, final short occurenceMask, @NotNull final GlobalSearchScope scope, final boolean caseSensitively) {
    CommonProcessors.CollectProcessor<PsiFile> processor = new CommonProcessors.CollectProcessor<PsiFile>();
    processFilesWithWord(processor, word, occurenceMask, scope, caseSensitively);
    return processor.getResults().isEmpty() ? PsiFile.EMPTY_ARRAY : processor.toArray(PsiFile.EMPTY_ARRAY);
  }

  public boolean processFilesWithWord(@NotNull final Processor<PsiFile> psiFileProcessor, @NotNull final String word, final short occurrenceMask, @NotNull final GlobalSearchScope scope, final boolean caseSensitively) {
    final ProjectFileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();

    final Processor<VirtualFile> virtualFileProcessor = new Processor<VirtualFile>() {
      public boolean process(final VirtualFile virtualFile) {
        LOG.assertTrue(virtualFile.isValid());
        return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
          public Boolean compute() {
            if (virtualFile.isValid() && scope.contains(virtualFile) && (index.isInContent(virtualFile) || index.isInLibrarySource(virtualFile))) {
              if (virtualFile.getFileType().isBinary()) return Boolean.TRUE;

              final PsiFile psiFile = myPsiManager.findFile(virtualFile);
              return psiFile == null || psiFileProcessor.process(psiFile);
            }
            return Boolean.TRUE;
          }
        }).booleanValue();
      }
    };

    final Set<VirtualFile> vFiles = new THashSet<VirtualFile>();
    return FileBasedIndex.getInstance().processValues(IdIndex.NAME, new IdIndexEntry(word, caseSensitively), null, new FileBasedIndex.ValueProcessor<Integer>() {
      public boolean process(final VirtualFile file, final Integer value) {
        final int mask = value.intValue();
        return (mask & occurrenceMask) == 0 || !vFiles.add(file) || virtualFileProcessor.process(file);
      }
    }, GlobalSearchScope.allScope(myProject));
  }

  @NotNull
  public PsiFile[] getFilesWithTodoItems() {
    final FileBasedIndex fileBasedIndex = FileBasedIndex.getInstance();
    final Set<PsiFile> allFiles = new HashSet<PsiFile>();
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    for (IndexPattern indexPattern : CacheUtil.getIndexPatterns()) {
      final Collection<VirtualFile> files = fileBasedIndex.getContainingFiles(
        TodoIndex.NAME, 
        new TodoIndexEntry(indexPattern.getPatternString(), indexPattern.isCaseSensitive()), GlobalSearchScope.allScope(myProject));
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          for (VirtualFile file : files) {
            if (projectFileIndex.isInContent(file)) {
              final PsiFile psiFile = myPsiManager.findFile(file);
              if (psiFile != null) {
                allFiles.add(psiFile);
              }
            }
          }
        }
        });
    }
    return allFiles.isEmpty() ? PsiFile.EMPTY_ARRAY : allFiles.toArray(new PsiFile[allFiles.size()]);
  }

  public int getTodoCount(@NotNull final VirtualFile file, final IndexPatternProvider patternProvider) {
    if (file instanceof VirtualFileWindow) return -1;
    final FileBasedIndex fileBasedIndex = FileBasedIndex.getInstance();
    int count = 0;
    for (IndexPattern indexPattern : patternProvider.getIndexPatterns()) {
      count += fetchCount(fileBasedIndex, file, indexPattern);
    }
    return count;
  }
   
  public int getTodoCount(@NotNull final VirtualFile file, final IndexPattern pattern) {
    if (file instanceof VirtualFileWindow) return -1;
    return fetchCount(FileBasedIndex.getInstance(), file, pattern);
  }

  private int fetchCount(final FileBasedIndex fileBasedIndex, final VirtualFile file, final IndexPattern indexPattern) {
    final int[] count = {0};
    fileBasedIndex.processValues(
      TodoIndex.NAME, new TodoIndexEntry(indexPattern.getPatternString(), indexPattern.isCaseSensitive()), file,
      new FileBasedIndex.ValueProcessor<Integer>() {
        public boolean process(final VirtualFile file, final Integer value) {
          count[0] += value.intValue();
          return true;
        }
      }, GlobalSearchScope.allScope(myProject));
    return count[0];
  }

  public void addOrInvalidateFile(@NotNull final VirtualFile file) {
    // empty
  }
}
