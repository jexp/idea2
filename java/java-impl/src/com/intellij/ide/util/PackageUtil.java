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
package com.intellij.ide.util;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.impl.ProjectRootUtil;
import com.intellij.openapi.roots.ModulePackageIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ui.configuration.CommonContentEntriesEditor;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.ActionRunner;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PackageUtil {
  private static final Logger LOG = Logger.getInstance("com.intellij.ide.util.PackageUtil");

  @Nullable
  public static PsiDirectory findPossiblePackageDirectoryInModule(Module module, String packageName) {
    PsiDirectory psiDirectory = null;
    if (!"".equals(packageName)) {
      PsiPackage rootPackage = findLongestExistingPackage(module.getProject(), packageName);
      if (rootPackage != null) {
        final PsiDirectory[] psiDirectories = getPackageDirectoriesInModule(rootPackage, module);
        if (psiDirectories.length > 0) {
          psiDirectory = psiDirectories[0];
        }
      }
    }
    if (psiDirectory == null) {
      if (checkSourceRootsConfigured(module)) {
        final VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots();
        for (VirtualFile sourceRoot : sourceRoots) {
          final PsiDirectory directory = PsiManager.getInstance(module.getProject()).findDirectory(sourceRoot);
          if (directory != null) {
            psiDirectory = directory;
            break;
          }
        }
      }
    }
    return psiDirectory;
  }

  /**
   * @deprecated
   */
  @Nullable
  public static PsiDirectory findOrCreateDirectoryForPackage(Project project,
                                                             String packageName,
                                                             PsiDirectory baseDir,
                                                             boolean askUserToCreate) throws IncorrectOperationException {

    PsiDirectory psiDirectory = null;

    if (!"".equals(packageName)) {
      PsiPackage rootPackage = findLongestExistingPackage(project, packageName);
      if (rootPackage != null) {
        int beginIndex = rootPackage.getQualifiedName().length() + 1;
        packageName = beginIndex < packageName.length() ? packageName.substring(beginIndex) : "";
        String postfixToShow = packageName.replace('.', File.separatorChar);
        if (packageName.length() > 0) {
          postfixToShow = File.separatorChar + postfixToShow;
        }
        psiDirectory = DirectoryChooserUtil.selectDirectory(project, rootPackage.getDirectories(), baseDir, postfixToShow);
        if (psiDirectory == null) return null;
      }
    }

    if (psiDirectory == null) {
      PsiDirectory[] sourceDirectories = ProjectRootUtil.getRootDirectories(project, OrderRootType.SOURCES);
      psiDirectory = DirectoryChooserUtil.selectDirectory(project, sourceDirectories, baseDir,
                                     File.separatorChar + packageName.replace('.', File.separatorChar));
      if (psiDirectory == null) return null;
    }

    String restOfName = packageName;
    boolean askedToCreate = false;
    while (restOfName.length() > 0) {
      final String name = getLeftPart(restOfName);
      PsiDirectory foundExistingDirectory = psiDirectory.findSubdirectory(name);
      if (foundExistingDirectory == null) {
        if (!askedToCreate && askUserToCreate) {
          int toCreate = Messages.showYesNoDialog(project,
                                                  IdeBundle.message("prompt.create.non.existing.package", packageName),
                                                  IdeBundle.message("title.package.not.found"),
                                                  Messages.getQuestionIcon());
          if (toCreate != 0) {
            return null;
          }
          askedToCreate = true;
        }
        psiDirectory = createSubdirectory(psiDirectory, name, project);
      }
      else {
        psiDirectory = foundExistingDirectory;
      }
      restOfName = cutLeftPart(restOfName);
    }
    return psiDirectory;
  }

  private static PsiDirectory createSubdirectory(final PsiDirectory oldDirectory,
                                                 final String name, Project project) throws IncorrectOperationException {
    final PsiDirectory[] psiDirectory = new PsiDirectory[1];
    final IncorrectOperationException[] exception = new IncorrectOperationException[1];

    CommandProcessor.getInstance().executeCommand(project, new Runnable(){
      public void run() {
        psiDirectory[0] = ApplicationManager.getApplication().runWriteAction(new Computable<PsiDirectory>() {
          public PsiDirectory compute() {
            try {
              return oldDirectory.createSubdirectory(name);
            }
            catch (IncorrectOperationException e) {
              exception[0] = e;
              return null;
            }
          }
        });
      }
    }, IdeBundle.message("command.create.new.subdirectory"), null);

    if (exception[0] != null) throw exception[0];

    return psiDirectory[0];
  }

  public static PsiDirectory findOrCreateDirectoryForPackage(@NotNull Module module,
                                                             String packageName,
                                                             PsiDirectory baseDir,
                                                             boolean askUserToCreate) throws IncorrectOperationException {
    final Project project = module.getProject();
    PsiDirectory psiDirectory = null;
    if (!"".equals(packageName)) {
      PsiPackage rootPackage = findLongestExistingPackage(module, packageName);
      if (rootPackage != null) {
        int beginIndex = rootPackage.getQualifiedName().length() + 1;
        packageName = beginIndex < packageName.length() ? packageName.substring(beginIndex) : "";
        String postfixToShow = packageName.replace('.', File.separatorChar);
        if (packageName.length() > 0) {
          postfixToShow = File.separatorChar + postfixToShow;
        }
        PsiDirectory[] moduleDirectories = getPackageDirectoriesInModule(rootPackage, module);
        psiDirectory = DirectoryChooserUtil.selectDirectory(project, moduleDirectories, baseDir, postfixToShow);
        if (psiDirectory == null) return null;
      }
    }

    if (psiDirectory == null) {
      if (!checkSourceRootsConfigured(module)) return null;
      final VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots();
      List<PsiDirectory> directoryList = new ArrayList<PsiDirectory>();
      for (VirtualFile sourceRoot : sourceRoots) {
        final PsiDirectory directory = PsiManager.getInstance(project).findDirectory(sourceRoot);
        if (directory != null) {
          directoryList.add(directory);
        }
      }
      PsiDirectory[] sourceDirectories = directoryList.toArray(new PsiDirectory[directoryList.size()]);
      psiDirectory = DirectoryChooserUtil.selectDirectory(project, sourceDirectories, baseDir,
                                     File.separatorChar + packageName.replace('.', File.separatorChar));
      if (psiDirectory == null) return null;
    }

    String restOfName = packageName;
    boolean askedToCreate = false;
    while (restOfName.length() > 0) {
      final String name = getLeftPart(restOfName);
      PsiDirectory foundExistingDirectory = psiDirectory.findSubdirectory(name);
      if (foundExistingDirectory == null) {
        if (!askedToCreate && askUserToCreate) {
          if (!ApplicationManager.getApplication().isUnitTestMode()) {
            int toCreate = Messages.showYesNoDialog(project,
                                                    IdeBundle.message("prompt.create.non.existing.package", packageName),
                                                    IdeBundle.message("title.package.not.found"),
                                                    Messages.getQuestionIcon());
            if (toCreate != 0) {
              return null;
            }
          }
          askedToCreate = true;
        }

        final PsiDirectory psiDirectory1 = psiDirectory;
        try {
          psiDirectory = ActionRunner.runInsideWriteAction(new ActionRunner.InterruptibleRunnableWithResult<PsiDirectory>() {
            public PsiDirectory run() throws Exception {
              return psiDirectory1.createSubdirectory(name);
            }
          });
        }
        catch(IncorrectOperationException e) {
          throw e;
        }
        catch(IOException e) {
          throw new IncorrectOperationException(e.toString(), e);
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
      else {
        psiDirectory = foundExistingDirectory;
      }
      restOfName = cutLeftPart(restOfName);
    }
    return psiDirectory;
  }

  private static PsiDirectory[] getPackageDirectoriesInModule(PsiPackage rootPackage, Module module) {
    final PsiManager manager = PsiManager.getInstance(module.getProject());
    final String packageName = rootPackage.getQualifiedName();
    final List<PsiDirectory> moduleDirectoryList = new ArrayList<PsiDirectory>();
    ModulePackageIndex.getInstance(module).getDirsByPackageName(packageName, false).forEach(new Processor<VirtualFile>() {
      public boolean process(final VirtualFile directory) {
        moduleDirectoryList.add(manager.findDirectory(directory));
        return true;
      }
    });

    return moduleDirectoryList.toArray(new PsiDirectory[moduleDirectoryList.size()]);
  }

  private static PsiPackage findLongestExistingPackage(Project project, String packageName) {
    PsiManager manager = PsiManager.getInstance(project);
    String nameToMatch = packageName;
    while (true) {
      PsiPackage aPackage = JavaPsiFacade.getInstance(manager.getProject()).findPackage(nameToMatch);
      if (aPackage != null && isWritablePackage(aPackage)) return aPackage;
      int lastDotIndex = nameToMatch.lastIndexOf('.');
      if (lastDotIndex >= 0) {
        nameToMatch = nameToMatch.substring(0, lastDotIndex);
      }
      else {
        return null;
      }
    }
  }

  private static boolean isWritablePackage(PsiPackage aPackage) {
    PsiDirectory[] directories = aPackage.getDirectories();
    for (PsiDirectory directory : directories) {
      if (directory.isValid() && directory.isWritable()) {
        return true;
      }
    }
    return false;
  }

  private static PsiDirectory getWritableDirectory(Query<VirtualFile> vFiles, PsiManager manager) {
    for (VirtualFile vFile : vFiles) {
      PsiDirectory directory = manager.findDirectory(vFile);
      if (directory != null && directory.isValid() && directory.isWritable()) {
        return directory;
      }
    }
    return null;
  }

  private static PsiPackage findLongestExistingPackage(Module module, String packageName) {
    final PsiManager manager = PsiManager.getInstance(module.getProject());

    String nameToMatch = packageName;
    while (true) {
      Query<VirtualFile> vFiles = ModulePackageIndex.getInstance(module).getDirsByPackageName(nameToMatch, false);
      PsiDirectory directory = getWritableDirectory(vFiles, manager);
      if (directory != null) return JavaDirectoryService.getInstance().getPackage(directory);

      int lastDotIndex = nameToMatch.lastIndexOf('.');
      if (lastDotIndex >= 0) {
        nameToMatch = nameToMatch.substring(0, lastDotIndex);
      }
      else {
        return null;
      }
    }
  }

  private static String getLeftPart(String packageName) {
    int index = packageName.indexOf('.');
    return index > -1 ? packageName.substring(0, index) : packageName;
  }

  private static String cutLeftPart(String packageName) {
    int index = packageName.indexOf('.');
    return index > -1 ? packageName.substring(index + 1) : "";
  }

  public static boolean checkSourceRootsConfigured(final Module module) {
    VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots();
    if (sourceRoots.length == 0) {
      Messages.showErrorDialog(
          module.getProject(),
          ProjectBundle.message("module.source.roots.not.configured.error", module.getName()),
          ProjectBundle.message("module.source.roots.not.configured.title")
        );

      ProjectSettingsService.getInstance(module.getProject()).showModuleConfigurationDialog(module.getName(), CommonContentEntriesEditor.NAME, false);

      sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots();
      if (sourceRoots.length == 0) {
        return false;
      }
    }
    return true;
  }
}
