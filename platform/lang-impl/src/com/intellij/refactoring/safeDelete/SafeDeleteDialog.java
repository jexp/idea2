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

package com.intellij.refactoring.safeDelete;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.DeleteUtil;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.util.TextOccurrencesUtil;

import javax.swing.*;
import java.awt.*;

/**
 * @author dsl
 */
public class SafeDeleteDialog extends DialogWrapper {
  private final PsiElement[] myElements;
  private final Callback myCallback;

  private JCheckBox myCbSearchInComments;
  private JCheckBox myCbSearchTextOccurrences;

  public interface Callback {
    void run(SafeDeleteDialog dialog);
  }

  public SafeDeleteDialog(Project project, PsiElement[] elements, Callback callback) {
    super(project, true);
    myElements = elements;
    myCallback = callback;

    setTitle(SafeDeleteHandler.REFACTORING_NAME);
    init();
  }

  public boolean isSearchInComments() {
    return myCbSearchInComments.isSelected();
  }

  public boolean isSearchForTextOccurences() {
    if (myCbSearchTextOccurrences != null) {
      return myCbSearchTextOccurrences.isSelected();
    }
    return false;
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("refactoring.safeDelete");
  }

  protected JComponent createNorthPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    final GridBagConstraints gbc = new GridBagConstraints();

    final String warningMessage = DeleteUtil.generateWarningMessage(IdeBundle.message("search.for.usages.and.delete.elements"), myElements);

    gbc.insets = new Insets(4, 8, 4, 8);
    gbc.weighty = 1;
    gbc.weightx = 1;
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 2;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.anchor = GridBagConstraints.WEST;
    panel.add(new JLabel(warningMessage), gbc);

    gbc.gridy++;
    gbc.gridx = 0;
    gbc.weightx = 0.0;
    gbc.gridwidth = 1;
    myCbSearchInComments = new JCheckBox();
    myCbSearchInComments.setText(RefactoringBundle.getSearchInCommentsAndStringsText());
    panel.add(myCbSearchInComments, gbc);

    if (needSearchForTextOccurrences()) {
      gbc.gridx++;
      myCbSearchTextOccurrences = new JCheckBox();
      myCbSearchTextOccurrences.setText(RefactoringBundle.getSearchForTextOccurrencesText());
      panel.add(myCbSearchTextOccurrences, gbc);
    }

    final RefactoringSettings refactoringSettings = RefactoringSettings.getInstance();
    myCbSearchInComments.setSelected(refactoringSettings.SAFE_DELETE_SEARCH_IN_COMMENTS);
    if (myCbSearchTextOccurrences != null) {
      myCbSearchTextOccurrences.setSelected(refactoringSettings.SAFE_DELETE_SEARCH_IN_NON_JAVA);
    }
    return panel;
  }

  protected JComponent createCenterPanel() {
    return null;
  }

  private boolean needSearchForTextOccurrences() {
    for (PsiElement element : myElements) {
      if (TextOccurrencesUtil.isSearchTextOccurencesEnabled(element)) {
        return true;
      }
    }
    return false;
  }


  protected void doOKAction() {
    if (myCallback != null) {
      myCallback.run(this);
    } else {
      super.doOKAction();
    }
    final RefactoringSettings refactoringSettings = RefactoringSettings.getInstance();
    refactoringSettings.SAFE_DELETE_SEARCH_IN_COMMENTS = isSearchInComments();
    if (myCbSearchTextOccurrences != null) {
      refactoringSettings.SAFE_DELETE_SEARCH_IN_NON_JAVA = isSearchForTextOccurences();
    }
  }
}
