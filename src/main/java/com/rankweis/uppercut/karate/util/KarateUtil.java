package com.rankweis.uppercut.karate.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.rankweis.uppercut.karate.psi.GherkinFile;
import com.rankweis.uppercut.karate.psi.GherkinFileType;
import com.rankweis.uppercut.karate.psi.GherkinPsiElement;
import com.rankweis.uppercut.karate.psi.KarateDeclaration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class KarateUtil {

  public static List<GherkinPsiElement> findProperties(Project project, String key) {
    List<GherkinPsiElement> result = new ArrayList<>();
    Collection<VirtualFile> virtualFiles =
      FileTypeIndex.getFiles(GherkinFileType.INSTANCE, GlobalSearchScope.allScope(project));
    for (VirtualFile virtualFile : virtualFiles) {
      GherkinFile simpleFile = (GherkinFile) PsiManager.getInstance(project).findFile(virtualFile);
      if (simpleFile != null) {
        Collection<KarateDeclaration> properties =
          PsiTreeUtil.findChildrenOfType(simpleFile, KarateDeclaration.class);
        for (KarateDeclaration property : properties) {
          if (key.equals(property.getName())) {
            result.add(property);
          }
        }
      }
    }
    return result;
  }

  public static List<KarateDeclaration> findProperties(Project project) {
    List<KarateDeclaration> result = new ArrayList<>();
    Collection<VirtualFile> virtualFiles =
      FileTypeIndex.getFiles(GherkinFileType.INSTANCE, GlobalSearchScope.allScope(project));
    for (VirtualFile virtualFile : virtualFiles) {
      GherkinFile simpleFile = (GherkinFile) PsiManager.getInstance(project).findFile(virtualFile);
      if (simpleFile != null) {
        result.addAll(PsiTreeUtil.findChildrenOfType(simpleFile, KarateDeclaration.class));
      }
    }
    return result;
  }

  public static GherkinPsiElement createProperty(Project project, String name) {
    GherkinFile file = createFile(project, name);
    return (GherkinPsiElement) file.getFirstChild();
  }

  public static GherkinFile createFile(Project project, String text) {
    String name = "dummy.simple";
    return (GherkinFile) PsiFileFactory.getInstance(project)
      .createFileFromText(name, GherkinFileType.INSTANCE, text);
  }

}
