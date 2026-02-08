package com.rankweis.uppercut.karate.conformance;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.rankweis.uppercut.karate.lexer.KarateJavascriptParsingExtensionPoint;
import com.rankweis.uppercut.karate.lexer.LegacyUppercutLexer;
import com.rankweis.uppercut.karate.lexer.UppercutLexer;
import com.rankweis.uppercut.karate.psi.GherkinKeywordProvider;
import com.rankweis.uppercut.karate.psi.i18n.JsonGherkinKeywordProvider;
import io.karatelabs.js.KarateJsNoPluginExtension;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Benchmark comparing the optimized UppercutLexer against the legacy version.
 * Runs each conformance .feature file through both lexers multiple times and
 * reports per-file and aggregate timing with percentage improvement.
 */
public class LexerBenchmarkTest extends BasePlatformTestCase {

  private static final int WARMUP_ITERATIONS = 50;
  private static final int MEASURED_ITERATIONS = 200;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ExtensionTestUtil.maskExtensions(KarateJavascriptParsingExtensionPoint.EP_NAME,
      List.of(new KarateJsNoPluginExtension()), getTestRootDisposable());
  }

  @Override
  protected String getTestDataPath() {
    return "src/test/testData";
  }

  public void testLexerPerformanceComparison() throws IOException {
    File dir = new File(getTestDataPath(), "conformance");
    File[] files = dir.listFiles((d, name) -> name.endsWith(".feature"));
    assertNotNull("Conformance directory not found", files);
    assertTrue("No .feature files found", files.length > 0);
    Arrays.sort(files);

    GherkinKeywordProvider provider = JsonGherkinKeywordProvider.getKeywordProvider(true);

    // Load all file contents upfront
    List<FileData> fileDataList = new ArrayList<>();
    for (File file : files) {
      String content = Files.readString(file.toPath());
      fileDataList.add(new FileData(file.getName(), content));
    }

    // Warmup both lexers
    System.out.println("Warming up (" + WARMUP_ITERATIONS + " iterations)...");
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      for (FileData fd : fileDataList) {
        lexAllTokens(new UppercutLexer(provider), fd.content);
        lexAllTokens(new LegacyUppercutLexer(provider), fd.content);
      }
    }

    // Measure
    System.out.println("Measuring (" + MEASURED_ITERATIONS + " iterations)...\n");
    List<FileResult> results = new ArrayList<>();
    long totalLegacyNs = 0;
    long totalOptimizedNs = 0;

    for (FileData fd : fileDataList) {
      // Measure legacy
      long legacyNs = 0;
      for (int i = 0; i < MEASURED_ITERATIONS; i++) {
        long start = System.nanoTime();
        lexAllTokens(new LegacyUppercutLexer(provider), fd.content);
        legacyNs += System.nanoTime() - start;
      }

      // Measure optimized
      long optimizedNs = 0;
      for (int i = 0; i < MEASURED_ITERATIONS; i++) {
        long start = System.nanoTime();
        lexAllTokens(new UppercutLexer(provider), fd.content);
        optimizedNs += System.nanoTime() - start;
      }

      double legacyAvgUs = legacyNs / (MEASURED_ITERATIONS * 1000.0);
      double optimizedAvgUs = optimizedNs / (MEASURED_ITERATIONS * 1000.0);
      double improvement = ((legacyAvgUs - optimizedAvgUs) / legacyAvgUs) * 100.0;

      results.add(new FileResult(fd.name, fd.content.length(), legacyAvgUs, optimizedAvgUs, improvement));
      totalLegacyNs += legacyNs;
      totalOptimizedNs += optimizedNs;
    }

    // Print results table
    int maxNameLen = results.stream().mapToInt(r -> r.name.length()).max().orElse(20);
    maxNameLen = Math.max(maxNameLen, 4); // "File" header

    String headerFmt = "%-" + maxNameLen + "s  %6s  %10s  %10s  %8s%n";
    String rowFmt = "%-" + maxNameLen + "s  %6d  %10.1f  %10.1f  %+7.1f%%%n";

    System.out.printf(headerFmt, "File", "Bytes", "Legacy(us)", "New(us)", "Change");
    System.out.println("-".repeat(maxNameLen + 42));

    for (FileResult r : results) {
      System.out.printf(rowFmt, r.name, r.bytes, r.legacyUs, r.optimizedUs, -r.improvement);
    }

    // Aggregate
    double totalLegacyUs = totalLegacyNs / (MEASURED_ITERATIONS * 1000.0);
    double totalOptimizedUs = totalOptimizedNs / (MEASURED_ITERATIONS * 1000.0);
    double totalImprovement = ((totalLegacyUs - totalOptimizedUs) / totalLegacyUs) * 100.0;

    System.out.println("-".repeat(maxNameLen + 42));
    System.out.printf(rowFmt, "TOTAL", results.stream().mapToInt(r -> r.bytes).sum(),
      totalLegacyUs, totalOptimizedUs, -totalImprovement);

    System.out.printf("%nFiles: %d | Warmup: %d | Measured: %d iterations each%n",
      results.size(), WARMUP_ITERATIONS, MEASURED_ITERATIONS);
    System.out.printf("Overall improvement: %.1f%%%n", totalImprovement);
  }

  private int lexAllTokens(LexerBase lexer, String content) {
    lexer.start(content, 0, content.length(), 0);
    int tokenCount = 0;
    while (lexer.getTokenType() != null) {
      tokenCount++;
      lexer.advance();
    }
    return tokenCount;
  }

  private record FileData(String name, String content) {}

  private record FileResult(String name, int bytes, double legacyUs, double optimizedUs,
                            double improvement) {}
}
