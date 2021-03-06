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
package com.siyeh.ig.errorhandling;

import com.intellij.psi.*;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ExceptionFromCatchWhichDoesntWrapInspection
        extends BaseInspection {

    /** @noinspection PublicField*/
    public boolean ignoreGetMessage = false;

    @Override @NotNull
    public String getID() {
        return "ThrowInsideCatchBlockWhichIgnoresCaughtException";
    }

    @Override @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "exception.from.catch.which.doesnt.wrap.display.name");
    }

    @Override @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "exception.from.catch.which.doesnt.wrap.problem.descriptor");
    }

    @Override @Nullable
    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
                "exception.from.catch.which.doesntwrap.ignore.option"), this,
                "ignoreGetMessage");
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ExceptionFromCatchWhichDoesntWrapVisitor();
    }

    private class ExceptionFromCatchWhichDoesntWrapVisitor
            extends BaseInspectionVisitor {

        @Override public void visitThrowStatement(PsiThrowStatement statement) {
            super.visitThrowStatement(statement);
            if (!ControlFlowUtils.isInCatchBlock(statement)) {
                return;
            }
            final PsiExpression exception = statement.getException();
            if (!(exception instanceof PsiNewExpression)) {
                return;
            }
            final PsiNewExpression newExpression = (PsiNewExpression)exception;
            final PsiMethod constructor = newExpression.resolveConstructor();
            if (constructor == null) {
                return;
            }
            final PsiExpressionList argumentList =
                    newExpression.getArgumentList();
            if (argumentList == null) {
                return;
            }
            final PsiExpression[] arguments = argumentList.getExpressions();
            if (argumentsContainsCatchParameter(arguments)) {
                return;
            }
            registerStatementError(statement);
        }

        private boolean argumentsContainsCatchParameter(
                PsiExpression[] arguments) {
            for (final PsiExpression argument : arguments) {
                final PsiReferenceExpression referenceExpression;
                if (!(argument instanceof PsiReferenceExpression)) {
                    if (!ignoreGetMessage ||
                            !(argument instanceof PsiMethodCallExpression)) {
                        continue;
                    }
                    final PsiMethodCallExpression methodCallExpression =
                            (PsiMethodCallExpression)argument;
                    final PsiReferenceExpression methodExpression =
                            methodCallExpression.getMethodExpression();
                    final PsiExpression expression =
                            methodExpression.getQualifierExpression();
                    if (expression == null) {
                        continue;
                    }
                    if (!(expression instanceof PsiReferenceExpression)) {
                        continue;
                    }
                    referenceExpression = (PsiReferenceExpression)expression;
                } else {
                    referenceExpression = (PsiReferenceExpression)argument;
                }
                final PsiElement referent = referenceExpression.resolve();
                if (!(referent instanceof PsiParameter)) {
                    continue;
                }
                final PsiParameter parameter = (PsiParameter)referent;
                final PsiElement declarationScope =
                        parameter.getDeclarationScope();
                if (declarationScope instanceof PsiCatchSection) {
                    return true;
                }
            }
            return false;
        }
    }
}