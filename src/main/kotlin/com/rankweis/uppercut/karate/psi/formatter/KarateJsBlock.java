package com.rankweis.uppercut.karate.psi.formatter;

import static com.rankweis.uppercut.karate.lexer.karatelabs.KarateLexerAdapter.getElement;
import static com.rankweis.uppercut.karate.lexer.karatelabs.KarateLexerAdapter.getElements;
import static com.rankweis.uppercut.karate.lexer.karatelabs.KarateLexerAdapter.getType;
import static com.rankweis.uppercut.karate.lexer.karatelabs.KarateLexerAdapter.getTypes;
import static com.rankweis.uppercut.karate.psi.GherkinElementTypes.JAVASCRIPT;
import static io.karatelabs.js.Token.B_COMMENT;
import static io.karatelabs.js.Token.ELSE;
import static io.karatelabs.js.Token.EQ;
import static io.karatelabs.js.Token.EQ_EQ;
import static io.karatelabs.js.Token.EQ_EQ_EQ;
import static io.karatelabs.js.Token.EQ_GT;
import static io.karatelabs.js.Token.GT;
import static io.karatelabs.js.Token.GT_EQ;
import static io.karatelabs.js.Token.GT_GT_EQ;
import static io.karatelabs.js.Token.GT_GT_GT;
import static io.karatelabs.js.Token.LT;
import static io.karatelabs.js.Token.LT_EQ;
import static io.karatelabs.js.Token.LT_LT_EQ;
import static io.karatelabs.js.Token.L_COMMENT;
import static io.karatelabs.js.Token.L_CURLY;
import static io.karatelabs.js.Token.RETURN;
import static io.karatelabs.js.Token.R_CURLY;
import static io.karatelabs.js.Token.SEMI;
import static io.karatelabs.js.Token.WS;
import static io.karatelabs.js.Token.WS_LF;
import static io.karatelabs.js.Type.BLOCK;
import static io.karatelabs.js.Type.STATEMENT;

