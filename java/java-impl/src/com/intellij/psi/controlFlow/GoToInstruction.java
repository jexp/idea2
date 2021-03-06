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
package com.intellij.psi.controlFlow;

import com.intellij.openapi.diagnostic.Logger;

public class GoToInstruction extends BranchingInstruction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.controlFlow.GoToInstruction");

  public final boolean isReturn; //true if goto has been generated as a result of return statement

  public GoToInstruction(int offset) {
    this(offset, BranchingInstruction.Role.END);
  }
  public GoToInstruction(int offset, Role role) {
    this (offset,role,false);
  }
  public GoToInstruction(int offset, Role role, boolean isReturn) {
    super(offset, role);
    this.isReturn = isReturn;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    final String sRole = "["+role.toString()+"]";
    return "GOTO " + sRole + " " + offset + (isReturn ? " RETURN" : "");
  }

  public int nNext() { return 1; }

  public int getNext(int index, int no) {
    LOG.assertTrue(no == 0);
    return offset;
  }

  public void accept(ControlFlowInstructionVisitor visitor, int offset, int nextOffset) {
    visitor.visitGoToInstruction(this, offset, nextOffset);
  }
}
