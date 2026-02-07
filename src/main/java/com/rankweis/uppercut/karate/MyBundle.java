// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.

package com.rankweis.uppercut.karate;

import com.intellij.DynamicBundle;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class MyBundle {

  private static final @NonNls String BUNDLE = "messages.MyBundle";
  private static final DynamicBundle INSTANCE = new DynamicBundle(MyBundle.class, BUNDLE);

  private MyBundle() {
  }

  public static @NotNull @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
    Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }

  public static @NotNull Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
    Object @NotNull ... params) {
    return INSTANCE.getLazyMessage(key, params);
  }
}
