package com.rankweis.uppercut.karate.format;

import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
import com.intellij.formatting.Indent;
import com.intellij.lang.ASTNode;
import com.rankweis.uppercut.karate.psi.KarateJsLanguage;
import com.rankweis.uppercut.karate.psi.formatter.KarateJsBlock;
import com.rankweis.uppercut.karate.psi.parser.KarateJsParserDefinition;
import java.util.ArrayList;
import java.util.List;

public class KarateJsFormatter {

  private static final KarateJsParserDefinition parserDefinition = new KarateJsParserDefinition();

  public List<Block> getJsSubBlocks(ASTNode astNode, Alignment alignment) {
    List<Block> result = new ArrayList<>();
    boolean isSingleLine =
      astNode.getTreeParent().getElementType().getLanguage() != KarateJsLanguage.INSTANCE && !astNode.getText()
        .contains("\n");

    for (ASTNode child : astNode.getChildren(null)) {
      if (!parserDefinition.getWhitespaceTokens().contains(child.getElementType())) {
        KarateJsBlock e = new KarateJsBlock(child, Indent.getNoneIndent(), isSingleLine);
        e.setAlignment(alignment);
        result.add(e);
      }
    }
    return result;
  }

}
