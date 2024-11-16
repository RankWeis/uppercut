package com.rankweis.uppercut.karate.inspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.rankweis.uppercut.karate.MyBundle;
import com.rankweis.uppercut.karate.psi.GherkinElementVisitor;
import com.rankweis.uppercut.karate.psi.impl.GherkinExamplesBlockImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dennis.Ushakov
 */
public final class KarateExamplesColonInspection extends GherkinInspection {

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public String getShortName() {
    return "KarateExamplesColon";
  }

  @NotNull
  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new GherkinElementVisitor() {
      @Override
      public void visitExamplesBlock(GherkinExamplesBlockImpl block) {
        final PsiElement examples = block.getFirstChild();
        assert examples != null;
        final PsiElement next = examples.getNextSibling();
        final String text = next != null ? next.getText() : null;
        if (text == null || !text.contains(":")) {

          holder.registerProblem(examples,
                                 new TextRange(0, examples.getTextRange().getEndOffset() - examples.getTextOffset()),
                                 MyBundle.message("inspection.missed.colon.example.name"),
                                 new CucumberAddExamplesColonFix());
        }
      }
    };
  }
}
