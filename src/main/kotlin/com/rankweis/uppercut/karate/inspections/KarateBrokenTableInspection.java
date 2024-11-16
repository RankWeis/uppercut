package com.rankweis.uppercut.karate.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.rankweis.uppercut.karate.MyBundle;
import com.rankweis.uppercut.karate.psi.GherkinElementVisitor;
import com.rankweis.uppercut.karate.psi.GherkinExamplesBlock;
import com.rankweis.uppercut.karate.psi.GherkinScenarioOutline;
import com.rankweis.uppercut.karate.psi.GherkinStep;
import com.rankweis.uppercut.karate.psi.GherkinTable;
import com.rankweis.uppercut.karate.psi.GherkinTableRow;
import java.util.List;
import org.jetbrains.annotations.NotNull;


public final class KarateBrokenTableInspection extends GherkinInspection {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new GherkinElementVisitor() {
      @Override
      public void visitScenarioOutline(GherkinScenarioOutline outline) {
        final List<GherkinExamplesBlock> examples = outline.getExamplesBlocks();
        for (GherkinExamplesBlock block : examples) {
          if (block.getTable() != null) {
            checkTable(block.getTable(), holder);
          }
        }
      }

      @Override
      public void visitStep(GherkinStep step) {
        final GherkinTable table = PsiTreeUtil.getChildOfType(step, GherkinTable.class);
        if (table != null) {
          checkTable(table, holder);
        }
      }
    };
  }

  private static void checkTable(@NotNull final GherkinTable table, @NotNull final ProblemsHolder holder) {
    GherkinTableRow header = table.getHeaderRow();
    for (GherkinTableRow row : table.getDataRows()) {
      if (header == null) {
        header = row;
      }
      if (row.getPsiCells().size() != header.getPsiCells().size()) {
        holder.registerProblem(row, MyBundle.message("inspection.gherkin.table.is.broken.row.error.message"));
      }
    }
  }

  @NotNull
  @Override
  public String getShortName() {
    return "KarateBrokenTableInspection";
  }
}
