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

package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass;
import com.intellij.codeInsight.hint.*;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.config.IntentionManagerSettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.RowIcon;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Alarm;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author max
 * @author Mike
 * @author Valentin
 * @author Eugene Belyaev
 * @author Konstantin Bulenkov
 * @author and me too (Chinee?)
 */
public class IntentionHintComponent extends JPanel implements Disposable, ScrollAwareHint {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.IntentionHintComponent.ListPopupRunnable");

  static final Icon ourIntentionIcon = IconLoader.getIcon("/actions/realIntentionBulb.png");
  static final Icon ourBulbIcon = IconLoader.getIcon("/actions/intentionBulb.png");
  static final Icon ourQuickFixIcon = IconLoader.getIcon("/actions/quickfixBulb.png");
  static final Icon ourIntentionOffIcon = IconLoader.getIcon("/actions/realIntentionOffBulb.png");
  static final Icon ourQuickFixOffIcon = IconLoader.getIcon("/actions/quickfixOffBulb.png");
  static final Icon ourArrowIcon = IconLoader.getIcon("/general/arrowDown.png");
  private static final Border INACTIVE_BORDER = null;
  private static final Insets INACTIVE_MARGIN = new Insets(0, 0, 0, 0);
  private static final Insets ACTIVE_MARGIN = new Insets(0, 0, 0, 0);

  private final Editor myEditor;

  private static final Alarm myAlarm = new Alarm();

  private final RowIcon myHighlightedIcon;
  private final JButton myButton;

  private final Icon mySmartTagIcon;

  private static final int DELAY = 500;
  private final MyComponentHint myComponentHint;
  private static final Color BACKGROUND_COLOR = new Color(255, 255, 255, 0);
  private boolean myPopupShown = false;
  private boolean myDisposed = false;
  private ListPopup myPopup;
  private final PsiFile myFile;

  public static IntentionHintComponent showIntentionHint(Project project, final PsiFile file, Editor editor, ShowIntentionsPass.IntentionsInfo intentions,
                                                         boolean showExpanded) {
    final Point position = getHintPosition(editor);
    return showIntentionHint(project, file, editor, intentions, showExpanded, position);
  }

