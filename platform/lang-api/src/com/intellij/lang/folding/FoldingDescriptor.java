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
package com.intellij.lang.folding;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Defines a single folding region in the code.
 *
 * @author max
 * @see FoldingBuilder
 */                                                    
public class FoldingDescriptor {
  public static final FoldingDescriptor[] EMPTY = new FoldingDescriptor[0];

  private final ASTNode myElement;
  private final TextRange myRange;
  @Nullable private final FoldingGroup myGroup;

  /**
   * Creates a folding region related to the specified AST node and covering the specified
   * text range.
   * @param node  The node to which the folding region is related. The node is then passed to
   *              {@link FoldingBuilder#getPlaceholderText(com.intellij.lang.ASTNode)} and
   *              {@link FoldingBuilder#isCollapsedByDefault(com.intellij.lang.ASTNode)}.
   * @param range The folded text range.
   */
  public FoldingDescriptor(@NotNull ASTNode node, @NotNull TextRange range) {
    this(node, range, null);
  }

  public FoldingDescriptor(@NotNull PsiElement element, @NotNull TextRange range) {
    this(ObjectUtils.assertNotNull(element.getNode()), range, null);
  }

  /**
   * Creates a folding region related to the specified AST node and covering the specified
   * text range.
   * @param node  The node to which the folding region is related. The node is then passed to
   *              {@link FoldingBuilder#getPlaceholderText(com.intellij.lang.ASTNode)} and
   *              {@link FoldingBuilder#isCollapsedByDefault(com.intellij.lang.ASTNode)}.
   * @param range The folded text range.
   * @param group Regions with the same group instance expand and collapse together.
   */
  public FoldingDescriptor(@NotNull ASTNode node, @NotNull TextRange range, @Nullable FoldingGroup group) {
    assert range.getStartOffset() + 1 < range.getEndOffset() : range;
    myElement = node;
    ProperTextRange.assertProperRange(range);
    myRange = range;
    myGroup = group;
    assert getRange().getLength() >= 2 : "range:" + getRange();
  }

  /**
   * @return the node to which the folding region is related.
   */
  @NotNull 
  public ASTNode getElement() {
    return myElement;
  }

  /**
   * Returns the folded text range.
   * @return the folded text range.
   */
  public TextRange getRange() {
    return getRange(myElement, myRange);
  }

  public static TextRange getRange(ASTNode node, TextRange range) {
    PsiElement element = node.getPsi();
    PsiFile containingFile = element.getContainingFile();
    InjectedLanguageManager injectedManager = InjectedLanguageManager.getInstance(containingFile.getProject());
    boolean isInjected = injectedManager.isInjectedFragment(containingFile);
    if (isInjected) {
      range = injectedManager.injectedToHost(element, range);
    }
    return range;
  }

  @Nullable
  public FoldingGroup getGroup() {
    return myGroup;
  }

  @Nullable
  public String getPlaceholderText() {
    final PsiElement psiElement = myElement.getPsi();
    if (psiElement == null) return null;

    final Language lang = psiElement.getLanguage();
    final FoldingBuilder foldingBuilder = LanguageFolding.INSTANCE.forLanguage(lang);
    if (foldingBuilder != null) {
      return foldingBuilder.getPlaceholderText(myElement);
    }
    return null;
  }

}
