package com.rankweis.uppercut.karate.extension;

import com.intellij.json.codeinsight.JsonLiteralChecker;
import com.intellij.json.codeinsight.StandardJsonLiteralChecker;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.rankweis.uppercut.karate.psi.KarateLanguage;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

public class KarateJsonLiteralChecker implements JsonLiteralChecker {

  private static final Pattern VALID_HEX_ESCAPE = Pattern.compile("\\\\(x[0-9a-fA-F]{2})");
  private static final Pattern INVALID_NUMERIC_ESCAPE = Pattern.compile("\\\\[1-9]");

  public @Nullable String getErrorForNumericLiteral(String literalText) {
    return null;
  }

  public @Nullable Pair<TextRange, String> getErrorForStringFragment(Pair<TextRange, String> fragment,
    JsonStringLiteral stringLiteral) {
    String fragmentText = fragment.second;
    if (fragmentText.startsWith("\\") && fragmentText.length() > 1 && fragmentText.endsWith("\n")
      && StringUtil.isEmptyOrSpaces(fragmentText.substring(1, fragmentText.length() - 1))) {
      return null;
    } else if (fragmentText.startsWith("\\x") && VALID_HEX_ESCAPE.matcher(fragmentText).matches()) {
      return null;
    } else if (!StandardJsonLiteralChecker.VALID_ESCAPE.matcher(fragmentText).matches()
      && !INVALID_NUMERIC_ESCAPE.matcher(fragmentText).matches()) {
      return null;
    } else {
      String error = StandardJsonLiteralChecker.getStringError(fragmentText);
      return error == null ? null : Pair.create(fragment.first, error);
    }
  }

  public boolean isApplicable(PsiElement element) {
    return element.getLanguage() == KarateLanguage.INSTANCE
      || element.getContainingFile().getLanguage() == KarateLanguage.INSTANCE;
  }
}
