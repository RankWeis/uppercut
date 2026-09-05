package com.rankweis.uppercut.help;

import com.intellij.openapi.help.WebHelpProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Routes the IDE's context help - the "?" button in Settings and the F1 help action - to the plugin's
 * documentation site. A help topic id of {@code com.rankweis.<page>} opens {@code <page>} on the site.
 */
public class UppercutWebHelpProvider extends WebHelpProvider {

  public static final String SITE = "https://rankweis.github.io/uppercut/";

  /** Help topic for the Settings > Tools > Karate page. */
  public static final String SETTINGS_TOPIC = "com.rankweis.settings";

  @Override
  public @Nullable String getHelpPageUrl(@NotNull String helpTopicId) {
    String page = helpTopicId.substring(getHelpTopicPrefix().length());
    return SITE + page;
  }
}
