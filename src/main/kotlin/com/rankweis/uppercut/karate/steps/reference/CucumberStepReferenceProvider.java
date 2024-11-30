package com.rankweis.uppercut.karate.steps.reference;

import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.QUOTED_STRING;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.rankweis.uppercut.karate.psi.KarateDeclaration;
import com.rankweis.uppercut.karate.psi.impl.GherkinStepImpl;
import com.rankweis.uppercut.karate.psi.impl.KarateReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

public class CucumberStepReferenceProvider extends PsiReferenceProvider {

  private static final Pattern VARIABLE_PATTERN = Pattern.compile("([\\w.]+)");
  
  @Override
  public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
    @NotNull ProcessingContext context) {
    if (element instanceof GherkinStepImpl) {
      List<PsiReference> references = new ArrayList<>();
      Matcher m = VARIABLE_PATTERN.matcher(element.getText());
      while (m.find()) {
        int start = m.start();
        int end = m.end();
        String content = m.group();
        if (QUOTED_STRING.contains(element.findElementAt(m.start()).getNode().getElementType())) {
          continue;
        }
        references.add(new KarateReference(element, new TextRange(start, end), true));
        String[] dotSplitted = content.split("\\.");
        for (int i = 0; i < dotSplitted.length; i++) {
          StringBuilder builder = new StringBuilder();
          for (int j = 0; j <= i; j++) {
            if (!builder.isEmpty()) {
              builder.append(".");
            }
            builder.append(dotSplitted[j]);
          }
          int splittedEnd = start + builder.length();
          TextRange textRange = new TextRange(start, splittedEnd);
          KarateReference reference =
            new KarateReference(element, textRange, true);
          references.add(reference);
        }
      }
      @Unmodifiable @NotNull Collection<KarateDeclaration>
        declarations = PsiTreeUtil.findChildrenOfType(element.getParent(), KarateDeclaration.class);
      TextRange textRange = new TextRange(0, element.getTextLength());
      KarateReference reference =
        new KarateReference(element, textRange, true);
      declarations.forEach(declaration -> declaration.addReference(reference));
      return references.toArray(new PsiReference[0]);
    }
    return PsiReference.EMPTY_ARRAY;
  }
}
