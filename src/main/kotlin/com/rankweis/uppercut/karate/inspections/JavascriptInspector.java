package com.rankweis.uppercut.karate.inspections;

import static com.rankweis.uppercut.karate.psi.UppercutElementTypes.DECLARATION;
import static com.rankweis.uppercut.karate.psi.UppercutElementTypes.SCENARIO;
import static com.rankweis.uppercut.karate.psi.UppercutElementTypes.STEP;

import com.intellij.codeInspection.InspectionSuppressor;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.psi.PsiElement;
import com.rankweis.uppercut.karate.psi.KarateLanguage;
import com.rankweis.uppercut.karate.psi.KarateTokenTypes;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavascriptInspector implements InspectionSuppressor {

  private static final Set<String> SUPPRESSED_INSPECTIONS = new HashSet<>();

  public void visitElement(@NotNull PsiElement element) {
  }

  @Override
  public boolean isSuppressedFor(@NotNull PsiElement element, @NotNull String toolId) {
    if (element.getText() != null) {
      if (toolId.equals("JSUnresolvedReference")) {
        PsiElement cur = element;
        while (cur != null && cur.getText() != null) {
          if ((cur.getText().equals("karate") || cur.getText().equals("Java")) && cur.getPrevSibling() == null) {
            return true;
          }
          cur = cur.getPrevSibling();
        }
      } else if (toolId.equals("JSUnresolvedVariable")) {
        PsiElement cur = element.getParent();
        while (cur != null && cur.getNode() != KarateLanguage.INSTANCE) {
          if (cur.getNode() != null && cur.getNode().getElementType() == STEP) {
            break;
          }
          cur = cur.getParent();
        }
        while (cur != null && cur.getLanguage() == KarateLanguage.INSTANCE) {
          PsiElement child = cur.getFirstChild();
          while (child != null) {
            PsiElement elements = child.getFirstChild();
            while (elements != null && elements.getLanguage() == KarateLanguage.INSTANCE
              && elements.getNode() != null) {
              if (elements.getNode().getElementType() == KarateTokenTypes.DECLARATION
                && elements.getText().equals(element.getText())) {
                return true;
              }
              elements = elements.getNextSibling();
            }
            child = child.getNextSibling();
          }
          cur = cur.getPrevSibling();
        }
        cur = element.getContainingFile().getFirstChild();
        boolean hasDeclarationInBackground = Arrays.stream(cur.getChildren())
          .filter(e -> e.getFirstChild() != null && e.getFirstChild().getText().equals("Background"))
          .map(PsiElement::getChildren)
          .flatMap(Arrays::stream)
          .filter(e -> e.getNode() != null && e.getNode().getElementType() == STEP)
          .flatMap(e -> Arrays.stream(e.getChildren()))
          .anyMatch(e -> e.getNode() != null && e.getNode().getElementType() == DECLARATION && e.getText()
            .equals(element.getText()));
        if (hasDeclarationInBackground) {
          return true;
        }
      }
    }
    return SUPPRESSED_INSPECTIONS.contains(toolId)
      && element.getContainingFile().getLanguage() == KarateLanguage.INSTANCE;
  }

  @Override
  public SuppressQuickFix @NotNull [] getSuppressActions(@Nullable PsiElement element, @NotNull String toolId) {
    return new SuppressQuickFix[0];
  }

  static {
    SUPPRESSED_INSPECTIONS.add("ReservedWordAsName");
  }
}
