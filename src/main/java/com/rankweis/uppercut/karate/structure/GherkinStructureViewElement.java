// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.rankweis.uppercut.karate.structure;

import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.rankweis.uppercut.karate.UppercutIcon;
import com.rankweis.uppercut.karate.psi.GherkinFeature;
import com.rankweis.uppercut.karate.psi.GherkinPsiElement;
import com.rankweis.uppercut.karate.psi.GherkinPystring;
import com.rankweis.uppercut.karate.psi.GherkinStep;
import com.rankweis.uppercut.karate.psi.GherkinStepsHolder;
import com.rankweis.uppercut.karate.psi.impl.GherkinFeatureHeaderImpl;
import com.rankweis.uppercut.karate.psi.impl.GherkinTableImpl;
import com.rankweis.uppercut.karate.psi.impl.GherkinTagImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A node of the Structure view for a feature file: the feature, its scenarios and their steps.
 *
 * <p>Built on the public {@link StructureViewTreeElement} API rather than the platform's
 * {@code PsiTreeElementBase}, which lives in an {@code impl} package that the modules this plugin
 * declares no longer expose (Plugin Verifier reports it missing from 2026.2 on).
 */
public class GherkinStructureViewElement implements StructureViewTreeElement, ItemPresentation {

  private static final StructureViewTreeElement[] NO_CHILDREN = new StructureViewTreeElement[0];

  private final SmartPsiElementPointer<PsiElement> pointer;

  protected GherkinStructureViewElement(@NotNull PsiElement psiElement) {
    pointer = SmartPointerManager.createPointer(psiElement);
  }

  private @Nullable PsiElement getElement() {
    return pointer.getElement();
  }

  /** The PSI element this node stands for; the model uses it to select the node for the caret. */
  @Override
  public Object getValue() {
    return getElement();
  }

  @Override
  public StructureViewTreeElement @NotNull [] getChildren() {
    PsiElement element = getElement();
    if (element == null) {
      return NO_CHILDREN;
    }
    List<StructureViewTreeElement> result = new ArrayList<>();
    for (PsiElement child : element.getChildren()) {
      if (child instanceof GherkinPsiElement
          && !(child instanceof GherkinFeatureHeaderImpl)
          && !(child instanceof GherkinTableImpl)
          && !(child instanceof GherkinTagImpl)
          && !(child instanceof GherkinPystring)) {
        result.add(new GherkinStructureViewElement(child));
      }
    }
    return result.toArray(NO_CHILDREN);
  }

  @Override
  public @NotNull ItemPresentation getPresentation() {
    return this;
  }

  @Override
  public @Nullable String getPresentableText() {
    PsiElement element = getElement();
    if (!(element instanceof NavigationItem item)) {
      return null;
    }
    ItemPresentation presentation = item.getPresentation();
    return presentation == null ? null : presentation.getPresentableText();
  }

  @Override
  public @Nullable Icon getIcon(boolean unused) {
    PsiElement element = getElement();
    if (element instanceof GherkinFeature || element instanceof GherkinStepsHolder) {
      return AllIcons.Nodes.LogFolder;
    }
    if (element instanceof GherkinStep) {
      return UppercutIcon.FILE;
    }
    return null;
  }

  @Override
  public void navigate(boolean requestFocus) {
    PsiElement element = getElement();
    if (element instanceof Navigatable navigatable) {
      navigatable.navigate(requestFocus);
    }
  }

  @Override
  public boolean canNavigate() {
    PsiElement element = getElement();
    return element instanceof Navigatable navigatable && navigatable.canNavigate();
  }

  @Override
  public boolean canNavigateToSource() {
    PsiElement element = getElement();
    return element instanceof Navigatable navigatable && navigatable.canNavigateToSource();
  }

  // The structure view rebuilds its tree on every change and matches old nodes to new ones by
  // equality, so two nodes for the same PSI element must be equal - otherwise expansion state and
  // the selection are lost on each keystroke.
  @Override
  public boolean equals(Object o) {
    return this == o
      || o instanceof GherkinStructureViewElement other && Objects.equals(getElement(), other.getElement());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(getElement());
  }
}
