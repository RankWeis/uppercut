// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.rankweis.uppercut.karate.inspections;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.rankweis.uppercut.karate.MyBundle;
import com.rankweis.uppercut.karate.psi.GherkinElementFactory;
import com.rankweis.uppercut.karate.psi.GherkinExamplesBlock;
import com.rankweis.uppercut.karate.psi.GherkinFile;
import com.rankweis.uppercut.karate.psi.GherkinKeywordTable;
import com.rankweis.uppercut.karate.psi.GherkinScenarioOutline;
import com.rankweis.uppercut.karate.psi.GherkinStep;
import com.rankweis.uppercut.karate.psi.GherkinTable;
import com.rankweis.uppercut.karate.psi.GherkinTableCell;
import com.rankweis.uppercut.karate.psi.GherkinTableRow;
import com.rankweis.uppercut.karate.psi.GherkinUtil;
import com.rankweis.uppercut.karate.psi.i18n.JsonGherkinKeywordProvider;
import com.rankweis.uppercut.karate.psi.impl.GherkinExamplesBlockImpl;
import com.rankweis.uppercut.karate.psi.impl.GherkinScenarioOutlineImpl;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class CucumberCreateExamplesSectionFix implements LocalQuickFix {

  @Override
  public @NotNull String getFamilyName() {
    return MyBundle.message("intention.family.name.create.examples.section");
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
    final GherkinScenarioOutlineImpl outline = (GherkinScenarioOutlineImpl) descriptor.getPsiElement();

    final GherkinFile featureFile = (GherkinFile)outline.getContainingFile();

    final String language = GherkinUtil.getFeatureLanguage(featureFile);
    final GherkinKeywordTable keywordsTable = JsonGherkinKeywordProvider.getKeywordProvider(featureFile).getKeywordsTable(language);

    final StringBuilder buff = new StringBuilder();
    buff.append(keywordsTable.getScenarioOutlineKeyword()).append(": boo\n");
    buff.append(keywordsTable.getExampleSectionKeyword()).append(":\n|");

    final List<String> params = new ArrayList<>();
    final PsiElement[] elements = outline.getChildren();
    for (PsiElement element : elements) {
      if (!(element instanceof GherkinStep step)) {
        continue;
      }
      final List<String> substitutions = step.getParamsSubstitutions();
      for (String substitution : substitutions) {
        if (!params.contains(substitution)) {
          params.add(substitution);
        }
      }
    }
    if (params.isEmpty()) {
      buff.append(" |");
    } else {
      for (String substitution : params) {
        buff.append(' ').append(substitution).append(" |");
      }
    }

    final String text = buff.toString();
    GherkinScenarioOutline
      fakeScenario = (GherkinScenarioOutline) GherkinElementFactory.createScenarioFromText(project, language, text);

    final GherkinExamplesBlock fakeExampleSection = fakeScenario.getExamplesBlocks().get(0);
    assert fakeExampleSection != null;

    GherkinExamplesBlockImpl addedSection = (GherkinExamplesBlockImpl)outline.add(fakeExampleSection);
    addedSection = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(addedSection);
    final GherkinTable table = addedSection != null ? addedSection.getTable() : null;
    assert table != null;
    final GherkinTableRow headerRow = table.getHeaderRow();
    assert headerRow != null;
    final List<GherkinTableCell> cells = headerRow.getPsiCells();
    final int firstCellOffset = !cells.isEmpty() && cells.get(0).getTextLength() > 0 ?
                                 cells.get(0).getTextOffset() : headerRow.getTextOffset() + 1;

    final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    assert editor != null;

    // commit current document
    final Document document = editor.getDocument();
    PsiDocumentManager.getInstance(project).commitDocument(document);

    editor.getCaretModel().moveToOffset(firstCellOffset);
  }
}
