package com.rankweis.uppercut.karate.run;

import com.rankweis.uppercut.karate.KarateIcons;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.SimpleConfigurationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import org.jetbrains.annotations.NotNull;

public class KarateConfigurationType extends SimpleConfigurationType {

  protected KarateConfigurationType() {
    super("karateConfigurationType", "Karate", "Karate Configuration",
      NotNullLazyValue.lazy(() -> KarateIcons.FILE));
  }
    

  @Override public @NotNull RunConfiguration createTemplateConfiguration(@NotNull Project project) {
    return new KarateRunConfiguration(project, this, "");
  }
}
