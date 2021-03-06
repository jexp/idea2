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
package com.intellij.util.text;

import com.intellij.openapi.util.TextRange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.List;

public class CharArrayUtil {
  private static final int GET_CHARS_THRESHOLD = 10;

  public static void getChars(CharSequence src, char[] dst, int dstOffset) {
    getChars(src, dst, dstOffset, src.length());
  }

  public static void getChars(CharSequence src, char[] dst, int dstOffset, int len) {
    if (len >= GET_CHARS_THRESHOLD) {
      if (src instanceof String) {
        ((String)src).getChars(0, len, dst, dstOffset);
        return;
      }
      else if (src instanceof CharBuffer) {
        final CharBuffer buffer = (CharBuffer)src;
        final int i = buffer.position();
        buffer.get(dst, dstOffset, len);
        buffer.position(i);
        return;
      }
      else if (src instanceof CharSequenceBackedByArray) {
        ((CharSequenceBackedByArray)src.subSequence(0, len)).getChars(dst, dstOffset);
        return;
      }
      else if (src instanceof StringBuffer) {
        ((StringBuffer)src).getChars(0, len, dst, dstOffset);
        return;
      }
      else if (src instanceof StringBuilder) {
        ((StringBuilder)src).getChars(0, len, dst, dstOffset);
        return;
      }
    }

    for (int i = 0; i < len; i++) {
      dst[i + dstOffset] = src.charAt(i);
    }
  }

  public static char[] fromSequenceStrict(CharSequence seq) {
    char[] chars = fromSequence(seq);
    if (seq.length() == chars.length) return chars;
    char[] strictchars = new char[seq.length()];
    System.arraycopy(chars, 0, strictchars, 0, seq.length());
    return strictchars;
  }

  public static char[] fromSequenceWithoutCopying(CharSequence seq) {
    if (seq instanceof CharSequenceBackedByArray) {
      return ((CharSequenceBackedByArray)seq).getChars();
    }

    if (seq instanceof CharBuffer) {
      final CharBuffer buffer = (CharBuffer)seq;
      if (buffer.hasArray() && !buffer.isReadOnly() && buffer.arrayOffset() == 0) {
        return buffer.array();
      }
    }

    return null;
  }

  public static char[] fromSequence(CharSequence seq) {
    if (seq instanceof CharSequenceBackedByArray) {
      return ((CharSequenceBackedByArray)seq).getChars();
    }

    if (seq instanceof CharBuffer) {
      final CharBuffer buffer = (CharBuffer)seq;
      if (buffer.hasArray() && !buffer.isReadOnly() && buffer.arrayOffset() == 0) {
        final char[] bufArray = buffer.array();
        /* return larger array. Clients may use seq.length() to calculate correct processing range.
        if (bufArray.length == seq.length())
        */ return bufArray;
      }

      char[] chars = new char[seq.length()];
      buffer.position(0);
      buffer.get(chars);
      buffer.position(0);
      return chars;
    }

    if (seq instanceof StringBuffer) {
      char[] chars = new char[seq.length()];
      ((StringBuffer)seq).getChars(0, seq.length(), chars, 0);
      return chars;
    }

    if (seq instanceof String) {
      char[] chars = new char[seq.length()];
      ((String)seq).getChars(0, seq.length(), chars, 0);
      return chars;
    }

    return seq.toString().toCharArray();
  }

  public static int shiftForward(CharSequence buffer, int offset, String chars) {
    while (true) {
      if (offset >= buffer.length()) break;
      char c = buffer.charAt(offset);
      int i;      
      for (i = 0; i < chars.length(); i++) {
        if (c == chars.charAt(i)) break;
      }
      if (i == chars.length()) break;
      offset++;
    }
    return offset;
  }

  public static int shiftForwardCarefully(CharSequence buffer, int offset, String chars) {
    if (offset + 1 >= buffer.length()) return offset;
    if (!isSuitable(chars, buffer.charAt(offset))) return offset;
    offset++;
    while (true) {
      if (offset >= buffer.length()) return offset - 1;
      char c = buffer.charAt(offset);
      if (!isSuitable(chars, c)) return offset - 1;
      offset++;
    }
  }

