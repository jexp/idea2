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
package com.intellij.util.xml.impl;

import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.ReflectionCache;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.xml.*;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.*;

/**
 * @author peter
 */
public class StaticGenericInfoBuilder {
  private static final Set ADDER_PARAMETER_TYPES = new THashSet<Class>(Arrays.asList(Class.class, int.class));
  private final Class myClass;
  private final Type myType;
  private final MultiValuesMap<XmlName, JavaMethod> myCollectionGetters = new MultiValuesMap<XmlName, JavaMethod>();
  private final MultiValuesMap<XmlName, JavaMethod> myCollectionAdders = new MultiValuesMap<XmlName, JavaMethod>();
  private final MultiValuesMap<XmlName, JavaMethod> myCollectionClassAdders = new MultiValuesMap<XmlName, JavaMethod>();
  private final MultiValuesMap<XmlName, JavaMethod> myCollectionIndexAdders = new MultiValuesMap<XmlName, JavaMethod>();
  private final MultiValuesMap<XmlName, JavaMethod> myCollectionIndexClassAdders = new MultiValuesMap<XmlName, JavaMethod>();
  private final MultiValuesMap<XmlName, JavaMethod> myCollectionClassIndexAdders = new MultiValuesMap<XmlName, JavaMethod>();
  private final Map<XmlName, Type> myCollectionChildrenTypes = new THashMap<XmlName, Type>();
  private final Map<JavaMethodSignature, String[]> myCompositeCollectionGetters = new THashMap<JavaMethodSignature, String[]>();
  private final Map<JavaMethodSignature, Pair<String,String[]>> myCompositeCollectionAdders = new THashMap<JavaMethodSignature, Pair<String,String[]>>();
  private final FactoryMap<XmlName, TIntObjectHashMap<Collection<JavaMethod>>> myFixedChildrenGetters = new FactoryMap<XmlName, TIntObjectHashMap<Collection<JavaMethod>>>() {
    protected TIntObjectHashMap<Collection<JavaMethod>> create(final XmlName key) {
      return new TIntObjectHashMap<Collection<JavaMethod>>();
    }
  };
  private final Map<JavaMethodSignature, AttributeChildDescriptionImpl> myAttributes = new THashMap<JavaMethodSignature, AttributeChildDescriptionImpl>();

  private boolean myValueElement;
  private JavaMethod myNameValueGetter;
  private JavaMethod myCustomChildrenGetter;

  public StaticGenericInfoBuilder(final DomManagerImpl domManager, final Class aClass, Type type) {
    myClass = aClass;
    myType = type;

    final Set<JavaMethod> methods = new THashSet<JavaMethod>();
    for (final Method method : ReflectionCache.getMethods(myClass)) {
      methods.add(JavaMethod.getMethod(myClass, method));
    }
    for (final JavaMethod method : methods) {
      if (DomImplUtil.isGetter(method) && method.getAnnotation(NameValue.class) != null) {
        myNameValueGetter = method;
        break;
      }
    }

    {
      final Class implClass = domManager.getImplementation(myClass);
      if (implClass != null) {
        for (Method method : ReflectionCache.getMethods(implClass)) {
          final int modifiers = method.getModifiers();
          if (!Modifier.isAbstract(modifiers) && !Modifier.isVolatile(modifiers)) {
            final JavaMethodSignature signature = JavaMethodSignature.getSignature(method);
            if (signature.findMethod(myClass) != null) {
              methods.remove(JavaMethod.getMethod(myClass, signature));
            }
          }
        }
      }
    }

    for (Iterator<JavaMethod> iterator = methods.iterator(); iterator.hasNext();) {
      final JavaMethod method = iterator.next();
      if (isCoreMethod(method) || DomImplUtil.isTagValueSetter(method) || method.getAnnotation(PropertyAccessor.class) != null) {
        iterator.remove();
      }
    }

    for (Iterator<JavaMethod> iterator = methods.iterator(); iterator.hasNext();) {
      final JavaMethod method = iterator.next();
      if (DomImplUtil.isGetter(method) && processGetterMethod(method)) {
        iterator.remove();
      }
    }

    for (Iterator<JavaMethod> iterator = methods.iterator(); iterator.hasNext();) {
      final JavaMethod method = iterator.next();
      final SubTagsList subTagsList = method.getAnnotation(SubTagsList.class);
      if (subTagsList != null && method.getName().startsWith("add")) {
        final String localName = subTagsList.tagName();
        assert StringUtil.isNotEmpty(localName);
        final String[] set = subTagsList.value();
        assert Arrays.asList(set).contains(localName);
        myCompositeCollectionAdders.put(method.getSignature(), Pair.create(localName, set));
        iterator.remove();
      }
      else if (isAddMethod(method)) {
        final XmlName xmlName = extractTagName(method, "add");
        if (myCollectionGetters.containsKey(xmlName)) {
          MultiValuesMap<XmlName, JavaMethod> adders = getAddersMap(method);
          if (adders != null) {
            adders.put(xmlName, method);
            iterator.remove();
          }
        }
      }
    }

    if (false) {
      if (!methods.isEmpty()) {
        StringBuilder sb = new StringBuilder(myClass + " should provide the following implementations:");
        for (JavaMethod method : methods) {
          sb.append("\n  ");
          sb.append(method);
        }
        assert false : sb.toString();
        //System.out.println(sb.toString());
      }
    }
  }

