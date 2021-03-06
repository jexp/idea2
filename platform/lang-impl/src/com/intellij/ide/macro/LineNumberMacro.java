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

package com.intellij.ide.macro;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;

public final class LineNumberMacro extends Macro {
  public String getName() {
    return "LineNumber";
  }

  public String getDescription() {
    return IdeBundle.message("macro.line.number");
  }

  public String expand(DataContext dataContext) {
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) return null;
    if (ToolWindowManager.getInstance(project).isEditorComponentActive()){
      Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
      if (editor != null){
        return String.valueOf(editor.getCaretModel().getLogicalPosition().line + 1);
      }
    }
    return null;
  }
}
