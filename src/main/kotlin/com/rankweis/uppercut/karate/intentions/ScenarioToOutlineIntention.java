// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0
// license that can be found in the LICENSE file.

package com.rankweis.uppercut.karate.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.rankweis.uppercut.karate.CucumberUtil;
import com.rankweis.uppercut.karate.MyBundle;
import com.rankweis.uppercut.karate.psi.GherkinElementFactory;
import com.rankweis.uppercut.karate.psi.GherkinFile;
import com.rankweis.uppercut.karate.psi.GherkinKeywordTable;
import com.rankweis.uppercut.karate.psi.GherkinScenario;
import com.rankweis.uppercut.karate.psi.GherkinStep;
import com.rankweis.uppercut.karate.psi.GherkinStepsHolder;
import com.rankweis.uppercut.karate.psi.GherkinTag;
import com.rankweis.uppercut.karate.psi.GherkinUtil;
import com.rankweis.uppercut.karate.psi.KarateLanguage;
import com.rankweis.uppercut.karate.psi.i18n.JsonGherkinKeywordProvider;
import com.rankweis.uppercut.karate.steps.AbstractStepDefinition;
import com.rankweis.uppercut.karate.steps.reference.CucumberStepReference;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

public final class ScenarioToOutlineIntention implements IntentionAction {

  public static final String ARGUMENT = "argument";

  @Override
  @NotNull
  public String getText() {
    return MyBundle.message("intention.convert.scenario.to.outline.name");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return MyBundle.message("intention.convert.scenario.to.outline.name");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file.getLanguage() instanceof KarateLanguage)) {
      return false;
    }
    int offset = editor.getCaretModel().getOffset();
    final PsiElement element = file.findElementAt(offset);
    if (element == null) {
      return false;
    }
    final GherkinScenario scenario = PsiTreeUtil.getParentOfType(element, GherkinScenario.class);
    return scenario != null && canConvertScenario(scenario);
  }

  private static boolean canConvertScenario(GherkinScenario scenario) {
    if (scenario.isBackground()) {
      return false;
    }
    for (GherkinStep step : scenario.getSteps()) {
      if (step.getTable() != null || step.getPystring() != null) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    final GherkinScenario scenario = PsiTreeUtil.getParentOfType(element, GherkinScenario.class);
    assert scenario != null;
    assert file instanceof GherkinFile;

    final String language = GherkinUtil.getFeatureLanguage((GherkinFile) file);
    final GherkinKeywordTable keywordsTable =
      JsonGherkinKeywordProvider.getKeywordProvider().getKeywordsTable(language);

    final StringBuilder newScenarioText = new StringBuilder();
    for (GherkinTag tag : scenario.getTags()) {
      newScenarioText.append(tag.getName()).append(' ');
    }
    if (scenario.getTags().length > 0) {
      newScenarioText.append('\n');
    }
    newScenarioText.append(keywordsTable.getScenarioOutlineKeyword()).append(": ").append(scenario.getScenarioName());
    Map<String, String> examples = new LinkedHashMap<>();
    for (GherkinStep step : scenario.getSteps()) {
      CucumberStepReference reference = CucumberUtil.getCucumberStepReference(step);
      final AbstractStepDefinition definition = reference != null ? reference.resolveToDefinition() : null;
      if (definition != null) {
        String stepName = replaceVarNames(step.getName(), definition, examples);
        newScenarioText.append("\n").append(step.getKeyword().getText()).append(" ").append(stepName);
      } else {
        newScenarioText.append("\n").append(step.getText());
      }
    }
    newScenarioText.append("\n").append(buildExamplesSection(examples, keywordsTable.getExampleSectionKeyword()));

    final GherkinStepsHolder
      newScenario = GherkinElementFactory.createScenarioFromText(project, language, newScenarioText.toString());
    scenario.replace(newScenario);
  }

  private static String replaceVarNames(String stepName, AbstractStepDefinition definition,
    Map<String, String> examples) {
    final List<String> varNames = definition.getVariableNames();
    if (!varNames.isEmpty()) {
      final Pattern pattern = definition.getPattern();

      if (pattern != null) {
        int offset = 0;
        Matcher matcher = pattern.matcher(stepName);
        if (matcher.matches()) {
          int groupCount = matcher.groupCount();
          for (int i = 0; i < groupCount; i++) {
            if (matcher.group(i + 1) == null) {
              continue;
            }
            String name = (i < varNames.size()) ? varNames.get(i) : ARGUMENT;
            name = uniqueName(name, examples, matcher.group(i + 1));
            final int start = matcher.start(i + 1);
            final int end = matcher.end(i + 1);

            String referencedValue = "<" + name + ">";
            stepName =
              StringUtil.replaceSubstring(stepName, new TextRange(start + offset, end + offset), referencedValue);
            offset += referencedValue.length() - (end - start);
          }
        }
      }
    }
    return stepName;
  }

  private static String buildExamplesSection(Map<String, String> examples, String keyword) {
    StringBuilder builder = new StringBuilder(keyword);
    builder.append(":\n");
    if (!examples.isEmpty()) {
      for (String key : examples.keySet()) {
        builder.append("|").append(key);
      }
      builder.append("|\n");
      for (String key : examples.keySet()) {
        builder.append("|").append(examples.get(key));
      }
      builder.append("|\n");
    }
    return builder.toString();
  }

  private static String uniqueName(@NotNull String name, @NotNull Map<String, String> examples, @NotNull String value) {
    String candidate = name;
    int i = 1;
    while (examples.containsKey(candidate) && !examples.get(candidate).equals(value)) {
      candidate = name + i++;
    }
    examples.put(candidate, value);
    return candidate;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
