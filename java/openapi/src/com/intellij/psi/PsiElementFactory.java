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
package com.intellij.psi;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Service for creating instances of Java, JavaDoc, JSP and XML PSI elements which don't have
 * an underlying source code file.
 *
 * @see com.intellij.psi.JavaPsiFacade#getElementFactory()
 */
public interface PsiElementFactory extends PsiJavaParserFacade {
  /**
   * Creates an empty class with the specified name.
   *
   * @param name the name of the class to create.
   * @return the created class instance.
   * @throws IncorrectOperationException if <code>name</code> is not a valid Java identifier.
   */
  @NotNull PsiClass createClass(@NonNls @NotNull String name) throws IncorrectOperationException;

  /**
   * Creates an empty interface with the specified name.
   *
   * @param name the name of the interface to create.
   * @return the created interface instance.
   * @throws IncorrectOperationException if <code>name</code> is not a valid Java identifier.
   */
  @NotNull PsiClass createInterface(@NonNls @NotNull String name) throws IncorrectOperationException;

  /**
   * Creates an empty enum with the specified name.
   *
   * @param name the name of the enum to create.
   * @return the created enum instance.
   * @throws IncorrectOperationException if <code>name</code> is not a valid Java identifier.
   */

  PsiClass createEnum(@NotNull @NonNls String name) throws IncorrectOperationException;

  /**
   * Creates a field with the specified name and type.
   *
   * @param name the name of the field to create.
   * @param type the type of the field to create.
   * @return the created field instance.
   * @throws IncorrectOperationException <code>name</code> is not a valid Java identifier
   *                                     or <code>type</code> represents an invalid type.
   */
  @NotNull PsiField createField(@NotNull @NonNls String name, @NotNull PsiType type) throws IncorrectOperationException;

  /**
   * Creates an empty method with the specified name and return type.
   *
   * @param name       the name of the method to create.
   * @param returnType the return type of the method to create.
   * @return the created method instance.
   * @throws IncorrectOperationException <code>name</code> is not a valid Java identifier
   *                                     or <code>type</code> represents an invalid type.
   */
  @NotNull PsiMethod createMethod(@NotNull @NonNls String name, PsiType returnType) throws IncorrectOperationException;

  /**
   * Creates an empty constructor.
   *
   * @return the created constructor instance.
   */
  @NotNull PsiMethod createConstructor();

  /**
   * Creates an empty class initializer block.
   *
   * @return the created initializer block instance.
   * @throws IncorrectOperationException in case of an internal error.
   */
  @NotNull PsiClassInitializer createClassInitializer() throws IncorrectOperationException;

  /**
   * Creates a parameter with the specified name and type.
   *
   * @param name the name of the parameter to create.
   * @param type the type of the parameter to create.
   * @return the created parameter instance.
   * @throws IncorrectOperationException <code>name</code> is not a valid Java identifier
   *                                     or <code>type</code> represents an invalid type.
   */
  @NotNull PsiParameter createParameter(@NotNull @NonNls String name, @NotNull PsiType type) throws IncorrectOperationException;

  /**
   * Creates an empty Java code block.
   *
   * @return the created code block instance.
   */
  @NotNull PsiCodeBlock createCodeBlock();

  /**
   * Creates a class type for the specified class.
   *
   * @param aClass the class for which the class type is created.
   * @return the class type instance.
   */
  @NotNull PsiClassType createType(@NotNull PsiClass aClass);

  /**
   * Creates a class type for the specified class, using the specified substitutor
   * to replace generic type parameters on the class.
   *
   * @param resolve     the class for which the class type is created.
   * @param substitutor the substitutor to use.
   * @return the class type instance.
   */
  @NotNull PsiClassType createType(@NotNull PsiClass resolve, @NotNull PsiSubstitutor substitutor);

  /*
    additional languageLevel parameter to memorize language level for allowing/prohibiting boxing/unboxing
   */
  @NotNull PsiClassType createType(@NotNull PsiClass resolve, @NotNull PsiSubstitutor substitutor, @NotNull LanguageLevel languageLevel);

  @NotNull PsiClassType createType(@NotNull PsiClass resolve, @NotNull PsiSubstitutor substitutor, @NotNull LanguageLevel languageLevel, @NotNull PsiAnnotation[] annotations);

  /**
   * Creates a class type for the specified reference pointing to a class.
   *
   * @param classReference the class reference for which the class type is created.
   * @return the class type instance.
   */
  @NotNull PsiClassType createType(@NotNull PsiJavaCodeReferenceElement classReference);

  /**
   * Detaches type from reference(s) or type elements it was created from.
   *
   * @param type the type to detach.
   * @return the detached type.
   * @deprecated Optimization method, do not use if you do not understand what it does.
   */
  @NotNull PsiType detachType(@NotNull PsiType type);

