package com.rankweis.uppercut.karate.format;

import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
import com.intellij.lang.ASTNode;
import com.rankweis.uppercut.karate.psi.formatter.KarateJsBlock;
import java.util.ArrayList;
import java.util.List;

public class KarateJsFormatter {


  public List<Block> getJsSubBlocks(ASTNode astNode, Alignment alignment) {
    List<Block> result = new ArrayList<>();
    for (ASTNode child : astNode.getChildren(null)) {
      if (child.getText() != null && child.getText().strip().isEmpty()) {
        result.add(new KarateJsBlock(child, null));
      }
    }
    return result;
  }

}
