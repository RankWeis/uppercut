package com.rankweis.uppercut.karate.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import org.jetbrains.annotations.NotNull;
import com.rankweis.uppercut.karate.MyBundle;

/**
 * @author Roman.Chernyatchik
 */
public abstract class GherkinInspection extends LocalInspectionTool {
  @Override
  @NotNull
  public String getGroupDisplayName() {
    return MyBundle.message("cucumber.inspection.group.name");
  }

}