package com.rankweis.uppercut.karate.psi;

import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.COMMENTS;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.KEYWORDS;

import com.intellij.lang.cacheBuilder.DefaultWordsScanner;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.tree.TokenSet;
import com.rankweis.uppercut.karate.MyBundle;
import com.rankweis.uppercut.karate.lexer.UppercutLexer;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public final class UppercutFindUsagesProvider implements FindUsagesProvider {
  @Override
  public WordsScanner getWordsScanner() {
    return new DefaultWordsScanner(new UppercutLexer(
      new PlainKarateKeywordProvider()), KEYWORDS, COMMENTS, TokenSet.EMPTY);
  }

  @Override
  public boolean canFindUsagesFor(@NotNull PsiElement psiElement) {
    return psiElement instanceof PsiNamedElement;
  }

  @Override
  public String getHelpId(@NotNull PsiElement psiElement) {
    return "reference.dialogs.findUsages.other";
  }

  @NotNull
  @Override
  public String getType(@NotNull PsiElement element) {
    if (element instanceof GherkinStep) {
      return MyBundle.message("cucumber.step");
    } else if (element instanceof GherkinStepParameter) {
      return MyBundle.message("cucumber.step.parameter");
    }
    return MyBundle.message("gherkin.find.usages.unknown.element.type");
  }

  @NotNull
  @Override
  public String getDescriptiveName(@NotNull PsiElement element) {
    return element instanceof PsiNamedElement ? Objects.requireNonNull(((PsiNamedElement) element).getName()) : "";
  }

  @NotNull
  @Override
  public String getNodeText(@NotNull PsiElement element, boolean useFullName) {
    return getDescriptiveName(element);
  }
}
