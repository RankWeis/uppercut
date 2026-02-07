package com.rankweis.uppercut.karate.debugging;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

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
      //      .filter(vPath -> vPath.contains("karate"))
      .map(p -> {
        try {
          return new URI("jar:file:" + p).toURL();
        } catch (MalformedURLException e) {
          throw new RuntimeException(e);
        } catch (URISyntaxException e) {
          throw new RuntimeException(e);
        }
      }).collect(Collectors.toSet());
    managedUrls.addAll(sideloadedClasses);
    classLoader = new URLClassLoader(managedUrls.toArray(new URL[0]), this.getClass().getClassLoader());
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
