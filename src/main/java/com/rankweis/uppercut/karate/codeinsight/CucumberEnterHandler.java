// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.rankweis.uppercut.karate.codeinsight;

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharArrayUtil;
import com.rankweis.uppercut.karate.psi.GherkinFile;
import com.rankweis.uppercut.karate.psi.KarateTokenTypes;
import org.jetbrains.annotations.NotNull;


public final class CucumberEnterHandler extends EnterHandlerDelegateAdapter {
  public static final String PYSTRING_QUOTES = "\"\"\"";

  @Override
  public Result preprocessEnter(@NotNull PsiFile file,
                                @NotNull Editor editor,
                                @NotNull Ref<Integer> caretOffset,
                                @NotNull Ref<Integer> caretAdvance,
                                @NotNull DataContext dataContext,
                                EditorActionHandler originalHandler) {
    if (!(file instanceof GherkinFile)) {
      return Result.Continue;
    }
    int caretOffsetValue = caretOffset.get().intValue();
    if (caretOffsetValue < 3) {
      return Result.Continue;
    }
    final Document document = editor.getDocument();
    final String docText = document.getText();
    PsiDocumentManager.getInstance(file.getProject()).commitDocument(editor.getDocument());
    final PsiElement probableQuotes = file.findElementAt(caretOffsetValue - 1);
    IElementType elementType = probableQuotes != null ? probableQuotes.getNode().getElementType() : null;
    if (elementType == KarateTokenTypes.PYSTRING_INCOMPLETE) {
      final PsiElement probablePyStringText =
        document.getTextLength() == PYSTRING_QUOTES.length() ? null : file.findElementAt(caretOffsetValue - 1 - PYSTRING_QUOTES.length());
      if (probablePyStringText == null || probablePyStringText.getNode().getElementType() != KarateTokenTypes.PYSTRING) {
        int line = document.getLineNumber(caretOffsetValue);
        int lineStart = document.getLineStartOffset(line);
        int textStart = CharArrayUtil.shiftForward(docText, lineStart, " \t");
        final String space = docText.subSequence(lineStart, textStart).toString();

        // insert closing triple quote
        EditorModificationUtil.insertStringAtCaret(editor, "\n" + space + "\n" + space + PYSTRING_QUOTES);
        editor.getCaretModel().moveCaretRelatively(-3, -1, false, false, true);
        return Result.Stop;
      }
    }
    return Result.Continue;
  }
}
