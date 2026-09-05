package com.rankweis.uppercut.karate.steps.reference;

import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.QUOTED_STRING;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.util.ProcessingContext;
import com.rankweis.uppercut.karate.psi.GherkinPystring;
import com.rankweis.uppercut.karate.psi.impl.GherkinStepImpl;
import com.rankweis.uppercut.karate.psi.impl.KarateReference;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

public class KarateStepReferenceProvider extends PsiReferenceProvider {

  private static final Pattern VARIABLE_PATTERN = Pattern.compile("([\\w.]+)");
  private static final Pattern DOT_PATTERN = Pattern.compile("\\.");

  @Override
  public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
    @NotNull ProcessingContext context) {
    if (element instanceof GherkinStepImpl step) {
      List<PsiReference> references = new ArrayList<>();

      // Step definition reference (for annotator, findDefinitions, rename)
      ASTNode keyword = step.getKeyword();
      int keywordLength = keyword != null ? keyword.getTextLength() + 1 : 0;
      references.add(new KarateStepReference(element,
        new TextRange(keywordLength, element.getTextLength())));

      // Variable references — skip tokens inside docstrings
      GherkinPystring pystring = step.getPystring();
      TextRange pystringRange = null;
      if (pystring != null) {
        int psStart = pystring.getTextOffset() - element.getTextOffset();
        pystringRange = TextRange.create(psStart, psStart + pystring.getTextLength());
      }
      Matcher m = VARIABLE_PATTERN.matcher(element.getText());
      while (m.find()) {
        int start = m.start();
        if (pystringRange != null && pystringRange.containsOffset(start)) {
          continue;
        }
        if (QUOTED_STRING.contains(element.findElementAt(start).getNode().getElementType())) {
          continue;
        }
        char first = m.group().charAt(0);
        if (Character.isDigit(first) || first == '.') {
          continue; // a number such as 158.0 or .5, not a variable
        }
        // One reference per segment of a dotted path, each keyed on the path up to it: on
        // `auth.token` the caret on `token` resolves through `auth`'s call read(...) into the called
        // feature, while the caret on `auth` resolves to `auth` itself - see KarateReference.
        String[] segments = DOT_PATTERN.split(m.group());
        int segmentStart = start;
        StringBuilder path = new StringBuilder();
        for (String segment : segments) {
          if (!path.isEmpty()) {
            path.append('.');
          }
          path.append(segment);
          if (!segment.isEmpty()) {
            TextRange range = new TextRange(segmentStart, segmentStart + segment.length());
            references.add(new KarateReference(element, range, path.toString(), true));
          }
          segmentStart += segment.length() + 1;
        }
      }
      return references.toArray(new PsiReference[0]);
    }
    return PsiReference.EMPTY_ARRAY;
  }
}
