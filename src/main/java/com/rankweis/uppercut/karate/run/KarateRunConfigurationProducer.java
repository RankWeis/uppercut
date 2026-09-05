package com.rankweis.uppercut.karate.run;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.LazyRunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.rankweis.uppercut.karate.psi.GherkinFile;
import com.rankweis.uppercut.karate.psi.GherkinStepsHolder;
import com.rankweis.uppercut.karate.psi.KarateTokenTypes;
import com.rankweis.uppercut.karate.run.KarateRunConfiguration.PreferredTest;
import java.util.Arrays;
import java.util.Comparator;
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

  /**
   * On a folder that holds feature files, the Karate run wins over the build tool's "Tests in ..." one.
   *
   * <p>The Gradle test producer replaces every other configuration for a package folder whenever
   * the project runs tests through Gradle (the default), and the platform keeps only one of two
   * configurations when either side replaces the other - so unless this producer replaces back, a
   * feature folder in a Gradle project offers no Karate run at all. Karate's own layout puts features
   * next to their JUnit runner class under {@code src/test/java}, which made that the common case.
   * Folders with no feature files of their own are left to the build tool.
   */
  @Override
  public boolean shouldReplace(@NotNull ConfigurationFromContext self, @NotNull ConfigurationFromContext other) {
    return ((KarateRunConfiguration) self.getConfiguration()).isFolderHasFeatures();
  }

  @Override
  public boolean isConfigurationFromContext(@NotNull KarateRunConfiguration configuration,
    @NotNull ConfigurationContext context) {

    PsiElement psiElement = context.getLocation().getPsiElement();
    PsiFile containingFile = psiElement.getContainingFile();
    if (!(containingFile instanceof GherkinFile)) {
      return false;
    }
    Project project = containingFile.getProject();
    PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
    Document document = psiDocumentManager.getDocument(containingFile);
    int textOffset = psiElement.getTextOffset();
    if (document == null) {
      return false;
    }
    PreferredTest preferredTest = PreferredTest.WHOLE_FILE;
    IElementType elementType = PsiUtilCore.getElementType(psiElement);
    if (elementType == KarateTokenTypes.TAG) {
      preferredTest = PreferredTest.ALL_TAGS;
    } else {
      GherkinStepsHolder holder;
      if (KarateTokenTypes.SCENARIOS_KEYWORDS.contains(elementType)) {
        holder = PsiTreeUtil.getParentOfType(psiElement, GherkinStepsHolder.class, false);
      } else {
        holder = PsiTreeUtil.getParentOfType(psiElement, GherkinStepsHolder.class);
      }
      if (holder != null) {
        preferredTest = PreferredTest.SINGLE_SCENARIO;
        textOffset = scenarioKeywordOffset(holder);
      }
    }
    VirtualFile virtualFile = context.getLocation().getVirtualFile();
    if (preferredTest == PreferredTest.ALL_TAGS) {
      return configuration.getName().equals(context.getPsiLocation().getText());
    } else if (preferredTest == PreferredTest.SINGLE_SCENARIO) {
      int holderLine = document.getLineNumber(textOffset) + 1;
      return configuration.getName().equals(virtualFile.getName() + ":" + holderLine);
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
    final PsiElement location = context.getPsiLocation();
    if (location == null
      || (!(location instanceof PsiDirectory) && !(location.getContainingFile() instanceof GherkinFile))) {
      return false;
    }
    Module contextModule = context.getModule();
    if (contextModule != null) {
      configuration.setModule(contextModule);
    }
    Project contextProject = context.getProject();
    Optional<VirtualFile> virtualFile =
      Optional.of(context).map(ConfigurationContext::getLocation).map(Location::getVirtualFile);
    // Karate treats the project/module root as its working directory (used to locate karate-config.js). Resolve it
    // from the content root that actually contains the feature file so generated roots never take precedence.
    String workingDir = virtualFile.map(vf -> getContentRootForFile(contextProject, vf))
      .orElseGet(() -> getModuleContentRoot(contextModule));
    if (workingDir == null) {
      workingDir = FileUtil.toSystemIndependentName(StringUtil.notNullize(contextProject.getBasePath()));
    }
    configuration.setWorkingDirectory(workingDir);
    if (location instanceof PsiDirectory) {
      return virtualFile.map(
          v -> this.setupRunnerParametersForFolderIfApplicable(contextProject, contextModule, configuration,
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
    int textOffset = psiElement.getTextOffset();
    if (document == null) {
      return false;
    }
    PreferredTest preferredTest = PreferredTest.WHOLE_FILE;
    IElementType elementType = PsiUtilCore.getElementType(psiElement);
    if (elementType == KarateTokenTypes.TAG) {
      preferredTest = PreferredTest.ALL_TAGS;
      configuration.setTag(psiElement.getText());
    } else {
      GherkinStepsHolder holder;
      if (KarateTokenTypes.SCENARIOS_KEYWORDS.contains(elementType)) {
        holder = PsiTreeUtil.getParentOfType(psiElement, GherkinStepsHolder.class, false);
      } else {
        holder = PsiTreeUtil.getParentOfType(psiElement, GherkinStepsHolder.class);
      }
      if (holder != null) {
        preferredTest = PreferredTest.SINGLE_SCENARIO;
        textOffset = scenarioKeywordOffset(holder);
      }
    }

    configuration.setPreferredTest(preferredTest);
    sourceElement.set(containingFile);
    int lineNumber = document.getLineNumber(textOffset) + 1;
    configuration.setLineNumber(lineNumber);
    configuration.setTestName(name);
    configuration.setPath(path);
    String relPath = getRelativePathFromFile(contextProject, contextModule, virtualFile.orElse(null), name);
    configuration.setRelPath(relPath);
    PsiElement nextElement =
      PsiUtilCore.getElementAtOffset(containingFile, psiElement.getTextOffset() + psiElement.getTextLength() + 1);
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

  private boolean setupRunnerParametersForFolderIfApplicable(@NotNull final Project project, final Module module,
    @NotNull final KarateRunConfiguration configuration,
    @NotNull final VirtualFile dir) {
    if (module == null) {
      return false;
    }
    configuration.setFolderHasFeatures(
      Arrays.stream(dir.getChildren()).map(VirtualFile::getName).anyMatch(s -> s.endsWith(".feature")));
    configuration.setPath(getRelativePathFromFile(project, module, dir, dir.getPath()));
    configuration.setPreferredTest(PreferredTest.ALL_IN_FOLDER);
    configuration.setName("Karate tests in '" + dir.getName() + "'");
    return true;
  }

  private static @Nullable String getModuleContentRoot(@Nullable Module module) {
    if (module == null) {
      return null;
    }
    VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
    if (contentRoots.length > 0) {
      return FileUtil.toSystemIndependentName(contentRoots[0].getPath());
    }
    return null;
  }

  /**
   * The offset of the {@code Scenario:} (or Outline/Example/Background) keyword inside the holder.
   *
   * <p>The holder's own {@code getTextOffset()} points at the first tag when the scenario is tagged,
   * because tags are children of the scenario PSI. Sending that line to Karate skips the scenario:
   * Karate 1.x matches by {@code Scenario.line} - the keyword line - and reports "skipping scenario
   * at line: N, needed: M" when the caller line doesn't equal it. So we walk to the keyword.
   */
  private static int scenarioKeywordOffset(@NotNull GherkinStepsHolder holder) {
    for (ASTNode node = holder.getNode().getFirstChildNode(); node != null; node = node.getTreeNext()) {
      IElementType type = node.getElementType();
      if (KarateTokenTypes.SCENARIOS_KEYWORDS.contains(type) || type == KarateTokenTypes.BACKGROUND_KEYWORD) {
        return node.getStartOffset();
      }
    }
    return holder.getTextOffset();
  }

  private static @Nullable String getContentRootForFile(@NotNull Project project, @Nullable VirtualFile file) {
    if (file == null) {
      return null;
    }
    VirtualFile contentRoot = ProjectFileIndex.getInstance(project).getContentRootForFile(file);
    return contentRoot == null ? null : FileUtil.toSystemIndependentName(contentRoot.getPath());
  }

  /**
   * Resolves a feature file (or folder) path relative to the classpath source root that actually contains it.
   * Karate resolves {@code classpath:} references against the classpath roots (e.g. {@code src/test/java},
   * {@code src/test/resources}), so the relative path must come from the specific source root holding the file -
   * not the first source root alphabetically, which can be a generated directory such as those registered by the
   * protobuf Gradle plugin (e.g. {@code build/extracted-include-protos/test}).
   */
  private static String getRelativePathFromFile(@NotNull Project project, @Nullable Module contextModule,
    @Nullable VirtualFile file, @Nullable String fallback) {
    ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
    if (file != null) {
      VirtualFile sourceRoot = fileIndex.getSourceRootForFile(file);
      if (sourceRoot != null) {
        String relative = FileUtil.getRelativePath(sourceRoot.getPath(), file.getPath(), '/');
        if (relative != null) {
          return relative;
        }
      }
    }
    String path = file == null ? null : file.getPath();
    if (contextModule == null || path == null) {
      return fallback == null ? "" : fallback;
    }
    return Arrays.stream(ModuleRootManager.getInstance(contextModule).getSourceRoots())
      .sorted(Comparator.comparingInt(root -> fileIndex.isInGeneratedSources(root) ? 1 : 0))
      .map(VirtualFile::getPath)
      .filter(s -> FileUtil.isAncestor(s, path, false))
      .map(s -> Optional.ofNullable(FileUtil.getRelativePath(s, path, '/')))
      .findFirst()
      .flatMap(Function.identity())
      .orElse(fallback);
  }
}
