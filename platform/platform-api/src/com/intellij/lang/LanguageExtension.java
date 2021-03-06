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
 * @author max
 */
package com.intellij.lang;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.KeyedExtensionCollector;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class LanguageExtension<T> extends KeyedExtensionCollector<T, Language> {
  private final T myDefaultImplementation;
  private final /* non static!!! */ Key<T> IN_LANGUAGE_CACHE = new Key<T>("EXTENSIONS_IN_LANGUAGE");

  public LanguageExtension(@NonNls final String epName) {
    this(epName, null);
  }

  public LanguageExtension(@NonNls final String epName, T defaultImplementation) {
    super(epName);
    myDefaultImplementation = defaultImplementation;
  }

  protected String keyToString(final Language key) {
    return key.getID();
  }

  public T forLanguage(Language l) {
    T cached = l.getUserData(IN_LANGUAGE_CACHE);
    if (cached != null) return cached;

    List<T> extensions = forKey(l);
    T result;
    if (extensions.isEmpty()) {

      Language base = l.getBaseLanguage();
      if (base != null) {
        result = forLanguage(base);
      }
      else {
        result = myDefaultImplementation;
      }
    }
    else {
      result = extensions.get(0);
    }

    l.putUserData(IN_LANGUAGE_CACHE, result);
    return result;
  }

  @NotNull
  public List<T> allForLanguage(Language l) {
    List<T> list = forKey(l);
    if (list.isEmpty()) {
      Language base = l.getBaseLanguage();
      if (base != null) {
        return allForLanguage(base);
      }
    }
    return list;
  }

  protected T getDefaultImplementation() {
    return myDefaultImplementation;
  }

  protected Key<T> getLanguageCache() {
    return IN_LANGUAGE_CACHE;
  }
}
