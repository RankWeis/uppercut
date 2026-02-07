package com.rankweis.uppercut.karate.psi.formatter;

import static com.rankweis.uppercut.karate.lexer.karatelabs.KarateLexerAdapter.getElement;
import static com.rankweis.uppercut.karate.lexer.karatelabs.KarateLexerAdapter.getElements;
import static com.rankweis.uppercut.karate.lexer.karatelabs.KarateLexerAdapter.getType;
import static com.rankweis.uppercut.karate.lexer.karatelabs.KarateLexerAdapter.getTypes;
import static io.karatelabs.js.Token.B_COMMENT;
import static io.karatelabs.js.Token.ELSE;
import static io.karatelabs.js.Token.EQ;
import static io.karatelabs.js.Token.EQ_EQ;
import static io.karatelabs.js.Token.EQ_EQ_EQ;
import static io.karatelabs.js.Token.EQ_GT;
import static io.karatelabs.js.Token.FUNCTION;
import static io.karatelabs.js.Token.GT;
import static io.karatelabs.js.Token.GT_EQ;
import static io.karatelabs.js.Token.GT_GT_EQ;
import static io.karatelabs.js.Token.GT_GT_GT;
import static io.karatelabs.js.Token.IDENT;
import static io.karatelabs.js.Token.LT;
import static io.karatelabs.js.Token.LT_EQ;
import static io.karatelabs.js.Token.LT_LT_EQ;
import static io.karatelabs.js.Token.L_COMMENT;
import static io.karatelabs.js.Token.L_CURLY;
import static io.karatelabs.js.Token.L_PAREN;
import static io.karatelabs.js.Token.PLUS_PLUS;
import static io.karatelabs.js.Token.RETURN;
import static io.karatelabs.js.Token.R_CURLY;
import static io.karatelabs.js.Token.SEMI;
import static io.karatelabs.js.Token.WS;
import static io.karatelabs.js.Token.WS_LF;
import static io.karatelabs.js.Type.ASSIGN_EXPR;
import static io.karatelabs.js.Type.BLOCK;
import static io.karatelabs.js.Type.FN_EXPR;
import static io.karatelabs.js.Type.LIT_OBJECT;
import static io.karatelabs.js.Type.LOGIC_EXPR;
import static io.karatelabs.js.Type.PROGRAM;
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
import io.karatelabs.js.Token;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class KarateJsBlock implements ASTBlock {

  private final ASTNode myNode;
  private final Indent myIndent;
  @Setter private Indent myChildrenIndent;
  @Setter private Alignment alignment;
  private final TextRange myTextRange;
  private final boolean myLeaf;
  private final Wrap myWrap;
  private List<Block> myChildren = null;

  private static final TokenSet BLOCKS_TO_NOT_INDENT = TokenSet.create(
    getElements(R_CURLY, L_CURLY).toArray(IElementType[]::new));

  private static final TokenSet BLOCKS_TO_INDENT_CHILDREN = TokenSet.create(
    getTypes(BLOCK, LIT_OBJECT).toArray(IElementType[]::new));

  private static final TokenSet BLOCKS_TO_NOT_LINE_FEED_BEFORE =
    TokenSet.create(getTypes(L_CURLY).toArray(IElementType[]::new));

  private static final TokenSet BLOCKS_TO_LINE_FEED_BEFORE =
    TokenSet.create(getTypes(FN_EXPR).toArray(IElementType[]::new));

  private static final TokenSet BLOCKS_TO_LINE_FEED_AFTER = TokenSet.create(
    getType(STATEMENT), getElement(L_COMMENT), getElement(B_COMMENT), getType(FN_EXPR));

  private static final TokenSet BLOCKS_TO_SPACE;

  private static final TokenSet BLOCKS_TO_SPACE_BEFORE = TokenSet.create(
    getType(LOGIC_EXPR));

  private static final TokenSet BLOCKS_TO_SPACE_AFTER = TokenSet.create(
    getElements(SEMI, PLUS_PLUS).toArray(IElementType[]::new));

  private static final TokenSet BLOCKS_TO_NOT_SPACE_AFTER = TokenSet.create(
    getElements(L_CURLY, PLUS_PLUS).toArray(IElementType[]::new)
  );

  private static final TokenSet BLOCKS_TO_NOT_SPACE_BEFORE = TokenSet.create(
    getElements(SEMI, Token.COMMA, R_CURLY).toArray(IElementType[]::new)
  );

  static {
    List<IElementType> types = new ArrayList<>(
      getTypes(EQ, EQ_GT, LT_LT_EQ, EQ_EQ, EQ_EQ_EQ, GT_EQ, GT, LT, LT_EQ, LT_LT_EQ, GT_GT_EQ, GT_GT_GT, RETURN));
    types.add(getType(STATEMENT));
    types.add(getType(BLOCK));
    types.add(getType(ASSIGN_EXPR));

    BLOCKS_TO_SPACE = TokenSet.create(types
      .toArray(IElementType[]::new));
  }

  private final boolean isSingleLine;

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

      Indent indent;
      Alignment blockAlignment = null;
      if (BLOCKS_TO_INDENT_CHILDREN.contains(myNode.getElementType())
        && !BLOCKS_TO_NOT_INDENT.contains(child.getElementType())) {
        indent = Indent.getNormalIndent();
      } else {
        indent = Indent.getNoneIndent();
      }
      if (child.getElementType() == getType(PROGRAM) || myNode.getElementType() == getType(PROGRAM)) {
        blockAlignment = alignment;
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

  @Override
  public Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
    ASTBlock block1 = (ASTBlock) child1;
    ASTBlock block2 = (ASTBlock) child2;
    if (block1 == null) {
      return null;
    }
    ASTNode node1 = block1.getNode();
    ASTNode node2 = block2.getNode();
    if (node1 == null) {
      return null;
    }
    IElementType elem1 = node1.getElementType();
    IElementType elem2 = node2.getElementType();
    int spaces = 0;
    boolean makeChange = false;
    if (BLOCKS_TO_NOT_SPACE_BEFORE.contains(elem2)
      || BLOCKS_TO_NOT_SPACE_AFTER.contains(elem1)) {
      makeChange = true;
    }
    if ((elem1 == getElement(FUNCTION) || elem1 == getElement(IDENT))
      && elem2 == getElement(L_PAREN)) {

      makeChange = true;
    }
    if (BLOCKS_TO_SPACE_AFTER.contains(
      node1.getElementType()) || BLOCKS_TO_SPACE_BEFORE.contains(node2.getElementType())) {
      makeChange = true;
      spaces = 1;
    }

    if (BLOCKS_TO_SPACE.contains(elem1) || BLOCKS_TO_SPACE.contains(elem2)
      || BLOCKS_TO_SPACE_AFTER.contains(node1.getElementType())) {

      makeChange = true;
      spaces = 1;
    }

    int lineFeeds = 0;
    if ((BLOCKS_TO_LINE_FEED_BEFORE.contains(elem2)
      || (BLOCKS_TO_LINE_FEED_AFTER.contains(elem1) && elem2 != getElement(L_COMMENT))
      || (elem1 == getElement(L_CURLY) && node1.getTreeParent().getElementType() == getType(BLOCK)))
      && !(elem1 == getType(STATEMENT) && (elem2 == getElement(ELSE)))) {
      makeChange = true;
      lineFeeds = 1;
    }

    boolean keepLineFeeds = true;
    if (BLOCKS_TO_NOT_LINE_FEED_BEFORE.contains(elem2)) {
      keepLineFeeds = false;
      lineFeeds = 0;
    }

    if (isSingleLine) {
      lineFeeds = 0;
    }
    if (elem1 == getElement(L_COMMENT) || elem2 == getElement(L_COMMENT)) {
      lineFeeds = 0;
    }
    if (makeChange) {
      return Spacing.createSpacing(spaces, spaces, lineFeeds, keepLineFeeds, 1);
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

  @Override public boolean isLeaf() {
    return false;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof KarateJsBlock that)) {
      return false;
    }
    return myLeaf == that.myLeaf && isSingleLine == that.isSingleLine && Objects.equals(myNode, that.myNode)
      && Objects.equals(myIndent, that.myIndent) && Objects.equals(myChildrenIndent, that.myChildrenIndent)
      && Objects.equals(alignment, that.alignment) && Objects.equals(myTextRange, that.myTextRange)
      && Objects.equals(myWrap, that.myWrap) && Objects.equals(myChildren, that.myChildren);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myNode, myIndent, myChildrenIndent, alignment, myTextRange, myLeaf, myWrap, myChildren,
      isSingleLine);
  }
}
