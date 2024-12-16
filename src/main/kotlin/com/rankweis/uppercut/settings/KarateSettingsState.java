package com.rankweis.uppercut.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.Nullable;

@State(
        name = "KarateSettingsState",
        storages = @Storage("KaratePluginSettings.xml")
)
@Service(Service.Level.APP)
public final class KarateSettingsState implements PersistentStateComponent<KarateSettingsState> {

    private String defaultEnvironment = "";
    private boolean useKarateJavaScriptEngine = false;

    public static KarateSettingsState getInstance() {
        return com.intellij.openapi.application.ApplicationManager.getApplication()
                .getService(KarateSettingsState.class);
    }

    @Nullable
    @Override
    public KarateSettingsState getState() {
        return this;
    }

    @Override
    public void loadState(KarateSettingsState state) {
        this.defaultEnvironment = state.defaultEnvironment;
        this.useKarateJavaScriptEngine = state.useKarateJavaScriptEngine;
    }

    public String getDefaultEnvironment() {
        return defaultEnvironment;
    }

    public void setDefaultEnvironment(String defaultEnvironment) {
        this.defaultEnvironment = defaultEnvironment;
    }

    public boolean isUseKarateJavaScriptEngine() {
        return useKarateJavaScriptEngine;
    }

    public void setUseKarateJavaScriptEngine(boolean useKarateJavaScriptEngine) {
        this.useKarateJavaScriptEngine = useKarateJavaScriptEngine;
    }
}