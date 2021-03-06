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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.InsertHandlerDecorator;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.ProcessingContext;
import com.intellij.util.Consumer;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlExtension;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public class XmlCompletionContributor extends CompletionContributor {

  @NonNls public static final String TAG_NAME_COMPLETION_FEATURE = "tag.name.completion";
  private static final InsertHandlerDecorator<LookupElement> QUOTE_EATER = new InsertHandlerDecorator<LookupElement>() {
    public void handleInsert(InsertionContext context, LookupElementDecorator<LookupElement> item) {
      final char completionChar = context.getCompletionChar();
      if (completionChar == '\'' || completionChar == '\"') {
        context.setAddCompletionChar(false);
        item.getDelegate().handleInsert(context);

        final Editor editor = context.getEditor();
        final Document document = editor.getDocument();
        int tailOffset = editor.getCaretModel().getOffset();
        if (document.getTextLength() > tailOffset) {
          final char c = document.getCharsSequence().charAt(tailOffset);
          if (c == completionChar || completionChar == '\'') {
            editor.getCaretModel().moveToOffset(tailOffset + 1);
          }
        }
      } else {
        item.getDelegate().handleInsert(context);
      }
    }
  };

  public XmlCompletionContributor() {
    extend(CompletionType.BASIC,
           XmlPatterns.psiElement().inside(XmlPatterns.xmlAttributeValue()),
           new CompletionProvider<CompletionParameters>(false) {
             @Override
             protected void addCompletions(@NotNull CompletionParameters parameters,
                                           ProcessingContext context,
                                           @NotNull final CompletionResultSet result) {
               final XmlAttributeValue attributeValue = PsiTreeUtil.getParentOfType(parameters.getPosition(), XmlAttributeValue.class, false);
               if (attributeValue == null) {
                 // we are injected, only getContext() returns attribute value
                 return;
               }

               final Ref<Boolean> addWordVariants = Ref.create(true);
               result.runRemainingContributors(parameters, new Consumer<LookupElement>() {
                 public void consume(LookupElement element) {
                   addWordVariants.set(false);
                   result.addElement(LookupElementDecorator.withInsertHandler(element, QUOTE_EATER));
                 }
               });
               if (addWordVariants.get().booleanValue()) {
                 ApplicationManager.getApplication().runReadAction(new Runnable() {
                   public void run() {
                     addWordVariants.set(attributeValue.getReferences().length == 0);
                   }
                 });
               }

               if (addWordVariants.get().booleanValue()) {
                 WordCompletionContributor.addWordCompletionWariants(result, parameters);
               }
             }
           });
  }

  public void fillCompletionVariants(final CompletionParameters parameters, final CompletionResultSet result) {
    super.fillCompletionVariants(parameters, result);
    if (result.isStopped()) {
      return;
    }

    final PsiElement element = parameters.getPosition();

    if (parameters.getCompletionType() == CompletionType.CLASS_NAME) {
      if (!isXmlNameCompletion(parameters)) return;
      result.stopHere();
      if (!(element.getParent() instanceof XmlTag)) {
        return;
      }
      final XmlTag parent = (XmlTag)element.getParent();
      final String namespace = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        public String compute() {
          return parent.getNamespace();
        }
      });
      final XmlElementDescriptor parentDescriptor = ApplicationManager.getApplication().runReadAction(new Computable<XmlElementDescriptor>() {
        public XmlElementDescriptor compute() {
          return parent.getDescriptor();
        }
      });
      final String prefix = result.getPrefixMatcher().getPrefix();
      final int pos = prefix.indexOf(':');
      final String namespacePrefix = pos > 0 ? prefix.substring(0, pos) : null;

      final PsiReference reference = ApplicationManager.getApplication().runReadAction(new Computable<PsiReference>() {
        public PsiReference compute() {
          return parent.getReference();
        }
      });
      if (reference != null && namespace.length() > 0 && parentDescriptor != null && !(parentDescriptor instanceof AnyXmlElementDescriptor)) {
        final Set<LookupElement> set = new HashSet<LookupElement>();
        new XmlCompletionData().completeReference(reference, set, element, parameters.getOriginalFile(), parameters.getOffset());
        for (final LookupElement item : set) {
          result.addElement(item);
        }
      } else {

        final CompletionResultSet newResult = result.withPrefixMatcher(pos >= 0 ? prefix.substring(pos + 1) : prefix);

        final XmlFile file = (XmlFile)parameters.getOriginalFile();
        final List<Pair<String,String>> names =
          ApplicationManager.getApplication().runReadAction(new Computable<List<Pair<String, String>>>() {
            public List<Pair<String, String>> compute() {
              return XmlExtension.getExtension(file).getAvailableTagNames(file, parent);
            }
          });
        for (Pair<String, String> pair : names) {
          final String name = pair.getFirst();
          final String ns = pair.getSecond();
          final LookupItem item = new LookupItem<String>(name, name) {
            public int hashCode() {
              final int hashCode = name.hashCode() * 239;
              return ns == null ? hashCode : hashCode + ns.hashCode();
            }
          };
          final XmlTagInsertHandler insertHandler = new ExtendedTagInsertHandler(name, ns, namespacePrefix);
          item.setInsertHandler(insertHandler);
          if (!StringUtil.isEmpty(ns)) {
            item.setAttribute(LookupItem.TAIL_TEXT_ATTR, " (" + ns + ")");
            item.setAttribute(LookupItem.TAIL_TEXT_SMALL_ATTR, "");
          }
          newResult.addElement(item);
        }
      }
    }
  }

  private static boolean isXmlNameCompletion(final CompletionParameters parameters) {
    final ASTNode node = parameters.getPosition().getNode();
    if (node == null || node.getElementType() != XmlTokenType.XML_NAME) {
      return false;
    }
    return true;
  }

  @Override
  public String advertise(@NotNull final CompletionParameters parameters) {
    if (isXmlNameCompletion(parameters) && parameters.getCompletionType() == CompletionType.BASIC) {
      if (FeatureUsageTracker.getInstance().isToBeShown(TAG_NAME_COMPLETION_FEATURE, parameters.getPosition().getProject())) {
        final String shortcut = getActionShortcut(IdeActions.ACTION_CLASS_NAME_COMPLETION);
        if (shortcut != null) {
          return XmlBundle.message("tag.name.completion.hint", shortcut);
        }

      }
    }
    return super.advertise(parameters);
  }

  public void beforeCompletion(@NotNull final CompletionInitializationContext context) {
    final int offset = context.getStartOffset();
    final XmlAttributeValue attributeValue = PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), offset, XmlAttributeValue.class, true);
    if (attributeValue != null && offset == attributeValue.getTextRange().getStartOffset()) {
      context.setFileCopyPatcher(new DummyIdentifierPatcher(""));
    }
  }
}
