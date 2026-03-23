package com.rankweis.uppercut.karate.injector;

import com.intellij.openapi.extensions.ExtensionPointName;

public interface GherkinInjectorExtensionPoint {
  ExtensionPointName<GherkinInjectorExtensionPoint> EP_NAME =
    ExtensionPointName.create("com.rankweis.injectorExtensionPoint");

  /**
   * Returns the prefix to be injected for the specified language
   *
   * @param language injected language
   * @return injection prefix
   */
  default String getPrefix(String language) {
    return "";
  }

  /**
   * Returns the suffix to be injected for the specified language
   *
   * @param language injected language
   * @return injection suffix
   */
  default String getSuffix(String language) {
    return "";
  }
}