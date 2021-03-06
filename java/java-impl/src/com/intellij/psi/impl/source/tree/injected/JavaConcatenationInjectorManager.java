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
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.lang.injection.ConcatenationAwareInjector;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.PsiParameterizedCachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.ParameterizedCachedValue;
import com.intellij.psi.util.ParameterizedCachedValueProvider;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author cdr
 */
public class JavaConcatenationInjectorManager implements ProjectComponent, ModificationTracker {
  public static final ExtensionPointName<ConcatenationAwareInjector> CONCATENATION_INJECTOR_EP_NAME = ExtensionPointName.create("com.intellij.concatenationAwareInjector");
  private final AtomicReference<MultiHostInjector> myRegisteredConcatenationAdapter = new AtomicReference<MultiHostInjector>();
  private final InjectedLanguageManager myInjectedLanguageManager;
  private volatile long myModificationCounter;
  private static final ConcatenationPsiCachedValueProvider CONCATENATION_PSI_CACHED_VALUE_PROVIDER = new ConcatenationPsiCachedValueProvider();

  public JavaConcatenationInjectorManager(Project project, InjectedLanguageManager injectedLanguageManager, PsiManagerEx psiManagerEx) {
    myInjectedLanguageManager = injectedLanguageManager;
    final ExtensionPoint<ConcatenationAwareInjector> concatPoint = Extensions.getArea(project).getExtensionPoint(CONCATENATION_INJECTOR_EP_NAME);
    concatPoint.addExtensionPointListener(new ExtensionPointListener<ConcatenationAwareInjector>() {
      public void extensionAdded(ConcatenationAwareInjector injector, @Nullable PluginDescriptor pluginDescriptor) {
        registerConcatenationInjector(injector);
      }

      public void extensionRemoved(ConcatenationAwareInjector injector, @Nullable PluginDescriptor pluginDescriptor) {
        unregisterConcatenationInjector(injector);
      }
    });
    psiManagerEx.registerRunnableToRunOnAnyChange(new Runnable() {
      public void run() {
        myModificationCounter++; // clear caches even on non-physical changes
      }
    });
  }

  public void projectOpened() {

  }

  public void projectClosed() {

  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "JavaConcatenationInjectorManager";
  }

  public void initComponent() {

  }

  public void disposeComponent() {

  }

  public static JavaConcatenationInjectorManager getInstance(final Project project) {
    return project.getComponent(JavaConcatenationInjectorManager.class);
  }

  public long getModificationCount() {
    return myModificationCounter;
  }

  private static class ConcatenationPsiCachedValueProvider implements ParameterizedCachedValueProvider<Places, PsiElement> {
    public CachedValueProvider.Result<Places> compute(PsiElement context) {
      PsiElement element = context;
      PsiElement parent = context.getParent();
      while (parent instanceof PsiBinaryExpression && ((PsiBinaryExpression)parent).getOperationSign().getTokenType() == JavaTokenType.PLUS
             || parent instanceof PsiAssignmentExpression && ((PsiAssignmentExpression)parent).getOperationSign().getTokenType() == JavaTokenType.PLUSEQ
             || parent instanceof PsiConditionalExpression && ((PsiConditionalExpression)parent).getCondition() != element
             || parent instanceof PsiTypeCastExpression
             || parent instanceof PsiParenthesizedExpression) {
        element = parent;
        parent = parent.getParent();
      }

      PsiElement[] operands;
      PsiElement anchor;
      if (element instanceof PsiBinaryExpression || element instanceof PsiAssignmentExpression) {
        List<PsiElement> operandList = new ArrayList<PsiElement>();
        collectOperands(element, operandList);
        operands = operandList.toArray(new PsiElement[operandList.size()]);
        anchor = element;
      }
      else {
        operands = new PsiElement[]{context};
        anchor = context;
      }
      Project project = context.getProject();

      MultiHostRegistrarImpl registrar = new MultiHostRegistrarImpl(project, context.getContainingFile(), anchor);
      JavaConcatenationInjectorManager concatenationInjectorManager = getInstance(project);
      for (ConcatenationAwareInjector concatenationInjector : concatenationInjectorManager.myConcatenationInjectors) {
        concatenationInjector.getLanguagesToInject(registrar, operands);
      }

      CachedValueProvider.Result<Places> result = new CachedValueProvider.Result<Places>(registrar.result, PsiModificationTracker.MODIFICATION_COUNT, concatenationInjectorManager);

      if (registrar.result != null) {
        // store this everywhere
        ParameterizedCachedValue<Places, PsiElement> cachedValue = context.getManager().getCachedValuesManager().createParameterizedCachedValue(this, false);
        ((PsiParameterizedCachedValue<Places, PsiElement>)cachedValue).setValue(result);

        for (PsiElement operand : operands) {
          operand.putUserData(INJECTED_PSI_IN_CONCATENATION, cachedValue);
        }
        anchor.putUserData(INJECTED_PSI_IN_CONCATENATION, cachedValue);
        context.putUserData(INJECTED_PSI_IN_CONCATENATION, cachedValue);
      }

      return result;
    }
  }

  private static final Key<ParameterizedCachedValue<Places, PsiElement>> INJECTED_PSI_IN_CONCATENATION = Key.create("INJECTED_PSI_IN_CONCATENATION");
  private class Concatenation2InjectorAdapter implements MultiHostInjector {
    public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
      if (myConcatenationInjectors.isEmpty()) return;

      ParameterizedCachedValue<Places, PsiElement> cachedValue = context.getUserData(INJECTED_PSI_IN_CONCATENATION);
      Places result;
      if (cachedValue == null) {
        CachedValueProvider.Result<Places> res = CONCATENATION_PSI_CACHED_VALUE_PROVIDER.compute(context);
        result = res == null ? null : res.getValue();
      }
      else {
        result = cachedValue.getValue(context);
      }
      if (result != null) {
        ((MultiHostRegistrarImpl)registrar).addToResults(result);
      }
    }

    @NotNull
    public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
      return Arrays.asList(PsiLiteralExpression.class);
    }
  }

  private static void collectOperands(PsiElement expression, List<PsiElement> operands) {
    if (expression instanceof PsiBinaryExpression) {
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)expression;
      collectOperands(binaryExpression.getLOperand(), operands);
      collectOperands(binaryExpression.getROperand(), operands);
    }
    else if (expression != null) {
      operands.add(expression);
    }
  }

  private final List<ConcatenationAwareInjector> myConcatenationInjectors = ContainerUtil.createEmptyCOWList();
  public void registerConcatenationInjector(@NotNull ConcatenationAwareInjector injector) {
    myConcatenationInjectors.add(injector);
    concatenationInjectorsChanged();
  }

  public boolean unregisterConcatenationInjector(@NotNull ConcatenationAwareInjector injector) {
    boolean removed = myConcatenationInjectors.remove(injector);
    concatenationInjectorsChanged();
    return removed;
  }

  private void concatenationInjectorsChanged() {
    myModificationCounter++;
    if (myConcatenationInjectors.isEmpty()) {
      MultiHostInjector prev = myRegisteredConcatenationAdapter.getAndSet(null);
      if (prev != null) {
        myInjectedLanguageManager.unregisterMultiHostInjector(prev);
      }
    }
    else {
      MultiHostInjector adapter = new Concatenation2InjectorAdapter();
      if (myRegisteredConcatenationAdapter.compareAndSet(null, adapter)) {
        myInjectedLanguageManager.registerMultiHostInjector(adapter);
      }
    }
  }
}
