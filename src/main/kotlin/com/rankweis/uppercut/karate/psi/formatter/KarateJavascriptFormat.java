package com.rankweis.uppercut.karate.psi.formatter;

import com.intellij.application.options.CodeStyle;
import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
import com.intellij.formatting.FormattingMode;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Wrap;
import com.intellij.formatting.WrapType;
import com.intellij.lang.ASTNode;
import com.intellij.lang.javascript.JSLanguageDialect;
import com.intellij.lang.javascript.formatter.JSBlockContext;
import com.intellij.lang.javascript.formatter.JSCodeStyleSettings;
import com.intellij.lang.javascript.formatter.blocks.JSBlock;
import com.intellij.lang.javascript.formatter.blocks.SubBlockVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import java.util.ArrayList;
import java.util.List;

public class KarateJavascriptFormat {

  private final JSLanguageDialect dialect;

  public KarateJavascriptFormat(JSLanguageDialect dialect) {
    this.dialect = dialect;
  }

  public List<Block> getJsSubBlocks(ASTNode astNode, Alignment alignment) {
    List<Block> result = new ArrayList<>();
    PsiFile file = astNode.getPsi().getContainingFile();
    CodeStyleSettings settings = CodeStyle.getSettings(file);
    CodeStyle.runWithLocalSettings(file.getProject(), settings, (s) -> {

      JSCodeStyleSettings explicitSettings =
        s.getCustomSettings(JSCodeStyleSettings.getSettingsClass(dialect.getBaseLanguage())).clone();
      CommonCodeStyleSettings commonCodeStyleSettings = s.getCommonSettings(dialect.getBaseLanguage());

      commonCodeStyleSettings.SPACE_WITHIN_BRACES = true;
      explicitSettings.SPACE_BEFORE_FUNCTION_LEFT_PARENTH = false;
      commonCodeStyleSettings.SPACE_BEFORE_METHOD_PARENTHESES = false;

      Wrap childWrap = null;
      if (!astNode.getText().contains("\n")) {
        commonCodeStyleSettings.WRAP_LONG_LINES = false;
        commonCodeStyleSettings.KEEP_SIMPLE_METHODS_IN_ONE_LINE = true;
        commonCodeStyleSettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
        commonCodeStyleSettings.KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = true;
        commonCodeStyleSettings.KEEP_SIMPLE_CLASSES_IN_ONE_LINE = true;
        commonCodeStyleSettings.KEEP_MULTIPLE_EXPRESSIONS_IN_ONE_LINE = true;

        explicitSettings.SPACES_WITHIN_OBJECT_LITERAL_BRACES = true;
        explicitSettings.SPACES_WITHIN_OBJECT_TYPE_BRACES = true;
        commonCodeStyleSettings.SPACE_WITHIN_BRACES = true;
      } else {
        childWrap = Wrap.createWrap(WrapType.NORMAL, false);
      }

      JSBlockContext context =
        new KarateJavaScriptBlockContext(s, dialect.getBaseLanguage(), explicitSettings, FormattingMode.REFORMAT);
      JSBlock parent = new KarateJavascriptBlock(astNode, alignment, Indent.getNoneIndent(), childWrap,
        (x) -> x.getTreeParent() == astNode ? alignment : null, context);
      SubBlockVisitor subBlockVisitor = context.createSubBlockVisitor(parent, null);
      result.add(parent);
      //      for (ASTNode jsChild : children) {
      //        if (jsChild.getElementType() == TokenType.WHITE_SPACE) {
      //          subBlockVisitor.visit(jsChild);
      //        }
      //        Block b = context.createBlock(jsChild, childWrap, alignmentChild, Indent.getNoneIndent(), null, parent);
      //        result.add(b);
      //      }
      //      result.addAll(subBlockVisitor.getBlocks());
    });
    return result;
  }

}
