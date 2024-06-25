// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.rankweis.uppercut.karate.inspections.suppress;

import static com.intellij.codeInspection.SuppressionUtil.COMMON_SUPPRESS_REGEXP;

import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.codeInspection.SuppressionUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.rankweis.uppercut.karate.psi.GherkinSuppressionHolder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class GherkinSuppressionUtil {
  // the same regexp as for ruby
  private static final Pattern SUPPRESS_IN_LINE_COMMENT_PATTERN = Pattern.compile("#" + COMMON_SUPPRESS_REGEXP);

  private GherkinSuppressionUtil() {
  }

  public static SuppressQuickFix @NotNull [] getDefaultSuppressActions(@NotNull final String toolId) {
    return new SuppressQuickFix[]{
      new GherkinSuppressForStepCommentFix(toolId),
      new GherkinSuppressForScenarioCommentFix(toolId),
      new GherkinSuppressForFeatureCommentFix(toolId),
    };
  }

  public static boolean isSuppressedFor(@NotNull final PsiElement element, @NotNull final String toolId) {
    return ReadAction.compute(() -> getSuppressedIn(element, toolId) != null).booleanValue();
  }

  @Nullable
  private static PsiComment getSuppressedIn(@NotNull PsiElement place, @NotNull String toolId) {
    // find suppression holder with suppression comment about given inspection tool
    PsiElement suppressionHolder = PsiTreeUtil.getNonStrictParentOfType(place, GherkinSuppressionHolder.class);
    while (suppressionHolder != null) {
      final PsiComment suppressionHolderElement = getSuppressionComment(toolId, suppressionHolder);
      if (suppressionHolderElement != null) {
        return suppressionHolderElement;
      }
      suppressionHolder = PsiTreeUtil.getParentOfType(suppressionHolder, GherkinSuppressionHolder.class);
    }
    return null;
  }

  @Nullable
  private static PsiComment getSuppressionComment(@NotNull String toolId,
                                                  @NotNull PsiElement element) {
    final PsiElement comment = PsiTreeUtil.skipWhitespacesBackward(element);
    if (comment instanceof PsiComment) {
      String text = comment.getText();
      Matcher matcher = SUPPRESS_IN_LINE_COMMENT_PATTERN.matcher(text);
      if (matcher.matches() && SuppressionUtil.isInspectionToolIdMentioned(matcher.group(1), toolId)) {
        return (PsiComment)comment;
      }
    }
    return null;
  }
}
