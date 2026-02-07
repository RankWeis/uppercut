package com.rankweis.uppercut.karate.psi.formatter;

import com.intellij.formatting.Spacing;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.javascript.JSTokenTypes;
import com.intellij.lang.javascript.formatter.JSCodeStyleSettings;
import com.intellij.lang.javascript.formatter.JSSpacingProcessor;
import com.intellij.lang.javascript.psi.JSFunction;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;

public class KarateJavaScriptSpacingProcessor extends JSSpacingProcessor {

  public KarateJavaScriptSpacingProcessor(ASTNode parent, ASTNode child1,
    ASTNode child2, CodeStyleSettings topSettings,
    Language dialect,
    @NotNull JSCodeStyleSettings jsCodeStyleSettings) {
    super(parent, child1, child2, topSettings, dialect, jsCodeStyleSettings);
  }

  @Override protected void processBlock(int blankLinesAroundFunction) {
    ASTNode superParent = this.myParent.getTreeParent();
    boolean keepOneLine =
      superParent.getPsi() instanceof JSFunction ? this.myCommonSettings.KEEP_SIMPLE_METHODS_IN_ONE_LINE
        : this.myCommonSettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE;
    if (keepOneLine && (this.type1 == JSTokenTypes.LBRACE || this.type2 == JSTokenTypes.RBRACE)) {
      int blankLinesSetting = this.myCommonSettings.KEEP_BLANK_LINES_IN_CODE;
      int minSpaces = myCommonSettings.SPACE_WITHIN_BRACES ? 1 : 0;
      this.myResult = Spacing.createDependentLFSpacing(minSpaces, 1, this.myParent.getTextRange(),
        this.myCommonSettings.KEEP_LINE_BREAKS, blankLinesSetting);
    } else {
      super.processBlock(blankLinesAroundFunction);
    }
  }
}
