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
package com.intellij.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.tabs.*;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.MouseListener;
import java.util.concurrent.CopyOnWriteArraySet;

public class JBTabsPaneImpl implements TabbedPane, SwingConstants {

  private final JBTabs myTabs;
  private final CopyOnWriteArraySet<ChangeListener> myListeners = new CopyOnWriteArraySet<ChangeListener>();

  public JBTabsPaneImpl(@Nullable Project project, int tabPlacement, Disposable parent) {
    myTabs = new JBTabsImpl(project, ActionManager.getInstance(), project != null ? IdeFocusManager.getInstance(project) : null, parent);
    myTabs.addListener(new TabsListener.Adapter() {
      @Override
      public void selectionChanged(TabInfo oldSelection, TabInfo newSelection) {
        fireChanged(new ChangeEvent(myTabs));
      }
    }).getPresentation().setPaintBorder(1, 1, 1, 1).setTabSidePaintBorder(2).setPaintFocus(false).setGhostsAlwaysVisible(true);

    setTabPlacement(tabPlacement);
  }

  private void fireChanged(ChangeEvent event) {
    for (ChangeListener each : myListeners) {
      each.stateChanged(event);
    }
  }

  public JComponent getComponent() {
    return myTabs.getComponent();
  }

  public void putClientProperty(Object key, Object value) {
    myTabs.getComponent().putClientProperty(key, value);
  }

  public void setKeyboardNavigation(PrevNextActionsDescriptor installKeyboardNavigation) {
    myTabs.setNavigationActiondBinding(installKeyboardNavigation.getPrevActionId(), installKeyboardNavigation.getNextActionId());
  }

  public void addChangeListener(ChangeListener listener) {
    myListeners.add(listener);
  }

  public int getTabCount() {
    return myTabs.getTabCount();
  }

  public void insertTab(String title, Icon icon, Component c, String tip, int index) {
    assert c instanceof JComponent;
    myTabs.addTab(new TabInfo((JComponent)c).setText(title).setTooltipText(tip).setIcon(icon), index);
  }

  public void setTabPlacement(int tabPlacement) {
    final JBTabsPresentation presentation = myTabs.getPresentation();
    switch (tabPlacement) {
      case TOP:
        presentation.setTabsPosition(JBTabsPosition.top);
        break;
      case BOTTOM:
        presentation.setTabsPosition(JBTabsPosition.bottom);
        break;
      case LEFT:
        presentation.setTabsPosition(JBTabsPosition.left);
        break;
      case RIGHT:
        presentation.setTabsPosition(JBTabsPosition.right);
        break;
      default:
        throw new IllegalArgumentException("Invalid tab placement code=" + tabPlacement);
    }
  }

  public void addMouseListener(MouseListener listener) {
    myTabs.getComponent().addMouseListener(listener);
  }

  public int getSelectedIndex() {
    return myTabs.getIndexOf(myTabs.getSelectedInfo());
  }

  public Component getSelectedComponent() {
    final TabInfo selected = myTabs.getSelectedInfo();
    return selected != null ? selected.getComponent() : null;
  }

  public void setSelectedIndex(int index) {
    myTabs.select(getTabAt(index), false);
  }

  public void removeTabAt(int index) {
    myTabs.removeTab(getTabAt(index));
  }

  private TabInfo getTabAt(int index) {
    checkIndex(index);
    return myTabs.getTabAt(index);
  }

  private void checkIndex(int index) {
    if (index < 0 || index >= getTabCount()) {
      throw new ArrayIndexOutOfBoundsException("tabCount=" + getTabCount() + " index=" + index);
    }
  }

  public void revalidate() {
    myTabs.getComponent().revalidate();
  }

  public Color getForegroundAt(int index) {
    return getTabAt(index).getDefaultForeground();
  }

  public void setForegroundAt(int index, Color color) {
    getTabAt(index).setDefaultForeground(color);
  }

  public Component getComponentAt(int i) {
    return getTabAt(i).getComponent();
  }

  public void setTitleAt(int index, String title) {
    getTabAt(index).setText(title);
  }

  public void setToolTipTextAt(int index, String toolTipText) {
    getTabAt(index).setTooltipText(toolTipText);
  }

  public void setComponentAt(int index, Component c) {
    getTabAt(index).setComponent(c);
  }

  public void setIconAt(int index, Icon icon) {
    getTabAt(index).setIcon(icon);
  }

  public void setEnabledAt(int index, boolean enabled) {
    getTabAt(index).setEnabled(enabled);
  }

  public int getTabLayoutPolicy() {
    return myTabs.getPresentation().isSingleRow() ? JTabbedPane.SCROLL_TAB_LAYOUT : JTabbedPane.WRAP_TAB_LAYOUT;
  }

  public void setTabLayoutPolicy(int policy) {
    switch (policy) {
      case JTabbedPane.SCROLL_TAB_LAYOUT:
        myTabs.getPresentation().setSingleRow(true);
        break;
      case JTabbedPane.WRAP_TAB_LAYOUT:
        myTabs.getPresentation().setSingleRow(false);
        break;
      default:
        throw new IllegalArgumentException("Unsupported tab layout policy: " + policy);
    }
  }

  public void scrollTabToVisible(int index) {
  }

  public String getTitleAt(int i) {
    return getTabAt(i).getText();
  }

  public void removeAll() {
    myTabs.removeAllTabs();
  }

  public void updateUI() {
    myTabs.getComponent().updateUI();
  }

  public void removeChangeListener(ChangeListener listener) {
    myListeners.remove(listener);
  }

  public JBTabs getTabs() {
    return myTabs;
  }

  public boolean isDisposed() {
    return myTabs.isDisposed();
  }
}
