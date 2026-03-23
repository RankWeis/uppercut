package com.rankweis.uppercut.karate.run.ui;

import com.intellij.ui.PanelWithAnchor;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.jetbrains.annotations.Nullable;

public class KarateGeneralPanel implements PanelWithAnchor {

  private JPanel panel;
  private JTextField file;
  private JTextField env;
  private JComponent anchor;

  public JComponent createComponent() {
    // all listeners will be removed when dialog is closed
    return panel;
  }

  @Override public JComponent getAnchor() {
    return anchor;
  }

  @Override public void setAnchor(@Nullable JComponent anchor) {
    this.anchor = anchor;
  }
}
