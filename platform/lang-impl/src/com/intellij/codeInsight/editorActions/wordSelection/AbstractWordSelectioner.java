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

package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase;
import com.intellij.codeInsight.editorActions.SelectWordUtil;

import java.util.List;
import java.util.ArrayList;

/**
 * @author yole
 */
public abstract class AbstractWordSelectioner extends ExtendWordSelectionHandlerBase {
  public boolean canSelect(final PsiElement e) {
    return false;
  }

  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    List<TextRange> ranges;
    if (canSelect(e)) {
      ranges = super.select(e, editorText, cursorOffset, editor);
    }
    else {
      ranges = new ArrayList<TextRange>();
    }
    SelectWordUtil.addWordSelection(editor.getSettings().isCamelWords(), editorText, cursorOffset, ranges);
    return ranges;
  }
}