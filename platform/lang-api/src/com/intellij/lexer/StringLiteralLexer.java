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
package com.intellij.lexer;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.psi.tree.IElementType;

/**
 * @author max
 */
public class StringLiteralLexer extends LexerBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lexer.StringLiteralLexer");

  private static final short AFTER_FIRST_QUOTE = 1;
  private static final short AFTER_LAST_QUOTE = 2;

  public static final char NO_QUOTE_CHAR = (char)-1;

  private CharSequence myBuffer;
  private int myStart;
  private int myEnd;
  private int myState;
  private int myLastState;
  private int myBufferEnd;
  private char myQuoteChar;
  private IElementType myOriginalLiteralToken;
  private final boolean myCanEscapeEolOrFramingSpaces;
  private final String myAdditionalValidEscapes;
  private boolean mySeenEscapedSpacesOnly;

  public StringLiteralLexer(char quoteChar, final IElementType originalLiteralToken) {
    this(quoteChar, originalLiteralToken, false, null);
  }

  /**
   * @param canEscapeEolOrFramingSpaces true if following sequences are acceptable
   *    '\' in the end of the buffer (meaning escaped end of line) or
   *    '\ ' (escaped space) in the beginning and in the end of the buffer (meaning escaped space, to avoid auto trimming on load)
   */
  public StringLiteralLexer(char quoteChar, final IElementType originalLiteralToken, boolean canEscapeEolOrFramingSpaces, String additionalValidEscapes) {
    myQuoteChar = quoteChar;
    myOriginalLiteralToken = originalLiteralToken;
    myCanEscapeEolOrFramingSpaces = canEscapeEolOrFramingSpaces;
    myAdditionalValidEscapes = additionalValidEscapes;
  }

  public void start(CharSequence buffer, int startOffset, int endOffset, int initialState) {
    myBuffer = buffer;
    myStart = startOffset;
    if (myQuoteChar == NO_QUOTE_CHAR) {
      myState = AFTER_FIRST_QUOTE;
    }
    else {
      myState = initialState;
    }
    myLastState = initialState;
    myBufferEnd = endOffset;
    myEnd = locateToken(myStart);
    mySeenEscapedSpacesOnly = true;
  }

  public int getState() {
    return myLastState;
  }

  private static boolean isHexDigit(char c) {
    return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
  }

  public IElementType getTokenType() {
    if (myStart >= myEnd) return null;

    if (myBuffer.charAt(myStart) != '\\') {
      mySeenEscapedSpacesOnly = false;
      return myOriginalLiteralToken;
    }

    if (myStart + 1 >= myEnd) return StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN;
    char nextChar = myBuffer.charAt(myStart + 1);
    mySeenEscapedSpacesOnly &= nextChar == ' ';
    if (myCanEscapeEolOrFramingSpaces &&
        (nextChar == '\n' || nextChar == ' ' && (mySeenEscapedSpacesOnly || isTrailingSpace(myStart+2)))
      ) {
      return StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN;
    }
    if (nextChar == 'u') {
      for(int i = myStart + 2; i < myStart + 6; i++) {
        if (i >= myEnd || !isHexDigit(myBuffer.charAt(i))) return StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN;
      }
      return StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN;
    }

    switch (nextChar) {
      case 'n':
      case 'r':
      case 'b':
      case 't':
      case 'f':
      case '\'':
      case '\"':
      case '\\':
      case '0':
      case '1':
      case '2':
      case '3':
      case '4':
      case '5':
      case '6':
      case '7':
        return StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN;
    }
    if (myAdditionalValidEscapes != null && myAdditionalValidEscapes.indexOf(nextChar) != -1) {
      return StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN;
    }

    return StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN;
  }

  // all subsequent chars are escaped spaces
  private boolean isTrailingSpace(final int start) {
    for (int i=start;i<myBufferEnd;i+=2) {
      final char c = myBuffer.charAt(i);
      if (c != '\\') return false;
      if (i==myBufferEnd-1) return false;
      if (myBuffer.charAt(i+1) != ' ') return false;
    }
    return true;
  }

  public int getTokenStart() {
    return myStart;
  }

  public int getTokenEnd() {
    return myEnd;
  }

  private int locateToken(int start) {
    if (start == myBufferEnd) {
      myState = AFTER_LAST_QUOTE;
    }
    if (myState == AFTER_LAST_QUOTE) return start;
    int i = start;
    if (myBuffer.charAt(i) == '\\') {
      LOG.assertTrue(myState == AFTER_FIRST_QUOTE);
      i++;
      if (i == myBufferEnd || myBuffer.charAt(i) == '\n' && !myCanEscapeEolOrFramingSpaces) {
        myState = AFTER_LAST_QUOTE;
        return i;
      }

      if (myBuffer.charAt(i) >= '0' && myBuffer.charAt(i) <= '7') {
        char first = myBuffer.charAt(i);
        i++;
        if (i < myBufferEnd && myBuffer.charAt(i) >= '0' && myBuffer.charAt(i) <= '7') {
          i++;
          if (i < myBufferEnd && first <= '3' && myBuffer.charAt(i) >= '0' && myBuffer.charAt(i) <= '7') {
            i++;
          }
        }
        return i;
      }

      if (myBuffer.charAt(i) == 'u') {
        i++;
        for (; i < start + 6; i++) {
          if (i == myBufferEnd || myBuffer.charAt(i) == '\n' || myBuffer.charAt(i) == myQuoteChar) {
            return i;
          }
        }
        return i;
      }
      else {
        return i + 1;
      }
    }
    else {
      LOG.assertTrue(myState == AFTER_FIRST_QUOTE || myBuffer.charAt(i) == myQuoteChar);
      while (i < myBufferEnd) {
        if (myBuffer.charAt(i) == '\\') {
          return i;
        }
        //if (myBuffer[i] == '\n') {
        //  myState = AFTER_LAST_QUOTE;
        //  return i;
        //}
        if (myState == AFTER_FIRST_QUOTE && myBuffer.charAt(i) == myQuoteChar) {
          myState = AFTER_LAST_QUOTE;
          return i + 1;
        }
        i++;
        myState = AFTER_FIRST_QUOTE;
      }
    }

    return i;
  }

  public void advance() {
    myLastState = myState;
    myStart = myEnd;
    myEnd = locateToken(myStart);
  }

  public CharSequence getBufferSequence() {
    return myBuffer;
  }

  public int getBufferEnd() {
    return myBufferEnd;
  }

}
