/*
 * Copyright 2003-2009 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class VariableAccessUtils{

    private VariableAccessUtils(){}

    public static boolean variableIsAssignedFrom(@NotNull PsiVariable variable,
                                                 @Nullable PsiElement context){
        if (context == null) {
            return false;
        }
        final VariableAssignedFromVisitor visitor =
                new VariableAssignedFromVisitor(variable);
        context.accept(visitor);
        return visitor.isAssignedFrom();
    }

    public static boolean variableIsPassedAsMethodArgument(
            @NotNull PsiVariable variable, @Nullable PsiElement context) {
        if (context == null) {
            return false;
        }
        final VariablePassedAsArgumentVisitor visitor =
                new VariablePassedAsArgumentVisitor(variable);
        context.accept(visitor);
        return visitor.isPassed();
    }

    public static boolean variableIsPassedAsMethodArgument(
            @NotNull PsiVariable variable, Set<String> excludes,
            @Nullable PsiElement context) {
        if (context == null) {
            return false;
        }
        final VariablePassedAsArgumentExcludedVisitor visitor =
                new VariablePassedAsArgumentExcludedVisitor(variable, excludes);
        context.accept(visitor);
        return visitor.isPassed();
    }

    public static boolean variableIsUsedInArrayInitializer(
            @NotNull PsiVariable variable, @Nullable PsiElement context) {
        if (context == null) {
            return false;
        }
        final VariableUsedInArrayInitializerVisitor visitor =
                new VariableUsedInArrayInitializerVisitor(variable);
        context.accept(visitor);
        return visitor.isPassed();
    }

    public static boolean variableIsAssigned(
            @NotNull PsiVariable variable, @Nullable PsiElement context) {
        if (context == null) {
            return false;
        }
        final VariableAssignedVisitor visitor =
                new VariableAssignedVisitor(variable, true);
        context.accept(visitor);
        return visitor.isAssigned();
    }

    public static boolean variableIsAssigned(
            @NotNull PsiVariable variable, @Nullable PsiElement context,
            boolean recurseIntoClasses) {
        if (context == null) {
            return false;
        }
        final VariableAssignedVisitor visitor =
                new VariableAssignedVisitor(variable, recurseIntoClasses);
        context.accept(visitor);
        return visitor.isAssigned();
    }

    public static boolean variableIsReturned(
            @NotNull PsiVariable variable, @Nullable PsiElement context) {
        if (context == null) {
            return false;
        }
        final VariableReturnedVisitor visitor =
                new VariableReturnedVisitor(variable);
        context.accept(visitor);
        return visitor.isReturned();
    }

    public static boolean variableValueIsUsed(
            @NotNull PsiVariable variable, @Nullable PsiElement context) {
        if (context == null) {
            return false;
        }
        final VariableValueUsedVisitor visitor =
                new VariableValueUsedVisitor(variable);
        context.accept(visitor);
        return visitor.isVariableValueUsed();
    }

    public static boolean arrayContentsAreAccessed(
            @NotNull PsiVariable variable, @Nullable PsiElement context) {
        if (context == null) {
            return false;
        }
        final ArrayContentsAccessedVisitor visitor =
                new ArrayContentsAccessedVisitor(variable);
        context.accept(visitor);
        return visitor.isAccessed();
    }

    public static boolean arrayContentsAreAssigned(
            @NotNull PsiVariable variable, @Nullable PsiElement context) {
        if (context == null) {
            return false;
        }
        final ArrayContentsAssignedVisitor visitor =
                new ArrayContentsAssignedVisitor(variable);
        context.accept(visitor);
        return visitor.isAssigned();
    }

    public static boolean variableIsUsedInInnerClass(
            @NotNull PsiVariable variable, @Nullable PsiElement context) {
        if (context == null) {
            return false;
        }
        final VariableUsedInInnerClassVisitor visitor =
                new VariableUsedInInnerClassVisitor(variable);
        context.accept(visitor);
        return visitor.isUsedInInnerClass();
    }

    public static boolean mayEvaluateToVariable(
            @Nullable PsiExpression expression,
            @NotNull PsiVariable variable) {
        if (expression == null){
            return false;
        }
        if(expression instanceof PsiBinaryExpression) {
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression)expression;
            final PsiExpression lOperand = binaryExpression.getLOperand();
            final PsiExpression rOperand = binaryExpression.getROperand();
            return mayEvaluateToVariable(lOperand, variable) ||
                    mayEvaluateToVariable(rOperand, variable);
        }
        if(expression instanceof PsiParenthesizedExpression){
            final PsiParenthesizedExpression parenthesizedExpression =
                    (PsiParenthesizedExpression)expression;
            final PsiExpression containedExpression =
                    parenthesizedExpression.getExpression();
            return mayEvaluateToVariable(containedExpression, variable);
        }
        if(expression instanceof PsiTypeCastExpression){
            final PsiTypeCastExpression typeCastExpression =
                    (PsiTypeCastExpression)expression;
            final PsiExpression containedExpression =
                    typeCastExpression.getOperand();
            return mayEvaluateToVariable(containedExpression, variable);
        }
        if(expression instanceof PsiConditionalExpression){
            final PsiConditionalExpression conditional =
                    (PsiConditionalExpression) expression;
            final PsiExpression thenExpression = conditional.getThenExpression();
            final PsiExpression elseExpression = conditional.getElseExpression();
            return mayEvaluateToVariable(thenExpression, variable) ||
                    mayEvaluateToVariable(elseExpression, variable);
        }
        if(expression instanceof PsiArrayAccessExpression){
            final PsiElement parent = expression.getParent();
            if (parent instanceof PsiArrayAccessExpression){
                return false;
            }
            final PsiType type = variable.getType();
            if (!(type instanceof PsiArrayType)) {
                return false;
            }
            final PsiArrayType arrayType = (PsiArrayType)type;
            final int dimensions = arrayType.getArrayDimensions();
            if (dimensions <= 1) {
                return false;
            }
            PsiArrayAccessExpression arrayAccessExpression =
                    (PsiArrayAccessExpression)expression;
            PsiExpression arrayExpression =
                    arrayAccessExpression.getArrayExpression();
            int count = 1;
            while (arrayExpression instanceof PsiArrayAccessExpression) {
                arrayAccessExpression =
                        (PsiArrayAccessExpression)arrayExpression;
                arrayExpression = arrayAccessExpression.getArrayExpression();
                count++;
            }
            return count != dimensions &&
                    mayEvaluateToVariable(arrayExpression, variable);
        }
        return evaluatesToVariable(expression, variable);
    }

    public static boolean evaluatesToVariable(
            @Nullable PsiExpression expression,
            @NotNull PsiVariable variable) {
        final PsiExpression strippedExpression =
                ParenthesesUtils.stripParentheses(expression);
        if(strippedExpression == null){
            return false;
        }
        if (!(expression instanceof PsiReferenceExpression)) {
            return false;
        }
        final PsiReferenceExpression referenceExpression =
                (PsiReferenceExpression) expression;
        final PsiElement referent = referenceExpression.resolve();
        return variable.equals(referent);
    }

    public static boolean variableIsUsed(@NotNull PsiVariable variable,
                                         @Nullable PsiElement context){
        if (context == null) {
            return false;
        }
        final VariableUsedVisitor visitor =
                new VariableUsedVisitor(variable);
        context.accept(visitor);
        return visitor.isUsed();
    }

    public static boolean variableIsIncremented(
            @NotNull PsiVariable variable, @Nullable PsiStatement statement) {
        if (!(statement instanceof PsiExpressionStatement)) {
            return false;
        }
        final PsiExpressionStatement expressionStatement =
                (PsiExpressionStatement)statement;
        PsiExpression expression = expressionStatement.getExpression();
        expression = ParenthesesUtils.stripParentheses(expression);
        if (expression instanceof PsiPrefixExpression) {
            final PsiPrefixExpression prefixExpression =
                    (PsiPrefixExpression)expression;
            final PsiJavaToken sign = prefixExpression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (!tokenType.equals(JavaTokenType.PLUSPLUS)) {
                return false;
            }
            final PsiExpression operand = prefixExpression.getOperand();
            return evaluatesToVariable(operand, variable);
        } else if (expression instanceof PsiPostfixExpression) {
            final PsiPostfixExpression postfixExpression =
                    (PsiPostfixExpression)expression;
            final PsiJavaToken sign = postfixExpression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (!tokenType.equals(JavaTokenType.PLUSPLUS)) {
                return false;
            }
            final PsiExpression operand = postfixExpression.getOperand();
            return evaluatesToVariable(operand, variable);
        } else if (expression instanceof PsiAssignmentExpression) {
            final PsiAssignmentExpression assignmentExpression =
                    (PsiAssignmentExpression) expression;
            final IElementType tokenType =
                    assignmentExpression.getOperationTokenType();
            PsiExpression lhs = assignmentExpression.getLExpression();
            lhs = ParenthesesUtils.stripParentheses(lhs);
            if (!evaluatesToVariable(lhs, variable)) {
                return false;
            }
            PsiExpression rhs = assignmentExpression.getRExpression();
            rhs = ParenthesesUtils.stripParentheses(rhs);
            if (tokenType == JavaTokenType.EQ) {
                if (!(rhs instanceof PsiBinaryExpression)) {
                    return false;
                }
                final PsiBinaryExpression binaryExpression =
                        (PsiBinaryExpression) rhs;
                PsiExpression lOperand = binaryExpression.getLOperand();
                lOperand = ParenthesesUtils.stripParentheses(lOperand);
                PsiExpression rOperand = binaryExpression.getROperand();
                rOperand = ParenthesesUtils.stripParentheses(rOperand);
                if (ExpressionUtils.isOne(lOperand)) {
                    if (evaluatesToVariable(rOperand, variable)) {
                        return true;
                    }
                } else if (ExpressionUtils.isOne(rOperand)) {
                    if (evaluatesToVariable(lOperand, variable)) {
                        return true;
                    }
                }
            } else if (tokenType == JavaTokenType.PLUSEQ) {
                if (ExpressionUtils.isOne(rhs)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean variableIsAssignedBeforeReference(
            @NotNull PsiReferenceExpression referenceExpression,
            @Nullable PsiElement context) {
        if (context == null) {
            return false;
        }
        final PsiElement target = referenceExpression.resolve();
        if (!(target instanceof PsiVariable)) {
            return false;
        }
        final PsiVariable variable = (PsiVariable)target;
        return variableIsAssignedAtPoint(variable, context,
                referenceExpression);
    }

    public static boolean variableIsAssignedAtPoint(
            @NotNull PsiVariable variable, @Nullable PsiElement context,
            @NotNull PsiElement point) {
        if (context == null) {
            return false;
        }
        final PsiElement directChild =
                getDirectChildWhichContainsElement(context, point);
        if (directChild == null) {
            return false;
        }
        final PsiElement[] children = context.getChildren();
        for (PsiElement child : children) {
            if (child == directChild) {
                return variableIsAssignedAtPoint(variable, directChild, point);
            }
            if (variableIsAssigned(variable, child)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static PsiElement getDirectChildWhichContainsElement(
            @NotNull PsiElement ancestor,
            @NotNull PsiElement descendant) {
        if (ancestor == descendant) {
            return null;
        }
        PsiElement child = descendant;
        PsiElement parent = child.getParent();
        while (!parent.equals(ancestor)) {
            child = parent;
            parent = child.getParent();
            if (parent == null) {
                return null;
            }
        }
        return child;
    }
}