  /**
   * Creates a substitutor for the specified class which replaces all type parameters
   * with their corresponding raw types.
   *
   * @param owner the class or method for which the substitutor is created.
   * @return the substitutor instance.
   */
  @NotNull PsiSubstitutor createRawSubstitutor(@NotNull PsiTypeParameterListOwner owner);

  /**
   * Creates a substitutor which uses the specified mapping between type parameters and types.
   *
   * @param map the type parameter to type map used by the substitutor.
   * @return the substitutor instance.
   */
  @NotNull PsiSubstitutor createSubstitutor(@NotNull Map<PsiTypeParameter, PsiType> map);

  /**
   * Returns the primitive type instance for the specified type name.
   *
   * @param text the name of a Java primitive type (for example, <code>int</code>)
   * @return the primitive type instance, or null if <code>name</code> is not a valid
   *         primitive type name.
   */
  @Nullable PsiPrimitiveType createPrimitiveType(@NotNull String text);

  /**
   * @deprecated use {@link #createTypeByFQClassName(String, GlobalSearchScope)}
   */
  @NotNull PsiClassType createTypeByFQClassName(@NotNull @NonNls String qName);

  /**
   * Creates a class type referencing a class with the specified class name in the specified
   * search scope.
   *
   * @param qName        the full-qualified name of the class to create the reference to.
   * @param resolveScope the scope in which the class is searched.
   * @return the class type instance.
   */
  @NotNull PsiClassType createTypeByFQClassName(@NotNull @NonNls String qName, @NotNull GlobalSearchScope resolveScope);

  /**
   * Creates a type element referencing the specified type.
   *
   * @param psiType the type to reference.
   * @return the type element instance.
   */
  @NotNull PsiTypeElement createTypeElement(@NotNull PsiType psiType);

  /**
   * Creates a reference element resolving to the specified class type.
   *
   * @param type the class type to create the reference to.
   * @return the reference element instance.
   */
  @NotNull PsiJavaCodeReferenceElement createReferenceElementByType(@NotNull PsiClassType type);

  /**
   * Creates a reference element resolving to the specified class.
   *
   * @param aClass the class to create the reference to.
   * @return the reference element instance.
   */
  @NotNull PsiJavaCodeReferenceElement createClassReferenceElement(@NotNull PsiClass aClass);

  /**
   * Creates a reference element resolving to the class with the specified name
   * in the specified search scope. The text of the created reference is the short name of the class.
   *
   * @param qName        the full-qualified name of the class to create the reference to.
   * @param resolveScope the scope in which the class is searched.
   * @return the reference element instance.
   */
  @NotNull PsiJavaCodeReferenceElement createReferenceElementByFQClassName(@NotNull String qName, @NotNull GlobalSearchScope resolveScope);

  /**
   * Creates a reference element resolving to the class with the specified name
   * in the specified search scope. The text of the created reference is the fully qualified name of the class.
   *
   * @param qName        the full-qualified name of the class to create the reference to.
   * @param resolveScope the scope in which the class is searched.
   * @return the reference element instance.
   */
  @NotNull PsiJavaCodeReferenceElement createFQClassNameReferenceElement(@NotNull String qName, @NotNull GlobalSearchScope resolveScope);

  /**
   * Creates a reference element resolving to the specified package.
   *
   * @param aPackage the package to create the reference to.
   * @return the reference element instance.
   * @throws IncorrectOperationException if <code>aPackage</code> is the default (root) package.
   */
  @NotNull PsiJavaCodeReferenceElement createPackageReferenceElement(@NotNull PsiPackage aPackage) throws IncorrectOperationException;

  /**
   * Creates a reference element resolving to the package with the specified name.
   *
   * @param packageName the name of the package to create the reference to.
   * @return the reference element instance.
   * @throws IncorrectOperationException if <code>packageName</code> is an empty string.
   */
  @NotNull PsiJavaCodeReferenceElement createPackageReferenceElement(@NotNull String packageName) throws IncorrectOperationException;

  /**
   * Creates a reference expression resolving to the specified class.
   *
   * @param aClass the class to create the reference to.
   * @return the reference expression instance.
   * @throws IncorrectOperationException never (the exception is kept for compatibility purposes).
   */
  @NotNull PsiReferenceExpression createReferenceExpression(@NotNull PsiClass aClass) throws IncorrectOperationException;

  /**
   * Creates a reference expression resolving to the specified package.
   *
   * @param aPackage the package to create the reference to.
   * @return the reference expression instance.
   * @throws IncorrectOperationException if <code>aPackage</code> is the default (root) package.
   */
  @NotNull PsiReferenceExpression createReferenceExpression(@NotNull PsiPackage aPackage) throws IncorrectOperationException;

