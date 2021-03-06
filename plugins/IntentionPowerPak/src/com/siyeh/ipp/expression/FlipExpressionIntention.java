/*
 * Copyright 2007 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.expression;

import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaToken;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ComparisonUtils;
import com.siyeh.IntentionPowerPackBundle;
import org.jetbrains.annotations.NotNull;

public class FlipExpressionIntention extends MutablyNamedIntention {

    public String getTextForElement(PsiElement element) {
        final PsiBinaryExpression expression = (PsiBinaryExpression)element;
        final PsiJavaToken sign = expression.getOperationSign();
        final String operatorText = sign.getText();
        return IntentionPowerPackBundle.message("flip.smth.intention.name",
                operatorText);
    }

    @NotNull
    public PsiElementPredicate getElementPredicate() {
        return new ExpressionPredicate();
    }

    public void processIntention(@NotNull PsiElement element)
            throws IncorrectOperationException {
        final PsiBinaryExpression expression = (PsiBinaryExpression)element;
        final PsiExpression lhs = expression.getLOperand();
        final PsiExpression rhs = expression.getROperand();
        final PsiJavaToken sign = expression.getOperationSign();
        assert rhs != null;
        final String expString = rhs.getText() + sign.getText() + lhs.getText();
        replaceExpression(expString, expression);
    }
}