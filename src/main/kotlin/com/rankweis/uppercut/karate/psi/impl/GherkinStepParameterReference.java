package com.rankweis.uppercut.karate.psi.impl;

import com.rankweis.uppercut.karate.psi.GherkinExamplesBlock;
import com.rankweis.uppercut.karate.psi.GherkinScenarioOutline;
import com.rankweis.uppercut.karate.psi.GherkinStepParameter;
import com.rankweis.uppercut.karate.psi.GherkinTable;
import com.rankweis.uppercut.karate.psi.GherkinTableCell;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

public class GherkinStepParameterReference extends GherkinSimpleReference {
  public GherkinStepParameterReference(GherkinStepParameter stepParameter) {
    super(stepParameter);
  }

  @NotNull
  @Override
  public GherkinStepParameter getElement() {
    return (GherkinStepParameter)super.getElement();
  }

  @Override
  public PsiElement resolve() {
    final GherkinScenarioOutline scenario = PsiTreeUtil.getParentOfType(getElement(), GherkinScenarioOutline.class);
    if (scenario == null) {
      return null;
    }
    final GherkinExamplesBlock exampleBlock = PsiTreeUtil.getChildOfType(scenario, GherkinExamplesBlock.class);
    if (exampleBlock == null) {
      return null;
    }
    final GherkinTable table = PsiTreeUtil.getChildOfType(exampleBlock, GherkinTable.class);
    if (table == null) {
      return null;
    }
    final GherkinTableHeaderRowImpl header = PsiTreeUtil.getChildOfType(table, GherkinTableHeaderRowImpl.class);
    if (header == null) {
      return null;
    }
    for (PsiElement cell : header.getChildren()) {
      if (cell instanceof GherkinTableCell) {
        final String cellText = cell.getText();
        if (cellText.equals(getElement().getName())) {
          return cell;
        }
      }
    }
    return null;
  }
}
