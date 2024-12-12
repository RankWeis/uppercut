package com.rankweis.uppercut.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.NlsContexts.ConfigurableName;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Setter
@Getter
public class UppercutSettingsConfigurable implements SearchableConfigurable, Configurable.NoScroll {

    private KarateSettingsState settingsState;

    public UppercutSettingsConfigurable() {
        settingsState = KarateSettingsState.getInstance();
    }

    private JPanel panel;
    private JTextField defaultEnvironmentField;
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
        return !defaultEnvironmentField.getText().equals(settingsState.getDefaultEnvironment()) ||
                useKarateJsCheckbox.isSelected() != settingsState.isUseKarateJavaScriptEngine();
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        return FormBuilder.createFormBuilder()
                .addLabeledComponent("Default environment:", defaultEnvironmentField = new JBTextField(), true)
                .addComponent(new JBSplitter())
                .addComponent(useKarateJsCheckbox = new JBCheckBox("Use Karate JavaScript engine (restart required)"))
                .getPanel();
    }

    @Override
    public void disposeUIResources() {
        SearchableConfigurable.super.disposeUIResources();
        panel = null;
        defaultEnvironmentField = null;
        useKarateJsCheckbox = null;
    }

    @Override
    public void reset() {
        SearchableConfigurable.super.reset();
        defaultEnvironmentField.setText(settingsState.getDefaultEnvironment());
        useKarateJsCheckbox.setSelected(settingsState.isUseKarateJavaScriptEngine());
    }

    @Override
    public void apply() {
        settingsState.setDefaultEnvironment(defaultEnvironmentField.getText());
        settingsState.setUseKarateJavaScriptEngine(useKarateJsCheckbox.isSelected());
    }
}