  /**
   * Creates a Java idenitifier with the specified text.
   *
   * @param text the text of the identifier to create.
   * @return the idenitifier instance.
   * @throws IncorrectOperationException if <code>text</code> is not a valid Java identifier.
   */
  @NotNull PsiIdentifier createIdentifier(@NotNull @NonNls String text) throws IncorrectOperationException;

  /**
   * Creates a Java keyword with the specified text.
   *
   * @param keyword the text of the keyword to create.
   * @return the keyword instance.
   * @throws IncorrectOperationException if <code>text</code> is not a valid Java keyword.
   */
  @NotNull PsiKeyword createKeyword(@NotNull @NonNls String keyword) throws IncorrectOperationException;

  /**
   * Creates an import statement for importing the specified class.
   *
   * @param aClass the class to create the import statement for.
   * @return the import statement instance.
   * @throws IncorrectOperationException if <code>aClass</code> is an anonymous or local class.
   */
  @NotNull PsiImportStatement createImportStatement(@NotNull PsiClass aClass) throws IncorrectOperationException;

  /**
   * Creates an on-demand import statement for importing classes from the package with the specified name.
   *
   * @param packageName the name of package to create the import statement for.
   * @return the import statement instance.
   * @throws IncorrectOperationException if <code>packageName</code> is not a valid qualified package name.
   */
  @NotNull PsiImportStatement createImportStatementOnDemand(@NotNull @NonNls String packageName) throws IncorrectOperationException;

  /**
   * Creates a local variable declaration statement with the specified name, type and initializer,
   * optionally without reformatting the declaration.
   *
   * @param name        the name of the variable to create.
   * @param type        the type of the variable to create.
   * @param initializer the initializer for the variable.
   * @return the variable instance.
   * @throws IncorrectOperationException if <code>name</code> is not a valid identifier or
   *                                     <code>type</code> is not a valid type.
   */
  @NotNull PsiDeclarationStatement createVariableDeclarationStatement(@NonNls @NotNull String name, @NotNull PsiType type, PsiExpression initializer)
    throws IncorrectOperationException;

  /**
   * Creates a PSI element for the "&#64;param" JavaDoc tag.
   *
   * @param parameterName the name of the parameter for which the tag is created.
   * @param description   the description of the parameter for which the tag is created.
   * @return the created tag.
   * @throws IncorrectOperationException if the name or description are invalid.
   */
  @NotNull PsiDocTag createParamTag(@NotNull String parameterName, String description) throws IncorrectOperationException;

  /**
   * Creates a Java expression code fragment from the text of the expression.
   *
   * @param text         the text of the expression to create.
   * @param context      the context for resolving references from the code fragment.
   * @param expectedType expected type of the expression (does not have any effect on creation
   *                     but can be accessed as {@link PsiExpressionCodeFragment#getExpectedType()}).
   * @param isPhysical   whether the code fragment is created as a physical element
   *                     (see {@link PsiElement#isPhysical()}).
   * @return the created code fragment.
   */
  @NotNull PsiExpressionCodeFragment createExpressionCodeFragment(@NotNull String text, PsiElement context, final PsiType expectedType, boolean isPhysical);

  /**
   * Creates a Java code fragment from the text of a Java code block.
   *
   * @param text       the text of the code block to create.
   * @param context    the context for resolving references from the code fragment.
   * @param isPhysical whether the code fragment is created as a physical element
   *                   (see {@link PsiElement#isPhysical()}).
   * @return the created code fragment.
   */
  @NotNull
  JavaCodeFragment createCodeBlockCodeFragment(@NotNull String text, PsiElement context, boolean isPhysical);

  /**
   * Creates a Java type code fragment from the text of the name of a Java type (the name
   * of a primitive type, array type or class), with <code>void</code> and ellipsis
   * not treated as a valid type.
   *
   * @param text       the text of the Java type to create.
   * @param context    the context for resolving references from the code fragment.
   * @param isPhysical whether the code fragment is created as a physical element
   *                   (see {@link PsiElement#isPhysical()}).
   * @return the created code fragment.
   */
  @NotNull PsiTypeCodeFragment createTypeCodeFragment(@NotNull String text, PsiElement context, boolean isPhysical);

  /**
   * Creates a Java type code fragment from the text of the name of a Java type (the name
   * of a primitive type, array type or class), with <code>void</code> optionally treated
   * as a valid type, and ellipsis not treated as a valid type.
   *
   * @param text        the text of the Java type to create.
   * @param context     the context for resolving references from the code fragment.
   * @param isVoidValid whether <code>void</code> is a valid type for the fragment.
   * @param isPhysical  whether the code fragment is created as a physical element
   *                    (see {@link PsiElement#isPhysical()}).
   * @return the created code fragment.
   */
  @NotNull PsiTypeCodeFragment createTypeCodeFragment(@NotNull String text, PsiElement context, boolean isVoidValid, boolean isPhysical);

