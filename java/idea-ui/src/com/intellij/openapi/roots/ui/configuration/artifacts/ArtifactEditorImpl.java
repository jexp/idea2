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
package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DataManager;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.impl.TypeSafeDataProviderAdapter;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.artifacts.actions.*;
import com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems.LibrarySourceItem;
import com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems.SourceItemsTree;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.artifacts.ModifiableArtifact;
import com.intellij.packaging.elements.ComplexPackagingElementType;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.packaging.elements.PackagingElementType;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.packaging.impl.elements.ArchivePackagingElement;
import com.intellij.packaging.ui.ManifestFileConfiguration;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.ui.TreeToolTipHandler;
import com.intellij.util.EventDispatcher;
import com.intellij.util.Icons;
import com.intellij.util.ui.ThreeStateCheckBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class ArtifactEditorImpl implements ArtifactEditorEx {
  private JPanel myMainPanel;
  private JCheckBox myBuildOnMakeCheckBox;
  private TextFieldWithBrowseButton myOutputDirectoryField;    
  private JPanel myEditorPanel;
  private JPanel myErrorPanelPlace;
  private ThreeStateCheckBox myShowContentCheckBox;
  private FixedSizeButton myShowSpecificContentOptionsButton;
  private ActionGroup myShowSpecificContentOptionsGroup;
  private final Project myProject;
  private final ComplexElementSubstitutionParameters mySubstitutionParameters = new ComplexElementSubstitutionParameters();
  private final EventDispatcher<ArtifactEditorListener> myDispatcher = EventDispatcher.create(ArtifactEditorListener.class);
  private final ArtifactEditorContextImpl myContext;
  private SourceItemsTree mySourceItemsTree;
  private final Artifact myOriginalArtifact;
  private final LayoutTreeComponent myLayoutTreeComponent;
  private TabbedPaneWrapper myTabbedPane;
  private ArtifactPropertiesEditors myPropertiesEditors;
  private ArtifactValidationManagerImpl myValidationManager;

  public ArtifactEditorImpl(final ArtifactsStructureConfigurableContext context, Artifact artifact) {
    myContext = new ArtifactEditorContextImpl(context, this);
    myOriginalArtifact = artifact;
    myProject = context.getProject();
    mySourceItemsTree = new SourceItemsTree(myContext, this);
    myLayoutTreeComponent = new LayoutTreeComponent(this, mySubstitutionParameters, myContext, myOriginalArtifact);
    myPropertiesEditors = new ArtifactPropertiesEditors(myContext, myOriginalArtifact, myOriginalArtifact);
    Disposer.register(this, mySourceItemsTree);
    Disposer.register(this, myLayoutTreeComponent);
    myBuildOnMakeCheckBox.setSelected(artifact.isBuildOnMake());
    final String outputPath = artifact.getOutputPath();
    myOutputDirectoryField.addBrowseFolderListener(CompilerBundle.message("dialog.title.output.directory.for.artifact"),
                                                   CompilerBundle.message("chooser.description.select.output.directory.for.0.artifact",
                                                                          getArtifact().getName()), myProject,
                                                   FileChooserDescriptorFactory.createSingleFolderDescriptor());
    myShowSpecificContentOptionsGroup = createShowSpecificContentOptionsGroup();
    myShowSpecificContentOptionsButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UNKNOWN, myShowSpecificContentOptionsGroup).getComponent().show(myShowSpecificContentOptionsButton, 0, 0);
      }
    });
    setOutputPath(outputPath);
    myValidationManager = new ArtifactValidationManagerImpl(this);
    myContext.setValidationManager(myValidationManager);
    updateShowContentCheckbox();
  }

  private ActionGroup createShowSpecificContentOptionsGroup() {
    final DefaultActionGroup group = new DefaultActionGroup();
    for (ComplexPackagingElementType<?> type : PackagingElementFactory.getInstance().getComplexElementTypes()) {
      group.add(new ToggleShowElementContentAction(type, this));
    }
    return group;
  }

  private void setOutputPath(@Nullable String outputPath) {
    myOutputDirectoryField.setText(outputPath != null ? FileUtil.toSystemDependentName(outputPath) : null);
  }

  public void apply() {
    final ModifiableArtifact modifiableArtifact = myContext.getModifiableArtifactModel().getOrCreateModifiableArtifact(myOriginalArtifact);
    modifiableArtifact.setBuildOnMake(myBuildOnMakeCheckBox.isSelected());
    modifiableArtifact.setOutputPath(getConfiguredOutputPath());
    myPropertiesEditors.applyProperties();
    myLayoutTreeComponent.saveElementProperties();
  }

  @Nullable
  private String getConfiguredOutputPath() {
    String outputPath = FileUtil.toSystemIndependentName(myOutputDirectoryField.getText().trim());
    if (outputPath.length() == 0) {
      outputPath = null;
    }
    return outputPath;
  }

  public SourceItemsTree getSourceItemsTree() {
    return mySourceItemsTree;
  }

  public void addListener(@NotNull final ArtifactEditorListener listener) {
    myDispatcher.addListener(listener);
  }

  public ArtifactEditorContextImpl getContext() {
    return myContext;
  }

  public void removeListener(@NotNull final ArtifactEditorListener listener) {
    myDispatcher.removeListener(listener);
  }

  public Artifact getArtifact() {
    return myContext.getArtifactModel().getArtifactByOriginal(myOriginalArtifact);
  }

  public CompositePackagingElement<?> getRootElement() {
    return myLayoutTreeComponent.getRootElement();
  }

  public void rebuildTries() {
    myLayoutTreeComponent.rebuildTree();
    mySourceItemsTree.rebuildTree();
  }

  public void queueValidation() {
    myValidationManager.queueValidation();
  }

  public JComponent createMainComponent() {
    mySourceItemsTree.initTree();
    myLayoutTreeComponent.initTree();
    myMainPanel.putClientProperty(DataManager.CLIENT_PROPERTY_DATA_PROVIDER, new TypeSafeDataProviderAdapter(new MyDataProvider()));

    myErrorPanelPlace.add(myValidationManager.getMainErrorPanel(), BorderLayout.CENTER);

    Splitter splitter = new Splitter(false);
    final JPanel leftPanel = new JPanel(new BorderLayout());
    leftPanel.add(myLayoutTreeComponent.getTreePanel(), BorderLayout.CENTER);
    final Border border = BorderFactory.createEmptyBorder(3, 3, 3, 3);
    leftPanel.setBorder(border);
    splitter.setFirstComponent(leftPanel);

    final JPanel rightPanel = new JPanel(new BorderLayout());
    final JPanel rightTopPanel = new JPanel(new BorderLayout());
    rightTopPanel.add(new JLabel("Available Elements (drag'n'drop to layout tree)"), BorderLayout.SOUTH);
    rightPanel.add(rightTopPanel, BorderLayout.NORTH);
    rightPanel.add(ScrollPaneFactory.createScrollPane(mySourceItemsTree.getTree()), BorderLayout.CENTER);
    rightPanel.add(new JPanel(), BorderLayout.SOUTH);
    rightPanel.setBorder(border);
    splitter.setSecondComponent(rightPanel);


    myShowContentCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final ThreeStateCheckBox.State state = myShowContentCheckBox.getState();
        if (state == ThreeStateCheckBox.State.SELECTED) {
          mySubstitutionParameters.setSubstituteAll();
        }
        else if (state == ThreeStateCheckBox.State.NOT_SELECTED) {
          mySubstitutionParameters.setSubstituteNone();
        }
        myShowContentCheckBox.setThirdStateEnabled(false);
        myLayoutTreeComponent.rebuildTree();
      }
    });

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, createToolbarActionGroup(), true);
    leftPanel.add(toolbar.getComponent(), BorderLayout.NORTH);
    rightTopPanel.setPreferredSize(new Dimension(-1, toolbar.getComponent().getPreferredSize().height));

    myTabbedPane = new TabbedPaneWrapper(this);
    myTabbedPane.addTab("Output Layout", splitter);
    myPropertiesEditors.addTabs(myTabbedPane);
    myEditorPanel.add(myTabbedPane.getComponent(), BorderLayout.CENTER);

    final LayoutTree tree = myLayoutTreeComponent.getLayoutTree();
    new ShowAddPackagingElementPopupAction(this).registerCustomShortcutSet(CommonShortcuts.getNew(), tree);
    PopupHandler.installPopupHandler(tree, createPopupActionGroup(), ActionPlaces.UNKNOWN, ActionManager.getInstance());
    TreeToolTipHandler.install(tree);
    ToolTipManager.sharedInstance().registerComponent(tree);
    rebuildTries();
    return getMainComponent();
  }

  public void updateShowContentCheckbox() {
    final ThreeStateCheckBox.State state;
    if (mySubstitutionParameters.isAllSubstituted()) {
      state = ThreeStateCheckBox.State.SELECTED;
    }
    else if (mySubstitutionParameters.isNoneSubstituted()) {
      state = ThreeStateCheckBox.State.NOT_SELECTED;
    }
    else {
      state = ThreeStateCheckBox.State.DONT_CARE;
    }
    myShowContentCheckBox.setThirdStateEnabled(state == ThreeStateCheckBox.State.DONT_CARE);
    myShowContentCheckBox.setState(state);
  }

  private DefaultActionGroup createToolbarActionGroup() {
    final DefaultActionGroup toolbarActionGroup = new DefaultActionGroup();

    final List<AnAction> createActions = new ArrayList<AnAction>(createNewElementActions());
    for (AnAction createAction : createActions) {
      toolbarActionGroup.add(createAction);
    }

    toolbarActionGroup.add(new RemovePackagingElementAction(this));
    toolbarActionGroup.add(Separator.getInstance());
    toolbarActionGroup.add(new SortElementsToggleAction(this.getLayoutTreeComponent()));
    toolbarActionGroup.add(new MovePackagingElementAction(myLayoutTreeComponent, "Move Up", "", IconLoader.getIcon("/actions/moveUp.png"), -1));
    toolbarActionGroup.add(new MovePackagingElementAction(myLayoutTreeComponent, "Move Down", "", IconLoader.getIcon("/actions/moveDown.png"), 1));
    return toolbarActionGroup;
  }

  public List<AnAction> createNewElementActions() {
    final List<AnAction> createActions = new ArrayList<AnAction>();
    AddCompositeElementAction.addCompositeCreateActions(createActions, this);
    createActions.add(createAddNonCompositeElementGroup());
    return createActions;
  }

  private DefaultActionGroup createPopupActionGroup() {
    final LayoutTree tree = myLayoutTreeComponent.getLayoutTree();

    DefaultActionGroup popupActionGroup = new DefaultActionGroup();
    final List<AnAction> createActions = new ArrayList<AnAction>();
    AddCompositeElementAction.addCompositeCreateActions(createActions, this);
    for (AnAction createAction : createActions) {
      popupActionGroup.add(createAction);
    }
    popupActionGroup.add(createAddNonCompositeElementGroup());
    final RemovePackagingElementAction removeAction = new RemovePackagingElementAction(this);
    removeAction.registerCustomShortcutSet(CommonShortcuts.DELETE, tree);
    popupActionGroup.add(removeAction);
    popupActionGroup.add(new ExtractArtifactAction(this));
    popupActionGroup.add(new InlineArtifactAction(this));
    popupActionGroup.add(new RenamePackagingElementAction(this));
    popupActionGroup.add(Separator.getInstance());
    popupActionGroup.add(new HideContentAction(this));
    popupActionGroup.add(new LayoutTreeNavigateAction(myLayoutTreeComponent));
    popupActionGroup.add(new LayoutTreeFindUsagesAction(myLayoutTreeComponent, myProject, myContext.getParent()));

    popupActionGroup.add(Separator.getInstance());
    CommonActionsManager actionsManager = CommonActionsManager.getInstance();
    DefaultTreeExpander treeExpander = new DefaultTreeExpander(tree);
    popupActionGroup.add(actionsManager.createExpandAllAction(treeExpander, tree));
    popupActionGroup.add(actionsManager.createCollapseAllAction(treeExpander, tree));
    return popupActionGroup;
  }

  public ComplexElementSubstitutionParameters getSubstitutionParameters() {
    return mySubstitutionParameters;
  }

  private ActionGroup createAddNonCompositeElementGroup() {
    DefaultActionGroup group = new DefaultActionGroup(ProjectBundle.message("artifacts.add.copy.action"), true);
    group.getTemplatePresentation().setIcon(Icons.ADD_ICON);
    for (PackagingElementType<?> type : PackagingElementFactory.getInstance().getNonCompositeElementTypes()) {
      group.add(new AddNewPackagingElementAction(type, this));
    }
    return group;
  }

  public JComponent getMainComponent() {
    return myMainPanel;
  }

  public void addNewPackagingElement(@NotNull PackagingElementType<?> type) {
    myLayoutTreeComponent.addNewPackagingElement(type);
    mySourceItemsTree.rebuildTree();
  }

  public void removeSelectedElements() {
    myLayoutTreeComponent.removeSelectedElements();
  }

  public boolean isModified() {
    return myBuildOnMakeCheckBox.isSelected() != myOriginalArtifact.isBuildOnMake()
        || !Comparing.equal(getConfiguredOutputPath(), myOriginalArtifact.getOutputPath())
        || myPropertiesEditors.isModified()
        || myLayoutTreeComponent.isPropertiesModified();
  }

  public void dispose() {
  }

  public LayoutTreeComponent getLayoutTreeComponent() {
    return myLayoutTreeComponent;
  }

  public void updateOutputPath(@NotNull String oldArtifactName, @NotNull final String newArtifactName) {
    final String oldDefaultPath = ArtifactUtil.getDefaultArtifactOutputPath(oldArtifactName, myProject);
    if (Comparing.equal(oldDefaultPath, getConfiguredOutputPath())) {
      setOutputPath(ArtifactUtil.getDefaultArtifactOutputPath(newArtifactName, myProject));
      final CompositePackagingElement<?> root = getRootElement();
      if (root instanceof ArchivePackagingElement) {
        final String name = ((ArchivePackagingElement)root).getArchiveFileName();
        final String fileName = FileUtil.getNameWithoutExtension(name);
        final String extension = FileUtil.getExtension(name);
        if (fileName.equals(oldArtifactName) && extension.length() > 0) {
          myLayoutTreeComponent.editLayout(new Runnable() {
            public void run() {
              ((ArchivePackagingElement)getRootElement()).setArchiveFileName(newArtifactName + "." + extension);
            }
          });
          myLayoutTreeComponent.updateRootNode();
        }
      }
    }
  }

  public void putLibraryIntoDefaultLocation(@NotNull Library library) {
    myLayoutTreeComponent.putIntoDefaultLocations(Collections.singletonList(new LibrarySourceItem(library)));
  }

  public void addToClasspath(CompositePackagingElement<?> element, List<String> classpath) {
    myLayoutTreeComponent.saveElementProperties();
    final ManifestFileConfiguration manifest = myContext.getManifestFile(element, getArtifact().getArtifactType());
    manifest.addToClasspath(classpath);
    myLayoutTreeComponent.resetElementProperties();
  }

  public void setArtifactType(ArtifactType artifactType) {
    final ModifiableArtifact modifiableArtifact = myContext.getModifiableArtifactModel().getOrCreateModifiableArtifact(myOriginalArtifact);
    modifiableArtifact.setArtifactType(artifactType);

    myPropertiesEditors.removeTabs(myTabbedPane);
    myPropertiesEditors = new ArtifactPropertiesEditors(myContext, myOriginalArtifact, getArtifact());
    myPropertiesEditors.addTabs(myTabbedPane);

    final CompositePackagingElement<?> oldRootElement = getRootElement();
    final CompositePackagingElement<?> newRootElement = artifactType.createRootElement(getArtifact().getName());
    if (!newRootElement.getType().equals(oldRootElement.getType())) {
      ArtifactUtil.copyChildren(oldRootElement, newRootElement, myProject);
      myLayoutTreeComponent.setRootElement(newRootElement);
    }
  }

  private class MyDataProvider implements TypeSafeDataProvider {
    public void calcData(DataKey key, DataSink sink) {
      if (ARTIFACTS_EDITOR_KEY.equals(key)) {
        sink.put(ARTIFACTS_EDITOR_KEY, ArtifactEditorImpl.this);
      }
    }
  }

}