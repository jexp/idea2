/*
 * User: anna
 * Date: 22-Jul-2008
 */
package com.intellij.refactoring;

public class FindMethodDuplicatesMiscTest extends FindMethodDuplicatesBaseTest {
  @Override
  protected String getTestFilePath() {
    return "/refactoring/methodDuplicatesMisc/" + getTestName(false) + ".java";
  }

  public void testChangeReturnTypeByParameter() throws Exception {
    doTest();
  }

  public void testChangeReturnTypeByField() throws Exception {
    doTest();
  }

  public void testMethodTypeParameters() throws Exception {
    doTest();
  }

  public void testChangeReturnTypeByReturnExpression() throws Exception {
    doTest();
  }

  public void testChangeReturnTypeByReturnValue() throws Exception {
    doTest();
  }
}