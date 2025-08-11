package com.rankweis.uppercut.karate.run;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.LazyRunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtilCore;
import com.rankweis.uppercut.karate.psi.KarateTokenTypes;
import com.rankweis.uppercut.karate.run.KarateRunConfiguration.PreferredTest;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class KarateRunConfigurationProducer extends LazyRunConfigurationProducer<KarateRunConfiguration> {

  @NotNull
  @Override
  public ConfigurationFactory getConfigurationFactory() {
    return KarateConfigurationType.INSTANCE;
  }

  @Override
  public boolean isPreferredConfiguration(ConfigurationFromContext self, ConfigurationFromContext other) {
    return super.isPreferredConfiguration(self, other);
  }

  @Override
  public boolean shouldReplace(@NotNull ConfigurationFromContext self, @NotNull ConfigurationFromContext other) {
    return ((KarateRunConfiguration) self.getConfiguration()).isAllInFolderAreFeature();
  }

  @Override
  public boolean isConfigurationFromContext(@NotNull KarateRunConfiguration configuration,
    @NotNull ConfigurationContext context) {

    PsiElement psiElement = context.getLocation().getPsiElement();
    PsiFile containingFile = psiElement.getContainingFile();
    if (containingFile == null) {
      return false;
    }
    Project project = containingFile.getProject();
    PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
    Document document = psiDocumentManager.getDocument(containingFile);
    int textOffset = psiElement.getTextOffset();
    if (document == null) {
      return false;
    }
    int lineNumber = document.getLineNumber(textOffset) + 1;
    PreferredTest preferredTest = PreferredTest.WHOLE_FILE;
    IElementType elementType = PsiUtilCore.getElementType(psiElement);
    if (elementType == KarateTokenTypes.TAG) {
      preferredTest = PreferredTest.ALL_TAGS;
    } else if (KarateTokenTypes.SCENARIOS_KEYWORDS.contains(elementType)) {
      preferredTest = PreferredTest.SINGLE_SCENARIO;
    }
    VirtualFile virtualFile = context.getLocation().getVirtualFile();
    if (preferredTest == PreferredTest.ALL_TAGS) {
      return configuration.getName().equals(context.getPsiLocation().getText());
    } else if (preferredTest == PreferredTest.SINGLE_SCENARIO) {
      return configuration.getName().equals(virtualFile.get() + ":" + lineNumber);
    } else {
      return configuration.getName().equals(virtualFile.getName());
    }
  }

  @Override
  public @Nullable ConfigurationFromContext createConfigurationFromContext(@NotNull ConfigurationContext context) {
    return super.createConfigurationFromContext(context);
  }

  @Override
  protected boolean setupConfigurationFromContext(@NotNull KarateRunConfiguration configuration,
    @NotNull ConfigurationContext context,
    @NotNull Ref<PsiElement> sourceElement) {
    String baseDir = FileUtil.toSystemIndependentName(StringUtil.notNullize(context.getProject().getBasePath()));
    final PsiElement location = context.getPsiLocation();
    configuration.setWorkingDirectory(baseDir);
    Module contextModule = context.getModule();
    if (contextModule != null) {
      configuration.setModule(contextModule);
    }
    Optional<VirtualFile> virtualFile =
      Optional.of(context).map(ConfigurationContext::getLocation).map(Location::getVirtualFile);
    if (location instanceof PsiDirectory) {
      return virtualFile.map(
          v -> this.setupRunnerParametersForFolderIfApplicable(contextModule, configuration,
            v))
        .orElse(false);

    }
    final String name = virtualFile.map(VirtualFile::getName).orElse(null);
    final String path = virtualFile.map(VirtualFile::getPath).orElse(null);

    PsiElement psiElement = sourceElement.get();
    PsiFile containingFile = psiElement.getContainingFile();
    if (containingFile == null) {
      return false;
    }
    Project project = containingFile.getProject();
    PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
    Document document = psiDocumentManager.getDocument(containingFile);
    final int textOffset = psiElement.getTextOffset();
    if (document == null) {
      return false;
    }
    PreferredTest preferredTest = PreferredTest.WHOLE_FILE;
    IElementType elementType = PsiUtilCore.getElementType(psiElement);
    if (elementType == KarateTokenTypes.TAG) {
      preferredTest = PreferredTest.ALL_TAGS;
      Arrays.stream(ModuleManager.getInstance(context.getProject()).getModules())
        .flatMap(m -> Arrays.stream(ModuleRootManager.getInstance(m).getSourceRoots()))
        .map(root -> (path != null && path.contains(root.getPath())) ? root : null).filter(Objects::nonNull).findFirst()
        .ifPresent(vf -> configuration.setWorkingDirectory(vf.getPath()));
      configuration.setTag(psiElement.getText());
    } else if (KarateTokenTypes.SCENARIOS_KEYWORDS.contains(elementType)) {
      preferredTest = PreferredTest.SINGLE_SCENARIO;
    }

    configuration.setPreferredTest(preferredTest);
    sourceElement.set(containingFile);
    int lineNumber = document.getLineNumber(textOffset) + 1;
    configuration.setLineNumber(lineNumber);
    configuration.setTestName(name);
    configuration.setPath(path);
    String relPath = getRelativePathFromModule(contextModule, path, name);
    configuration.setRelPath(relPath);
    PsiElement nextElement =
      PsiUtilCore.getElementAtOffset(containingFile, psiElement.getTextOffset() + psiElement.getTextOffset() + 1);
    configuration.setTestDescription(nextElement.getText());
    if (preferredTest == PreferredTest.ALL_TAGS) {
      configuration.setName(configuration.getTag());
    } else if (preferredTest == PreferredTest.SINGLE_SCENARIO) {
      configuration.setName(name + ":" + lineNumber);
    } else {
      configuration.setName(name);
    }
    return true;
  }

  private boolean setupRunnerParametersForFolderIfApplicable(final Module module,
    @NotNull final KarateRunConfiguration configuration,
    @NotNull final VirtualFile dir) {
    if (module == null) {
      return false;
    }
    if (Arrays.stream(dir.getChildren()).map(VirtualFile::getName).allMatch(s -> s.endsWith(".feature"))) {
      configuration.setAllInFolderAreFeature(true);
    }
    configuration.setPath(getRelativePathFromModule(module, dir.getPath(), dir.getPath()));
    configuration.setPreferredTest(PreferredTest.ALL_IN_FOLDER);
    configuration.setName("Karate tests in '" + dir.getName() + "'");
    return true;
  }

  private static String getRelativePathFromModule(Module contextModule, String path, String name) {
    if (contextModule == null) {
      return "";
    }
    return Arrays.stream(ModuleRootManager.getInstance(contextModule).getSourceRoots()).map(
        VirtualFile::getPath)
      .filter(s -> FileUtil.isAncestor(s, path, false))
      .map(s -> Optional.ofNullable(FileUtil.getRelativePath(s, path, '/')))
      .findFirst()
      .flatMap(Function.identity())
      .orElse(name);
  }
}
