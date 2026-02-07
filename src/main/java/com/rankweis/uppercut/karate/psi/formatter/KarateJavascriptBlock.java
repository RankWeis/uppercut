package com.rankweis.uppercut.karate.psi.formatter;

import static com.rankweis.uppercut.karate.psi.UppercutElementTypes.JAVASCRIPT;

import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Spacing;
import com.intellij.formatting.Wrap;
import com.intellij.lang.ASTNode;
import com.intellij.lang.javascript.formatter.JSBlockContext;
import com.intellij.lang.javascript.formatter.blocks.JSBlock;
import com.intellij.lang.javascript.formatter.blocks.alignment.ASTNodeBasedAlignmentFactory;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class KarateJavascriptBlock extends JSBlock {

  public KarateJavascriptBlock(@NotNull ASTNode node,
    @Nullable Alignment alignment,
    @Nullable Indent indent,
    @Nullable Wrap wrap,
    @NotNull CodeStyleSettings topSettings) {
    super(node, alignment, indent, wrap, topSettings);
  }

  public KarateJavascriptBlock(@NotNull ASTNode node, @Nullable Alignment alignment, @Nullable Indent indent,
    @Nullable Wrap wrap,
    @Nullable ASTNodeBasedAlignmentFactory sharedAlignmentFactory,
    @NotNull JSBlockContext jsBlockContext) {
    super(node, alignment, indent, wrap, sharedAlignmentFactory, jsBlockContext);
  }

  @Override public @Nullable Spacing getSpacing(Block child1, @NotNull Block child2) {
    if (this.myNode.getElementType() == JAVASCRIPT) {
      return Spacing.createSpacing(0, 0, 1, true, 2);
    }
    return super.getSpacing(child1, child2);
  }
}
