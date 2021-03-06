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
package com.intellij.slicer;

import com.intellij.analysis.AnalysisScope;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author cdr
 */
public abstract class SliceRootNode extends SliceNode {
  private final SliceUsage myRootUsage;

  protected SliceRootNode(@NotNull Project project, @NotNull DuplicateMap targetEqualUsages,
                          AnalysisScope scope, final SliceUsage rootUsage) {
    super(project, new SliceUsage(rootUsage.getElement().getContainingFile(), scope), targetEqualUsages, Collections.<PsiElement>emptyList());
    myRootUsage = rootUsage;
  }

  void switchToAllLeavesTogether(SliceUsage rootUsage) {
    AbstractTreeNode node = new SliceNode(getProject(), rootUsage, targetEqualUsages, getTreeBuilder(), getLeafExpressions());
    myCachedChildren = Collections.singletonList(node);
  }

  @NotNull
  public Collection<? extends AbstractTreeNode> getChildren() {
    if (myCachedChildren == null) {
      switchToAllLeavesTogether(myRootUsage);
    }
    return myCachedChildren;
  }

  @Override
  public List<? extends AbstractTreeNode> getChildrenUnderProgress(ProgressIndicator progress) {
    return (List<? extends AbstractTreeNode>)getChildren();
  }

  @Override
  protected boolean shouldUpdateData() {
    return super.shouldUpdateData();
  }

  @Override
  protected void update(PresentationData presentation) {
    if (presentation != null) {
      presentation.setChanged(presentation.isChanged() || changed);
      changed = false;
    }
  }


  @Override
  public void customizeCellRenderer(SliceUsageCellRenderer renderer,
                                    JTree tree,
                                    Object value,
                                    boolean selected,
                                    boolean expanded,
                                    boolean leaf,
                                    int row,
                                    boolean hasFocus) {
  }

  public void restructureByLeaves(Collection<PsiElement> leafExpressions) {
    assert myCachedChildren.size() == 1;
    SliceNode root = (SliceNode)myCachedChildren.get(0);
    myCachedChildren = new ArrayList<AbstractTreeNode>(leafExpressions.size());
    for (PsiElement leaf : leafExpressions) {
      SliceLeafValueRootNode node = new SliceLeafValueRootNode(getProject(), leaf, root);
      myCachedChildren.add(node);
    }
  }
}