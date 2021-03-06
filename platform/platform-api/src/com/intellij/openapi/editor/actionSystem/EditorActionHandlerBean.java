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
package com.intellij.openapi.editor.actionSystem;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.pico.IdeaPicoContainer;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * @author yole
 */
public class EditorActionHandlerBean extends AbstractExtensionPointBean {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.actionSystem.EditorActionHandlerBean");
  
  public static final ExtensionPointName<EditorActionHandlerBean> EP_NAME = ExtensionPointName.create("com.intellij.editorActionHandler");

  // these must be public for scrambling compatibility
  @Attribute("action")
  public String action;
  @Attribute("implementationClass")
  public String implementationClass;

  private EditorActionHandler myHandler;

  public EditorActionHandler getHandler(EditorActionHandler originalHandler) {
    if (myHandler == null) {
      try {
        IdeaPicoContainer container = new IdeaPicoContainer(ApplicationManager.getApplication().getPicoContainer());
        container.registerComponentInstance(originalHandler);
        myHandler = instantiate(implementationClass, container);
      }
      catch(Exception e) {
        LOG.error(e);
        return null;
      }
    }
    return myHandler;
  }
}
