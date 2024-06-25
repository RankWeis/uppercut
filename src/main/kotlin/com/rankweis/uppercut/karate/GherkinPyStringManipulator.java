// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.rankweis.uppercut.karate;

import static com.rankweis.uppercut.karate.psi.GherkinLexer.PYSTRING_MARKER;

import com.rankweis.uppercut.karate.psi.GherkinFileType;
import com.rankweis.uppercut.karate.psi.GherkinPystring;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class GherkinPyStringManipulator extends AbstractElementManipulator<GherkinPystring> {
  private static final String PY_STRING_FILE_TEMPLATE =
    """
      Feature:\s
        Scenario: Test
          Given step
      ""\"%s""\"""";

  @Override
  public @NotNull TextRange getRangeInElement(@NotNull GherkinPystring element) {
    return TextRange.create(PYSTRING_MARKER.length(), element.getTextLength() - PYSTRING_MARKER.length());
  }

  @Override
  public @Nullable GherkinPystring handleContentChange(@NotNull GherkinPystring element,
                                                       @NotNull TextRange range, String newContent) throws IncorrectOperationException {


    String dummyFileText = String.format(PY_STRING_FILE_TEMPLATE, newContent);
    PsiFile dummyFile =
      PsiFileFactory.getInstance(element.getProject()).createFileFromText("test.feature", GherkinFileType.INSTANCE, dummyFileText);
    PsiElement pyStringQuotes = dummyFile.findElementAt(dummyFile.getTextLength() - 1);
    if (pyStringQuotes != null && pyStringQuotes.getParent() instanceof GherkinPystring pyStringElement) {
      return (GherkinPystring)element.replace(pyStringElement);
    }
    return element;
  }
}
