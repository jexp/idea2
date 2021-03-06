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
package com.intellij.xdebugger;

import com.intellij.mock.MockEditorFactory;
import com.intellij.mock.MockVirtualFileManager;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.ex.http.HttpFileSystem;
import com.intellij.openapi.vfs.impl.http.HttpFileSystemImpl;
import com.intellij.testFramework.PlatformLiteFixture;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.MutablePicoContainer;

/**
 * @author nik
 */
public abstract class XDebuggerTestCase extends PlatformLiteFixture {
  protected static final MyLineBreakpointType MY_LINE_BREAKPOINT_TYPE = new MyLineBreakpointType();
  protected static final MySimpleBreakpointType MY_SIMPLE_BREAKPOINT_TYPE = new MySimpleBreakpointType();

  protected void setUp() throws Exception {
    super.setUp();
    initApplication();
    registerExtensionPoint(XBreakpointType.EXTENSION_POINT_NAME, XBreakpointType.class);
    registerExtension(XBreakpointType.EXTENSION_POINT_NAME, MY_LINE_BREAKPOINT_TYPE);
    registerExtension(XBreakpointType.EXTENSION_POINT_NAME, MY_SIMPLE_BREAKPOINT_TYPE);

    MutablePicoContainer container = getApplication().getPicoContainer();
    registerComponentImplementation(container, EditorFactory.class, MockEditorFactory.class);
    registerComponentImplementation(container, VirtualFileManager.class, MockVirtualFileManager.class);
    registerComponentImplementation(container, HttpFileSystem.class, HttpFileSystemImpl.class);
  }

  public static class MyLineBreakpointType extends XLineBreakpointType<MyBreakpointProperties> {
    public MyLineBreakpointType() {
      super("testLine", "239");
    }

    public boolean canPutAt(@NotNull final VirtualFile file, final int line, @NotNull Project project) {
      return false;
    }

    public MyBreakpointProperties createBreakpointProperties(@NotNull final VirtualFile file, final int line) {
      return null;
    }

    public MyBreakpointProperties createProperties() {
      return new MyBreakpointProperties();
    }
  }

  public static class MySimpleBreakpointType extends XBreakpointType<XBreakpoint<MyBreakpointProperties>,MyBreakpointProperties> {
    public MySimpleBreakpointType() {
      super("test", "239");
    }

    public String getDisplayText(final XBreakpoint<MyBreakpointProperties> breakpoint) {
      return "";
    }

    public MyBreakpointProperties createProperties() {
      return new MyBreakpointProperties();
    }
  }

  protected static class MyBreakpointProperties extends XBreakpointProperties<MyBreakpointProperties> {
    @Attribute("option")
    public String myOption;

    public MyBreakpointProperties() {
    }

    public MyBreakpointProperties(final String option) {
      myOption = option;
    }

    public MyBreakpointProperties getState() {
      return this;
    }

    public void loadState(final MyBreakpointProperties state) {
      myOption = state.myOption;
    }
  }
}
