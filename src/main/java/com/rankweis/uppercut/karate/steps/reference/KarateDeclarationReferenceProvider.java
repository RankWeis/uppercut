package com.rankweis.uppercut.karate.steps.reference;

import static com.intellij.psi.tree.TokenSet.WHITE_SPACE;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.DECLARATION;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.STEP_PARAMETER_BRACE;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.STEP_PARAMETER_TEXT;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.TEXT;
import static com.rankweis.uppercut.karate.psi.UppercutElementTypes.STEP_PARAMETER;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.ProcessingContext;
import com.rankweis.uppercut.karate.psi.KarateDeclaration;
import com.rankweis.uppercut.karate.psi.impl.KarateReference;
import org.jetbrains.annotations.NotNull;

public class KarateDeclarationReferenceProvider extends PsiReferenceProvider {

  private static final TokenSet TEXT_AND_PARAM_SET =
    TokenSet.create(TEXT, DECLARATION, STEP_PARAMETER_TEXT, STEP_PARAMETER_BRACE, STEP_PARAMETER);
  private static final TokenSet TEXT_PARAM_AND_WHITE_SPACE_SET = TokenSet.orSet(TEXT_AND_PARAM_SET, WHITE_SPACE);

  @Override
  public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
    @NotNull ProcessingContext context) {
    if (element instanceof KarateDeclaration) {
      ASTNode textNode = element.getNode().findChildByType(TEXT_AND_PARAM_SET);
      if (textNode != null) {
        int start = textNode.getTextRange().getStartOffset();
        int end = textNode.getTextRange().getEndOffset();
        TextRange textRange = new TextRange(start, end);
        KarateReference reference = new KarateReference(element, textRange.shiftRight(-element.getTextOffset()), false);
        return new PsiReference[]{reference};
      }
    }
    return PsiReference.EMPTY_ARRAY;
  }
}