  private static boolean isSuitable(final String chars, final char c) {
    int i;
    for (i = 0; i < chars.length(); i++) {
      if (c == chars.charAt(i)) return true;
    }
    return false;
  }

  public static int shiftForward(char[] buffer, int offset, String chars) {
    return shiftForward(new CharArrayCharSequence(buffer), offset, chars);
  }

  public static int shiftBackward(CharSequence buffer, int offset, String chars) {
    if (offset >= buffer.length()) return offset;

    while (true) {
      if (offset < 0) break;
      char c = buffer.charAt(offset);
      int i;
      for (i = 0; i < chars.length(); i++) {
        if (c == chars.charAt(i)) break;
      }
      if (i == chars.length()) break;
      offset--;
    }
    return offset;
  }

  public static int shiftBackward(char[] buffer, int offset, String chars) {
    return shiftBackward(new CharArrayCharSequence(buffer), offset, chars);
  }

  public static int shiftForwardUntil(char[] buffer, int offset, String chars) {
    return shiftForwardUntil(new CharArrayCharSequence(buffer), offset, chars);
  }

  public static int shiftForwardUntil(CharSequence buffer, int offset, String chars) {
    while (true) {
      if (offset >= buffer.length()) break;
      char c = buffer.charAt(offset);
      int i;
      for (i = 0; i < chars.length(); i++) {
        if (c == chars.charAt(i)) break;
      }
      if (i < chars.length()) break;
      offset++;
    }
    return offset;
  }

  public static int shiftBackwardUntil(char[] buffer, int offset, String chars) {
    return shiftBackwardUntil(new CharArrayCharSequence(buffer), offset, chars);
  }

  public static int shiftBackwardUntil(CharSequence buffer, int offset, String chars) {
    if (offset >= buffer.length()) return offset;
    while (true) {
      if (offset < 0) break;
      char c = buffer.charAt(offset);
      int i;
      for (i = 0; i < chars.length(); i++) {
        if (c == chars.charAt(i)) break;
      }
      if (i < chars.length()) break;
      offset--;
    }
    return offset;
  }

  public static boolean regionMatches(char[] buffer, int offset, int bufferEnd, CharSequence s) {
    final int len = s.length();
    if (offset + len > bufferEnd) return false;
    if (offset < 0) return false;
    for (int i = 0; i < len; i++) {
      if (buffer[offset + i] != s.charAt(i)) return false;
    }
    return true;
  }

  public static boolean regionMatches(CharSequence buffer, int offset, int bufferEnd, CharSequence s) {
    final int len = s.length();
    if (offset + len > bufferEnd) return false;
    if (offset < 0) return false;
    
    //if (buffer instanceof String && s instanceof String) {
    //  return ((String)buffer).regionMatches(offset, (String)s, 0, len);
    //}
    
    for (int i = 0; i < len; i++) {
      if (buffer.charAt(offset + i) != s.charAt(i)) return false;
    }
    return true;
  }

  public static boolean regionMatches(CharSequence buffer, int offset, CharSequence s) {
    if (offset + s.length() > buffer.length()) return false;
    if (offset < 0) return false;
    for (int i = 0; i < s.length(); i++) {
      if (buffer.charAt(offset + i) != s.charAt(i)) return false;
    }
    return true;
  }

  public static boolean equals(char[] buffer1, int start1, int end1, char[] buffer2, int start2, int end2) {
    if (end1 - start1 != end2 - start2) return false;
    for (int i = start1; i < end1; i++) {
      if (buffer1[i] != buffer2[i - start1 + start2]) return false;
    }
    return true;
  }

  public static int indexOf(char[] buffer, String pattern, int fromIndex) {
    char[] chars = pattern.toCharArray();
    int limit = buffer.length - chars.length;
    if (fromIndex < 0) {
      fromIndex = 0;
    }
    SearchLoop:
    for (int i = fromIndex; i < limit; i++) {
      for (int j = 0; j < chars.length; j++) {
        if (chars[j] != buffer[i + j]) continue SearchLoop;
      }
      return i;
    }
    return -1;
  }

