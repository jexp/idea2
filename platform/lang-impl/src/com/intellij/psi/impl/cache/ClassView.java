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

package com.intellij.psi.impl.cache;

/**
 * @author max
 */ 
public interface ClassView extends DeclarationView {
  String getQualifiedName(long classId);
  boolean isInterface(long classId);
  boolean isAnonymous(long classId);
  boolean isAnnotationType (long classId);

  int getParametersListSize(long classId);
  String getParameterText(long classId, int parameterIdx);

  long[] getMethods(long classId);

  long[] getFields(long classId);

  long[] getInitializers(long classId);

  String getBaseClassReferenceText(long classId);
  boolean isInQualifiedNew(long classId);

  String[] getExtendsList(long classId);
  String[] getImplementsList(long classId);

  boolean isEnum(long classId);

  boolean isEnumConstantInitializer(long classId);
}
