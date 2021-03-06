/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.siyeh.ig.psiutils.CollectionUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

class CollectionUpdateCalledVisitor extends JavaRecursiveElementVisitor{

    /**
     * @noinspection StaticCollection
     */
    @NonNls private static final Set<String> updateNames =
            new HashSet<String>(31);
    static{
        updateNames.add("add");
        updateNames.add("addAll");
        updateNames.add("addAllAbsent");
        updateNames.add("addBefore");
        updateNames.add("addElement");
        updateNames.add("addFirst");
        updateNames.add("addIfAbsent");
        updateNames.add("addLast");
        updateNames.add("clear");
        updateNames.add("drainTo");
        updateNames.add("insertElementAt");
        updateNames.add("load");
        updateNames.add("loadFromXML");
        updateNames.add("offer");
        updateNames.add("push");
        updateNames.add("put");
        updateNames.add("putAll");
        updateNames.add("putIfAbsent");
        updateNames.add("remove");
        updateNames.add("removeAll");
        updateNames.add("removeAllElements");
        updateNames.add("replace");
        updateNames.add("retainAll");
        updateNames.add("removeElementAt");
        updateNames.add("removeFirst");
        updateNames.add("removeLast");
        updateNames.add("removeRange");
        updateNames.add("set");
        updateNames.add("setElementAt");
        updateNames.add("setProperty");
        updateNames.add("take");
    }

    private boolean updated = false;
    private final PsiVariable variable;

    CollectionUpdateCalledVisitor(PsiVariable variable){
        super();
        this.variable = variable;
    }

    @Override public void visitElement(@NotNull PsiElement element){
        if(!updated){
            super.visitElement(element);
        }
    }

    @Override public void visitMethodCallExpression(
            @NotNull PsiMethodCallExpression call){
        super.visitMethodCallExpression(call);
        if(updated){
            return;
        }
        final PsiReferenceExpression methodExpression =
                call.getMethodExpression();
        final String methodName = methodExpression.getReferenceName();
        if(!updateNames.contains(methodName)){
            return;
        }
        final PsiExpression qualifier =
                methodExpression.getQualifierExpression();
        if(qualifier == null || qualifier instanceof PsiThisExpression){
            final PsiMethod method = call.resolveMethod();
            if (method == null) {
                return;
            }
            final PsiClass aClass = method.getContainingClass();
            if (CollectionUtils.isCollectionClassOrInterface(aClass)){
                updated = true;
            }
        } else{
            checkQualifier(qualifier);
        }
    }

    private void checkQualifier(PsiExpression expression) {
        if (updated) {
            return;
        }
        if (expression instanceof PsiReferenceExpression) {
            final PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression) expression;
            final PsiElement referent = referenceExpression.resolve();
            if(referent == null){
                return;
            }
            if(referent.equals(variable)){
                updated = true;
            }
        } else if (expression instanceof PsiParenthesizedExpression) {
            final PsiParenthesizedExpression parenthesizedExpression =
                    (PsiParenthesizedExpression) expression;
            checkQualifier(parenthesizedExpression.getExpression());
        } else if (expression instanceof PsiConditionalExpression) {
            final PsiConditionalExpression conditionalExpression =
                    (PsiConditionalExpression) expression;
            final PsiExpression thenExpression =
                    conditionalExpression.getThenExpression();
            checkQualifier(thenExpression);
            final PsiExpression elseExpression =
                    conditionalExpression.getElseExpression();
            checkQualifier(elseExpression);
        }
    }

    public boolean isUpdated(){
        return updated;
    }
}
