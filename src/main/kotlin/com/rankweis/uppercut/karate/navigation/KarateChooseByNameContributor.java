package com.rankweis.uppercut.karate.navigation;

import com.intellij.navigation.ChooseByNameContributorEx;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FindSymbolParameters;
import com.intellij.util.indexing.IdFilter;
import com.rankweis.uppercut.karate.psi.KarateDeclaration;
import com.rankweis.uppercut.util.KarateUtil;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class KarateChooseByNameContributor implements ChooseByNameContributorEx {

  @Override public void processNames(@NotNull Processor<? super String> processor, @NotNull GlobalSearchScope scope,
    @Nullable IdFilter filter) {
    Project project = Objects.requireNonNull(scope.getProject());
    List<String> propertyKeys = ContainerUtil.map(
      KarateUtil.findProperties(project), KarateDeclaration::getName);
    ContainerUtil.process(propertyKeys, processor);
  }

  @Override
  public void processElementsWithName(@NotNull String name, @NotNull Processor<? super NavigationItem> processor,
    @NotNull FindSymbolParameters parameters) {
    List<NavigationItem> properties = ContainerUtil.map(
      KarateUtil.findProperties(parameters.getProject(), name), property -> (NavigationItem) property);
    ContainerUtil.process(properties, processor);
  }
}
