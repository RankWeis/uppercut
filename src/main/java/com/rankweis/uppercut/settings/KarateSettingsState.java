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
  /**
   * Whether a Debug run stops on a step that fails. On by default: it is the question a debugger is
   * usually opened for, and the alternative is guessing where to breakpoint and running again. Turn
   * it off when debugging a suite with many expected failures.
   */
  private boolean pauseOnFailedStep = true;
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
    this.pauseOnFailedStep = state.pauseOnFailedStep;
    this.defaultEnvironment = state.defaultEnvironment;
    this.useKarateJavaScriptEngine = state.useKarateJavaScriptEngine;
    this.defaultParallelism = state.defaultParallelism == null ? 1 : state.defaultParallelism;
    this.karateVersionPreference =
      state.karateVersionPreference == null ? KarateVersionPreference.AUTO : state.karateVersionPreference;
  }

}