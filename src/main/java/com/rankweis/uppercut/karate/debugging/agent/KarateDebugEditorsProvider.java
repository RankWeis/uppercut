package com.rankweis.uppercut.karate.debugging.agent;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.util.LocalTimeCounter;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.rankweis.uppercut.karate.psi.GherkinFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * What the debugger's expression fields - watches, breakpoint conditions, Evaluate - are edited as.
 * Karate expressions are Gherkin step right-hand sides, so the feature file type gives them the right
 * highlighting and completion.
 *
 * <p>Nothing evaluates them yet; the evaluator arrives with phase 3 of {@code docs/DEBUGGER.md}. But
 * the fields are built as soon as a session starts, so this has to work from the first pause.</p>
 *
 * <p>The {@code XExpression} overload is the one to implement: the {@code String} one it delegates to
 * is deprecated and throws {@code AbstractMethodError} by design, which is what a session start does
 * if only {@link #getFileType()} is provided.</p>
 */
public class KarateDebugEditorsProvider extends XDebuggerEditorsProvider {

  @Override
  public @NotNull FileType getFileType() {
    return GherkinFileType.INSTANCE;
  }

  @Override
  public @NotNull Document createDocument(@NotNull Project project, @NotNull XExpression expression,
    @Nullable XSourcePosition sourcePosition, @NotNull EvaluationMode mode, @Nullable String purpose) {
    String text = expression.getExpression();
    // eventSystemEnabled, so the file has a Document the editor can bind to; a plain in-memory file
    // would give back null here and leave the field with nothing to edit.
    PsiFile file = PsiFileFactory.getInstance(project).createFileFromText(
      "karate-debugger-expression." + GherkinFileType.INSTANCE.getDefaultExtension(),
      GherkinFileType.INSTANCE, text, LocalTimeCounter.currentTime(), true);
    Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    return document != null ? document : EditorFactory.getInstance().createDocument(text);
  }
}
