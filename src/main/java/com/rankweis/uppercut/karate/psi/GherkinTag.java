package com.rankweis.uppercut.karate.psi;

public interface GherkinTag extends GherkinPsiElement {
  GherkinTag[] EMPTY_ARRAY = new GherkinTag[0];

  String getName();
}
