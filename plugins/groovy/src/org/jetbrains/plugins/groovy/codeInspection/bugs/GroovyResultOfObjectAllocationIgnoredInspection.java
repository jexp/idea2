/*
 * Copyright 2007-2008 Dave Griffith
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
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

public class GroovyResultOfObjectAllocationIgnoredInspection extends BaseInspection {

  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return PROBABLE_BUGS;
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return "Result of object allocation ignored";
  }

  @Nullable
  protected String buildErrorString(Object... args) {
    return "Result of <code>new #ref()</code> is ignored #loc";

  }

  public boolean isEnabledByDefault() {
    return true;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor {

    public void visitNewExpression(GrNewExpression newExpression) {
      super.visitNewExpression(newExpression);
      final PsiElement parent = newExpression.getParent();
      if (!(parent instanceof GrCodeBlock)) {
        return;
      }
      if (parent instanceof GrOpenBlock) {
        final GrOpenBlock openBlock = (GrOpenBlock) parent;
        if (ControlFlowUtils.openBlockCompletesWithStatement(openBlock, newExpression)) {
          return;
        }
      } else if (parent instanceof GrClosableBlock) {
        final PsiElement grandParent = parent.getParent();
        if (grandParent instanceof GrMethodCallExpression) {
          return;
        } else if (grandParent instanceof GrReferenceExpression) {
          final PsiElement greatGrandParent = grandParent.getParent();
          if (greatGrandParent instanceof GrMethodCallExpression) {
            return;
          }
        }
      }
      if (newExpression.getArrayCount() != 0) {
        return;
      }
      registerError(newExpression.getReferenceElement());
    }
  }
}