  public static int indexOf(final CharSequence buffer, final CharSequence pattern, int fromIndex) {
    return indexOf(buffer, pattern, fromIndex, buffer.length());
  }

  public static int indexOf(final CharSequence buffer, final CharSequence pattern, int fromIndex, final int toIndex) {
    final int patternLength = pattern.length();
    int limit = toIndex - patternLength;
    if (fromIndex < 0) {
      fromIndex = 0;
    }
    SearchLoop:
    for (int i = fromIndex; i < limit; i++) {
      for (int j = 0; j < patternLength; j++) {
        if (pattern.charAt(j) != buffer.charAt(i + j)) continue SearchLoop;
      }
      return i;
    }
    return -1;
  }

  public static int lastIndexOf(CharSequence buffer, String pattern, int fromIndex) {
    char[] chars = pattern.toCharArray();
    int end = buffer.length() - chars.length;
    if (fromIndex > end) {
      fromIndex = end;
    }
    SearchLoop:
    for (int i = fromIndex; i >= 0; i--) {
      for (int j = 0; j < chars.length; j++) {
        if (chars[j] != buffer.charAt(i + j)) continue SearchLoop;
      }
      return i;
    }
    return -1;
  }

  public static int lastIndexOf(char[] buffer, String pattern, int fromIndex) {
    return lastIndexOf(new CharArrayCharSequence(buffer), pattern, fromIndex);
  }

  public static byte[] toByteArray(char[] chars) throws IOException {
    return toByteArray(chars, chars.length);
  }

  public static byte[] toByteArray(char[] chars, int size) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    OutputStreamWriter writer = new OutputStreamWriter(out);
    writer.write(chars, 0, size);
    writer.close();
    return out.toByteArray();
  }

  public static boolean containsOnlyWhiteSpaces(final CharSequence chars) {
    if (chars == null) return true;
    for (int i = 0; i < chars.length(); i++) {
      final char c = chars.charAt(i);
      if (c == ' ' || c == '\t' || c == '\n' || c == '\r') continue;
      return false;
    }
    return true;
  }

  public static boolean subArraysEqual(char[] ca1, int startOffset1, int endOffset1,char[] ca2, int startOffset2, int endOffset2) {
    if (endOffset1 - startOffset1 != endOffset2 - startOffset2) return false;
    for (int i = startOffset1; i < endOffset1; i++) {
      char c1 = ca1[i];
      char c2 = ca2[i - startOffset1 + startOffset2];
      if (c1 != c2) return false;
    }
    return true;
  }

  public static TextRange[] getIndents(CharSequence charsSequence, int shift) {
    List<TextRange> result = new ArrayList<TextRange>();
    int whitespaceEnd = -1;
    int lastTextFound = 0;
    for(int i = charsSequence.length() - 1; i >= 0; i--){
      final char charAt = charsSequence.charAt(i);
      final boolean isWhitespace = Character.isWhitespace(charAt);
      if(charAt == '\n'){
        result.add(new TextRange(i, (whitespaceEnd >= 0 ? whitespaceEnd : i) + 1).shiftRight(shift));
        whitespaceEnd = -1;
      }
      else if(whitespaceEnd >= 0 ){
        if(isWhitespace){
          continue;
        }
        else lastTextFound = result.size();
        whitespaceEnd = -1;
      }
      else if(isWhitespace){
        whitespaceEnd = i;
      } else {
        lastTextFound = result.size();
      }
    }
    if(whitespaceEnd > 0) result.add(new TextRange(0, whitespaceEnd + 1).shiftRight(shift));
    if(lastTextFound < result.size())
      result = result.subList(0, lastTextFound);
    return result.toArray(new TextRange[result.size()]);
  }

  public static boolean containLineBreaks(CharSequence seq) {
    return containLineBreaks(seq, 0, seq.length());
  }

  public static boolean containLineBreaks(CharSequence seq, int fromOffset, int endOffset) {
    if (seq == null) return false;
    for (int i = fromOffset; i < endOffset; i++) {
      final char c = seq.charAt(i);
      if (c == '\n' || c == '\r') return true;
    }
    return false;
  }
}
