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

package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CommentUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.highlighter.custom.CustomFileTypeLexer;
import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageCommenters;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.Indent;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.containers.IntArrayList;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CommentByBlockCommentHandler implements CodeInsightActionHandler {
  private Project myProject;
  private Editor myEditor;
  private PsiFile myFile;
  private Document myDocument;

  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    myProject = project;
    myEditor = editor;
    myFile = file;

    myDocument = editor.getDocument();

    if (!FileDocumentManager.getInstance().requestWriting(myDocument, project)) {
      return;
    }
    FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.comment.block");
    final Commenter commenter = findCommenter(myFile, myEditor);
    if (commenter == null) return;

    final SelectionModel selectionModel = myEditor.getSelectionModel();

    final String prefix = commenter.getBlockCommentPrefix();
    final String suffix = commenter.getBlockCommentSuffix();
    if (prefix == null || suffix == null) return;

    TextRange commentedRange = findCommentedRange(commenter);
    if (commentedRange != null) {
      final int commentStart = commentedRange.getStartOffset();
      final int commentEnd = commentedRange.getEndOffset();
      int selectionStart = commentStart;
      int selectionEnd = commentEnd;
      if (selectionModel.hasSelection()) {
        selectionStart = selectionModel.getSelectionStart();
        selectionEnd = selectionModel.getSelectionEnd();
      }
      if ((commentStart < selectionStart || commentStart >= selectionEnd) && (commentEnd <= selectionStart || commentEnd > selectionEnd)) {
        commentRange(selectionStart, selectionEnd, prefix, suffix, commenter);
      }
      else {
        uncommentRange(commentedRange, trim(prefix), trim(suffix), commenter);
      }
    }
    else {
      if (selectionModel.hasBlockSelection()) {
        final LogicalPosition start = selectionModel.getBlockStart();
        final LogicalPosition end = selectionModel.getBlockEnd();

        assert start != null;
        assert end != null;

        int startColumn = Math.min(start.column, end.column);
        int endColumn = Math.max(start.column, end.column);
        int startLine = Math.min(start.line, end.line);
        int endLine = Math.max(start.line, end.line);

        for (int i = startLine; i <= endLine; i++) {
          editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(i, endColumn));
          EditorModificationUtil.insertStringAtCaret(editor, suffix, true, true);
        }

        for (int i = startLine; i <= endLine; i++) {
          editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(i, startColumn));
          EditorModificationUtil.insertStringAtCaret(editor, prefix, true, true);
        }
      }
      else if (selectionModel.hasSelection()) {
        int selectionStart = selectionModel.getSelectionStart();
        int selectionEnd = selectionModel.getSelectionEnd();
        commentRange(selectionStart, selectionEnd, prefix, suffix, commenter);
      }
      else {
        EditorUtil.fillVirtualSpaceUntilCaret(editor);
        int caretOffset = myEditor.getCaretModel().getOffset();
        myDocument.insertString(caretOffset, prefix + suffix);
        myEditor.getCaretModel().moveToOffset(caretOffset + prefix.length());
      }
    }
  }

  @Nullable
  private static String trim(String s) {
    return s == null ? null : s.trim();
  }

  private boolean testSelectionForNonComments() {
    SelectionModel model = myEditor.getSelectionModel();
    if (!model.hasSelection()) {
      return true;
    }
    TextRange range = new TextRange(model.getSelectionStart(), model.getSelectionEnd() - 1);
    for (PsiElement element = myFile.findElementAt(range.getStartOffset());
         element != null && range.intersects(element.getTextRange());
         element = element.getNextSibling()) {
      if (!(element instanceof PsiWhiteSpace || PsiTreeUtil.getParentOfType(element, PsiComment.class, false) != null)) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  private TextRange findCommentedRange(final Commenter commenter) {
    final CharSequence text = myDocument.getCharsSequence();
    final FileType fileType = myFile.getFileType();
    if (fileType instanceof AbstractFileType) {
      Lexer lexer = new CustomFileTypeLexer(((AbstractFileType)fileType).getSyntaxTable());
      final int caretOffset = myEditor.getCaretModel().getOffset();
      int commentStart = CharArrayUtil.lastIndexOf(text, commenter.getBlockCommentPrefix(), caretOffset);
      if (commentStart == -1) return null;

      lexer.start(text, commentStart, text.length());
      if (lexer.getTokenType() == CustomHighlighterTokenType.MULTI_LINE_COMMENT && lexer.getTokenEnd() >= caretOffset) {
        return new TextRange(commentStart, lexer.getTokenEnd());
      }
      return null;
    }

    final String prefix = trim(commenter.getBlockCommentPrefix());
    final String suffix = trim(commenter.getBlockCommentSuffix());
    if (prefix == null || suffix == null) return null;

    if (!testSelectionForNonComments()) {
      return null;
    }

    TextRange commentedRange = getSelectedComments(text, prefix, suffix);
    if (commentedRange == null) {
      PsiElement comment = findCommentAtCaret();
      if (comment != null) {
        String commentText = comment.getText();
        if (commentText.startsWith(prefix) && commentText.endsWith(suffix)) {
          commentedRange = comment.getTextRange();
        }
      }
    }
    return commentedRange;
  }

  @Nullable
  private TextRange getSelectedComments(CharSequence text, String prefix, String suffix) {
    TextRange commentedRange = null;
    final SelectionModel selectionModel = myEditor.getSelectionModel();
    if (selectionModel.hasSelection()) {
      int selectionStart = selectionModel.getSelectionStart();
      selectionStart = CharArrayUtil.shiftForward(text, selectionStart, " \t\n");
      int selectionEnd = selectionModel.getSelectionEnd() - 1;
      selectionEnd = CharArrayUtil.shiftBackward(text, selectionEnd, " \t\n") + 1;
      if (selectionEnd - selectionStart >= prefix.length() + suffix.length() &&
          CharArrayUtil.regionMatches(text, selectionStart, prefix) &&
          CharArrayUtil.regionMatches(text, selectionEnd - suffix.length(), suffix)) {
        commentedRange = new TextRange(selectionStart, selectionEnd);
      }
    }
    return commentedRange;
  }

  private static Commenter findCommenter(PsiFile file, Editor editor) {
    final FileType fileType = file.getFileType();
    if (fileType instanceof AbstractFileType) {
      return ((AbstractFileType)fileType).getCommenter();
    }

    Language lang = PsiUtilBase.getLanguageInEditor(editor, file.getProject());

    return getCommenter(file, editor, lang);
  }

  public static Commenter getCommenter(PsiFile file, Editor editor, Language lang) {
    if (lang == null || LanguageCommenters.INSTANCE.forLanguage(lang) == null) {
      lang = file.getLanguage();
    }

    final FileViewProvider viewProvider = file.getViewProvider();
    if (viewProvider instanceof TemplateLanguageFileViewProvider &&
        lang == ((TemplateLanguageFileViewProvider)viewProvider).getTemplateDataLanguage()) {
      lang = viewProvider.getBaseLanguage();
    }

    return LanguageCommenters.INSTANCE.forLanguage(lang);
  }

  @Nullable
  private PsiElement findCommentAtCaret() {
    int offset = myEditor.getCaretModel().getOffset();
    SelectionModel selectionModel = myEditor.getSelectionModel();
    TextRange range = new TextRange(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
    if (offset == range.getEndOffset()) {
      offset--;
    }
    if (offset <= range.getStartOffset()) {
      offset++;
    }
    PsiElement elt = myFile.getViewProvider().findElementAt(offset);
    if (elt == null) return null;
    PsiElement comment =  PsiTreeUtil.getParentOfType(elt, PsiComment.class, false);
    if (comment == null || selectionModel.hasSelection() && !range.contains(comment.getTextRange())) {
      return null;
    }

    return comment;
  }

  public boolean startInWriteAction() {
    return true;
  }

  public void commentRange(int startOffset, int endOffset, String commentPrefix, String commentSuffix, Commenter commenter) {
    CharSequence chars = myDocument.getCharsSequence();
    LogicalPosition caretPosition = myEditor.getCaretModel().getLogicalPosition();

    if (startOffset == 0 || chars.charAt(startOffset - 1) == '\n' || chars.charAt(startOffset - 1) == '\r') {
      if (endOffset == myDocument.getTextLength() || chars.charAt(endOffset - 1) == '\n' || chars.charAt(endOffset - 1) == '\r') {
        CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(myProject);
        CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(myProject);
        String space;
        if (!settings.BLOCK_COMMENT_AT_FIRST_COLUMN) {
          final FileType fileType = myFile.getFileType();
          int line1 = myEditor.offsetToLogicalPosition(startOffset).line;
          int line2 = myEditor.offsetToLogicalPosition(endOffset - 1).line;
          Indent minIndent = CommentUtil.getMinLineIndent(myProject, myDocument, line1, line2, fileType);
          if (minIndent == null) {
            minIndent = codeStyleManager.zeroIndent();
          }
          space = codeStyleManager.fillIndent(minIndent, fileType);
        }
        else {
          space = "";
        }
        TextRange range = insertNestedComments(chars, startOffset, endOffset, space + commentPrefix + "\n", space + commentSuffix + "\n", commenter);
        myEditor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
        //myEditor.getSelectionModel().removeSelection();
        LogicalPosition pos = new LogicalPosition(caretPosition.line + 1, caretPosition.column);
        myEditor.getCaretModel().moveToLogicalPosition(pos);
        myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        return;
      }
    }

    TextRange range = insertNestedComments(chars, startOffset, endOffset, commentPrefix, commentSuffix, commenter);
    myEditor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
    //myEditor.getSelectionModel().removeSelection();
    LogicalPosition pos = new LogicalPosition(caretPosition.line, caretPosition.column + commentPrefix.length());
    myEditor.getCaretModel().moveToLogicalPosition(pos);
    myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
  }

  private int doBoundCommentingAndGetShift(int offset, String commented, int skipLength, String toInsert, boolean skipBrace, TextRange selection) {
    if (commented == null && (offset == selection.getStartOffset() || offset + (skipBrace ? skipLength : 0) == selection.getEndOffset())) {
      return 0;
    }
    if (commented == null) {
      myDocument.insertString(offset + (skipBrace ? skipLength : 0), toInsert);
      return toInsert.length();
    }
    else {
      myDocument.replaceString(offset, offset + skipLength, commented);
      return commented.length() - skipLength;
    }
  }

  private TextRange insertNestedComments(CharSequence chars, int startOffset, int endOffset, String commentPrefix, String commentSuffix, Commenter commenter) {
    String normalizedPrefix = commentPrefix.trim();
    String normalizedSuffix = commentSuffix.trim();
    IntArrayList nestedCommentPrefixes = new IntArrayList();
    IntArrayList nestedCommentSuffixes = new IntArrayList();
    String commentedPrefix = commenter.getCommentedBlockCommentPrefix();
    String commentedSuffix = commenter.getCommentedBlockCommentSuffix();
    for (int i = startOffset; i < endOffset; ++i) {
      if (CharArrayUtil.regionMatches(chars, i, normalizedPrefix)) {
        nestedCommentPrefixes.add(i);
      }
      else {
        if (CharArrayUtil.regionMatches(chars, i, normalizedSuffix)) {
          nestedCommentSuffixes.add(i);
        }
      }
    }
    int shift = 0;
    if (!(commentedSuffix == null && !nestedCommentSuffixes.isEmpty() && nestedCommentSuffixes.get(nestedCommentSuffixes.size() - 1) + commentSuffix.length() == endOffset)) {
      myDocument.insertString(endOffset, commentSuffix);
      shift += commentSuffix.length();
    }

    // process nested comments in back order
    int i = nestedCommentPrefixes.size() - 1;
    int j = nestedCommentSuffixes.size() - 1;
    final TextRange selection = new TextRange(startOffset, endOffset);
    while (i >= 0 && j >= 0) {
      final int prefixIndex = nestedCommentPrefixes.get(i);
      final int suffixIndex = nestedCommentSuffixes.get(j);
      if (prefixIndex > suffixIndex) {
        shift += doBoundCommentingAndGetShift(prefixIndex, commentedPrefix, normalizedPrefix.length(), commentSuffix, false, selection);
        --i;
      }
      else {
        //if (insertPos < myDocument.getTextLength() && Character.isWhitespace(myDocument.getCharsSequence().charAt(insertPos))) {
        //  insertPos = suffixIndex + commentSuffix.length();
        //}
        shift += doBoundCommentingAndGetShift(suffixIndex, commentedSuffix, normalizedSuffix.length(), commentPrefix, true, selection);
        --j;
      }
    }
    while (i >= 0) {
      final int prefixIndex = nestedCommentPrefixes.get(i);
      shift += doBoundCommentingAndGetShift(prefixIndex, commentedPrefix, normalizedPrefix.length(), commentSuffix, false, selection);
      --i;
    }
    while (j >= 0) {
      final int suffixIndex = nestedCommentSuffixes.get(j);
      shift += doBoundCommentingAndGetShift(suffixIndex, commentedSuffix, normalizedSuffix.length(), commentPrefix, true, selection);
      --j;
    }
    if (!(commentedPrefix == null && !nestedCommentPrefixes.isEmpty() && nestedCommentPrefixes.get(0) == startOffset)) {
      myDocument.insertString(startOffset, commentPrefix);
      shift += commentPrefix.length();
    }

    return new TextRange(startOffset, endOffset + shift);
  }

  private static int getNearest(String text, String pattern, int position) {
    int result = text.indexOf(pattern, position);
    return result == -1 ? text.length() : result;
  }

  static void commentNestedComments(@NotNull Document document, TextRange range, Commenter commenter) {
    final int offset = range.getStartOffset();
    final IntArrayList toReplaceWithComments = new IntArrayList();
    final IntArrayList prefixes = new IntArrayList();
    if (range.getLength() < 0) return; // TODO: reproduce and fix this case
    final String text = document.getCharsSequence().subSequence(range.getStartOffset(), range.getEndOffset()).toString();
    final String commentedPrefix = commenter.getCommentedBlockCommentPrefix();
    final String commentedSuffix = commenter.getCommentedBlockCommentSuffix();
    final String commentPrefix = commenter.getBlockCommentPrefix();
    final String commentSuffix = commenter.getBlockCommentSuffix();


    int nearestSuffix = getNearest(text, commentedSuffix, 0);
    int nearestPrefix = getNearest(text, commentedPrefix, 0);
    int level = 0;
    int lastSuffix = -1;
    for (int i = Math.min(nearestPrefix, nearestSuffix); i < text.length(); i = Math.min(nearestPrefix, nearestSuffix)) {
      if (i > nearestPrefix) {
        nearestPrefix = getNearest(text, commentedPrefix, i);
        continue;
      }
      if (i > nearestSuffix) {
        nearestSuffix = getNearest(text, commentedSuffix, i);
        continue;
      }
      if (i == nearestPrefix) {
        if (level <= 0) {
          if (lastSuffix != -1) {
            toReplaceWithComments.add(lastSuffix);
          }
          level = 1;
          lastSuffix = -1;
          toReplaceWithComments.add(i);
          prefixes.add(i);
        }
        else {
          level++;
        }
        nearestPrefix = getNearest(text, commentedPrefix, nearestPrefix + 1);
      }
      else {
        lastSuffix = i;
        level--;
        nearestSuffix = getNearest(text, commentedSuffix, nearestSuffix + 1);
      }
    }
    if (lastSuffix != -1) {
      toReplaceWithComments.add(lastSuffix);
    }

    int prefixIndex = prefixes.size() - 1;
    for (int i = toReplaceWithComments.size() - 1; i >= 0; i--) {
      int position = toReplaceWithComments.get(i);
      if (prefixIndex >= 0 && position == prefixes.get(prefixIndex)) {
        prefixIndex--;
        document.replaceString(offset + position, offset + position + commentedPrefix.length(), commentPrefix);
      }
      else {
        document.replaceString(offset + position, offset + position + commentedSuffix.length(), commentSuffix);
      }
    }
  }

  private TextRange expandRange(int delOffset1,  int delOffset2) {
    CharSequence chars = myDocument.getCharsSequence();
    int offset1 = CharArrayUtil.shiftBackward(chars, delOffset1 - 1, " \t");
    if (offset1 < 0 || chars.charAt(offset1) == '\n' || chars.charAt(offset1) == '\r') {
      int offset2 = CharArrayUtil.shiftForward(chars, delOffset2, " \t");
      if (offset2 == myDocument.getTextLength() || chars.charAt(offset2) == '\r' || chars.charAt(offset2) == '\n') {
        delOffset1 = offset1 + 1;
        if (offset2 < myDocument.getTextLength()) {
          delOffset2 = offset2 + 1;
          if (chars.charAt(offset2) == '\r' && offset2 + 1 < myDocument.getTextLength() && chars.charAt(offset2 + 1) == '\n') {
            delOffset2++;
          }
        }
      }
    }
    return new TextRange(delOffset1, delOffset2);
  }

  private Pair<TextRange, TextRange> findCommentBlock(TextRange range, String commentPrefix, String commentSuffix) {
    CharSequence chars = myDocument.getCharsSequence();
    int startOffset = range.getStartOffset();
    boolean endsProperly = CharArrayUtil.regionMatches(chars, range.getEndOffset() - commentSuffix.length(), commentSuffix);

    TextRange start = expandRange(startOffset, startOffset + commentPrefix.length());
    TextRange end;
    if (endsProperly) {
      end = expandRange(range.getEndOffset() - commentSuffix.length(), range.getEndOffset());
    }
    else {
      end = new TextRange(range.getEndOffset(), range.getEndOffset());
    }

    return new Pair<TextRange, TextRange>(start, end);
  }

  public void uncommentRange(TextRange range, String commentPrefix, String commentSuffix, Commenter commenter) {
    String text = myDocument.getCharsSequence().subSequence(range.getStartOffset(), range.getEndOffset()).toString();
    int startOffset = range.getStartOffset();
    //boolean endsProperly = CharArrayUtil.regionMatches(chars, range.getEndOffset() - commentSuffix.length(), commentSuffix);
    List<Pair<TextRange, TextRange>> ranges = new ArrayList<Pair<TextRange, TextRange>>();

    int position = 0;
    while (true) {
      int start = getNearest(text, commentPrefix, position);
      if (start == text.length()) {
        break;
      }
      position = start;
      int end = getNearest(text, commentSuffix, position + commentPrefix.length()) + commentSuffix.length();
      position = end;
      Pair<TextRange, TextRange> pair = findCommentBlock(new TextRange(start + startOffset, end + startOffset), commentPrefix, commentSuffix);
      ranges.add(pair);
    }

    for (int i = ranges.size() - 1; i >= 0; i--) {
      Pair<TextRange, TextRange> toDelete = ranges.get(i);
      myDocument.deleteString(toDelete.first.getStartOffset(), toDelete.first.getEndOffset());
      int shift = toDelete.first.getEndOffset() - toDelete.first.getStartOffset();
      myDocument.deleteString(toDelete.second.getStartOffset() - shift, toDelete.second.getEndOffset() - shift);
      if (commenter.getCommentedBlockCommentPrefix() != null) {
        commentNestedComments(myDocument, new TextRange(toDelete.first.getEndOffset() - shift, toDelete.second.getStartOffset() - shift), commenter);
      }
    }
  }
}
