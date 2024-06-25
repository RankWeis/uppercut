package com.rankweis.uppercut.settings;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.NlsContexts.ConfigurableName;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UppercutSettingsConfigurable implements SearchableConfigurable {

  private String defaultEnvironment;
  
  public UppercutSettingsConfigurable() {
    defaultEnvironment =
      PropertiesComponent.getInstance().getValue("uppercut.settings.defaultEnvironment");
  }

  private JTextField defaultEnvironmentField;
  private JPanel myMainPanel;

  @Override public @NotNull @NonNls String getId() {
    return "uppercut.settings";
  }

  @Override public @ConfigurableName String getDisplayName() {
    return "Karate Settings";
  }

  @Override public @Nullable JComponent createComponent() {
    return myMainPanel;
  }

  @Override public boolean isModified() {
    return !defaultEnvironmentField.getText().equals(defaultEnvironment);
  }

  @Override public void apply() throws ConfigurationException {
    setDefaultEnvironment(defaultEnvironmentField.getText());
    PropertiesComponent.getInstance().setValue("uppercut.settings.defaultEnvironment", getDefaultEnvironment());
  }

  public String getDefaultEnvironment() {
    return defaultEnvironment;
  }

  public void setDefaultEnvironment(String defaultEnvironment) {
    this.defaultEnvironment = defaultEnvironment;
  }

  private void createUIComponents() {
    String value = PropertiesComponent.getInstance().getValue("uppercut.settings.defaultEnvironment");
    defaultEnvironmentField = new JTextField(value);
    setDefaultEnvironment(value);
    defaultEnvironmentField.addActionListener((a) -> setDefaultEnvironment(defaultEnvironmentField.getText()));
  }
}
