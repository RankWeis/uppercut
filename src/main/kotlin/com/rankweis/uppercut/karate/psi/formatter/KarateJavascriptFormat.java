package com.rankweis.uppercut.karate.psi.formatter;

import com.intellij.application.options.CodeStyle;
import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
import com.intellij.formatting.Formatter;
import com.intellij.formatting.FormattingMode;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Wrap;
import com.intellij.formatting.WrapType;
import com.intellij.lang.ASTNode;
import com.intellij.lang.javascript.JSLanguageDialect;
import com.intellij.lang.javascript.formatter.JSBlockContext;
import com.intellij.lang.javascript.formatter.JSCodeStyleSettings;
import com.intellij.psi.TokenType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import java.util.ArrayList;
import java.util.List;

public class KarateJavascriptFormat {

  private final JSLanguageDialect dialect;

  public KarateJavascriptFormat(JSLanguageDialect dialect) {
    this.dialect = dialect;
  }


  public List<Block> getJsSubBlocks(ASTNode astNode, Alignment alignment) {
    List<Block> result = new ArrayList<>();
    final ASTNode[] children = astNode.getChildren(null);
    CodeStyleSettings settings = CodeStyle.getSettings(astNode.getPsi().getContainingFile());
    JSBlockContext context = new JSBlockContext(settings, dialect, null,
      FormattingMode.REFORMAT);
      Wrap wrap;
    Wrap childWrap = null;
    context.getCommonSettings().SPACE_BEFORE_METHOD_PARENTHESES = false;
    context.getDialectSettings().SPACE_BEFORE_FUNCTION_LEFT_PARENTH = false;
    context.getCommonSettings().SPACE_BEFORE_METHOD_PARENTHESES = false;
    if (!astNode.getText().contains("\n")) {
      wrap = Wrap.createWrap(WrapType.NONE, false);
      childWrap = Formatter.getInstance().createChildWrap(wrap, WrapType.NONE, false);
      JSCodeStyleSettings commonSettings = JSCodeStyleSettings.getSettings(astNode.getPsi());
      commonSettings.VAR_DECLARATION_WRAP = 0;
      commonSettings.FUNCTION_PARAMETER_DECORATOR_WRAP = 0;
      commonSettings.CLASS_DECORATOR_WRAP = 0;
      commonSettings.CLASS_FIELD_DECORATOR_WRAP = 0;
      commonSettings.CLASS_METHOD_DECORATOR_WRAP = 0;
      commonSettings.OBJECT_LITERAL_WRAP = 0;
      commonSettings.OBJECT_TYPES_WRAP = 0;
      commonSettings.IMPORTS_WRAP = 0;
      commonSettings.UNION_TYPES_WRAP = 0;
      commonSettings.BLANK_LINES_AROUND_FUNCTION = 0;
      CodeStyleSettings defaults = CodeStyleSettings.getDefaults();
      defaults.WRAP_LONG_LINES = false;
      defaults.METHOD_ANNOTATION_WRAP = 0;
      defaults.CLASS_ANNOTATION_WRAP = 0;
      defaults.FIELD_ANNOTATION_WRAP = 0;
      defaults.KEEP_SIMPLE_METHODS_IN_ONE_LINE = true;
      defaults.BLANK_LINES_AROUND_METHOD = 0;

      context =
        new JSBlockContext(defaults, dialect, commonSettings,
          FormattingMode.REFORMAT);
      context.getDialectSettings().FUNCTION_PARAMETER_DECORATOR_WRAP = 0;
      context.getDialectSettings().CLASS_DECORATOR_WRAP = 0;
      context.getDialectSettings().IMPORTS_WRAP = 0;
      context.getDialectSettings().OBJECT_LITERAL_WRAP = 0;
      context.getDialectSettings().CLASS_METHOD_DECORATOR_WRAP = 0;
      context.getDialectSettings().VAR_DECLARATION_WRAP = 0;
      context.getDialectSettings().BLANK_LINES_AROUND_FUNCTION = 0;
      context.getCommonSettings().FIELD_ANNOTATION_WRAP = 0;
      context.getCommonSettings().METHOD_ANNOTATION_WRAP = 0;
      context.getCommonSettings().WRAP_LONG_LINES = false;
      context.getCommonSettings().METHOD_ANNOTATION_WRAP = 0;
      context.getCommonSettings().CLASS_ANNOTATION_WRAP = 0;
      context.getCommonSettings().FIELD_ANNOTATION_WRAP = 0;
      context.getCommonSettings().KEEP_SIMPLE_METHODS_IN_ONE_LINE = true;
    }

    for (ASTNode jsChild : children) {
      if (jsChild.getElementType() == TokenType.WHITE_SPACE) {
        continue;
      }
      Block b = context.createBlock(jsChild, childWrap, alignment, Indent.getNormalIndent(), null, null);
      result.add(b);
    }
    return result;
  }

}
