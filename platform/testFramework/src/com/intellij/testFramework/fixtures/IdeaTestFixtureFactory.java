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

package com.intellij.testFramework.fixtures;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.builders.ModuleFixtureBuilder;
import org.jetbrains.annotations.Nullable;

/**
 * This is to be provided by IDEA and not by plugin authors.
 */
public abstract class IdeaTestFixtureFactory {
  private static final IdeaTestFixtureFactory ourInstance;

  static {
    try {
      final Class<?> aClass = Class.forName("com.intellij.testFramework.fixtures.impl.IdeaTestFixtureFactoryImpl");
      ourInstance = (IdeaTestFixtureFactory)aClass.newInstance();
    }
    catch (Exception e) {
      throw new RuntimeException("Can't instnatiate factory", e);
    }
  }

  public static IdeaTestFixtureFactory getFixtureFactory() {
    return ourInstance;
  }

  /**
   * @param aClass test fixture builder interface class
   * @param implClass implementation class. Should have accessible constructor, which
   * may take {@link com.intellij.testFramework.fixtures.TestFixtureBuilder} as argument.
   */
  public abstract <T extends ModuleFixtureBuilder> void registerFixtureBuilder(Class<T> aClass, Class<? extends T> implClass);

  public abstract void registerFixtureBuilder(Class<? extends ModuleFixtureBuilder> aClass, String implClassName);

  public abstract TestFixtureBuilder<IdeaProjectTestFixture> createFixtureBuilder();

  public abstract TestFixtureBuilder<IdeaProjectTestFixture> createLightFixtureBuilder();

  public abstract TestFixtureBuilder<IdeaProjectTestFixture> createLightFixtureBuilder(@Nullable LightProjectDescriptor projectDescriptor);

  public abstract CodeInsightTestFixture createCodeInsightFixture(IdeaProjectTestFixture projectFixture);

  public abstract CodeInsightTestFixture createCodeInsightFixture(IdeaProjectTestFixture projectFixture, TempDirTestFixture tempDirFixture);

  public abstract TempDirTestFixture createTempDirTestFixture();
}
