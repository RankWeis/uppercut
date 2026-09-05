package com.rankweis.uppercut.karate.debugging;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

@Slf4j
public class UppercutClassLoader {

  public static final UppercutClassLoader INSTANCE = new UppercutClassLoader();
  private Project project;

  @Getter private URLClassLoader classLoader;
  @Getter private Set<URL> managedUrls = new HashSet<>();
  private final Map<String, Class> fileCache = new HashMap<>();

  private void load() {
    if (project == null) {
      // This is only triggered when run from a launch configuration - launch configurations should send a load signal.
      log.warn("Unable to load Karate classes; this should never happen, please report this as a bug.");
    }
    Set<URL> sideloadedClasses = Arrays.stream(LibraryUtil.getLibraryRoots(project))
      .map(VirtualFile::getPath)
      .map(UppercutClassLoader::toClasspathUrl)
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());
    managedUrls.addAll(sideloadedClasses);
    classLoader = new URLClassLoader(managedUrls.toArray(new URL[0]), this.getClass().getClassLoader());
  }

  /**
   * Turns a library root path into a classpath URL, or null if it cannot be used.
   *
   * <p>Built through {@link java.io.File#toURI()} rather than by concatenating into a URI string:
   * a path only has to contain a space - {@code C:\Program Files\...}, the default JDK location on
   * Windows - for {@code new URI(...)} to throw "Illegal character in opaque part", which used to
   * abort the whole load and leave the debugger with no position manager.
   *
   * <p>Roots inside an archive arrive suffixed with {@code !/} and sometimes an entry path; only the
   * archive itself belongs on a classpath, and URLClassLoader reads jars and zips directly.
   */
  static @Nullable URL toClasspathUrl(String libraryRootPath) {
    try {
      String path = libraryRootPath;
      int separator = path.indexOf("!/");
      if (separator >= 0) {
        path = path.substring(0, separator);
      }
      return new File(path).toURI().toURL();
    } catch (MalformedURLException | IllegalArgumentException e) {
      // One unusable root must not cost the others - and with them, debugging entirely.
      log.warn("Skipping library root that cannot be turned into a classpath URL: " + libraryRootPath, e);
      return null;
    }
  }

  public Class<?> getClass(String className) {
    if (fileCache.containsKey(className)) {
      return fileCache.get(className);
    }
    try {
      if (classLoader == null) {
        return null;
      }
      Class<?> myClass = classLoader.loadClass(className);
      fileCache.put(className, myClass);
      return myClass;
    } catch (ClassNotFoundException e) {
      return null;
    }
  }

  public void setProject(Project project) {
    this.project = project;
    load();
  }
}
