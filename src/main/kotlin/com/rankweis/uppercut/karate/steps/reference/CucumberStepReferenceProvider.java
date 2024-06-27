package com.rankweis.uppercut.karate.steps.reference;

import static com.intellij.psi.tree.TokenSet.WHITE_SPACE;
import static com.rankweis.uppercut.karate.psi.GherkinElementTypes.STEP_PARAMETER;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.DECLARATION;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.QUOTE;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.STEP_PARAMETER_BRACE;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.STEP_PARAMETER_TEXT;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.TEXT;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.VARIABLE;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.ProcessingContext;
import com.rankweis.uppercut.karate.psi.KarateDeclaration;
import com.rankweis.uppercut.karate.psi.impl.GherkinStepImpl;
import com.rankweis.uppercut.karate.psi.impl.KarateReference;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class CucumberStepReferenceProvider extends PsiReferenceProvider {

  private static final TokenSet TEXT_AND_PARAM_SET =
    TokenSet.create(TEXT, DECLARATION, VARIABLE, STEP_PARAMETER_TEXT, STEP_PARAMETER_BRACE, STEP_PARAMETER, QUOTE);
  private static final TokenSet TEXT_PARAM_AND_WHITE_SPACE_SET = TokenSet.orSet(TEXT_AND_PARAM_SET, WHITE_SPACE);

  @Override
  public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
    @NotNull ProcessingContext context) {
    if (element instanceof GherkinStepImpl || element instanceof KarateDeclaration) {
      List<PsiReference> references = new ArrayList<>();
      ASTNode variableNode = element.getNode().findChildByType(VARIABLE);
      if (variableNode != null) {
        if (variableNode.getText().contains(".")) {
          String[] dotSplitted = variableNode.getText().split("\\.");
          for (int i = 0; i < dotSplitted.length; i++) {
            StringBuilder builder = new StringBuilder();
            for (int j = 0; j <= i; j++) {
              if (!builder.isEmpty()) {
                builder.append(".");
              }
              builder.append(dotSplitted[j]);
            }
            int start = variableNode.getTextRange().getStartOffset();
            int end = start + builder.length();
            TextRange textRange = new TextRange(start, end);
            KarateReference reference =
              new KarateReference(element, textRange.shiftRight(-element.getTextOffset()), true);
            references.add(reference);
          }
        }
        int start = variableNode.getTextRange().getStartOffset();
        int end = variableNode.getTextRange().getEndOffset();
        TextRange textRange = new TextRange(start, end);
        KarateReference reference =
          new KarateReference(element, textRange.shiftRight(-element.getTextOffset()), true);
        references.add(reference);
      }
      return references.toArray(new PsiReference[0]);
    }
    return PsiReference.EMPTY_ARRAY;
  }
}
