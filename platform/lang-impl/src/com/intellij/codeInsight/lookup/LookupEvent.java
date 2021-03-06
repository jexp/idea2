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

package com.intellij.codeInsight.lookup;

import org.jetbrains.annotations.Nullable;

import java.util.EventObject;

public class LookupEvent extends EventObject {

  private final Lookup myLookup;
  private final LookupElement myItem;
  private final char myCompletionChar;

  public LookupEvent(Lookup lookup, LookupElement item){
    this(lookup, item, (char)0);
  }

  public LookupEvent(Lookup lookup, LookupElement item, char completionChar){
    super(lookup);
    myLookup = lookup;
    myItem = item;
    myCompletionChar = completionChar;
  }

  public Lookup getLookup(){
    return myLookup;
  }

  @Nullable("in case ENTER was pressed when no suggestions were available")
  public LookupElement getItem(){
    return myItem;
  }

  public char getCompletionChar(){
    return myCompletionChar;
  }
}
