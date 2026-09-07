package com.rankweis.uppercut.karate.run;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Arrays;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Which Karate a given file or module is on.
 *
 * <p>Scoped to the module, and only widened to the project when the module has no Karate of its own.
 * A monorepo can hold modules on different majors - `testProjects/karate-versions` is exactly that -
 * so a project-wide scan reports v2 for every file as soon as one module has Karate 2 on it. But
 * module scoping alone would regress setups where the karate jars hang off a sibling module (root
 * module configurations, shared test-support modules), so the module scan only decides when the
 * module actually has karate on it.
 *
 * <p>Used by the run configuration to pick which Karate the launch drives.
 */
public final class KarateLibraries {

  private KarateLibraries() {
  }

  public static VirtualFile[] rootsFor(@NotNull Project project, @Nullable Module module) {
    if (module == null) {
      return LibraryUtil.getLibraryRoots(project);
    }
    VirtualFile[] moduleRoots =
      OrderEnumerator.orderEntries(module).recursively().librariesOnly().classes().getRoots();
    if (moduleScanIsAuthoritative(Arrays.stream(moduleRoots).map(VirtualFile::getName))) {
      return moduleRoots;
    }
    return LibraryUtil.getLibraryRoots(project);
  }

  /** The module scan decides only when the module actually has karate; otherwise widen to the project. */
  static boolean moduleScanIsAuthoritative(java.util.stream.Stream<String> libraryNames) {
    return libraryNames.anyMatch(n -> n.startsWith("karate-"));
  }

}
