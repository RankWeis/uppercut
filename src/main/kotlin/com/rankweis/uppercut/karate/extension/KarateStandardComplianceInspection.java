package com.rankweis.uppercut.karate.extension;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.json.codeinsight.JsonStandardComplianceInspection;
import com.intellij.json.psi.JsonLiteral;
import com.intellij.json.psi.JsonPsiUtil;
import com.intellij.json.psi.JsonReferenceExpression;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.rankweis.uppercut.karate.psi.KarateLanguage;
import org.jetbrains.annotations.NotNull;

public class KarateStandardComplianceInspection extends JsonStandardComplianceInspection {

  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

    if (!holder.getFile().getLanguage().equals(KarateLanguage.INSTANCE)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    } else {
      return new StandardKarateValidatingElementVisitor(holder);
    }
  }

  public @NotNull OptPane getOptionsPane() {
    return OptPane.EMPTY;
  }

  private final class StandardKarateValidatingElementVisitor
    extends JsonStandardComplianceInspection.StandardJsonValidatingElementVisitor {

    StandardKarateValidatingElementVisitor(ProblemsHolder holder) {
      super(holder);
    }

    protected boolean allowComments() {
      return true;
    }

    protected boolean allowSingleQuotes() {
      return true;
    }

    protected boolean allowIdentifierPropertyNames() {
      return true;
    }

    protected boolean allowTrailingCommas() {
      return true;
    }

    protected boolean allowNanInfinity() {
      return true;
    }

    protected boolean isValidPropertyName(@NotNull PsiElement literal) {
      if (!(literal instanceof JsonLiteral)) {
        return literal instanceof JsonReferenceExpression && StringUtil.isJavaIdentifier(literal.getText());
      } else {
        String textWithoutHostEscaping = JsonPsiUtil.getElementTextWithoutHostEscaping(literal);
        return textWithoutHostEscaping.startsWith("\"") || textWithoutHostEscaping.startsWith("'");
      }
    }

    @Override
    public void visitReferenceExpression(@NotNull JsonReferenceExpression reference) {
    }
  }

}
