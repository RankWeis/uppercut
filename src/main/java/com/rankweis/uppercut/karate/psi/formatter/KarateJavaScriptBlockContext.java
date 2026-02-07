package com.rankweis.uppercut.karate.psi.formatter;

import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
import com.intellij.formatting.FormattingMode;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Wrap;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.javascript.JavaScriptSupportLoader;
import com.intellij.lang.javascript.formatter.JSBlockContext;
import com.intellij.lang.javascript.formatter.JSCodeStyleSettings;
import com.intellij.lang.javascript.formatter.JSSpacingProcessor;
import com.intellij.lang.javascript.formatter.blocks.JSBlock;
import com.intellij.lang.javascript.formatter.blocks.alignment.ASTNodeBasedAlignmentFactory;
import com.intellij.lang.typescript.formatter.TypedJSSpacingProcessor;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class KarateJavaScriptBlockContext extends JSBlockContext {

  public KarateJavaScriptBlockContext(@NotNull CodeStyleSettings topSettings, @NotNull Language dialect,
    @Nullable JSCodeStyleSettings explicitSettings,
    @NotNull FormattingMode formattingMode) {
    super(topSettings, dialect, explicitSettings, formattingMode);
  }

  @Override
  public @NotNull Block createBlock(@NotNull ASTNode child, @Nullable Wrap wrap, @Nullable Alignment childAlignment,
    @Nullable Indent childIndent, @Nullable ASTNodeBasedAlignmentFactory alignmentFactory,
    @Nullable JSBlock parentBlock) {
    return super.createBlock(child, wrap, childAlignment, childIndent, alignmentFactory, parentBlock);
  }

  @Override protected @NotNull JSSpacingProcessor createSpacingProcessor(ASTNode node, ASTNode child1, ASTNode child2) {
    return isTypedJsDialect(getDialect()) ? new TypedJSSpacingProcessor(node, child1, child2,
      this.getTopSettings(), this.getDialect(), this.myDialectSettings)
      : new KarateJavaScriptSpacingProcessor(node, child1, child2, this.getTopSettings(), this.getDialect(),
        this.myDialectSettings);
  }

  private static boolean isTypedJsDialect(@NotNull Language dialect) {
    return dialect == JavaScriptSupportLoader.FLOW_JS || dialect.isKindOf(JavaScriptSupportLoader.TYPESCRIPT);
  }
}
