/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.pattern;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.ide.DataManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.JavaRefactoringActionHandlerFactory;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.util.SmartList;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.util.AnnotateFix;
import org.intellij.plugins.intelliLang.util.AnnotationUtilEx;
import org.intellij.plugins.intelliLang.util.PsiUtilEx;
import org.intellij.plugins.intelliLang.util.SubstitutedExpressionEvaluationHelper;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.text.MessageFormat;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Inspection that validates if string literals, compile-time constants or
 * substituted expressions match the pattern of the context they're used in.
 */
public class PatternValidator extends LocalInspectionTool {
  private static final Key<CachedValue<Pattern>> COMPLIED_PATTERN = Key.create("COMPILED_PATTERN");

  public boolean CHECK_NON_CONSTANT_VALUES = true;

  private final Configuration myConfiguration;

  public PatternValidator() {
    myConfiguration = Configuration.getInstance();
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  public String getGroupDisplayName() {
    return InspectionProvider.PATTERN_VALIDATION;
  }

  @NotNull
  public String getDisplayName() {
    return "Validate Annotated Patterns";
  }

  @Nullable
  public JComponent createOptionsPanel() {
    final JPanel jPanel = new JPanel(new BorderLayout());
    final JCheckBox jCheckBox = new JCheckBox("Flag non compile-time constant expressions");
    jCheckBox.setToolTipText(
        "If checked, the inspection will flag expressions with unknown values " + "and offer to add a substitution (@Subst) annotation");
    jCheckBox.setSelected(CHECK_NON_CONSTANT_VALUES);
    jCheckBox.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        CHECK_NON_CONSTANT_VALUES = jCheckBox.isSelected();
      }
    });
    jPanel.add(jCheckBox, BorderLayout.NORTH);
    return jPanel;
  }

  @NotNull
  @NonNls
  public String getShortName() {
    return "PatternValidation";
  }

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {

      public final void visitReferenceExpression(PsiReferenceExpression expression) {
        visitExpression(expression);
      }

      @Override
      public void visitExpression(PsiExpression expression) {
        final PsiElement element = expression.getParent();
        if (element instanceof PsiExpressionList) {
          // this checks method arguments
          check(expression, holder, false);
        }
        else if (element instanceof PsiNameValuePair) {
          final PsiNameValuePair valuePair = (PsiNameValuePair)element;
          final String name = valuePair.getName();
          if (name == null || name.equals(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME)) {
            // check whether @Subst complies with pattern
            check(expression, holder, true);
          }
        }
      }

      @Override
      public void visitReturnStatement(PsiReturnStatement statement) {
        final PsiExpression returnValue = statement.getReturnValue();
        if (returnValue != null) {
          check(returnValue, holder, false);
        }
      }

      @Override
      public void visitVariable(PsiVariable var) {
        final PsiExpression initializer = var.getInitializer();
        if (initializer != null) {
          // variable/field initializer
          check(initializer, holder, false);
        }
      }

      @Override
      public void visitAssignmentExpression(PsiAssignmentExpression expression) {
        final PsiExpression e = expression.getRExpression();
        if (e != null) {
          check(e, holder, false);
        }
        visitExpression(expression);
      }

      private void check(@NotNull PsiExpression expression, ProblemsHolder holder, boolean isAnnotationValue) {
        if (expression instanceof PsiConditionalExpression) {
          final PsiConditionalExpression expr = (PsiConditionalExpression)expression;
          PsiExpression e = expr.getThenExpression();
          if (e != null) {
            check(e, holder, isAnnotationValue);
          }
          e = expr.getElseExpression();
          if (e != null) {
            check(e, holder, isAnnotationValue);
          }
        }
        else {
          final PsiType type = expression.getType();
          // optimiziation: only check expressions of type String
          if (type != null && PsiUtilEx.isString(type)) {
            final PsiModifierListOwner element;
            if (isAnnotationValue) {
              final PsiAnnotation psiAnnotation = PsiTreeUtil.getParentOfType(expression, PsiAnnotation.class);
              if (psiAnnotation != null && myConfiguration.getSubstAnnotationClass().equals(psiAnnotation.getQualifiedName())) {
                element = PsiTreeUtil.getParentOfType(expression, PsiModifierListOwner.class);
              }
              else {
                return;
              }
            }
            else {
              element = AnnotationUtilEx.getAnnotatedElementFor(expression, AnnotationUtilEx.LookupType.PREFER_CONTEXT);
            }
            if (element != null && PsiUtilEx.isLanguageAnnotationTarget(element)) {
              PsiAnnotation[] annotations = AnnotationUtilEx.getAnnotationFrom(element, myConfiguration.getPatternAnnotationPair(), true);
              checkExpression(expression, annotations, holder);
            }
          }
        }
      }
    };
  }

  private void checkExpression(PsiExpression expression, final PsiAnnotation[] annotations, ProblemsHolder holder) {
    if (annotations.length == 0) return;
    final PsiAnnotation psiAnnotation = annotations[0];

    // cache compiled pattern with annotation
    CachedValue<Pattern> p = psiAnnotation.getUserData(COMPLIED_PATTERN);
    if (p == null) {
      final CachedValueProvider<Pattern> provider = new CachedValueProvider<Pattern>() {
        public Result<Pattern> compute() {
          final String pattern = AnnotationUtilEx.calcAnnotationValue(psiAnnotation, "value");
          Pattern p = null;
          if (pattern != null) {
            try {
              p = Pattern.compile(pattern);
            }
            catch (PatternSyntaxException e) {
              // pattern stays null
            }
          }
          return Result.create(p, (Object[])annotations);
        }
      };
      p = expression.getManager().getCachedValuesManager().createCachedValue(provider, false);
      psiAnnotation.putUserData(COMPLIED_PATTERN, p);
    }

    final Pattern pattern = p.getValue();
    if (pattern == null) return;

    List<PsiExpression> nonConstantElements = new SmartList<PsiExpression>();
    String o = SubstitutedExpressionEvaluationHelper.computeExpression(expression, nonConstantElements);
    if (o != null) {
      if (!pattern.matcher(o).matches()) {
        if (annotations.length > 1) {
          // the last element contains the element's actual annotation
          final String fqn = annotations[annotations.length - 1].getQualifiedName();
          assert fqn != null;

          final String name = StringUtil.getShortName(fqn);
          holder.registerProblem(expression, MessageFormat.format("Expression ''{0}'' doesn''t match ''{1}'' pattern: {2}", o, name,
                                                                  pattern.pattern()));
        }
        else {
          holder.registerProblem(expression,
                                 MessageFormat.format("Expression ''{0}'' doesn''t match pattern: {1}", o, pattern.pattern()));
        }
      }
    }
    else if (CHECK_NON_CONSTANT_VALUES) {
      for (PsiExpression expr : nonConstantElements) {
        final PsiElement e;
        if (expr instanceof PsiReferenceExpression) {
          e = ((PsiReferenceExpression)expr).resolve();
        }
        else if (expr instanceof PsiMethodCallExpression) {
          e = ((PsiMethodCallExpression)expr).getMethodExpression().resolve();
        }
        else {
          e = expr;
        }
        final PsiModifierListOwner owner = e instanceof PsiModifierListOwner? (PsiModifierListOwner)e : null;
        LocalQuickFix quickFix;
        if (owner != null && PsiUtilEx.isLanguageAnnotationTarget(owner)) {
          PsiAnnotation[] resolvedAnnos = AnnotationUtilEx.getAnnotationFrom(owner, myConfiguration.getPatternAnnotationPair(), true);
          if (resolvedAnnos.length == 2 && annotations.length == 2 && Comparing.strEqual(resolvedAnnos[1].getQualifiedName(), annotations[1].getQualifiedName())) {
            // both target and source annotated indirectly with the same anno
            return;
          }

          final String classname = Configuration.getInstance().getSubstAnnotationPair().first;
          final AnnotateFix fix = new AnnotateFix((PsiModifierListOwner)e, classname);
          quickFix = fix.canApply() ? fix : new IntroduceVariableFix(expr);
        }
        else {
          quickFix = new IntroduceVariableFix(expr);
        }
        holder.registerProblem(expr, "Unsubstituted expression", quickFix);
      }
    }
  }

  private static class IntroduceVariableFix implements LocalQuickFix {
    private final PsiExpression myExpr;

    public IntroduceVariableFix(PsiExpression expr) {
      myExpr = expr;
    }

    @NotNull
    public String getName() {
      return "Introduce Variable";
    }

    @NotNull
    public String getFamilyName() {
      return getName();
    }

    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final RefactoringActionHandler handler = JavaRefactoringActionHandlerFactory.getInstance().createIntroduceVariableHandler();
      handler.invoke(project, new PsiElement[]{myExpr}, DataManager.getInstance().getDataContext());
      // how to automatically annotate the variable after it has been introduced?
    }
  }
}