  @Nullable
  private MultiValuesMap<XmlName, JavaMethod> getAddersMap(final JavaMethod method) {
    final Class<?>[] parameterTypes = method.getParameterTypes();
    switch (parameterTypes.length) {
      case 0:
        return myCollectionAdders;
      case 1:
        if (Class.class.equals(parameterTypes[0])) return myCollectionClassAdders;
        if (isInt(parameterTypes[0])) return myCollectionIndexAdders;
        break;
      case 2:
        if (isIndexClassAdder(parameterTypes[0], parameterTypes[1])) return myCollectionIndexClassAdders;
        if (isIndexClassAdder(parameterTypes[1], parameterTypes[0])) return myCollectionClassIndexAdders;
    }
    return null;
  }

  private static boolean isIndexClassAdder(final Class<?> first, final Class<?> second) {
    return isInt(first) && second.equals(Class.class);
  }

  private static boolean isInt(final Class<?> aClass) {
    return aClass.equals(int.class) || aClass.equals(Integer.class);
  }

  private static Set<XmlName> getXmlNames(final SubTagsList subTagsList) {
    return ContainerUtil.map2Set(subTagsList.value(), new Function<String, XmlName>() {
      public XmlName fun(String s) {
        return new XmlName(s);
      }
    });
  }


  private boolean isAddMethod(JavaMethod method) {
    final XmlName tagName = extractTagName(method, "add");
    if (tagName == null) return false;

    final Type type = myCollectionChildrenTypes.get(tagName);
    if (type == null || !ReflectionUtil.getRawType(type).isAssignableFrom(method.getReturnType())) return false;

    return ADDER_PARAMETER_TYPES.containsAll(Arrays.asList(method.getParameterTypes()));
  }

  @Nullable
  private XmlName extractTagName(JavaMethod method, @NonNls String prefix) {
    final String name = method.getName();
    if (!name.startsWith(prefix)) return null;

    final SubTagList subTagAnnotation = method.getAnnotation(SubTagList.class);
    if (subTagAnnotation != null && !StringUtil.isEmpty(subTagAnnotation.value())) {
      return DomImplUtil.createXmlName(subTagAnnotation.value(), method);
    }

    final String tagName = getNameStrategy(false).convertName(name.substring(prefix.length()));
    return StringUtil.isEmpty(tagName) ? null : DomImplUtil.createXmlName(tagName, method);
  }

  private static boolean isDomElement(final Type type) {
    return type != null && DomElement.class.isAssignableFrom(ReflectionUtil.getRawType(type));
  }

