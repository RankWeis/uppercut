package com.rankweis.uppercut.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

@Setter
@Getter
@State(
  name = "KarateSettingsState",
  storages = @Storage("KaratePluginSettings.xml")
)
@Service(Service.Level.APP)
public final class KarateSettingsState implements PersistentStateComponent<KarateSettingsState> {

  private String defaultEnvironment = "";
  private Integer defaultParallelism = 1;
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
    this.defaultParallelism = state.defaultParallelism == null ? 1 : state.defaultParallelism;
  }

}