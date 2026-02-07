package com.rankweis.uppercut.karate.run;

import com.intellij.execution.application.JavaSettingsEditorBase;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.ui.CommonParameterFragments;
import com.intellij.execution.ui.ModuleClasspathCombo;
import com.intellij.execution.ui.SettingsEditorFragment;
import com.intellij.openapi.externalSystem.service.execution.configuration.fragments.SettingsEditorLabeledComponent;
import com.intellij.openapi.project.Project;
import java.util.List;
import javax.swing.JTextField;

public class KarateSettingsEditor extends JavaSettingsEditorBase<KarateRunConfiguration> {

  public KarateSettingsEditor(Project project, ConfigurationFactory factory, String name) {
    super(new KarateRunConfiguration(project, factory, name));
  }

  @Override protected void customizeFragments(List<SettingsEditorFragment<KarateRunConfiguration, ?>> fragments,
    SettingsEditorFragment<KarateRunConfiguration, ModuleClasspathCombo> moduleClasspath,
    CommonParameterFragments<KarateRunConfiguration> commonParameterFragments) {
    fragments.add(getTestNameField());
    fragments.add(getTagsField());
    fragments.add(getParallelism());
    fragments.add(getEnv());
    fragments.add(getDebugPort());
  }

  private SettingsEditorFragment<KarateRunConfiguration,
    SettingsEditorLabeledComponent<JTextField>> getTestNameField() {
    JTextField textField = new JTextField();
    return new SettingsEditorFragment<>("karate.test.name", "Test name", "Tests",
      new SettingsEditorLabeledComponent<>("Test Name", textField),
      4, (settings, component) -> component.getComponent().setText(settings.getName()),
      (settings, component) -> settings.setTestName(component.getComponent().getText()),
      x -> true
    );
  }

  private SettingsEditorFragment<KarateRunConfiguration, SettingsEditorLabeledComponent<JTextField>> getParallelism() {
    JTextField textField = new JTextField();
    return new SettingsEditorFragment<>("karate.test.parallel", "Parallelism", "Test Options",
      new SettingsEditorLabeledComponent<>("Parallelism", textField),
      6, (settings, component) -> component.getComponent().setText(settings.getParallelism()),
      (settings, component) -> settings.setParallelism(component.getComponent().getText()),
      x -> true
    );
  }

  private SettingsEditorFragment<KarateRunConfiguration, SettingsEditorLabeledComponent<JTextField>> getEnv() {
    JTextField textField = new JTextField();
    return new SettingsEditorFragment<>("karate.test.env", "Environment", "Test Options",
      new SettingsEditorLabeledComponent<>("Environment", textField),
      7, (settings, component) -> component.getComponent().setText(settings.getEnv()),
      (settings, component) -> settings.setEnv(component.getComponent().getText()),
      x -> true
    );
  }

  private SettingsEditorFragment<KarateRunConfiguration, SettingsEditorLabeledComponent<JTextField>> getTagsField() {
    JTextField textField = new JTextField();
    return new SettingsEditorFragment<>("karate.test.tag", "Tag", "Tests",
      new SettingsEditorLabeledComponent<>("Tag", textField),
      5, (settings, component) -> component.getComponent().setText(settings.getTag()),
      (settings, component) -> settings.setTag(component.getComponent().getText()),
      x -> true
    );
  }

  private SettingsEditorFragment<KarateRunConfiguration, SettingsEditorLabeledComponent<JTextField>> getDebugPort() {
    JTextField textField = new JTextField();
    return new SettingsEditorFragment<>("karate.test.debugPort", "Debug port", "Tests",
      new SettingsEditorLabeledComponent<>("Debug port (will suspend if set)", textField),
      6, (settings, component) -> component.getComponent().setText(settings.getDebugPort()),
      (settings, component) -> settings.setDebugPort(component.getComponent().getText()),
      x -> true
    );
  }

}
