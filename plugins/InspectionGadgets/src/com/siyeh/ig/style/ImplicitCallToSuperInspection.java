/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.style;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ImplicitCallToSuperInspection extends BaseInspection {

    @SuppressWarnings("PublicField")
    public boolean m_ignoreForObjectSubclasses = false;

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "implicit.call.to.super.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "implicit.call.to.super.problem.descriptor");
    }

    public InspectionGadgetsFix buildFix(Object... infos) {
        return new AddExplicitSuperCall();
    }

    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
                "implicit.call.to.super.ignore.option"),
                this, "m_ignoreForObjectSubclasses");
    }

    private static class AddExplicitSuperCall extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "implicit.call.to.super.make.explicit.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement methodName = descriptor.getPsiElement();
            final PsiMethod method = (PsiMethod)methodName.getParent();
            if (method == null) {
                return;
            }
            final PsiCodeBlock body = method.getBody();
            final PsiManager psiManager = PsiManager.getInstance(project);
          final PsiElementFactory factory = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory();
            final PsiStatement newStatement =
                    factory.createStatementFromText("super();", null);
            final CodeStyleManager styleManager =
                    psiManager.getCodeStyleManager();
            if (body == null) {
                return;
            }
            final PsiJavaToken brace = body.getLBrace();
            body.addAfter(newStatement, brace);
            styleManager.reformat(body);
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ImplicitCallToSuperVisitor();
    }

    private class ImplicitCallToSuperVisitor extends BaseInspectionVisitor {

        @Override public void visitMethod(@NotNull PsiMethod method) {
            super.visitMethod(method);
            if (!method.isConstructor()) {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
                return;
            }
            if (containingClass.isEnum()) {
                return;
            }
            if (m_ignoreForObjectSubclasses) {
                final PsiClass superClass = containingClass.getSuperClass();
                if (superClass != null) {
                    final String superClassName = superClass.getQualifiedName();
                    if ("java.lang.Object".equals(superClassName)) {
                        return;
                    }
                }
            }
            final PsiCodeBlock body = method.getBody();
            if (body == null) {
                return;
            }
            final PsiStatement[] statements = body.getStatements();
            if (statements.length == 0) {
                registerMethodError(method);
                return;
            }
            final PsiStatement firstStatement = statements[0];
            if (isConstructorCall(firstStatement)) {
                return;
            }
            registerMethodError(method);
        }

        private boolean isConstructorCall(PsiStatement statement) {
            if (!(statement instanceof PsiExpressionStatement)) {
                return false;
            }
            final PsiExpressionStatement expressionStatement =
                    (PsiExpressionStatement)statement;
            final PsiExpression expression =
                    expressionStatement.getExpression();
            if (!(expression instanceof PsiMethodCallExpression)) {
                return false;
            }
            final PsiMethodCallExpression methodCall =
                    (PsiMethodCallExpression)expression;
            final PsiReferenceExpression methodExpression =
                    methodCall.getMethodExpression();
            final String text = methodExpression.getText();
            return PsiKeyword.SUPER.equals(text) ||
                    PsiKeyword.THIS.equals(text);
        }
    }
}
