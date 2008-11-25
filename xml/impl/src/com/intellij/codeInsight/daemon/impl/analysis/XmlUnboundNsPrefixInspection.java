package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.xml.*;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlExtension;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.util.XmlTagUtil;
import com.intellij.xml.util.XmlUtil;
import com.intellij.lang.injection.InjectedLanguageManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class XmlUnboundNsPrefixInspection extends LocalInspectionTool {

  @NonNls private static final String XML = "xml";

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new XmlElementVisitor() {

      private Boolean isXml;

      private boolean isXmlFile(XmlElement element) {
        if (isXml == null) {
          final PsiFile file = element.getContainingFile();
          isXml = file instanceof XmlFile && !InjectedLanguageManager.getInstance(element.getProject()).isInjectedFragment(file);
        }
        return isXml.booleanValue();
      }

      @Override
      public void visitXmlToken(final XmlToken token) {
        if (isXmlFile(token) && token.getTokenType() == XmlTokenType.XML_NAME) {
          PsiElement element = token.getPrevSibling();
          while(element instanceof PsiWhiteSpace) element = element.getPrevSibling();

          if (element instanceof XmlToken && ((XmlToken)element).getTokenType() == XmlTokenType.XML_START_TAG_START) {
            PsiElement parent = element.getParent();

            if (parent instanceof XmlTag && !(token.getNextSibling() instanceof OuterLanguageElement)) {
              XmlTag tag = (XmlTag)parent;
              checkUnboundNamespacePrefix(tag, tag, tag.getNamespacePrefix(), token, holder);
            }
          }
        }
      }

      @Override
      public void visitXmlAttribute(final XmlAttribute attribute) {
        if (!isXmlFile(attribute)) {
          return;
        }
        final String namespace = attribute.getNamespace();
        if (attribute.isNamespaceDeclaration() || XmlUtil.XML_SCHEMA_INSTANCE_URI.equals(namespace)) {
          return;
        }

        XmlTag tag = attribute.getParent();
        XmlElementDescriptor elementDescriptor = tag.getDescriptor();
        if (elementDescriptor == null ||
            elementDescriptor instanceof AnyXmlElementDescriptor) {
          return;
        }


        final String name = attribute.getName();

        checkUnboundNamespacePrefix(attribute, tag, XmlUtil.findPrefixByQualifiedName(name), null, holder);
      }
    };
  }

  private static void checkUnboundNamespacePrefix(final XmlElement element, final XmlTag context, String namespacePrefix, final XmlToken token,
                                                  final ProblemsHolder holder) {

    if (namespacePrefix.length() == 0 && (!(element instanceof XmlTag) || !(element.getParent() instanceof XmlDocument))
      || XML.equals(namespacePrefix)) {
      return;
    }

    final String namespaceByPrefix = context.getNamespaceByPrefix(namespacePrefix);
    if (namespaceByPrefix.length() != 0) {
      return;
    }

    final XmlFile containingFile = (XmlFile)context.getContainingFile();
    if (!HighlightLevelUtil.shouldInspect(containingFile)) return;

    final XmlExtension extension = XmlExtension.getExtension(containingFile);
    if (extension.isPrefixDeclared(context, namespacePrefix)) {
      return;
    }

    final String localizedMessage = XmlErrorMessages.message("unbound.namespace", namespacePrefix);

    if (namespacePrefix.length() == 0) {
      final XmlTag tag = (XmlTag)element;
      if (!XmlUtil.JSP_URI.equals(tag.getNamespace())) {
        reportTagProblem(tag, localizedMessage, null, ProblemHighlightType.INFORMATION, new CreateNSDeclarationIntentionFix(context, namespacePrefix, token),
                         holder);
      }
      return;
    }

    final int prefixLength = namespacePrefix.length();
    final TextRange range = new TextRange(0, prefixLength);
    final HighlightInfoType infoType = extension.getHighlightInfoType(containingFile);
    final ProblemHighlightType highlightType = infoType == HighlightInfoType.ERROR ? ProblemHighlightType.ERROR : ProblemHighlightType.LIKE_UNKNOWN_SYMBOL;
    if (element instanceof XmlTag) {
      final CreateNSDeclarationIntentionFix fix = new CreateNSDeclarationIntentionFix(context, namespacePrefix, token);
      reportTagProblem(element, localizedMessage, range, highlightType, fix, holder);
    } else {
      holder.registerProblem(element, localizedMessage, highlightType, range);
    }
  }

  private static void reportTagProblem(final XmlElement element, final String localizedMessage, final TextRange range, final ProblemHighlightType highlightType,
                                       final CreateNSDeclarationIntentionFix fix,
                                       final ProblemsHolder holder) {

    XmlToken nameToken = XmlTagUtil.getStartTagNameElement((XmlTag)element);
    if (nameToken != null) {
      holder.registerProblem(nameToken, localizedMessage, highlightType, range, fix);
    }
    nameToken = XmlTagUtil.getEndTagNameElement((XmlTag)element);
    if (nameToken != null) {
      holder.registerProblem(nameToken, localizedMessage, highlightType, range, fix);
    }
  }


  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  public String getGroupDisplayName() {
    return XmlBundle.message("xml.inspections.group.name");
  }

  @NotNull
  public String getDisplayName() {
    return XmlBundle.message("xml.inspections.unbound.prefix");
  }

  @NotNull
  @NonNls
  public String getShortName() {
    return "XmlUnboundNsPrefix";
  }
}
