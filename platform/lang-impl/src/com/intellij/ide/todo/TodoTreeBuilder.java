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

package com.intellij.ide.todo;

import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.todo.nodes.TodoFileNode;
import com.intellij.ide.todo.nodes.TodoItemNode;
import com.intellij.ide.todo.nodes.TodoTreeHelper;
import com.intellij.ide.util.treeView.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.StatusBarProgress;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageTreeColorsScheme;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.util.*;

/**
 * @author Vladimir Kondratyev
 */
public abstract class TodoTreeBuilder extends AbstractTreeBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.todo.TodoTreeBuilder");
  protected final Project myProject;

  /**
   * All files that have T.O.D.O items are presented as tree. This tree help a lot
   * to separate these files by directories.
   */
  protected final FileTree myFileTree;
  /**
   * This set contains "dirty" files. File is "dirty" if it's currently not nkown
   * whether the file contains T.O.D.O item or not. To determine this it's necessary
   * to perform some (perhaps, CPU expensive) operation. These "dirty" files are
   * validated in <code>validateCache()</code> method.
   */
  protected final HashSet<VirtualFile> myDirtyFileSet;

  protected final HashMap<VirtualFile, EditorHighlighter> myFile2Highlighter;

  protected final PsiSearchHelper mySearchHelper;
  /**
   * If this flag is false then the updateTree() method does nothing. But when
   * the flag becomes true and myDirtyFileSet isn't empty the update is invoked.
   * This is done for optimization reasons: if TodoPane is not visible then
   * updates isn't invoked.
   */
  private boolean myUpdatable;

  /** Updates tree if containing files change VCS status. */
  private final MyFileStatusListener myFileStatusListener;

  TodoTreeBuilder(JTree tree, DefaultTreeModel treeModel, Project project) {
    super(tree, treeModel, null, MyComparator.ourInstance, false);
    myProject = project;

    myFileTree = new FileTree();
    myDirtyFileSet = new HashSet<VirtualFile>();

    myFile2Highlighter = new HashMap<VirtualFile, EditorHighlighter>();

    PsiManager psiManager = PsiManager.getInstance(myProject);
    mySearchHelper = psiManager.getSearchHelper();
    psiManager.addPsiTreeChangeListener(new MyPsiTreeChangeListener());

    myFileStatusListener = new MyFileStatusListener();

    getUpdater().setDelay(1500);
  }

  /**
   * Initializes the builder. Subclasses should don't forget to call this method after constuctor has
   * been invoked.
   */
  public final void init() {
    TodoTreeStructure todoTreeStructure = createTreeStructure();
    setTreeStructure(todoTreeStructure);
    todoTreeStructure.setTreeBuilder(this);

    rebuildCache();
    initRootNode();

    Object selectableElement = todoTreeStructure.getFirstSelectableElement();
    if (selectableElement != null) {
      buildNodeForElement(selectableElement);
      DefaultMutableTreeNode node = getNodeForElement(selectableElement);
      if (node != null) {
        getTree().getSelectionModel().setSelectionPath(new TreePath(node.getPath()));
      }
    }

    FileStatusManager.getInstance(myProject).addFileStatusListener(myFileStatusListener);
  }

  public final void dispose() {
    FileStatusManager.getInstance(myProject).removeFileStatusListener(myFileStatusListener);
    super.dispose();
  }

  final boolean isUpdatable() {
    return myUpdatable;
  }

  /**
   * Sets whenther the builder updates the tree when data change.
   */
  final void setUpdatable(boolean updatable) {
    if (myUpdatable != updatable) {
      myUpdatable = updatable;
      if (myUpdatable) {
        updateTree(false);
      }
    }
  }

  protected abstract TodoTreeStructure createTreeStructure();

  protected boolean validateNode(final Object child) {
    if (child instanceof ProjectViewNode) {
      final ProjectViewNode projectViewNode = (ProjectViewNode)child;
      projectViewNode.update();
      if (projectViewNode.getValue() == null) {
        return false;
      }
    }
    return true;
  }

  public final TodoTreeStructure getTodoTreeStructure() {
    return (TodoTreeStructure)getTreeStructure();
  }

  protected final AbstractTreeUpdater createUpdater() {
    return new AbstractTreeUpdater(this) {
      @Override
      protected ActionCallback beforeUpdate(final TreeUpdatePass pass) {
        if (!myDirtyFileSet.isEmpty()) { // suppress redundant cache validations
          validateCache();
          getTodoTreeStructure().validateCache();
        }

        return new ActionCallback.Done();
      }
    };
  }

  /**
   * @return read-only iterator of all current PSI files that can contain TODOs.
   *         Don't invoke its <code>remove</code> method. For "removing" use <code>markFileAsDirty</code> method.
   *         <b>Note, that <code>next()</code> method of iterator can return <code>null</code> elements.</b>
   *         These <code>null</code> elements correspond to the invalid PSI files (PSI file cannot be found by
   *         virtual file, or virtual file is invalid).
   *         The reason why we return such "dirty" iterator is the peformance.
   */
  public Iterator<PsiFile> getAllFiles() {
    final Iterator<VirtualFile> iterator = myFileTree.getFileIterator();
    return new Iterator<PsiFile>() {
      public boolean hasNext() {
        return iterator.hasNext();
      }

      @Nullable public PsiFile next() {
        VirtualFile vFile = iterator.next();
        if (vFile == null || !vFile.isValid()) {
          return null;
        }
        PsiFile psiFile = PsiManager.getInstance(myProject).findFile(vFile);
        if (psiFile == null || !psiFile.isValid()) {
          return null;
        }
        return psiFile;
      }

      public void remove() {
        throw new IllegalArgumentException();
      }
    };
  }

  /**
   * @return read-only iterator of all valid PSI files that can have T.O.D.O items
   *         and which are located under specified <code>psiDirctory</code>.
   * @see com.intellij.ide.todo.FileTree#getFiles(com.intellij.openapi.vfs.VirtualFile)
   */
  public Iterator<PsiFile> getFiles(PsiDirectory psiDirectory) {
    return getFiles(psiDirectory, true);
  }

  /**
   * @return read-only iterator of all valid PSI files that can have T.O.D.O items
   *         and which are located under specified <code>psiDirctory</code>.
   * @see FileTree#getFiles(VirtualFile)
   */
  public Iterator<PsiFile> getFiles(PsiDirectory psiDirectory, final boolean skip) {
    ArrayList<VirtualFile> files = myFileTree.getFiles(psiDirectory.getVirtualFile());
    ArrayList<PsiFile> psiFileList = new ArrayList<PsiFile>(files.size());
    PsiManager psiManager = PsiManager.getInstance(myProject);
    for (VirtualFile file : files) {
      final Module module = ModuleUtil.findModuleForPsiElement(psiDirectory);
      if (module != null) {
        final boolean isInContent = ModuleRootManager.getInstance(module).getFileIndex().isInContent(file);
        if (!isInContent) continue;
      }
      if (file.isValid()) {
        PsiFile psiFile = psiManager.findFile(file);
        if (psiFile != null) {
          final PsiDirectory directory = psiFile.getContainingDirectory();
          if (directory == null || !skip || !TodoTreeHelper.getInstance(myProject).skipDirectory(directory)) {
            psiFileList.add(psiFile);
          }
        }
      }
    }
    return psiFileList.iterator();
  }

  /**
   * @return read-only iterator of all valid PSI files that can have T.O.D.O items
   *         and which are located under specified <code>psiDirctory</code>.
   * @see FileTree#getFiles(VirtualFile)
   */
  public Iterator<PsiFile> getFilesUnderDirectory(PsiDirectory psiDirectory) {
    ArrayList<VirtualFile> files = myFileTree.getFilesUnderDirectory(psiDirectory.getVirtualFile());
    ArrayList<PsiFile> psiFileList = new ArrayList<PsiFile>(files.size());
    PsiManager psiManager = PsiManager.getInstance(myProject);
    for (VirtualFile file : files) {
      final Module module = ModuleUtil.findModuleForPsiElement(psiDirectory);
      if (module != null) {
        final boolean isInContent = ModuleRootManager.getInstance(module).getFileIndex().isInContent(file);
        if (!isInContent) continue;
      }
      if (file.isValid()) {
        PsiFile psiFile = psiManager.findFile(file);
        if (psiFile != null) {
          psiFileList.add(psiFile);
        }
      }
    }
    return psiFileList.iterator();
  }



  /**
    * @return read-only iterator of all valid PSI files that can have T.O.D.O items
    *         and which in specified <code>module</code>.
    * @see FileTree#getFiles(VirtualFile)
    */
   public Iterator<PsiFile> getFiles(Module module) {
    if (module.isDisposed()) return Collections.<PsiFile>emptyList().iterator();
    ArrayList<PsiFile> psiFileList = new ArrayList<PsiFile>();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    final VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
    for (VirtualFile virtualFile : contentRoots) {
      ArrayList<VirtualFile> files = myFileTree.getFiles(virtualFile);
      PsiManager psiManager = PsiManager.getInstance(myProject);
      for (VirtualFile file : files) {
        if (fileIndex.getModuleForFile(file) != module) continue;
        if (file.isValid()) {
          PsiFile psiFile = psiManager.findFile(file);
          if (psiFile != null) {
            psiFileList.add(psiFile);
          }
        }
      }
    }
    return psiFileList.iterator();
   }


  /**
   * @return <code>true</code> if specified <code>psiFile</code> can contains too items.
   *         It means that file is in "dirty" file set or in "current" file set.
   */
  private boolean canContainTodoItems(PsiFile psiFile) {
    VirtualFile vFile = psiFile.getVirtualFile();
    return myFileTree.contains(vFile) || myDirtyFileSet.contains(vFile);
  }

  /**
   * Marks specified PsiFile as dirty. It means that file is being add into "dirty" file set.
   * It presents in current file set also but the next validateCache call will validate this
   * "dirty" file. This method should be invoked when any modifications inside the file
   * have happend.
   */
  private void markFileAsDirty(@NotNull PsiFile psiFile) {
    VirtualFile vFile = psiFile.getVirtualFile();
    if (vFile != null) { // If PSI file isn't valid then its VirtualFile can be null
        myDirtyFileSet.add(vFile);
    }
  }

  /**
   * Clear and rebuild whole the caches. It means that after rebuilding all caches are valid.
   */
  abstract void rebuildCache();

  private void validateCache() {
    TodoTreeStructure treeStructure = getTodoTreeStructure();
    // First of all we need to update "dirty" file set.
    for (Iterator<VirtualFile> i = myDirtyFileSet.iterator(); i.hasNext();) {
      VirtualFile file = i.next();
      PsiFile psiFile = file.isValid() ? PsiManager.getInstance(myProject).findFile(file) : null;
      if (psiFile == null || !treeStructure.accept(psiFile)) {
        if (myFileTree.contains(file)) {
          myFileTree.removeFile(file);
          if (myFile2Highlighter.containsKey(file)) { // highlighter isn't needed any more
            myFile2Highlighter.remove(file);
          }
        }
      }
      else { // file is valid and contains T.O.D.O items
        myFileTree.removeFile(file);
        myFileTree.add(file); // file can be moved. remove/add calls move it to another place
        if (myFile2Highlighter.containsKey(file)) { // update highlighter's text
          Document document = PsiDocumentManager.getInstance(myProject).getDocument(psiFile);
          EditorHighlighter highlighter = myFile2Highlighter.get(file);
          highlighter.setText(document.getCharsSequence());
        }
      }
      i.remove();
    }
    LOG.assertTrue(myDirtyFileSet.isEmpty());
    // Now myDirtyFileSet should be empty
  }

  protected boolean isAutoExpandNode(NodeDescriptor descriptor) {
    return getTodoTreeStructure().isAutoExpandNode(descriptor);
  }

  protected boolean isAlwaysShowPlus(NodeDescriptor nodeDescriptor) {
    final Object element= nodeDescriptor.getElement();
    if (element instanceof TodoItemNode){
      return false;
    } else if(element instanceof PsiFileNode) {
      return getTodoTreeStructure().mySearchHelper.getTodoItemsCount(((PsiFileNode)element).getValue()) > 0;
    }
    return true;
  }

  /**
   * @return first <code>SmartTodoItemPointer</code> that is the children (in depth) of the specified <code>element</code>.
   *         If <code>element</code> itself is a <code>TodoItem</code> then the method returns the <code>element</code>.
   */
  public TodoItemNode getFirstPointerForElement(Object element) {
    if (element instanceof TodoItemNode) {
      return (TodoItemNode)element;
    }
    else {
      Object[] children = getTreeStructure().getChildElements(element);
      if (children.length == 0) {
        return null;
      }
      Object firstChild = children[0];
      if (firstChild instanceof TodoItemNode) {
        return (TodoItemNode)firstChild;
      }
      else {
        return getFirstPointerForElement(firstChild);
      }
    }
  }

  /**
   * @return last <code>SmartTodoItemPointer</code> that is the children (in depth) of the specified <code>element</code>.
   *         If <code>element</code> itself is a <code>TodoItem</code> then the method returns the <code>element</code>.
   */
  public TodoItemNode getLastPointerForElement(Object element) {
    if (element instanceof TodoItemNode) {
      return (TodoItemNode)element;
    }
    else {
      Object[] children = getTreeStructure().getChildElements(element);
      if (children.length == 0) {
        return null;
      }
      Object firstChild = children[children.length - 1];
      if (firstChild instanceof TodoItemNode) {
        return (TodoItemNode)firstChild;
      }
      else {
        return getLastPointerForElement(firstChild);
      }
    }
  }

  protected final void updateTree(boolean later) {
    if (myUpdatable) {
      getUpdater().addSubtreeToUpdate(getRootNode());
      if (!later) {
        getUpdater().performUpdate();
      }
    }
  }

  static PsiFile getFileForNode(DefaultMutableTreeNode node) {
    Object obj = node.getUserObject();
    if (obj instanceof TodoFileNode) {
      return ((TodoFileNode)obj).getValue();
    }
    else if (obj instanceof TodoItemNode) {
      SmartTodoItemPointer pointer = ((TodoItemNode)obj).getValue();
      return pointer.getTodoItem().getFile();
    }
    return null;
  }

  void collapseAll() {
    int row = getTree().getRowCount() - 1;
    while (row > 0) {
      getTree().collapseRow(row);
      row--;
    }
  }

  void expandAll() {
    for (int i = 0; i < getTree().getRowCount(); i++) {
      getTree().expandRow(i);
    }
  }

  /**
   * Sets whether packages are shown or not.
   */
  void setShowPackages(boolean state) {
    getTodoTreeStructure().setShownPackages(state);
    ArrayList<Object> pathsToExpand = new ArrayList<Object>();
    ArrayList<Object> pathsToSelect = new ArrayList<Object>();
    TreeBuilderUtil.storePaths(this, getRootNode(), pathsToExpand, pathsToSelect, true);
    getTree().clearSelection();
    getTodoTreeStructure().validateCache();
    updateTree(false);
    TreeBuilderUtil.restorePaths(this, pathsToExpand, pathsToSelect, true);
  }

  /**
   * @param state if <code>true</code> then view is in "flatten packages" mode.
   */
  void setFlattenPackages(boolean state) {
    ArrayList<Object> pathsToExpand = new ArrayList<Object>();
    ArrayList<Object> pathsToSelect = new ArrayList<Object>();
    TreeBuilderUtil.storePaths(this, getRootNode(), pathsToExpand, pathsToSelect, true);
    getTree().clearSelection();
    TodoTreeStructure todoTreeStructure = getTodoTreeStructure();
    todoTreeStructure.setFlattenPackages(state);
    todoTreeStructure.validateCache();
    updateTree(false);
    TreeBuilderUtil.restorePaths(this, pathsToExpand, pathsToSelect, true);
  }

  /**
   * Sets new <code>TodoFilter</code>, rebuild whole the caches and immediately update the tree.
   *
   * @see TodoTreeStructure#setTodoFilter
   */
  void setTodoFilter(TodoFilter filter) {
    getTodoTreeStructure().setTodoFilter(filter);
    rebuildCache();
    updateTree(false);
  }

  /**
   * @return next <code>TodoItem</code> for the passed <code>pointer</code>. Returns <code>null</code>
   *         if the <code>pointer</code> is the last t.o.d.o item in the tree.
   */
  public TodoItemNode getNextPointer(TodoItemNode pointer) {
    Object sibling = getNextSibling(pointer);
    if (sibling == null) {
      return null;
    }
    if (sibling instanceof TodoItemNode) {
      return (TodoItemNode)sibling;
    }
    else {
      return getFirstPointerForElement(sibling);
    }
  }

  /**
   * @return next sibling of the passed element. If there is no sibling then
   *         returns <code>null</code>.
   */
  Object getNextSibling(Object obj) {
    Object parent = getTreeStructure().getParentElement(obj);
    if (parent == null) {
      return null;
    }
    Object[] children = getTreeStructure().getChildElements(parent);
    int idx = -1;
    for (int i = 0; i < children.length; i++) {
      if (obj.equals(children[i])) {
        idx = i;
        break;
      }
    }
    if (idx == -1) {
      return null;
    }
    if (idx < children.length - 1) {
      return children[idx + 1];
    }
    // passed object is the last in the list. In this case we have to return first child of the
    // next parent's sibling.
    return getNextSibling(parent);
  }

  /**
   * @return next <code>SmartTodoItemPointer</code> for the passed <code>pointer</code>. Returns <code>null</code>
   *         if the <code>pointer</code> is the last t.o.d.o item in the tree.
   */
  public TodoItemNode getPreviousPointer(TodoItemNode pointer) {
    Object sibling = getPreviousSibling(pointer);
    if (sibling == null) {
      return null;
    }
    if (sibling instanceof TodoItemNode) {
      return (TodoItemNode)sibling;
    }
    else {
      return getLastPointerForElement(sibling);
    }
  }

  /**
   * @return previous sibling of the element of passed type. If there is no sibling then
   *         returns <code>null</code>.
   */
  Object getPreviousSibling(Object obj) {
    Object parent = getTreeStructure().getParentElement(obj);
    if (parent == null) {
      return null;
    }
    Object[] children = getTreeStructure().getChildElements(parent);
    int idx = -1;
    for (int i = 0; i < children.length; i++) {
      if (obj.equals(children[i])) {
        idx = i;

        break;
      }
    }
    if (idx == -1) {
      return null;
    }
    if (idx > 0) {
      return children[idx - 1];
    }
    // passed object is the first in the list. In this case we have to return last child of the
    // previous parent's sibling.
    return getPreviousSibling(parent);
  }

  /**
   * @return <code>SelectInEditorManager</code> for the specified <code>psiFile</code>. Highlighters are
   *         lazy created and initialized.
   */
  public EditorHighlighter getHighlighter(PsiFile psiFile, Document document) {
    VirtualFile file = psiFile.getVirtualFile();
    if (myFile2Highlighter.containsKey(file)) {
      return myFile2Highlighter.get(file);
    }
    else {
      EditorHighlighter highlighter = HighlighterFactory.createHighlighter(UsageTreeColorsScheme.getInstance().getScheme(), file.getName(), myProject);
      highlighter.setText(document.getCharsSequence());
      myFile2Highlighter.put(file, highlighter);
      return highlighter;
    }
  }

  void setShowModules(boolean state) {
    getTodoTreeStructure().setShownModules(state);
    ArrayList<Object> pathsToExpand = new ArrayList<Object>();
    ArrayList<Object> pathsToSelect = new ArrayList<Object>();
    TreeBuilderUtil.storePaths(this, getRootNode(), pathsToExpand, pathsToSelect, true);
    getTree().clearSelection();
    getTodoTreeStructure().validateCache();
    updateTree(false);
    TreeBuilderUtil.restorePaths(this, pathsToExpand, pathsToSelect, true);
  }

  public boolean isDirectoryEmpty(@NotNull PsiDirectory psiDirectory){
    return myFileTree.isDirectoryEmpty(psiDirectory.getVirtualFile());
  }

  @NotNull
  protected ProgressIndicator createProgressIndicator() {
    return new StatusBarProgress();
  }

  private static final class MyComparator implements Comparator<NodeDescriptor> {
    public static final Comparator<NodeDescriptor> ourInstance = new MyComparator();

    public int compare(NodeDescriptor descriptor1, NodeDescriptor descriptor2) {
      int weight1 = descriptor1.getWeight();
      int weight2 = descriptor2.getWeight();
      if (weight1 != weight2) {
        return weight1 - weight2;
      }
      else {
        return descriptor1.getIndex() - descriptor2.getIndex();
      }
    }
  }

  private final class MyPsiTreeChangeListener extends PsiTreeChangeAdapter {
    public void childAdded(PsiTreeChangeEvent e) {
      // If local modification
      if (e.getFile() != null) {
        markFileAsDirty(e.getFile());
        updateTree(true);
        return;
      }
      // If added element if PsiFile and it doesn't contains TODOs, then do nothing
      PsiElement child = e.getChild();
      if (!(child instanceof PsiFile)) {
        return;
      }
      PsiFile psiFile = (PsiFile)e.getChild();
      markFileAsDirty(psiFile);
      updateTree(true);
    }

    public void beforeChildRemoval(PsiTreeChangeEvent e) {
      // If local midification
      if (e.getFile() != null) {
        markFileAsDirty(e.getFile());
        updateTree(true);
        return;
      }
      //
      PsiElement child = e.getChild();
      if (child instanceof PsiFile) { // file will be removed
        PsiFile psiFile = (PsiFile)child;
        markFileAsDirty(psiFile);
        updateTree(true);
      }
      else if (child instanceof PsiDirectory) { // directory will be removed
        PsiDirectory psiDirectory = (PsiDirectory)child;
        for (Iterator<PsiFile> i = getAllFiles(); i.hasNext();) {
          PsiFile psiFile = i.next();
          if (psiFile == null) { // skip invalid PSI files
            continue;
          }
          if (PsiTreeUtil.isAncestor(psiDirectory, psiFile, true)) {
            markFileAsDirty(psiFile);
          }
        }
        updateTree(true);
      }
    }

    public void childMoved(PsiTreeChangeEvent e) {
      if (e.getFile() != null) { // local change
        markFileAsDirty(e.getFile());
        updateTree(true);
        return;
      }
      if (e.getChild() instanceof PsiFile) { // file was moved
        PsiFile psiFile = (PsiFile)e.getChild();
        if (!canContainTodoItems(psiFile)) { // moved file doesn't contain TODOs
          return;
        }
        markFileAsDirty(psiFile);
        updateTree(true);
      }
      else if (e.getChild() instanceof PsiDirectory) { // directory was moved. mark all its files as dirty.
        PsiDirectory psiDirectory = (PsiDirectory)e.getChild();
        boolean shouldUpdate = false;
        for (Iterator<PsiFile> i = getAllFiles(); i.hasNext();) {
          PsiFile psiFile = i.next();
          if (psiFile == null) { // skip invalid PSI files
            continue;
          }
          if (PsiTreeUtil.isAncestor(psiDirectory, psiFile, true)) {
            markFileAsDirty(psiFile);
            shouldUpdate = true;
          }
        }
        if (shouldUpdate) {
          updateTree(true);
        }
      }
    }

    public void childReplaced(PsiTreeChangeEvent e) {
      if (e.getFile() != null) {
        markFileAsDirty(e.getFile());
        updateTree(true);
      }
    }

    public void childrenChanged(PsiTreeChangeEvent e) {
      if (e.getFile() != null) {
        markFileAsDirty(e.getFile());
        updateTree(true);
      }
    }

    public void propertyChanged(PsiTreeChangeEvent e) {
      String propertyName = e.getPropertyName();
      if (propertyName.equals(PsiTreeChangeEvent.PROP_ROOTS)) { // rebuild all tree when source roots were changed
        getUpdater().runBeforeUpdate(
          new Runnable() {
            public void run() {
              rebuildCache();
            }
          }
        );
        updateTree(true);
      }
      else if (PsiTreeChangeEvent.PROP_WRITABLE.equals(propertyName)) {
        PsiFile psiFile = (PsiFile)e.getElement();
        if (!canContainTodoItems(psiFile)) { // don't do anything if file cannot contain todos
          return;
        }
        updateTree(true);
      }
      else if (PsiTreeChangeEvent.PROP_FILE_NAME.equals(propertyName)) {
        PsiFile psiFile = (PsiFile)e.getElement();
        if (!canContainTodoItems(psiFile)) {
          return;
        }
        updateTree(true);
      }
      else if (PsiTreeChangeEvent.PROP_DIRECTORY_NAME.equals(propertyName)) {
        PsiDirectory psiDirectory = (PsiDirectory)e.getElement();
        Iterator<PsiFile> iterator = getFiles(psiDirectory);
        if (iterator.hasNext()) {
          updateTree(true);
        }
      }
    }
  }

  private final class MyFileStatusListener implements FileStatusListener {
    public void fileStatusesChanged() {
      updateTree(true);
    }

    public void fileStatusChanged(@NotNull VirtualFile virtualFile) {
      PsiFile psiFile = PsiManager.getInstance(myProject).findFile(virtualFile);
      if (psiFile != null && canContainTodoItems(psiFile)) {
        updateTree(true);
      }
    }
  }
}