package com.rankweis.uppercut.karate.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.rankweis.uppercut.karate.MyBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @author Roman.Chernyatchik
 */
public abstract class GherkinInspection extends LocalInspectionTool {
  @Override
  @NotNull
  public String getGroupDisplayName() {
    return MyBundle.message("karate.inspection.group.name");
  }

}