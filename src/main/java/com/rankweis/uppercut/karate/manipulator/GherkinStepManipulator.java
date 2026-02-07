// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.rankweis.uppercut.karate.manipulator;

import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.QUOTED_STRING;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.util.IncorrectOperationException;
import com.rankweis.uppercut.karate.psi.GherkinFileType;
import com.rankweis.uppercut.karate.psi.GherkinStep;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class GherkinStepManipulator extends AbstractElementManipulator<GherkinStep> {

  private static final String KARATE_REFERENCE_FILE_TEMPLATE =
    """
      Feature:\s
        Scenario: Test
          %s
      """;

  @Override
  public @Nullable GherkinStep handleContentChange(@NotNull GherkinStep element,
    @NotNull TextRange range, String newContent) throws IncorrectOperationException {

    String newStep = replaceRangeInString(element.getText(), range, newContent);
    // Don't change quoted strings when handling content change.
    if (element.findElementAt(range.getStartOffset()) instanceof  LeafPsiElement leafPsiElement) {
      if (QUOTED_STRING.contains(leafPsiElement.getElementType())) {
        return element;
      }
    }
    String dummyFileText = String.format(KARATE_REFERENCE_FILE_TEMPLATE, newStep);
    PsiFile dummyFile =
      PsiFileFactory.getInstance(element.getProject())
        .createFileFromText("test.feature", GherkinFileType.INSTANCE, dummyFileText);
    PsiElement karateReferenceElem = dummyFile.findElementAt(32);
    if (karateReferenceElem != null && karateReferenceElem.getParent() instanceof GherkinStep gherkinStep) {
      return (GherkinStep) element.replace(gherkinStep);
    }
    return element;
  }

  @Override public @NotNull TextRange getRangeInElement(@NotNull GherkinStep element) {
    return new TextRange(0, element.getTextLength());
  }

  private String replaceRangeInString(String str, TextRange range, String newText) {
    StringBuilder buf = new StringBuilder(str);
    buf.replace(range.getStartOffset(), range.getEndOffset(), newText);
    return buf.toString();
  }
}
