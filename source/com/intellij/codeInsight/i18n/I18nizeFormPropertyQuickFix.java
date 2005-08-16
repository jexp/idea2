package com.intellij.codeInsight.i18n;

import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.GuiEditor;
import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.lw.StringDescriptor;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 16.08.2005
 * Time: 22:04:02
 * To change this template use File | Settings | File Templates.
 */
public class I18nizeFormPropertyQuickFix extends I18nizeFormQuickFix {
  private final IntrospectedProperty myProperty;

  public I18nizeFormPropertyQuickFix(final GuiEditor editor, final String name, final RadComponent component,
                                     final IntrospectedProperty property) {
    super(editor, name, component);
    myProperty = property;
  }

  protected StringDescriptor getStringDescriptorValue() {
    return (StringDescriptor) myProperty.getValue(myComponent);
  }

  protected void setStringDescriptorValue(final StringDescriptor descriptor) throws Exception {
    myProperty.setValue(myComponent, descriptor);
  }
}
