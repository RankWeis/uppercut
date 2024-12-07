package com.rankweis.uppercut.parser.types;

import com.intellij.psi.tree.IElementType;
import com.rankweis.uppercut.karate.psi.KarateJsLanguage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class KarateJsElementType extends IElementType {

  public KarateJsElementType(@NonNls @NotNull String debugName) {
    super(debugName, KarateJsLanguage.INSTANCE);
  }

  protected KarateJsElementType(@NonNls @NotNull String debugName, boolean register) {
    super(debugName, KarateJsLanguage.INSTANCE, register);
  }
}
