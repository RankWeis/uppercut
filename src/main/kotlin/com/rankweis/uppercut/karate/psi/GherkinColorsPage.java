package com.rankweis.uppercut.karate.psi;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.rankweis.uppercut.karate.UppercutIcon;
import com.rankweis.uppercut.karate.MyBundle;
import java.util.Map;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

/**
 * @author Roman.Chernyatchik
 */
public final class GherkinColorsPage implements ColorSettingsPage {

 private static final String DEMO_TEXT =
   """
     # language: en
     Feature: Karate Colors Settings Page
       In order to customize Karate language (*.feature files) highlighting
       Our users can use this settings preview pane

       @wip
       Scenario Outline: Different Gherkin language structures
         Given Some feature file with content
         ""\"
         Feature: Some feature
           Scenario: Some scenario
         ""\"
         * def var = "val"
         * def json =
         \"""
         {
           "key1": "value",
           "key2": #(var)
         \"""
         And I want to add new cucumber step
         And Also a step with "<regexp_param>regexp</regexp_param>" parameter
         When I open <<outline_param>ruby_ide</outline_param>>
         Then Steps autocompletion feature will help me with all these tasks

       Examples:
         | <th>ruby_ide</th> |
         | RubyMine |""";

  private static final AttributesDescriptor[] ATTRS = new AttributesDescriptor[]{
    new AttributesDescriptor(MyBundle.message("color.settings.gherkin.text"), GherkinHighlighter.TEXT),
    new AttributesDescriptor(MyBundle.message("color.settings.gherkin.comment"), GherkinHighlighter.COMMENT),
    new AttributesDescriptor(MyBundle.message("color.settings.gherkin.keyword"), GherkinHighlighter.KEYWORD),
    new AttributesDescriptor(MyBundle.message("color.settings.gherkin.variable"), GherkinHighlighter.DECLARATION),
    new AttributesDescriptor(MyBundle.message("color.settings.gherkin.json.expression"), GherkinHighlighter.KARATE_REFERENCE),
    new AttributesDescriptor("Step signifier", GherkinHighlighter.STEP_KEYWORD),
    new AttributesDescriptor(MyBundle.message("color.settings.gherkin.tag"), GherkinHighlighter.TAG),
    new AttributesDescriptor(MyBundle.message("color.settings.gherkin.pystring"), GherkinHighlighter.PYSTRING),
    new AttributesDescriptor(MyBundle.message("color.settings.gherkin.table.header.cell"), GherkinHighlighter.TABLE_HEADER_CELL),
    new AttributesDescriptor(MyBundle.message("color.settings.gherkin.table.cell"), GherkinHighlighter.TABLE_CELL),
    new AttributesDescriptor(MyBundle.message("color.settings.gherkin.table.pipe"), GherkinHighlighter.PIPE),
    new AttributesDescriptor(MyBundle.message("color.settings.gherkin.outline.param.substitution"), GherkinHighlighter.OUTLINE_PARAMETER_SUBSTITUTION),
    new AttributesDescriptor(MyBundle.message("color.settings.gherkin.regexp.param"), GherkinHighlighter.REGEXP_PARAMETER),
  };

  // Empty still
  private static final Map<String, TextAttributesKey> ADDITIONAL_HIGHLIGHT_DESCRIPTORS = Map.of(
    "th", GherkinHighlighter.TABLE_HEADER_CELL,
    "outline_param", GherkinHighlighter.OUTLINE_PARAMETER_SUBSTITUTION,
    "regexp_param", GherkinHighlighter.REGEXP_PARAMETER);

  @Override
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return ADDITIONAL_HIGHLIGHT_DESCRIPTORS;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return MyBundle.message("color.settings.gherkin.name");
  }

  @Override
  @NotNull
  public Icon getIcon() {
    return UppercutIcon.FILE;
  }

  @Override
  public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
    return ATTRS;
  }

  @Override
  public ColorDescriptor @NotNull [] getColorDescriptors() {
    return ColorDescriptor.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public SyntaxHighlighter getHighlighter() {
    return new UppercutSyntaxHighlighter(new PlainKarateKeywordProvider());
  }

  @Override
  @NotNull
  public String getDemoText() {
    return DEMO_TEXT;
  }
}
