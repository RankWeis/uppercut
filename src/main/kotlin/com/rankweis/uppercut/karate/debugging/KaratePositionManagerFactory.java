package com.rankweis.uppercut.karate.debugging;

import com.intellij.debugger.PositionManager;
import com.intellij.debugger.PositionManagerFactory;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import org.jetbrains.annotations.NotNull;

public class KaratePositionManagerFactory extends PositionManagerFactory {

  @Override
  public PositionManager createPositionManager(@NotNull DebugProcess process) {
    return new KaratePositionManager((DebugProcessImpl) process);
  }
}
