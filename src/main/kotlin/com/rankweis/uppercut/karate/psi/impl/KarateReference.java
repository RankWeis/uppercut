package com.rankweis.uppercut.karate.psi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.rankweis.uppercut.karate.psi.GherkinFile;
import com.rankweis.uppercut.karate.psi.GherkinFileType;
import com.rankweis.uppercut.karate.psi.GherkinPsiElement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class KarateReference extends PsiReferenceBase<PsiElement> implements PsiPolyVariantReference {

  private final String key;

  public KarateReference(@NotNull PsiElement element,
    TextRange rangeInElement, boolean soft) {
    super(element, rangeInElement, soft);
    key = element.getText().substring(rangeInElement.getStartOffset(), rangeInElement.getEndOffset());
  }

  @Override public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    Project project = myElement.getProject();
    final List<GherkinPsiElement> properties = findProperties(project, key);
    List<ResolveResult> results = new ArrayList<>();
    for (GherkinPsiElement property : properties) {
      results.add(new PsiElementResolveResult(property));
    }
    return results.toArray(new ResolveResult[0]);
  }

  @Override public @Nullable PsiElement resolve() {
    ResolveResult[] resolveResults = multiResolve(false);
    return resolveResults.length == 1 ? resolveResults[0].getElement() : null;
  }

  @Override public Object @NotNull [] getVariants() {
    return super.getVariants();
  }

  public static List<GherkinPsiElement> findProperties(Project project, String key) {
    List<GherkinPsiElement> result = new ArrayList<>();
    Collection<VirtualFile> virtualFiles =
      FileTypeIndex.getFiles(GherkinFileType.INSTANCE, GlobalSearchScope.allScope(project));
    for (VirtualFile virtualFile : virtualFiles) {
      GherkinFile simpleFile = (GherkinFile) PsiManager.getInstance(project).findFile(virtualFile);
      if (simpleFile != null) {
        GherkinPsiElement[] properties = PsiTreeUtil.getChildrenOfType(simpleFile, GherkinPsiElement.class);
        if (properties != null) {
          for (GherkinPsiElement property : properties) {
            if (key.equals(property.getText())) {
              result.add(property);
            }
          }
        }
      }
    }
    return result;
  }
}