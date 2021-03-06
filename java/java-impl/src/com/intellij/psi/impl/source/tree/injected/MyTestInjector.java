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

/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Sep 4, 2007
 * Time: 7:17:07 PM
 */
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.injection.ConcatenationAwareInjector;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.PsiCommentImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.Arrays;
import java.util.List;

public class MyTestInjector {
  private LanguageInjector myInjector;
  private ConcatenationAwareInjector myQLInPlaceInjector;
  private ConcatenationAwareInjector myJSInPlaceInjector;
  private ConcatenationAwareInjector mySeparatedJSInjector;
  private final PsiManager myPsiManager;
  private ConcatenationAwareInjector myQLPrefixedInjector;
  private MultiHostInjector myMultiHostInjector;
  private ConcatenationAwareInjector myBrokenUpPrefix;

  @TestOnly
  public MyTestInjector(PsiManager psiManager) {
    myPsiManager = psiManager;
  }

  public void injectAll() {
    myInjector = injectVariousStuffEverywhere(myPsiManager);

    Project project = myPsiManager.getProject();
    Language ql = findLanguageByID("JPAQL");
    Language js = findLanguageByID("JavaScript");
    myQLInPlaceInjector = registerForStringVarInitializer(project, ql, "ql", null, null);
    myQLPrefixedInjector = registerForStringVarInitializer(project, ql, "qlPrefixed", "xxx", null);
    myJSInPlaceInjector = registerForStringVarInitializer(project, js, "js", null, null);
    mySeparatedJSInjector = registerForStringVarInitializer(project, js, "jsSeparated", " + ", " + 'separator'");
    myBrokenUpPrefix = registerForStringVarInitializer(project, js, "jsBrokenPrefix", "xx ", "");
  }

  private static ConcatenationAwareInjector registerForStringVarInitializer(@NotNull Project project,
                                                                            @NotNull final Language language,
                                                                            @NonNls final String varName,
                                                                            @NonNls final String prefix,
                                                                            @NonNls final String suffix) {
    ConcatenationAwareInjector injector = new ConcatenationAwareInjector() {
      public void getLanguagesToInject(@NotNull MultiHostRegistrar injectionPlacesRegistrar, @NotNull PsiElement... operands) {
        PsiVariable variable = PsiTreeUtil.getParentOfType(operands[0], PsiVariable.class);
        if (variable == null) return;
        if (!varName.equals(variable.getName())) return;
        if (!(operands[0] instanceof PsiLiteralExpression)) return;
        boolean started = false;
        String prefixFromPrev="";
        for (int i = 0; i < operands.length; i++) {
          PsiElement operand = operands[i];
          if (!(operand instanceof PsiLiteralExpression)) {
            continue;
          }
          Object value = ((PsiLiteralExpression)operand).getValue();
          if (!(value instanceof String)) {
            prefixFromPrev += value;
            continue;
          }
          TextRange textRange = textRangeToInject((PsiLanguageInjectionHost)operand);
          if (!started) {
            injectionPlacesRegistrar.startInjecting(language);
            started = true;
          }
          injectionPlacesRegistrar.addPlace(prefixFromPrev + (i == 0 ? "" : prefix==null?"":prefix), i == operands.length - 1 ? null : suffix, (PsiLanguageInjectionHost)operand, textRange);
          prefixFromPrev = "";
        }
        if (started) {
          injectionPlacesRegistrar.doneInjecting();
        }
      }
    };
    JavaConcatenationInjectorManager.getInstance(project).registerConcatenationInjector(injector);
    return injector;
  }


