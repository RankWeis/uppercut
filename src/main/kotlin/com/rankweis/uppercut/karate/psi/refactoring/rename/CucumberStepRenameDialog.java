package com.rankweis.uppercut.karate.psi.refactoring.rename;

import com.rankweis.uppercut.karate.CucumberUtil;
import com.rankweis.uppercut.karate.steps.AbstractStepDefinition;
import com.rankweis.uppercut.karate.steps.reference.CucumberStepReference;
import com.rankweis.uppercut.karate.MyBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ReadOnlyFragmentModificationException;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.ReadonlyFragmentModificationHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenameDialog;
import com.intellij.refactoring.rename.RenameProcessor;
import java.awt.GridBagConstraints;
import java.util.EnumSet;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.intellij.lang.regexp.RegExpCapability;
import org.intellij.lang.regexp.RegExpLexer;
import org.intellij.lang.regexp.RegExpTT;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CucumberStepRenameDialog extends RenameDialog {
  private AbstractStepDefinition myStepDefinition;

  public CucumberStepRenameDialog(@NotNull Project project,
                                  @NotNull PsiElement psiElement,
                                  @Nullable PsiElement nameSuggestionContext, Editor editor) {
    super(project, psiElement, nameSuggestionContext, editor);
  }

  @Override
  protected RenameProcessor createRenameProcessor(@NotNull String newName) {
    return new RenameProcessor(getProject(), getPsiElement(), newName,
                               getRefactoringScope(), isSearchInComments(), isSearchInNonJavaFiles());
  }

  @Override
  protected String getFullName() {
    return MyBundle.message("cucumber.step");
  }

  @Override
  protected void createNewNameComponent() {
    super.createNewNameComponent();

    final Runnable guardRunnable = () -> {
      final Editor editor = getNameSuggestionsField().getEditor();
      if (editor != null) {
        editor.getSelectionModel().removeSelection();
        editor.getCaretModel().moveToOffset(0);
        final Document document = editor.getDocument();
        EditorActionManager.getInstance().setReadonlyFragmentModificationHandler(document, new ReadonlyFragmentModificationHandler() {
            @Override
            public void handle(final ReadOnlyFragmentModificationException e) {
              //do nothing
            }
          });

        guardRegexpSpecialSymbols(editor);
      }
    };

    SwingUtilities.invokeLater(guardRunnable);
  }

  private AbstractStepDefinition getStepDefinition() {
    if (myStepDefinition == null) {
      final CucumberStepReference ref = CucumberUtil.getCucumberStepReference(getPsiElement());
      if (ref != null) {
        myStepDefinition = ref.resolveToDefinition();
      }
    }
    return myStepDefinition;
  }

  private static void guardRegexpSpecialSymbols(@NotNull final Editor editor) {
    final String text = editor.getDocument().getText();
    final RegExpLexer lexer = new RegExpLexer(EnumSet.noneOf(RegExpCapability.class));

    lexer.start(text);
    while (lexer.getTokenType() != null) {
      if (lexer.getTokenType() != RegExpTT.CHARACTER) {
        editor.getDocument().createGuardedBlock(lexer.getTokenStart(), lexer.getTokenEnd());
      }
      lexer.advance();
    }
  }

  @Override
  public String[] getSuggestedNames() {
    AbstractStepDefinition stepDefinition = getStepDefinition();
    if (stepDefinition != null) {
      String result = stepDefinition.getCucumberRegex();
      if (result != null) {
        result = StringUtil.trimStart(result, "^");
        result = StringUtil.trimEnd(result, "$");

        return new String[]{result};
      }
    }

    return super.getSuggestedNames();
  }

  @Override
  protected void processNewNameChanged() {
    getPreviewAction().setEnabled(true);
    getRefactorAction().setEnabled(true);
  }

  @Override
  protected void createCheckboxes(JPanel panel, GridBagConstraints gbConstraints) {
    super.createCheckboxes(panel, gbConstraints);
    getCbSearchInComments().setVisible(false);
  }
}
