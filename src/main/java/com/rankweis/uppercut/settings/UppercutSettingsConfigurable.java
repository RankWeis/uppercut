package com.rankweis.uppercut.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.NlsContexts.ConfigurableName;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import java.text.NumberFormat;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.text.NumberFormatter;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Setter
@Getter
@Slf4j
public class UppercutSettingsConfigurable implements SearchableConfigurable, Configurable.NoScroll {

  private KarateSettingsState settingsState;

  public UppercutSettingsConfigurable() {
    settingsState = KarateSettingsState.getInstance();
    format.setGroupingUsed(false);
    numberFormatter.setValueClass(Integer.class);
  }

  NumberFormat format = NumberFormat.getIntegerInstance();

  NumberFormatter numberFormatter = new NumberFormatter(format);

  private JPanel panel;
  private JTextField defaultEnvironmentField;
  private JFormattedTextField defaultParallelismField;
  private JCheckBox useKarateJsCheckbox;
  private JPanel myMainPanel;

  @Override
  public @NotNull @NonNls String getId() {
    return "uppercut.settings";
  }

  @Override
  public @ConfigurableName String getDisplayName() {
    return "Karate Settings";
  }

  @Override
  public boolean isModified() {
    Integer defaultParallelism = 1;
    try {
      defaultParallelism = Integer.parseInt(defaultParallelismField.getText());
    } catch (NumberFormatException ignored) {
      log.warn("Default parallelism is not a number - this can be ignored");
    }
    return !defaultEnvironmentField.getText().equals(settingsState.getDefaultEnvironment())
      || useKarateJsCheckbox.isSelected() != settingsState.isUseKarateJavaScriptEngine()
      || !defaultParallelism.equals(settingsState.getDefaultParallelism());
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return FormBuilder.createFormBuilder()
      .addLabeledComponent("Default environment:", defaultEnvironmentField = new JBTextField(), true)
      .addLabeledComponent("Default parallelism:", defaultParallelismField = new JFormattedTextField(numberFormatter),
        true)
      .addComponent(new JBSplitter())
      .addComponent(useKarateJsCheckbox = new JBCheckBox("Use Karate JavaScript engine (restart required)"))
      .getPanel();
  }

  @Override
  public void disposeUIResources() {
    SearchableConfigurable.super.disposeUIResources();
    panel = null;
    defaultEnvironmentField = null;
    defaultParallelismField = null;
    useKarateJsCheckbox = null;
  }

  @Override
  public void reset() {
    SearchableConfigurable.super.reset();
    defaultEnvironmentField.setText(settingsState.getDefaultEnvironment());
    defaultParallelismField.setText(String.valueOf(settingsState.getDefaultParallelism()));
    useKarateJsCheckbox.setSelected(settingsState.isUseKarateJavaScriptEngine());
  }

  @Override
  public void apply() {
    Integer defaultParallelism = 1;
    try {
      defaultParallelism = Integer.parseInt(defaultParallelismField.getText());
    } catch (NumberFormatException ignored) {
      log.warn("Default parallelism is not a number - this can be ignored");
    }
    settingsState.setDefaultEnvironment(defaultEnvironmentField.getText());
    settingsState.setDefaultParallelism(defaultParallelism);
    settingsState.setUseKarateJavaScriptEngine(useKarateJsCheckbox.isSelected());
  }
}
