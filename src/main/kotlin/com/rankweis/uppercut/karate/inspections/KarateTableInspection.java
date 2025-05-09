// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.rankweis.uppercut.karate.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.rankweis.uppercut.karate.MyBundle;
import com.rankweis.uppercut.karate.psi.GherkinElementVisitor;
import com.rankweis.uppercut.karate.psi.GherkinExamplesBlock;
import com.rankweis.uppercut.karate.psi.GherkinScenarioOutline;
import com.rankweis.uppercut.karate.psi.GherkinStep;
import com.rankweis.uppercut.karate.psi.GherkinTable;
import com.rankweis.uppercut.karate.psi.GherkinTableCell;
import com.rankweis.uppercut.karate.psi.GherkinTableRow;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;



public final class KarateTableInspection extends GherkinInspection {
  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public String getShortName() {
    return "KarateTableInspection";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new GherkinElementVisitor() {
      @Override
      public void visitScenarioOutline(GherkinScenarioOutline outline) {
        final List<GherkinExamplesBlock> examples = outline.getExamplesBlocks();
        if (!examples.isEmpty()) {
          Collection<String> columnNames = collectUsedColumnNames(outline);
          for (GherkinExamplesBlock block : examples) {
            checkTable(block.getTable(), columnNames, holder);
          }
        }
      }
    };
  }

  private static void checkTable(GherkinTable table, Collection<String> columnNames, ProblemsHolder holder) {
    final GherkinTableRow row = table != null ? table.getHeaderRow() : null;
    if (row == null) {
      return;
    }
    final List<GherkinTableCell> cells = row.getPsiCells();
    IntList unusedIndices = new IntArrayList();

    for (int i = 0, cellsSize = cells.size(); i < cellsSize; i++) {
      String columnName = cells.get(i).getText().trim();
      if (!columnNames.contains(columnName)) {
        unusedIndices.add(i);
      }
    }

    if (!unusedIndices.isEmpty()) {
      highlightUnusedColumns(row, unusedIndices, holder);
      for (GherkinTableRow tableRow : table.getDataRows()) {
        highlightUnusedColumns(tableRow, unusedIndices, holder);
      }
    }
  }

  private static void highlightUnusedColumns(GherkinTableRow row, IntList unusedIndices, ProblemsHolder holder) {
    final List<GherkinTableCell> cells = row.getPsiCells();
    final int cellsCount = cells.size();

    for (int i : unusedIndices.toIntArray()) {
      if (i < cellsCount && cells.get(i).getTextLength() > 0) {
        holder.registerProblem(cells.get(i), MyBundle.message("unused.table.column"), new RemoveTableColumnFix(i));
      }
    }
  }

  private static Collection<String> collectUsedColumnNames(GherkinScenarioOutline outline) {
    Set<String> result = new HashSet<>();
    for (GherkinStep step : outline.getSteps()) {
      result.addAll(step.getParamsSubstitutions());
    }
    return result;
  }
}
