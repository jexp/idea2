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
package org.jetbrains.plugins.groovy.dsl;

import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.psi.PsiElement;

/**
 * @author peter
 */
public class GroovyScriptDescriptor extends GroovyClassDescriptor implements ScriptDescriptor {
  @NotNull private final GroovyFile myFile;

  public GroovyScriptDescriptor(GroovyFile file, GroovyScriptClass scriptClass, PsiElement place) {
    super(scriptClass, place);
    myFile = file;
  }

  @Override
  public String getQualifiedName() {
    return "groovy.lang.Script";
  }

  @Nullable
  public String getExtension() {
    return myFile.getViewProvider().getVirtualFile().getExtension();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    GroovyScriptDescriptor that = (GroovyScriptDescriptor)o;

    if (!myFile.equals(that.myFile)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myFile.hashCode();
    return result;
  }
}
