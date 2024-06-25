package com.rankweis.uppercut.karate.psi.annotator;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author Roman.Chernyatchik
 */
public final class GherkinAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull final PsiElement psiElement, @NotNull final AnnotationHolder holder) {
        final GherkinAnnotatorVisitor visitor = new GherkinAnnotatorVisitor(holder);
        psiElement.accept(visitor);
    }
}
