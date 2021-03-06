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

package com.intellij.ui.tabs;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.ui.FileColorManager;
import com.intellij.util.containers.hash.LinkedHashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author spleaner
 */
@State(
  name = "FileColors",
  storages = {@Storage(
    id = "other",
    file = "$WORKSPACE_FILE$")})
public class FileColorManagerImpl extends FileColorManager implements PersistentStateComponent<Element> {
  private boolean myEnabled = false;

  private FileColorsModel myModel;

  private FileColorSharedConfigurationManager mySharedConfigurationManager;

  private Project myProject;
  private boolean myEnabledForTabs;

  private static Map<String, Color> ourDefaultColors;

  static {
    ourDefaultColors = new LinkedHashMap<String, Color>();
    ourDefaultColors.put("Blue", new Color(215, 237, 243));
    //ourDefaultColors.put("Blue 2", new Color(218, 224, 244));
    ourDefaultColors.put("Green", new Color(228, 241, 209));
    //ourDefaultColors.put("Green 2", new Color(223, 235, 226));
    ourDefaultColors.put("Orange", new Color(246, 224, 202));
    ourDefaultColors.put("Rose", new Color(242, 206, 202));
    ourDefaultColors.put("Violet", new Color(222, 213, 241));
    ourDefaultColors.put("Yellow", new Color(247, 241, 203));
  }

  public FileColorManagerImpl(@NotNull final Project project) {
    myProject = project;
    myModel = new FileColorsModel(project);
  }

  private void initSharedConfigurations() {
    if (mySharedConfigurationManager == null) {
      mySharedConfigurationManager = ServiceManager.getService(myProject, FileColorSharedConfigurationManager.class);
    }
  }

  public boolean isEnabled() {
    return myEnabled;
  }

  public void setEnabled(boolean enabled) {
    myEnabled = enabled;
  }

  public void setEnabledForTabs(boolean b) {
    myEnabledForTabs = b;
  }

  public boolean isEnabledForTabs() {
    return myEnabledForTabs;
  }

  public Element getState(final boolean shared) {
    Element element = new Element("state");
    if (!shared) {
      element.setAttribute("enabled", Boolean.toString(myEnabled));
      element.setAttribute("enabledForTabs", Boolean.toString(myEnabledForTabs));
    }

    myModel.save(element, shared);

    return element;
  }

  @SuppressWarnings({"MethodMayBeStatic"})
  @Nullable
  public Color getColor(@NotNull final String name) {
    return ourDefaultColors.get(name);
  }

  public static String toString(final Color color) {
    return Integer.toHexString((color.getRGB() & 0xffffff) | 0x1000000).substring(1);
  }

  public static Color fromString(final String hex) throws NumberFormatException {
    return Color.decode(String.format("#%s", hex));
  }

  public Element getState() {
    initSharedConfigurations();
    return getState(false);
  }

  @SuppressWarnings({"AutoUnboxing"})
  void loadState(Element state, final boolean shared) {
    if (!shared) {
      final String enabled = state.getAttributeValue("enabled");
      myEnabled = enabled == null ? true : Boolean.valueOf(enabled);

      final String enabledForTabs = state.getAttributeValue("enabledForTabs");
      myEnabledForTabs = enabledForTabs == null ? false : Boolean.valueOf(enabledForTabs);
    }

    myModel.load(state, shared);
  }

  @SuppressWarnings({"MethodMayBeStatic"})
  public Collection<String> getColorNames() {
    final Set<String> names = ourDefaultColors.keySet();
    final List<String> sorted = new ArrayList<String>(names);
    Collections.sort(sorted);
    return sorted;
  }

  @SuppressWarnings({"AutoUnboxing"})
  public void loadState(Element state) {
    initSharedConfigurations();
    loadState(state, false);
  }

  @Override
  public boolean isColored(@NotNull final String scopeName, final boolean shared) {
    return myModel.isColored(scopeName, shared);
  }

  @Nullable
  public Color getFileColor(@NotNull final PsiFile file) {
    final String colorName = myModel.getColor(file);
    return colorName == null ? null : ourDefaultColors.get(colorName);
  }

  public boolean isShared(@NotNull final String scopeName) {
    return myModel.isShared(scopeName);
  }

  FileColorsModel getModel() {
    return myModel;
  }

  boolean isShared(FileColorConfiguration configuration) {
    return myModel.isShared(configuration);
  }

  public Project getProject() {
    return myProject;
  }

  public List<FileColorConfiguration> getLocalConfigurations() {
    return myModel.getLocalConfigurations();
  }

  public List<FileColorConfiguration> getSharedConfigurations() {
    return myModel.getSharedConfigurations();
  }
}
