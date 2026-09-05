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

  /** Karate major version driving run configurations: AUTO (classpath detection), V1, or V2. */
  public enum KarateVersionPreference {
    AUTO,
    V1,
    V2
  }

  private String defaultEnvironment = "";
  private Integer defaultParallelism = 1;
  private boolean useKarateJavaScriptEngine = false;
  private KarateVersionPreference karateVersionPreference = KarateVersionPreference.AUTO;

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
    this.karateVersionPreference =
      state.karateVersionPreference == null ? KarateVersionPreference.AUTO : state.karateVersionPreference;
  }

}