import com.intellij.formatting.ASTBlock;
import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
import com.intellij.formatting.ChildAttributes;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Spacing;
import com.intellij.formatting.Wrap;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.rankweis.uppercut.karate.psi.GherkinElementTypes;
import com.rankweis.uppercut.karate.psi.KarateTokenTypes;
import io.karatelabs.js.Token;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class KarateJsBlock implements ASTBlock {

  private static final List<Character> NON_SPACED_CHARACTERS = List.of(',', '.', '[', ']');

  private final ASTNode myNode;
  private final Indent myIndent;
  private Alignment alignment;
  private final TextRange myTextRange;
  private final boolean myLeaf;
  private final Wrap myWrap;
  private List<Block> myChildren = null;

  private static final TokenSet BLOCKS_TO_INDENT = TokenSet.create(
    getType(STATEMENT));

  private static final TokenSet BLOCKS_TO_INDENT_CHILDREN = TokenSet.create(
    getType(STATEMENT));

  private static final TokenSet BLOCKS_TO_LINE_FEED_BEFORE = TokenSet.create();

  private static final TokenSet BLOCKS_TO_LINE_FEED_AFTER = TokenSet.create(
    getType(STATEMENT), getElement(L_COMMENT), getElement(B_COMMENT));

  private static final TokenSet BLOCKS_TO_SPACE;

  private static final TokenSet BLOCKS_TO_SPACE_AFTER = TokenSet.create(
    getElement(SEMI)
  );

  private static final TokenSet BLOCKS_TO_NOT_SPACE_AFTER = TokenSet.create(
    getElement(L_CURLY)
  );

  private static final TokenSet BLOCKS_TO_NOT_SPACE_BEFORE = TokenSet.create(
    getElements(SEMI, Token.COMMA, R_CURLY).toArray(IElementType[]::new)
  );

  static {
    List<IElementType> types = new ArrayList<>();
      types.addAll(getTypes(EQ, EQ_GT, LT_LT_EQ, EQ_EQ, EQ_EQ_EQ, GT_EQ, GT, LT, LT_EQ, LT_LT_EQ, GT_GT_EQ, GT_GT_GT, RETURN));
    types.add(getType(STATEMENT));
    types.add(getType(BLOCK));

     BLOCKS_TO_SPACE = TokenSet.create(types
        .toArray(IElementType[]::new));
  }

  private static final TokenSet READ_ONLY_BLOCKS =
    TokenSet.create(JAVASCRIPT, GherkinElementTypes.PYSTRING, KarateTokenTypes.COMMENT);
  private boolean isSingleLine;

  public KarateJsBlock(ASTNode node, Indent indent, boolean isSingleLine) {
    this(node, indent, node.getTextRange(), isSingleLine);
  }

  public KarateJsBlock(ASTNode node, Indent indent, final TextRange textRange, boolean isSingleLine) {
    this(node, indent, textRange, false, isSingleLine);
  }

  public KarateJsBlock(ASTNode node, Indent indent, final TextRange textRange, final boolean leaf,
    boolean isSingleLine) {
    this(node, indent, textRange, leaf, null, isSingleLine);
  }

  public KarateJsBlock(ASTNode myNode, Indent myIndent, TextRange myTextRange, boolean myLeaf, Wrap myWrap,
    boolean isSingleLine) {
    this.myNode = myNode;
    this.myIndent = myIndent;
    this.myTextRange = myTextRange;
    this.myLeaf = myLeaf;
    this.myWrap = myWrap;
    this.isSingleLine = isSingleLine;
  }

  @Override
  public ASTNode getNode() {
    return myNode;
  }

  @Override
  @NotNull
  public TextRange getTextRange() {
    return myTextRange;
  }

  @Override
  @NotNull
  public List<Block> getSubBlocks() {
    if (myLeaf) {
      return Collections.emptyList();
    }
    if (myChildren == null) {
      myChildren = buildChildren();
    }
    return myChildren;
  }

  private List<Block> buildChildren() {
    final ASTNode[] children = myNode.getChildren(null);
    if (children.length == 0) {
      return Collections.emptyList();
    }

    List<Block> result = new ArrayList<>();

    for (ASTNode child : children) {
      if (child.getElementType() == TokenType.WHITE_SPACE
        || child.getElementType() == getElement(WS)
        || child.getElementType() == getElement(WS_LF)) {

        continue;
      }

      boolean isTagInsideScenario = child.getElementType() == GherkinElementTypes.TAG &&
        myNode.getElementType() == GherkinElementTypes.SCENARIO_OUTLINE &&
        child.getStartOffset() > myNode.getStartOffset();
      Indent indent;
      Alignment blockAlignment = null;
      if (BLOCKS_TO_INDENT_CHILDREN.contains(myNode.getElementType()) || isTagInsideScenario) {
        indent = Indent.getNormalIndent();
      } else {
        indent = Indent.getNoneIndent();
      }
      // skip epmty cells
      if (child.getElementType() == GherkinElementTypes.TABLE_CELL) {
        if (child.getChildren(null).length == 0) {
          continue;
        }
      }
      if (child.getElementType() == JAVASCRIPT) {
        blockAlignment = Alignment.createAlignment();
      }
      if (child.getElementType() == getElement(L_COMMENT) || child.getElementType() == getElement(B_COMMENT)) {
        indent = Indent.getNormalIndent();
      }
      KarateJsBlock e = new KarateJsBlock(child, indent, isSingleLine);
      e.setAlignment(blockAlignment);
      result.add(e);
    }
    return result;
  }

  @Override
  @Nullable
  public Wrap getWrap() {
    return myWrap;
  }

  @Override
  public Indent getIndent() {
    return myIndent;
  }

  @Override
  public Alignment getAlignment() {
    return alignment;
  }

  public void setAlignment(Alignment alignment) {
    this.alignment = alignment;
  }

  @Override
  public Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
    ASTBlock block1 = (ASTBlock) child1;
    ASTBlock block2 = (ASTBlock) child2;
    ASTNode node1 = block1.getNode();
    ASTNode node2 = block2.getNode();
    boolean makeChange = false;
    int spaces = 0;
    int lineFeeds = 0;
    if (child1 == null) {
      return null;
    }
    if (BLOCKS_TO_NOT_SPACE_BEFORE.contains(node2.getElementType()) || BLOCKS_TO_NOT_SPACE_AFTER.contains(
      node1.getElementType())) {
      makeChange = true;
      spaces = 0;
    }
    if (BLOCKS_TO_SPACE_AFTER.contains(node1.getElementType())) {
      makeChange = true;
      spaces = 1;
    }

    if (BLOCKS_TO_SPACE.contains(node1.getElementType()) || BLOCKS_TO_SPACE.contains(node2.getElementType())
      || BLOCKS_TO_SPACE_AFTER.contains(node1.getElementType())) {

      makeChange = true;
      spaces = 1;
    }

    if ((BLOCKS_TO_LINE_FEED_BEFORE.contains(node2.getElementType())
      || (BLOCKS_TO_LINE_FEED_AFTER.contains(node1.getElementType()) && node2.getElementType() != getElement(L_COMMENT))
      || (node1.getElementType() == getElement(L_CURLY) && node1.getTreeParent().getElementType() == getType(BLOCK)))
      && !(node1.getElementType() == getType(STATEMENT) && (node2.getElementType() == getElement(ELSE)))) {
      makeChange = true;
      lineFeeds = 1;
    }
    //    if (BLOCKS_TO_LINE_FEED.contains(node1.getElementType()) && BLOCKS_TO_LINE_FEED.contains(node2
    //    .getElementType())
    //      && !isSingleLine) {
    //      return Spacing.createSpacing(1, 1, 1, true, 1);
    //    }
    if (isSingleLine) {
      lineFeeds = 0;
    }
    if (makeChange) {
      return Spacing.createSpacing(spaces, spaces, lineFeeds, true, 1);
    }
     return Spacing.createSafeSpacing(true, 1);
  }

  @Override
  @NotNull
  public ChildAttributes getChildAttributes(int newChildIndex) {
    ASTNode node = getNode();
    Indent childIndent =
      node != null && BLOCKS_TO_INDENT_CHILDREN.contains(getNode().getElementType()) ? Indent.getNormalIndent()
        : Indent.getNoneIndent();
    return new ChildAttributes(childIndent, null);
  }

  @Override public boolean isIncomplete() {
    return false;
  }

  public void setSingleLine(boolean singleLine) {
    isSingleLine = singleLine;
  }

  @Override public boolean isLeaf() {
    return false;
  }
}