  /**
   * Creates a Java type code fragment from the text of the name of a Java type (the name
   * of a primitive type, array type or class), with <code>void</code> optionally treated
   * as a valid type, and ellipsis not treated as a valid type.
   *
   * @param text          the text of the Java type to create.
   * @param context       the context for resolving references from the code fragment.
   * @param isVoidValid   whether <code>void</code> is a valid type for the fragment.
   * @param isPhysical    whether the code fragment is created as a physical element
   *                      (see {@link PsiElement#isPhysical()}).
   * @param allowEllipsis whether {@link PsiEllipsisType} is a valid type for the element.
   * @return the created code fragment.
   */
  @NotNull PsiTypeCodeFragment createTypeCodeFragment(@NotNull String text,
                                             PsiElement context,
                                             boolean isVoidValid,
                                             boolean isPhysical, boolean allowEllipsis);

  /**
   * Returns a synthetic Java class containing methods which are defined on Java arrays.
   *
   * @return the array synthetic class.
   */
  @NotNull PsiClass getArrayClass(@NotNull LanguageLevel languageLevel);

  /**
   * Returns the class type for a synthetic Java class containing methods which
   * are defined on Java arrays with the specified element type.
   *
   * @param componentType the component type of the array for which the class type is returned.
   * @param languageLevel
   * @return the class type the array synthetic class.
   */
  @NotNull PsiClassType getArrayClassType(@NotNull PsiType componentType, @NotNull final LanguageLevel languageLevel);

  /**
   * Creates a package statement for the specified package name.
   *
   * @param name the name of the package to use in the package statement.
   * @return the created package statement instance.
   * @throws IncorrectOperationException if <code>name</code> is not a valid package name.
   */
  @NotNull PsiPackageStatement createPackageStatement(@NotNull String name) throws IncorrectOperationException;

  /**
   * Creates a Java reference code fragment from the text of a Java reference to a
   * package or class.
   *
   * @param text              the text of the reference to create.
   * @param context           the context for resolving the reference.
   * @param isPhysical        whether the code fragment is created as a physical element
   *                          (see {@link PsiElement#isPhysical()}).
   * @param isClassesAccepted if true then classes as well as packages are accepted as
   *                          reference target, otherwise only packages are
   * @return the created reference fragment.
   */
  @NotNull PsiJavaCodeReferenceCodeFragment createReferenceCodeFragment(@NotNull String text,
                                                               PsiElement context,
                                                               boolean isPhysical,
                                                               boolean isClassesAccepted);

  /**
   * Creates an <code>import static</code> statement for importing the specified member
   * from the specified class.
   *
   * @param aClass     the class from which the member is imported.
   * @param memberName the name of the member to import.
   * @return the created statement.
   * @throws IncorrectOperationException if the class is inner or local, or
   *                                     <code>memberName</code> is not a valid identifier.
   */
  @NotNull PsiImportStaticStatement createImportStaticStatement(@NotNull PsiClass aClass, @NotNull String memberName) throws IncorrectOperationException;

  /**
   * Creates a parameter list from the specified parameter names and types.
   *
   * @param names the array of names for the parameters to create.
   * @param types the array of types for the parameters to create.
   * @return the created parameter list.
   * @throws IncorrectOperationException if some of the parameter names or types are invalid.
   */
  @NotNull PsiParameterList createParameterList(@NotNull @NonNls String[] names, @NotNull PsiType[] types) throws IncorrectOperationException;

  /**
   * Creates a reference list element from the specified array of references.
   *
   * @param references the array of references to include in the list.
   * @return the created reference list.
   * @throws IncorrectOperationException if some of the references are invalid.
   */
  @NotNull PsiReferenceList createReferenceList(@NotNull PsiJavaCodeReferenceElement[] references) throws IncorrectOperationException;

  @NotNull
  PsiSubstitutor createRawSubstitutor(@NotNull PsiSubstitutor baseSubstitutor, @NotNull PsiTypeParameter[] typeParameters);

  /**
   * Create a lightweight PsiElement of given element type in a lightweight non-physical PsiFile (aka DummyHolder) in a given context.
   * Element type's language should have a parser definition which supports parsing for this element type (first
   * parameter in {@link com.intellij.lang.PsiParser#parse(com.intellij.psi.tree.IElementType, com.intellij.lang.PsiBuilder)}.
   * @param text text to parse
   * @param type node type
   * @param context context
   * @return PsiElement of the desired element type
   */
  @NotNull
  PsiElement createDummyHolder(@NotNull String text, @NotNull IElementType type, @Nullable PsiElement context);
}
