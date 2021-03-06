/*
 * Copyright 2007 Bas Leijdekkers
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
package com.siyeh.ig.numeric;

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class UnpredictableBigDecimalConstructorCallInspection
        extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "unpredictable.big.decimal.constructor.call.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "unpredictable.big.decimal.constructor.call.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new UnpredictableBigDecimalConstructorCallVisitor();
    }

    private static class UnpredictableBigDecimalConstructorCallVisitor
            extends BaseInspectionVisitor {

        @Override public void visitNewExpression(PsiNewExpression expression) {
            super.visitNewExpression(expression);
            final PsiJavaCodeReferenceElement classReference =
                    expression.getClassReference();
            if (classReference == null) {
                return;
            }
            final String name = classReference.getReferenceName();
            if (!"BigDecimal".equals(name)) {
                return;
            }
            final PsiMethod constructor = expression.resolveConstructor();
            if (constructor == null) {
                return;
            }
            final PsiParameterList parameterList =
                    constructor.getParameterList();
            final int length = parameterList.getParametersCount();
            if (length != 1 && length != 2) {
                return;
            }
            final PsiParameter[] parameters = parameterList.getParameters();
            final PsiParameter firstParameter = parameters[0];
            final PsiType type = firstParameter.getType();
            if (type != PsiType.DOUBLE) {
                return;
            }
            registerNewExpressionError(expression);
        }
    }
}