  public void uninjectAll() {
    myPsiManager.unregisterLanguageInjector(myInjector);
    Project project = myPsiManager.getProject();
    boolean b = JavaConcatenationInjectorManager.getInstance(project).unregisterConcatenationInjector(myQLInPlaceInjector);
    assert b;
    b = JavaConcatenationInjectorManager.getInstance(project).unregisterConcatenationInjector(myJSInPlaceInjector);
    assert b;
    b = JavaConcatenationInjectorManager.getInstance(project).unregisterConcatenationInjector(mySeparatedJSInjector);
    assert b;
    b = JavaConcatenationInjectorManager.getInstance(project).unregisterConcatenationInjector(myQLPrefixedInjector);
    assert b;
    b = JavaConcatenationInjectorManager.getInstance(project).unregisterConcatenationInjector(myBrokenUpPrefix);
    assert b;
    b = InjectedLanguageManager.getInstance(project).unregisterMultiHostInjector(myMultiHostInjector);
    assert b;
  }

  private static Language findLanguageByID(@NonNls String id) {
    for (Language language : Language.getRegisteredLanguages()) {
      if (language == Language.ANY) continue;
      if (language.getID().equals(id)) return language;
    }
    return null;
  }

  private LanguageInjector injectVariousStuffEverywhere(PsiManager psiManager) {
    final Language ql = findLanguageByID("JPAQL");
    final Language js = findLanguageByID("JavaScript");
    myMultiHostInjector = new MultiHostInjector() {
      public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
        XmlAttributeValue value = (XmlAttributeValue)context;
        PsiElement parent = value.getParent();
        if (parent instanceof XmlAttribute) {
          @NonNls String attrName = ((XmlAttribute)parent).getLocalName();
          if ("jsInBraces".equals(attrName)) {
            registrar.startInjecting(js);
            String text = value.getText();
            int index = 0;
            while (text.indexOf('{', index) != -1) {
              int lbrace = text.indexOf('{', index);
              int rbrace = text.indexOf('}', index);
              registrar.addPlace("", "", (PsiLanguageInjectionHost)value, new TextRange(lbrace + 1, rbrace));
              index = rbrace + 1;
            }
            registrar.doneInjecting();
          }
        }
      }

      @NotNull
      public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
        return Arrays.asList(XmlAttributeValue.class);
      }
    };
    InjectedLanguageManager.getInstance(psiManager.getProject()).registerMultiHostInjector(myMultiHostInjector);

    LanguageInjector myInjector = new LanguageInjector() {
      public void getLanguagesToInject(@NotNull PsiLanguageInjectionHost host, @NotNull InjectedLanguagePlaces placesToInject) {
        if (host instanceof XmlAttributeValue) {
          XmlAttributeValue value = (XmlAttributeValue)host;
          PsiElement parent = value.getParent();
          if (parent instanceof XmlAttribute) {
            @NonNls String attrName = ((XmlAttribute)parent).getLocalName();
            if ("ql".equals(attrName)) {
              inject(host, placesToInject, ql);
              return;
            }
            if ("js".equals(attrName)) {
              inject(host, placesToInject, js);
              return;
            }
            if ("jsprefix".equals(attrName)) {
              inject(host, placesToInject, js, "function foo(doc, window){", "}");
              return;
            }
          }
        }
        if (host instanceof XmlText) {
          // inject to xml tags named 'ql'
          final XmlText xmlText = (XmlText)host;
          XmlTag tag = xmlText.getParentTag();
          if (tag == null) return;
          if ("ql".equals(tag.getLocalName())) {
            inject(host, placesToInject, ql);
            return;
          }
          if ("js".equals(tag.getLocalName())) {
            inject(host, placesToInject, js);
            return;
          }
          if ("jsprefix".equals(tag.getLocalName())) {
            inject(host, placesToInject, js, "function foo(doc, window){", "}");
            return;
          }

          if ("jsInHash".equals(tag.getLocalName())) {
            String text = xmlText.getText();
            if (text.contains("#")) {
              int start = text.indexOf('#');
              int end = text.lastIndexOf('#');
              if (start != end && start != -1) {
                placesToInject.addPlace(js, new TextRange(start + 1, end), null, null);
                return;
              }
            }
          }

        }
        if (host instanceof PsiCommentImpl) {
          String text = host.getText();
          if (text.startsWith("/*-{") && text.endsWith("}-*/")) {
            TextRange textRange = new TextRange(4, text.length()-4);
            if (!(host.getParent()instanceof PsiMethod)) return;
            PsiMethod method = (PsiMethod)host.getParent();
            if (!method.hasModifierProperty(PsiModifier.NATIVE) || !method.hasModifierProperty(PsiModifier.PUBLIC)) return;
            String paramList = "";
            for (PsiParameter parameter : method.getParameterList().getParameters()) {
              if (paramList.length()!=0) paramList += ",";
              paramList += parameter.getName();
            }
            @NonNls String header = "function " + method.getName() + "("+paramList+") {";
            Language gwt = findLanguageByID("GWT JavaScript");
            placesToInject.addPlace(gwt, textRange, header, "}");
            return;
          }
          PsiElement parent = host.getParent();
          if (parent instanceof PsiMethod && ((PsiMethod)parent).getName().equals("xml")) {
            placesToInject.addPlace(StdLanguages.XML, new TextRange(2,host.getTextLength()-2), null,null);
            return;
          }
        }
        // inject to all string literal initializers of variables named 'ql'
        if (host instanceof PsiLiteralExpression && ((PsiLiteralExpression)host).getValue() instanceof String) {
          PsiVariable variable = PsiTreeUtil.getParentOfType(host, PsiVariable.class);
          if (variable == null) return;
          if (host.getParent() instanceof PsiBinaryExpression) return;
          if ("ql".equals(variable.getName())) {
            placesToInject.addPlace(ql, textRangeToInject(host), null, null);
          }
          if ("xml".equals(variable.getName())) {
            placesToInject.addPlace(StdLanguages.XML, textRangeToInject(host), null, null);
          }
          if ("js".equals(variable.getName())) { // with prefix/suffix
            placesToInject.addPlace(js, textRangeToInject(host), "function foo(doc,window) {", "}");
          }

          if ("lang".equals(variable.getName())) {
            // various lang depending on field "languageID" content
            PsiClass aClass = PsiTreeUtil.getParentOfType(variable, PsiClass.class);
            aClass = aClass.findInnerClassByName("Language", false);
            String text = aClass.getInitializers()[0].getBody().getFirstBodyElement().getNextSibling().getText().substring(2);
            Language language = findLanguageByID(text);

            if (language != null) {
              placesToInject.addPlace(language, textRangeToInject(host), "", "");
            }
          }
        }
      }
    };

    psiManager.registerLanguageInjector(myInjector);

    return myInjector;
  }

  private static void inject(final PsiLanguageInjectionHost host, final InjectedLanguagePlaces placesToInject, final Language language) {
    inject(host, placesToInject, language, null, null);
  }
  private static void inject(final PsiLanguageInjectionHost host, final InjectedLanguagePlaces placesToInject, final Language language, @NonNls String prefix, String suffix) {
    TextRange insideQuotes = textRangeToInject(host);

    placesToInject.addPlace(language, insideQuotes, prefix, suffix);
  }

  public static TextRange textRangeToInject(PsiLanguageInjectionHost host) {
    ASTNode[] children = ((ASTNode)host).getChildren(null);
    TextRange insideQuotes = new ProperTextRange(0, host.getTextLength());

    if (children.length > 1 && children[0].getElementType() == XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER) {
      insideQuotes = new ProperTextRange(children[1].getTextRange().getStartOffset() - host.getTextRange().getStartOffset(), insideQuotes.getEndOffset());
    }
    if (children.length > 1 && children[children.length-1].getElementType() == XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER) {
      insideQuotes = new ProperTextRange(insideQuotes.getStartOffset(), children[children.length-2].getTextRange().getEndOffset() - host.getTextRange().getStartOffset());
    }
    if (host instanceof PsiLiteralExpression) {
      insideQuotes = new ProperTextRange(1, host.getTextLength()-1);
    }
    return insideQuotes;
  }
}
