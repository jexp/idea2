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
 * User: spleaner
 * Date: Jun 19, 2007
 * Time: 4:44:25 PM
 */
package com.intellij.xml.breadcrumbs;

import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.StringTokenizer;

public class XmlLanguageBreadcrumbsInfoProvider extends BreadcrumbsInfoProvider {
  @NonNls private static final String CLASS_ATTRIBUTE_NAME = "class";
  @NonNls private static final String ID_ATTRIBUTE_NAME = "id";

  public boolean acceptElement(@NotNull final PsiElement e) {
    return e instanceof XmlTag && e.isValid();
  }

  public Language[] getLanguages() {
    return new Language[]{XHTMLLanguage.INSTANCE, HTMLLanguage.INSTANCE};
  }

  @NotNull
  public String getElementInfo(@NotNull final PsiElement e) {
    final XmlTag tag = (XmlTag)e;
    final StringBuffer sb = new StringBuffer();

    sb.append(tag.getName());

    final boolean addHtmlInfo = e.getContainingFile().getLanguage() != XMLLanguage.INSTANCE;

    if (addHtmlInfo) {
      final String id_value = tag.getAttributeValue(ID_ATTRIBUTE_NAME);
      if (null != id_value) {
        sb.append("#").append(id_value);
      }

      final String class_value = tag.getAttributeValue(CLASS_ATTRIBUTE_NAME);
      if (null != class_value) {
        final StringTokenizer tokenizer = new StringTokenizer(class_value, " ");
        while (tokenizer.hasMoreTokens()) {
          sb.append(".").append(tokenizer.nextToken());
        }
      }
    }

    return sb.toString();
  }

  @Nullable
  public String getElementTooltip(@NotNull final PsiElement e) {
    final XmlTag tag = (XmlTag)e;
    final StringBuffer result = new StringBuffer("<");
    result.append(tag.getName());
    final XmlAttribute[] attributes = tag.getAttributes();
    for (final XmlAttribute each : attributes) {
      result.append(" ").append(each.getText());
    }

    if (tag.isEmpty()) {
      result.append("/>");
    }
    else {
      result.append(">...</").append(tag.getName()).append(">");
    }

    return result.toString();
  }
}