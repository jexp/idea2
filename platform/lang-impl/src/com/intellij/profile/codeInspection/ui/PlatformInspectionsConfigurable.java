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

package com.intellij.profile.codeInspection.ui;

import com.intellij.codeInspection.ModifiableModel;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.IconLoader;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import org.jetbrains.annotations.Nls;

import javax.swing.*;

/**
 * @author yole
 */
public class PlatformInspectionsConfigurable implements ErrorsConfigurable {
  private SingleInspectionProfilePanel myPanel;
  private ModifiableModel myProfile;

  @Nls
  public String getDisplayName() {
    return "Inspections";
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableErrorHighlighting.png");
  }

  public JComponent createComponent() {
    return getPanel();
  }

  public boolean isModified() {
    return myPanel != null && myPanel.isModified();
  }

  public void apply() throws ConfigurationException {
    getPanel().apply();
  }

  public void reset() {
    getPanel().reset();
  }

  public void disposeUIResources() {
    if (myPanel != null) {
      myPanel.disposeUI();
      myProfile = null;
      myPanel = null;
    }
  }

  public void selectProfile(final String name) {
  }

  public void selectInspectionTool(final String selectedToolShortName) {
    getPanel().selectInspectionTool(selectedToolShortName);
  }

  public Object getSelectedObject() {
    return null;
  }

  public String getHelpTopic() {
    return "preferences.inspections";
  }

  public SingleInspectionProfilePanel getPanel() {
    if (myProfile == null) {
      myProfile = new InspectionProfileImpl((InspectionProfileImpl) InspectionProfileManager.getInstance().getRootProfile());
    }
    if (myPanel == null) {
      myPanel = new SingleInspectionProfilePanel(myProfile.getName(), myProfile);
    }
    return myPanel;
  }
}
