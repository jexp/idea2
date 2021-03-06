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

package com.intellij.ide;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PsiCopyPasteManager {
  public static PsiCopyPasteManager getInstance() {
    return ServiceManager.getService(PsiCopyPasteManager.class);
  }

  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.PsiCopyPasteManagerImpl");

  private MyData myRecentData;
  private final CopyPasteManagerEx myCopyPasteManager;

  public PsiCopyPasteManager(CopyPasteManager copyPasteManager) {
    myCopyPasteManager = (CopyPasteManagerEx) copyPasteManager;
  }

  @Nullable
  public PsiElement[] getElements(boolean[] isCopied) {
    try {
      Transferable content = myCopyPasteManager.getSystemClipboardContents();
      Object transferData;
      try {
        transferData = content.getTransferData(ourDataFlavor);
      } catch (UnsupportedFlavorException e) {
        return null;
      } catch (IOException e) {
        return null;
      }

      if (!(transferData instanceof MyData)) {
        return null;
      }
      MyData dataProxy = (MyData) transferData;
      if (!Comparing.equal(dataProxy, myRecentData)) {
        return null;
      }
      if (isCopied != null) {
        isCopied[0] = myRecentData.isCopied();
      }
      return myRecentData.getElements();
    } catch (Exception e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(e);
      }
      return null;
    }
  }

  @Nullable
  static PsiElement[] getElements(final Transferable content) {
    if (content == null) return null;
    Object transferData;
    try {
      transferData = content.getTransferData(ourDataFlavor);
    } catch (UnsupportedFlavorException e) {
      return null;
    } catch (IOException e) {
      return null;
    }

    return transferData instanceof MyData ? ((MyData)transferData).getElements() : null;
  }

  public void clear() {
    Transferable old = myCopyPasteManager.getContents();
    myRecentData = null;
    myCopyPasteManager.setSystemClipboardContent(new StringSelection(""));
    myCopyPasteManager.fireContentChanged(old);
  }

  public void setElements(PsiElement[] elements, boolean copied) {
    Transferable old = myCopyPasteManager.getContents();
    myRecentData = new MyData(elements, copied);
    myCopyPasteManager.setSystemClipboardContent(new MyTransferable(myRecentData));
    myCopyPasteManager.fireContentChanged(old);
  }

  public boolean isCutElement(Object element) {
    if (myRecentData == null) return false;
    if (myRecentData.isCopied()) return false;
    PsiElement[] elements = myRecentData.getElements();
    if (elements == null) return false;
    for (PsiElement aElement : elements) {
      if (aElement == element) return true;
    }
    return false;
  }

  private static final DataFlavor ourDataFlavor;

  static {
    try {
      final Class<MyData> flavorClass = MyData.class;
      final Thread currentThread = Thread.currentThread();
      final ClassLoader currentLoader = currentThread.getContextClassLoader();
      try {
        currentThread.setContextClassLoader(flavorClass.getClassLoader());
        ourDataFlavor = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + flavorClass.getName());
      }
      finally {
        currentThread.setContextClassLoader(currentLoader);
      }
    }
    catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }


  private static class MyData {
    private PsiElement[] myElements;
    private final boolean myIsCopied;

    public MyData(PsiElement[] elements, boolean copied) {
      myElements = elements;
      myIsCopied = copied;
    }

    public PsiElement[] getElements() {
      if (myElements == null) return PsiElement.EMPTY_ARRAY;

      int validElementsCount = 0;

      for (PsiElement element : myElements) {
        if (element.isValid()) {
          validElementsCount++;
        }
      }

      if (validElementsCount == myElements.length) {
        return myElements;
      }

      PsiElement[] validElements = new PsiElement[validElementsCount];
      int j=0;
      for (PsiElement element : myElements) {
        if (element.isValid()) {
          validElements[j++] = element;
        }
      }

      myElements = validElements;
      return myElements;
    }

    public boolean isCopied() {
      return myIsCopied;
    }
  }

  public static class MyTransferable implements Transferable {
    private final MyData myDataProxy;
    private static final DataFlavor[] DATA_FLAVOR_ARRAY = new DataFlavor[]{ourDataFlavor, DataFlavor.stringFlavor, DataFlavor.javaFileListFlavor};

    public MyTransferable(MyData data) {
      myDataProxy = data;
    }

    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
      if (ourDataFlavor.equals(flavor)) {
        return myDataProxy;
      }
      if (DataFlavor.stringFlavor.equals(flavor)) {
        return getDataAsText();
      }
      if (DataFlavor.javaFileListFlavor.equals(flavor)) {
        return asFileList(myDataProxy.getElements());
      }
      return null;
    }

    @Nullable
    private String getDataAsText() {
      List<String> names = new ArrayList<String>();
      for (PsiElement element : myDataProxy.getElements()) {
        if (element instanceof PsiNamedElement) {
          String name = ((PsiNamedElement) element).getName();
          if (name != null) {
            names.add(name);
          }
        }
      }
      if (names.isEmpty()) {
        return null;
      }
      return StringUtil.join(names, "\n");
    }


    public DataFlavor[] getTransferDataFlavors() {
      return DATA_FLAVOR_ARRAY;
    }

    public boolean isDataFlavorSupported(DataFlavor flavor) {
      return flavor.equals(ourDataFlavor) || flavor.equals(DataFlavor.stringFlavor) || flavor.equals(DataFlavor.javaFileListFlavor);
    }

    public PsiElement[] getElements() {
      return myDataProxy.getElements();
    }
  }

  public static List<File> asFileList(final PsiElement[] elements) {
    List<File> result = new ArrayList<File>();
    for (PsiElement element : elements) {
      final PsiFileSystemItem psiFile;
      if (element instanceof PsiFileSystemItem) {
        psiFile = (PsiFileSystemItem)element;
      }
      else if (element instanceof PsiDirectoryContainer) {
        final PsiDirectory[] directories = ((PsiDirectoryContainer)element).getDirectories();
        psiFile = directories[0];
      }
      else {
        psiFile = element.getContainingFile();
      }
      if (psiFile != null) {
        VirtualFile vFile = psiFile.getVirtualFile();
        if (vFile != null && vFile.getFileSystem() instanceof LocalFileSystem) {
          result.add(new File(vFile.getPath()));
        }
      }
    }
    if (result.isEmpty()) {
      return null;
    }
    return result;
  }
}
