package com.rankweis.uppercut.karate.format;

import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
import com.intellij.lang.ASTNode;
import com.rankweis.uppercut.karate.psi.KarateJsLanguage;
import com.rankweis.uppercut.karate.psi.formatter.KarateJsBlock;
import java.util.ArrayList;
import java.util.List;

public class KarateJsFormatter {


  public List<Block> getJsSubBlocks(ASTNode astNode, Alignment alignment) {
    List<Block> result = new ArrayList<>();
    boolean isSingleLine = false;
    if(astNode.getTreeParent().getElementType().getLanguage() != KarateJsLanguage.INSTANCE && !astNode.getText().contains("\n")) {
      isSingleLine = true;
    }
    for (ASTNode child : astNode.getChildren(null)) {
      if (!child.getText().isBlank()) {
        result.add(new KarateJsBlock(child, null, isSingleLine));
      }
    }
    return result;
  }

}