  private boolean processGetterMethod(final JavaMethod method) {
    if (DomImplUtil.isTagValueGetter(method)) {
      myValueElement = true;
      return true;
    }

    final Class returnType = method.getReturnType();
    final boolean isAttributeValueMethod = GenericAttributeValue.class.isAssignableFrom(returnType);
    final JavaMethodSignature signature = method.getSignature();
    final Attribute annotation = signature.findAnnotation(Attribute.class, myClass);
    final boolean isAttributeMethod = annotation != null || isAttributeValueMethod;
    if (annotation != null) {
      assert
        isAttributeValueMethod || GenericAttributeValue.class.isAssignableFrom(returnType) :
        method + " should return GenericAttributeValue";
    }
    if (isAttributeMethod) {
      final String s = annotation == null ? null : annotation.value();
      String attributeName = StringUtil.isEmpty(s) ? getNameFromMethod(signature, true) : s;
      assert attributeName != null && StringUtil.isNotEmpty(attributeName) : "Can't guess attribute name from method name: " + method.getName();
      final XmlName attrName = DomImplUtil.createXmlName(attributeName, method);
      myAttributes.put(signature, new AttributeChildDescriptionImpl(attrName, method));
      return true;
    }

    if (isDomElement(returnType)) {
      final String qname = getSubTagName(signature);
      if (qname != null) {
        final XmlName xmlName = DomImplUtil.createXmlName(qname, method);
        assert !myCollectionChildrenTypes.containsKey(xmlName) : "Collection and fixed children cannot intersect: " + qname;
        int index = 0;
        final SubTag subTagAnnotation = signature.findAnnotation(SubTag.class, myClass);
        if (subTagAnnotation != null && subTagAnnotation.index() != 0) {
          index = subTagAnnotation.index();
        }
        final TIntObjectHashMap<Collection<JavaMethod>> map = myFixedChildrenGetters.get(xmlName);
        Collection<JavaMethod> methods = map.get(index);
        if (methods == null) {
          map.put(index, methods = new SmartList<JavaMethod>());
        }
        methods.add(method);
        return true;
      }
    }

    final Type type = DomReflectionUtil.extractCollectionElementType(method.getGenericReturnType());
    if (isDomElement(type)) {
      final CustomChildren customChildren = method.getAnnotation(CustomChildren.class);
      if (customChildren != null) {
        myCustomChildrenGetter = method;
        return true;
      }

      final SubTagsList subTagsList = method.getAnnotation(SubTagsList.class);
      if (subTagsList != null) {
        myCompositeCollectionGetters.put(signature, subTagsList.value());
        return true;
      }

      final String qname = getSubTagNameForCollection(signature);
      if (qname != null) {
        XmlName xmlName = DomImplUtil.createXmlName(qname, type, method);
        assert !myFixedChildrenGetters.containsKey(xmlName) : "Collection and fixed children cannot intersect: " + qname;
        myCollectionChildrenTypes.put(xmlName, type);
        myCollectionGetters.put(xmlName, method);
        return true;
      }
    }

    return false;
  }

  private static boolean isCoreMethod(final JavaMethod method) {
    if (method.getSignature().findMethod(DomElement.class) != null) return true;

    final Class<?> aClass = method.getDeclaringClass();
    return aClass.equals(GenericAttributeValue.class) || aClass.equals(GenericDomValue.class) && "getConverter".equals(method.getName());
  }

  @Nullable
  private String getSubTagName(final JavaMethodSignature method) {
    final SubTag subTagAnnotation = method.findAnnotation(SubTag.class, myClass);
    if (subTagAnnotation == null || StringUtil.isEmpty(subTagAnnotation.value())) {
      return getNameFromMethod(method, false);
    }
    return subTagAnnotation.value();
  }

  @Nullable
  private String getSubTagNameForCollection(final JavaMethodSignature method) {
    final SubTagList subTagList = method.findAnnotation(SubTagList.class, myClass);
    if (subTagList == null || StringUtil.isEmpty(subTagList.value())) {
      final String propertyName = getPropertyName(method);
      if (propertyName != null) {
        final String singular = StringUtil.unpluralize(propertyName);
        assert singular != null : "Can't unpluralize: " + propertyName;
        return getNameStrategy(false).convertName(singular);
      }
      else {
        return null;
      }
    }
    return subTagList.value();
  }

