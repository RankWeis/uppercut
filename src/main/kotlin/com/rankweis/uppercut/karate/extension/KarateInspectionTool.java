package com.rankweis.uppercut.karate.extension;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.PsiFile;
import com.rankweis.uppercut.karate.psi.KarateLanguage;
import org.jetbrains.annotations.NotNull;

public class KarateInspectionTool extends LocalInspectionTool {

  @Override
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (isKarateFile(file)) {
      // Return an empty array to suppress errors in Karate files
      return ProblemDescriptor.EMPTY_ARRAY;
    }
    return super.checkFile(file, manager, isOnTheFly);
  }

  private boolean isKarateFile(PsiFile file) {
    return file.getLanguage() == KarateLanguage.INSTANCE;
  }
}