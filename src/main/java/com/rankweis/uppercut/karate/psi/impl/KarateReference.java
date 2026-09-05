package com.rankweis.uppercut.karate.psi.impl;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.rankweis.uppercut.karate.navigation.KarateFeatureFiles;
import com.rankweis.uppercut.karate.psi.GherkinScenario;
import com.rankweis.uppercut.karate.psi.GherkinStep;
import com.rankweis.uppercut.karate.psi.GherkinStepsHolder;
import com.rankweis.uppercut.karate.psi.KarateDeclaration;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A variable reference on a step line. The key is the dotted path from the variable to the
 * referenced segment: for {@code auth.token} the reference on {@code token} has key
 * {@code auth.token} and the one on {@code auth} has key {@code auth}.
 *
 * <p>The head resolves to a {@code def} in the enclosing scenario or the Background. Each further
 * segment resolves only when the previous one is a {@code def x = call read('other.feature')}: a
 * called feature's variables come back as the properties of its result, so the segment is looked up
 * among that feature's own {@code def}s. Anything else - JSON, a response, a {@code karate.*}
 * member - stays unresolved, and the reference is soft so that is never an error.
 */
public class KarateReference extends PsiReferenceBase<PsiElement> implements PsiPolyVariantReference {

  private static final int MAX_CALL_DEPTH = 8;

  private final String key;

  public KarateReference(@NotNull PsiElement element,
    TextRange rangeInElement, boolean soft) {
    this(element, rangeInElement, rangeInElement.substring(element.getText()), soft);
  }

  public KarateReference(@NotNull PsiElement element, TextRange rangeInElement, @NotNull String key,
    boolean soft) {
    super(element, rangeInElement, soft);
    this.key = key;
  }

  @Override public boolean isReferenceTo(@NotNull PsiElement element) {
    return resolve() == element;
  }

  /**
   * Soft only while unresolved. Several references share an offset on a step line - this one and
   * the step-definition reference over the whole step - and the platform picks the one to underline
   * on Cmd-hover by: non-soft first, then resolving, then innermost. A resolving variable reference
   * has to be non-soft to win that, or the whole line lights up; an unresolved one stays soft so an
   * unknown name is never marked as an error.
   */
  @Override public boolean isSoft() {
    return multiResolve(false).length == 0;
  }

  @Override public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    if (!myElement.isValid()) {
      return ResolveResult.EMPTY_ARRAY;
    }
    return ResolveCache.getInstance(myElement.getProject())
      .resolveWithCaching(this, RESOLVER, false, incompleteCode);
  }

  private static final ResolveCache.PolyVariantResolver<KarateReference> RESOLVER =
    (reference, incompleteCode) -> reference.resolveInner();

  private ResolveResult @NotNull [] resolveInner() {
    String[] segments = key.split("\\.");
    KarateDeclaration declaration = resolveInScope(segments[0]);
    for (int i = 1; declaration != null && i < segments.length && i <= MAX_CALL_DEPTH; i++) {
      PsiFile called = calledFeature(declaration);
      declaration = called == null ? null : findDeclarationInFile(called, segments[i]);
    }
    return declaration == null
      ? ResolveResult.EMPTY_ARRAY
      : new PsiElementResolveResult[]{new PsiElementResolveResult(declaration)};
  }

  /** A {@code def name} in the enclosing scenario, then in the file's Background. */
  private @Nullable KarateDeclaration resolveInScope(String name) {
    PsiElement parent =
      PsiTreeUtil.findFirstParent(myElement, GherkinStepsHolder.class::isInstance);
    KarateDeclaration match = findDeclarationInSteps(
      PsiTreeUtil.getChildrenOfType(parent, GherkinStep.class), name);
    if (match != null) {
      return match;
    }
    Collection<GherkinScenario> scenarios =
      PsiTreeUtil.findChildrenOfType(myElement.getContainingFile(), GherkinScenario.class);
    for (GherkinScenario scenario : scenarios) {
      if (!scenario.isBackground()) {
        continue;
      }
      match = findDeclarationInSteps(PsiTreeUtil.getChildrenOfType(scenario, GherkinStep.class), name);
      if (match != null) {
        return match;
      }
    }
    return null;
  }

  /** The feature {@code declaration}'s step calls with {@code call read('...')}, if it does. */
  private static @Nullable PsiFile calledFeature(@NotNull KarateDeclaration declaration) {
    GherkinStep step = PsiTreeUtil.getParentOfType(declaration, GherkinStep.class);
    if (step == null) {
      return null;
    }
    String path = KarateFeatureFiles.calledFeaturePath(step);
    return path == null ? null : KarateFeatureFiles.resolve(declaration.getContainingFile(), path);
  }

  /** A {@code def name} anywhere in {@code file} - Background first, then each scenario in order. */
  private static @Nullable KarateDeclaration findDeclarationInFile(@NotNull PsiFile file, String name) {
    Collection<GherkinStepsHolder> holders = PsiTreeUtil.findChildrenOfType(file, GherkinStepsHolder.class);
    for (GherkinStepsHolder holder : holders) {
      if (holder instanceof GherkinScenario scenario && scenario.isBackground()) {
        KarateDeclaration match = findDeclarationInSteps(holder.getSteps(), name);
        if (match != null) {
          return match;
        }
      }
    }
    for (GherkinStepsHolder holder : holders) {
      KarateDeclaration match = findDeclarationInSteps(holder.getSteps(), name);
      if (match != null) {
        return match;
      }
    }
    return null;
  }

  private static @Nullable KarateDeclaration findDeclarationInSteps(GherkinStep @Nullable [] steps, String name) {
    if (steps == null) {
      return null;
    }
    for (GherkinStep step : steps) {
      KarateDeclaration[] decls = PsiTreeUtil.getChildrenOfType(step, KarateDeclaration.class);
      if (decls == null) {
        continue;
      }
      for (KarateDeclaration decl : decls) {
        if (name.equals(decl.getName())) {
          return decl;
        }
      }
    }
    return null;
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    if (myElement instanceof KarateDeclaration declaration) {
      return declaration.setName(newElementName);
    }
    return super.handleElementRename(newElementName);
  }

  @Override public @Nullable PsiElement resolve() {
    ResolveResult[] resolveResults = multiResolve(false);
    return resolveResults.length == 1 ? resolveResults[0].getElement() : null;
  }

  @Override public Object @NotNull [] getVariants() {
    return super.getVariants();
  }

  public String getKey() {
    return key;
  }
}