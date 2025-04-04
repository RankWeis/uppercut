package com.rankweis.uppercut.karate.run;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.SimpleConfigurationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.rankweis.uppercut.karate.UppercutIcon;
import org.jetbrains.annotations.NotNull;

public class KarateConfigurationType extends SimpleConfigurationType {

  public static KarateConfigurationType INSTANCE = new KarateConfigurationType();

  protected KarateConfigurationType() {
    super("karateConfigurationType", "Karate", "Karate Configuration",
      NotNullLazyValue.lazy(() -> UppercutIcon.FILE));
  }
    

  @Override public @NotNull RunConfiguration createTemplateConfiguration(@NotNull Project project) {
    return new KarateRunConfiguration(project, this, "Karate Run Template");
  }
}