  public static IntentionHintComponent showIntentionHint(Project project, final PsiFile file, Editor editor,
                                                         ShowIntentionsPass.IntentionsInfo intentions,
                                                         boolean showExpanded,
                                                         final Point position) {
    final IntentionHintComponent component = new IntentionHintComponent(project, file, editor, intentions);

    if (showExpanded) {
      component.showIntentionHintImpl(false, position);
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          component.showPopup();
        }
      });
    }
    else {
      component.showIntentionHintImpl(true, position);
    }
    Disposer.register(project, component);

    return component;
  }

  public void dispose() {
    myDisposed = true;
    myComponentHint.hide();
    super.hide();
  }

  public void editorScrolled() {
    closePopup();
  }

  //true if actions updated, there is nothing to do
  //false if has to recreate popup, no need to reshow
  //null if has to reshow
  public synchronized Boolean updateActions(ShowIntentionsPass.IntentionsInfo intentions) {
    if (myPopup.isDisposed()) return null;
    if (!myFile.isValid()) return null;
    IntentionListStep step = (IntentionListStep)myPopup.getListStep();
    if (!step.updateActions(intentions)) {
      return Boolean.TRUE;
    }
    if (!myPopupShown) {
      return Boolean.FALSE;
    }
    return null;
  }

  public synchronized void recreate() {
    IntentionListStep step = (IntentionListStep)myPopup.getListStep();
    recreateMyPopup(step);
  }

  private void showIntentionHintImpl(final boolean delay, final Point position) {
    final int offset = myEditor.getCaretModel().getOffset();

    myComponentHint.setShouldDelay(delay);

    HintManagerImpl hintManager = HintManagerImpl.getInstanceImpl();
    PriorityQuestionAction action = new PriorityQuestionAction() {
      public boolean execute() {
        showPopup();
        return true;
      }

      public int getPriority() {
        return 0;
      }
    };
    if (hintManager.canShowQuestionAction(action)) {
      hintManager.showQuestionHint(myEditor, position, offset, offset, myComponentHint, action);
    }
  }

  private static Point getHintPosition(Editor editor) {

    final int offset = editor.getCaretModel().getOffset();
    final LogicalPosition pos = editor.offsetToLogicalPosition(offset);
    int line = pos.line;

    final Point position = editor.logicalPositionToXY(new LogicalPosition(line, 0));
    final int yShift = (ourIntentionIcon.getIconHeight() - editor.getLineHeight() - 1) / 2 - 1;
    final int xShift = ourIntentionIcon.getIconWidth();

    LOG.assertTrue(editor.getComponent().isDisplayable());
    Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
    Point location = SwingUtilities.convertPoint(editor.getContentComponent(),
                                                 new Point(visibleArea.x - xShift, position.y + yShift),
                                                 editor.getComponent().getRootPane().getLayeredPane());

    return new Point(location.x, location.y);
  }

  private IntentionHintComponent(@NotNull Project project, @NotNull PsiFile file, @NotNull Editor editor, ShowIntentionsPass.IntentionsInfo intentions) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    myFile = file;
    myEditor = editor;

    setLayout(new BorderLayout());
    setOpaque(false);

    boolean showFix = false;
    for (final HighlightInfo.IntentionActionDescriptor pairs : intentions.errorFixesToShow) {
      IntentionAction fix = pairs.getAction();
      if (IntentionManagerSettings.getInstance().isShowLightBulb(fix)) {
        showFix = true;
        break;
      }
    }
    mySmartTagIcon = showFix ? ourQuickFixIcon : ourBulbIcon;

    myHighlightedIcon = new RowIcon(2);
    myHighlightedIcon.setIcon(mySmartTagIcon, 0);
    myHighlightedIcon.setIcon(ourArrowIcon, 1);

    myButton = new JButton(mySmartTagIcon);
    myButton.setFocusable(false);
    myButton.setMargin(INACTIVE_MARGIN);
    myButton.setBorderPainted(false);
    myButton.setContentAreaFilled(false);

    add(myButton, BorderLayout.CENTER);
    setBorder(INACTIVE_BORDER);

    myButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        showPopup();
      }
    });

    myButton.addMouseListener(new MouseAdapter() {
      public void mouseEntered(MouseEvent e) {
        onMouseEnter();
      }

      public void mouseExited(MouseEvent e) {
        onMouseExit();
      }
    });

    myComponentHint = new MyComponentHint(this);
    IntentionListStep step = new IntentionListStep(this, intentions, myEditor, myFile, project);
    recreateMyPopup(step);
  }

  public void hide() {
    Disposer.dispose(this);
  }

  private void onMouseExit() {
    Window ancestor = SwingUtilities.getWindowAncestor(myPopup.getContent());
    if (ancestor == null) {
      myButton.setBackground(BACKGROUND_COLOR);
      myButton.setIcon(mySmartTagIcon);
      setBorder(INACTIVE_BORDER);
      myButton.setMargin(INACTIVE_MARGIN);
      updateComponentHintSize();
    }
  }

  private void onMouseEnter() {
    myButton.setBackground(HintUtil.QUESTION_COLOR);
    myButton.setIcon(myHighlightedIcon);
    setBorder(BorderFactory.createLineBorder(Color.black));
    myButton.setMargin(ACTIVE_MARGIN);
    updateComponentHintSize();

    String acceleratorsText = KeymapUtil.getFirstKeyboardShortcutText(
      ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS));
    if (acceleratorsText.length() > 0) {
      myButton.setToolTipText(CodeInsightBundle.message("lightbulb.tooltip", acceleratorsText));
    }
  }

  private void updateComponentHintSize() {
    Component component = myComponentHint.getComponent();
    component.setSize(getPreferredSize().width, getHeight());
  }

  private void closePopup() {
    myPopup.cancel();
    myPopupShown = false;
  }

  private void showPopup() {
    if (myPopup == null || myPopup.isDisposed()) return;

    if (isShowing()) {
      myPopup.show(RelativePoint.getSouthWestOf(this));
    }
    else {
      myPopup.showInBestPositionFor(myEditor);
    }

    myPopupShown = true;
  }

  void recreateMyPopup(IntentionListStep step) {
    if (myPopup != null) {
      Disposer.dispose(myPopup);
    }
    myPopup = JBPopupFactory.getInstance().createListPopup(step);
    myPopup.addListener(new JBPopupListener.Adapter() {
      @Override
      public void onClosed(LightweightWindowEvent event) {
        myPopupShown = false;
      }
    });
    Disposer.register(this, myPopup);
    Disposer.register(myPopup, new Disposable() {
      public void dispose() {
        ApplicationManager.getApplication().assertIsDispatchThread();
      }
    });
  }

  void canceled(IntentionListStep intentionListStep) {
    if (myPopup.getListStep() != intentionListStep || myDisposed) {
      return;
    }
    // Root canceled. Create new popup. This one cannot be reused.
    recreateMyPopup(intentionListStep);
  }

  private class MyComponentHint extends LightweightHint {
    private boolean myVisible = false;
    private boolean myShouldDelay;

    private MyComponentHint(JComponent component) {
      super(component);
    }

    public void show(@NotNull final JComponent parentComponent, final int x, final int y, final JComponent focusBackComponent) {
      myVisible = true;
      if (myShouldDelay) {
        myAlarm.cancelAllRequests();
        myAlarm.addRequest(new Runnable() {
          public void run() {
            showImpl(parentComponent, x, y, focusBackComponent);
          }
        }, DELAY);
      }
      else {
        showImpl(parentComponent, x, y, focusBackComponent);
      }
    }

    private void showImpl(JComponent parentComponent, int x, int y, JComponent focusBackComponent) {
      if (!parentComponent.isShowing()) return;
      super.show(parentComponent, x, y, focusBackComponent);
    }

    public void hide() {
      super.hide();
      myVisible = false;
      myAlarm.cancelAllRequests();
    }

    public boolean isVisible() {
      return myVisible || super.isVisible();
    }

    public void setShouldDelay(boolean shouldDelay) {
      myShouldDelay = shouldDelay;
    }
  }

  public static class EnableDisableIntentionAction implements IntentionAction{
    private final String myActionFamilyName;
    private final IntentionManagerSettings mySettings = IntentionManagerSettings.getInstance();
    private final IntentionAction myAction;

    public EnableDisableIntentionAction(IntentionAction action) {
      myActionFamilyName = action.getFamilyName();
      myAction = action;
      // needed for checking errors in user written actions
      //noinspection ConstantConditions
      LOG.assertTrue(myActionFamilyName != null, "action "+action.getClass()+" family returned null");
    }

    @NotNull
    public String getText() {
      return mySettings.isEnabled(myAction) ?
             CodeInsightBundle.message("disable.intention.action", myActionFamilyName) :
             CodeInsightBundle.message("enable.intention.action", myActionFamilyName);
    }

    @NotNull
    public String getFamilyName() {
      return getText();
    }

    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
      return true;
    }

    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      mySettings.setEnabled(myAction, !mySettings.isEnabled(myAction));
    }

    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public String toString() {
      return getText();
    }
  }
}
