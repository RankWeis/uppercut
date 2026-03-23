package com.rankweis.uppercut.karate.conformance;

import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.rankweis.uppercut.karate.lexer.KarateJavascriptParsingExtensionPoint;
import com.rankweis.uppercut.karate.lexer.UppercutLexer;
import com.rankweis.uppercut.karate.psi.i18n.JsonGherkinKeywordProvider;
import io.karatelabs.js.KarateJsNoPluginExtension;
import io.karatelabs.js.Parser;
import io.karatelabs.js.Source;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conformance test suite that runs Karate's own .feature files through the
 * plugin's lexer and parser to detect false syntax errors.
 *
 * <p>Uses {@link BasePlatformTestCase} to get a full IDE environment with
 * JSON and XML plugin element types registered, so JSON/XML injection in the
 * lexer works correctly.
 */
public class KarateConformanceTest extends BasePlatformTestCase {

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

  public void testLexerConformance() throws IOException {
    File[] featureFiles = getConformanceFiles();
    List<String> failures = new ArrayList<>();
    int passed = 0;

    for (File file : featureFiles) {
      String content = Files.readString(file.toPath());
      UppercutLexer lexer = new UppercutLexer(
        JsonGherkinKeywordProvider.getKeywordProvider(true));
      lexer.start(content, 0, content.length(), 0);

      List<String> fileErrors = new ArrayList<>();
      while (lexer.getTokenType() != null) {
        IElementType tokenType = lexer.getTokenType();
        if (tokenType == TokenType.ERROR_ELEMENT
            || tokenType == TokenType.BAD_CHARACTER) {
          int offset = lexer.getTokenStart();
          String context = getSurroundingContext(content, offset);
          fileErrors.add(String.format(
            "  %s: %s at offset %d: ...%s...",
            file.getName(), tokenType, offset, context));
        }
        lexer.advance();
      }
      failures.addAll(fileErrors);
      if (fileErrors.isEmpty()) {
        passed++;
      }
    }

    System.out.printf("Lexer conformance: %d passed, %d with errors%n",
      passed, failures.size());

    if (!failures.isEmpty()) {
      fail(String.format(
        "Lexer errors in %d location(s):%n%s",
        failures.size(), String.join("\n", failures)));
    }
  }

  public void testParserConformance() throws IOException {
    File[] featureFiles = getConformanceFiles();
    List<String> failures = new ArrayList<>();
    int passed = 0;

    for (File file : featureFiles) {
      String content = Files.readString(file.toPath());
      PsiFile psiFile = myFixture.configureByText(file.getName(), content);

      List<PsiErrorElement> errors = collectErrors(psiFile);
      if (errors.isEmpty()) {
        passed++;
      }
      for (PsiErrorElement error : errors) {
        int offset = error.getTextOffset();
        String context = getSurroundingContext(content, offset);
        failures.add(String.format(
          "  %s: \"%s\" at offset %d: ...%s...",
          file.getName(), error.getErrorDescription(), offset, context));
      }
    }

    System.out.printf("Parser conformance: %d passed, %d with errors%n",
      passed, failures.size());

    if (!failures.isEmpty()) {
      fail(String.format(
        "Parser errors in %d location(s):%n%s",
        failures.size(), String.join("\n", failures)));
    }
  }

  public void testJsParserConformance() throws IOException {
    File[] featureFiles = getConformanceFiles();
    Pattern docstringPattern = Pattern.compile(
      "\"\"\"\\s*\\n(.*?)\\n\\s*\"\"\"", Pattern.DOTALL);
    // Keywords whose docstrings are plain text, not JS
    Pattern textKeywordPattern = Pattern.compile(
      "\\b(?:text|csv|yaml|bytes|doc)\\b\\s*[=:]?\\s*$", Pattern.MULTILINE);
    List<String> failures = new ArrayList<>();
    int jsBlocks = 0;
    int filesWithJs = 0;

    for (File file : featureFiles) {
      String content = Files.readString(file.toPath());
      Matcher matcher = docstringPattern.matcher(content);
      boolean fileHasJs = false;

      while (matcher.find()) {
        String block = matcher.group(1);
        String trimmed = block.trim();
        // Skip JSON blocks (start with { or [) and XML blocks (start with <)
        if (trimmed.startsWith("{") || trimmed.startsWith("[")
            || trimmed.startsWith("<")) {
          continue;
        }
        // Skip blocks preceded by text/csv/yaml/bytes/doc keywords
        String before = content.substring(
          Math.max(0, matcher.start() - 80), matcher.start());
        if (textKeywordPattern.matcher(before).find()) {
          continue;
        }
        // Skip blocks that don't look like JS (no keywords or operators)
        if (!trimmed.contains("var ") && !trimmed.contains("function ")
            && !trimmed.contains("def ") && !trimmed.contains("= ")
            && !trimmed.contains("if ") && !trimmed.contains("for ")
            && !trimmed.contains("return ")) {
          continue;
        }
        jsBlocks++;
        fileHasJs = true;
        try {
          new Parser(new Source(trimmed)).parse();
        } catch (Exception e) {
          failures.add(String.format("  %s: %s", file.getName(),
            e.getMessage().split("\n")[0]));
        }
      }
      if (fileHasJs) {
        filesWithJs++;
      }
    }

    System.out.printf(
      "JS parser conformance: %d blocks from %d files, %d failures%n",
      jsBlocks, filesWithJs, failures.size());

    if (!failures.isEmpty()) {
      fail(String.format(
        "JS parser errors in %d block(s):%n%s",
        failures.size(), String.join("\n", failures)));
    }
  }

  private File[] getConformanceFiles() {
    File dir = new File(getTestDataPath(), "conformance");
    File[] files = dir.listFiles((d, name) -> name.endsWith(".feature"));
    assertNotNull("Conformance directory not found: " + dir.getAbsolutePath(),
      files);
    assertTrue("No .feature files found in " + dir.getAbsolutePath(),
      files.length > 0);
    Arrays.sort(files);
    return files;
  }

  private List<PsiErrorElement> collectErrors(PsiFile file) {
    List<PsiErrorElement> errors = new ArrayList<>();
    collectErrorsRecursive(file, errors);
    return errors;
  }

  private void collectErrorsRecursive(
      com.intellij.psi.PsiElement element,
      List<PsiErrorElement> errors) {
    if (element instanceof PsiErrorElement) {
      errors.add((PsiErrorElement) element);
    }
    for (com.intellij.psi.PsiElement child : element.getChildren()) {
      collectErrorsRecursive(child, errors);
    }
  }

  private String getSurroundingContext(String content, int offset) {
    int start = Math.max(0, offset - 30);
    int end = Math.min(content.length(), offset + 30);
    return content.substring(start, end)
      .replace("\n", "\\n")
      .replace("\r", "\\r");
  }
}
