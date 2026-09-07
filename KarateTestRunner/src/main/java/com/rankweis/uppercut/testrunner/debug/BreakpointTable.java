package com.rankweis.uppercut.testrunner.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The breakpoints the IDE has set, and the one question the interceptor asks of them.
 *
 * <p><b>Why paths need matching rather than comparing:</b> the IDE sets a breakpoint on the file the
 * user is editing ({@code /repo/src/test/java/sample/users.feature}) while Karate reports the copy it
 * actually loaded ({@code build/resources/test/sample/users.feature}). Neither is a prefix of the
 * other; what they share is the tail below the source root, {@code sample/users.feature}. So a
 * breakpoint matches a reported path when one path's trailing segments equal the other's, comparing
 * from the file name backwards.</p>
 *
 * <p>Concretely: the two paths must share their last two segments - the file name and the directory
 * holding it - or, when one of them is a bare file name at the classpath root, just the file name.
 * Two segments is what separates {@code sample/users.feature} from {@code other/users.feature} while
 * still tolerating the {@code src/test/java} → {@code build/resources/test} rewrite that no rule can
 * see through. Two features with the same name in two directories with the same name are the known
 * false match; the IDE narrows it no further, because the path Karate reports is relative and there
 * is nothing more to compare.</p>
 */
final class BreakpointTable {

  private record Breakpoint(List<String> segments, int line) {
  }

  /** Swapped wholesale on BREAKPOINTS_END; read on every step by every scenario thread. */
  private volatile List<Breakpoint> committed = List.of();
  private final List<Breakpoint> pending = new CopyOnWriteArrayList<>();

  void clear() {
    pending.clear();
  }

  void add(String path, int line) {
    pending.add(new Breakpoint(segments(path), line));
  }

  void commit() {
    committed = List.copyOf(pending);
    pending.clear();
  }

  boolean isEmpty() {
    return committed.isEmpty();
  }

  int size() {
    return committed.size();
  }

  boolean matches(String reportedPath, int line) {
    if (reportedPath == null) {
      return false;
    }
    List<String> reported = segments(reportedPath);
    for (Breakpoint breakpoint : committed) {
      if (breakpoint.line() == line && sharesTail(breakpoint.segments(), reported)) {
        return true;
      }
    }
    return false;
  }

  /** True when the two paths share enough trailing segments to be the same file. */
  private static boolean sharesTail(List<String> a, List<String> b) {
    int comparable = Math.min(a.size(), b.size());
    if (comparable == 0) {
      return false;
    }
    int required = Math.min(2, comparable);
    int common = 0;
    while (common < comparable && a.get(a.size() - 1 - common).equals(b.get(b.size() - 1 - common))) {
      common++;
    }
    return common >= required;
  }

  /** Splits on both separators: the IDE may be on Windows while the reported path uses '/'. */
  private static List<String> segments(String path) {
    List<String> segments = new ArrayList<>();
    for (String segment : path.split("[/\\\\]")) {
      if (!segment.isEmpty() && !".".equals(segment)) {
        segments.add(segment);
      }
    }
    return segments;
  }
}