  @Nullable
  private String getNameFromMethod(final JavaMethodSignature method, boolean isAttribute) {
    final String propertyName = getPropertyName(method);
    return propertyName == null ? null : getNameStrategy(isAttribute).convertName(propertyName);
  }

  @Nullable
  private static String getPropertyName(JavaMethodSignature method) {
    return StringUtil.getPropertyName(method.getMethodName());
  }

  @NotNull
  private DomNameStrategy getNameStrategy(boolean isAttribute) {
    final DomNameStrategy strategy = DomImplUtil.getDomNameStrategy(ReflectionUtil.getRawType(myClass), isAttribute);
    return strategy != null ? strategy : DomNameStrategy.HYPHEN_STRATEGY;
  }

  final JavaMethod getCustomChildrenGetter() {
    return myCustomChildrenGetter;
  }

  final Map<JavaMethodSignature, AttributeChildDescriptionImpl> getAttributes() {
    return myAttributes;
  }

  final Map<JavaMethodSignature, Pair<FixedChildDescriptionImpl, Integer>> getFixedGetters() {
    final Map<JavaMethodSignature, Pair<FixedChildDescriptionImpl, Integer>> map = new THashMap<JavaMethodSignature, Pair<FixedChildDescriptionImpl, Integer>>();
    final Set<XmlName> names = myFixedChildrenGetters.keySet();
    for (final XmlName name : names) {
      final TIntObjectHashMap<Collection<JavaMethod>> map1 = myFixedChildrenGetters.get(name);
      int max = 0;
      final int[] ints = map1.keys();
      for (final int i : ints) {
        max = Math.max(max, i);
      }
      int count = max + 1;
      final Collection<JavaMethod>[] getters = new Collection[count];
      for (final int i : ints) {
        getters[i] = map1.get(i);
      }
      final FixedChildDescriptionImpl description = new FixedChildDescriptionImpl(name, map1.get(0).iterator().next().getGenericReturnType(), count, getters);
      for (int i = 0; i < getters.length; i++) {
        final Collection<JavaMethod> collection = getters[i];
        for (final JavaMethod method : collection) {
          map.put(method.getSignature(), Pair.create(description, i));
        }
      }
    }
    return map;
  }

  final Map<JavaMethodSignature, CollectionChildDescriptionImpl> getCollectionGetters() {
    final Map<JavaMethodSignature, CollectionChildDescriptionImpl> getters = new THashMap<JavaMethodSignature, CollectionChildDescriptionImpl>();
    for (final XmlName xmlName : myCollectionGetters.keySet()) {
      final Collection<JavaMethod> collGetters = myCollectionGetters.get(xmlName);
      final JavaMethod method = collGetters.iterator().next();


      final CollectionChildDescriptionImpl description = new CollectionChildDescriptionImpl(xmlName, DomReflectionUtil.extractCollectionElementType(method.getGenericReturnType()),
                                                                                            myCollectionAdders.get(xmlName),
                                                                                            myCollectionClassAdders.get(xmlName),
                                                                                            collGetters,
                                                                                            myCollectionIndexAdders.get(xmlName),
                                                                                            myCollectionIndexClassAdders.get(xmlName),
                                                                                            myCollectionClassIndexAdders.get(xmlName));
      for (final JavaMethod getter : collGetters) {
        getters.put(getter.getSignature(), description);
      }
    }

    return getters;
  }

  final Map<JavaMethodSignature, Pair<String, String[]>> getCompositeCollectionAdders() {
    return myCompositeCollectionAdders;
  }

  final Map<JavaMethodSignature, String[]> getCompositeCollectionGetters() {
    return myCompositeCollectionGetters;
  }

  public JavaMethod getNameValueGetter() {
    return myNameValueGetter;
  }

  public boolean isValueElement() {
    return myValueElement;
  }
}
