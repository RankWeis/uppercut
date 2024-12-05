package com.rankweis.uppercut.karate.psi.formatter;

import static com.rankweis.uppercut.karate.psi.GherkinElementTypes.JAVASCRIPT;
import static com.rankweis.uppercut.karate.psi.GherkinElementTypes.JSON;
import static com.rankweis.uppercut.karate.psi.GherkinElementTypes.STEP;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.CLOSE_PAREN;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.COLON;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.IDENTIFIERS;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.OPEN_PAREN;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.OPERATOR;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.TEXT_LIKE;

import com.intellij.formatting.ASTBlock;
import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
import com.intellij.formatting.ChildAttributes;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Spacing;
import com.intellij.formatting.SpacingBuilder;
import com.intellij.formatting.Wrap;
import com.intellij.formatting.WrapType;
import com.intellij.json.formatter.JsonBlock;
import com.intellij.json.formatter.JsonCodeStyleSettings;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.TokenType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.rankweis.uppercut.karate.lexer.KarateJavascriptParsingExtensionPoint;
import com.rankweis.uppercut.karate.psi.GherkinElementTypes;
import com.rankweis.uppercut.karate.psi.GherkinKeywordProvider;
import com.rankweis.uppercut.karate.psi.GherkinParserDefinition;
import com.rankweis.uppercut.karate.psi.GherkinTable;
import com.rankweis.uppercut.karate.psi.KarateTokenTypes;
import com.rankweis.uppercut.karate.psi.PlainKarateKeywordProvider;
import com.rankweis.uppercut.karate.psi.i18n.JsonGherkinKeywordProvider;
import io.netty.util.internal.StringUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class KarateJavaScriptBlock implements ASTBlock {

  private static final List<Character> NON_SPACED_CHARACTERS = List.of(',', '.', '[', ']');

  private final ASTNode myNode;
  private final Indent myIndent;
  private Alignment alignment;
  private final TextRange myTextRange;
  private final boolean myLeaf;
  private List<Block> myChildren = null;
  private final GherkinKeywordProvider myKeywordProvider = JsonGherkinKeywordProvider.getKeywordProvider();
  private final GherkinKeywordProvider myActionKeywordProvider = new PlainKarateKeywordProvider();
  private final JsonCodeStyleSettings jsonCustomSettings =
    CodeStyleSettings.getDefaults().getCustomSettings(JsonCodeStyleSettings.class);

  private static final TokenSet BLOCKS_TO_INDENT = TokenSet.create(GherkinElementTypes.FEATURE_HEADER,
    GherkinElementTypes.RULE,
    GherkinElementTypes.SCENARIO,
    GherkinElementTypes.SCENARIO_OUTLINE,
    GherkinElementTypes.STEP,
    GherkinElementTypes.TABLE,
    GherkinElementTypes.EXAMPLES_BLOCK);

  private static final TokenSet BLOCKS_TO_INDENT_CHILDREN = TokenSet.create(GherkinParserDefinition.GHERKIN_FILE,
    GherkinElementTypes.FEATURE,
    GherkinElementTypes.SCENARIO,
    GherkinElementTypes.RULE,
    GherkinElementTypes.SCENARIO_OUTLINE, JAVASCRIPT);

  private static final TokenSet READ_ONLY_BLOCKS =
    TokenSet.create(JAVASCRIPT, GherkinElementTypes.PYSTRING, KarateTokenTypes.COMMENT);

  public KarateJavaScriptBlock(ASTNode node) {
    this(node, Indent.getAbsoluteNoneIndent());
  }

  public KarateJavaScriptBlock(ASTNode node, Indent indent) {
    this(node, indent, node.getTextRange());
  }

  public KarateJavaScriptBlock(ASTNode node, Indent indent, final TextRange textRange) {
    this(node, indent, textRange, false);
  }

  public KarateJavaScriptBlock(ASTNode node, Indent indent, final TextRange textRange, final boolean leaf) {
    myNode = node;
    myIndent = indent;
    myTextRange = textRange;
    myLeaf = leaf;
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

    if (myNode.getElementType() == JAVASCRIPT) {
      return KarateJavascriptParsingExtensionPoint.EP_NAME.getExtensionList()
        .stream().findFirst()
        .map(extension -> extension.getJsSubBlocks(myNode, alignment))
        .orElse(result);
    }
    if (myNode.getElementType() == JSON) {
      JsonBlock parentblock =
        new JsonBlock(null, myNode, jsonCustomSettings, getAlignment(),
          Indent.getContinuationWithoutFirstIndent(), Wrap.createWrap(WrapType.NORMAL, true),
          createJsonSpacingBuilder(CodeStyleSettings.getDefaults()));

      if (!myNode.getText().strip().contains("\n")) {
        jsonCustomSettings.OBJECT_WRAPPING = 0;
        jsonCustomSettings.ARRAY_WRAPPING = 0;
        setAlignment(Alignment.createAlignment());
        parentblock =
          new JsonBlock(null, myNode, jsonCustomSettings, getAlignment(),
            Indent.getNoneIndent(), Wrap.createWrap(WrapType.NONE, false),
            createJsonSpacingBuilder(CodeStyleSettings.getDefaults()));
      } else {
        jsonCustomSettings.OBJECT_WRAPPING = 2;
        jsonCustomSettings.ARRAY_WRAPPING = 2;
      }
      for (ASTNode jsChild : children) {
        if (jsChild.getElementType() == TokenType.WHITE_SPACE) {
          continue;
        }
        JsonBlock jsonBlock =
          new JsonBlock(parentblock, jsChild, jsonCustomSettings, getAlignment(),
            Indent.getContinuationWithoutFirstIndent(), Wrap.createWrap(WrapType.NORMAL, true),
            createJsonSpacingBuilder(CodeStyleSettings.getDefaults()));
        result.add(jsonBlock);
      }
      return result;
    }
    for (ASTNode child : children) {
      if (child.getElementType() == TokenType.WHITE_SPACE) {
        continue;
      }

      boolean isTagInsideScenario = child.getElementType() == GherkinElementTypes.TAG &&
        myNode.getElementType() == GherkinElementTypes.SCENARIO_OUTLINE &&
        child.getStartOffset() > myNode.getStartOffset();
      Indent indent;
      Alignment blockAlignment = null;
      if (BLOCKS_TO_INDENT.contains(child.getElementType()) || isTagInsideScenario) {
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
      if (child.getElementType() == KarateTokenTypes.COMMENT) {
        final ASTNode commentIndentElement = child.getTreePrev();
        if (commentIndentElement != null && (commentIndentElement.getText().contains("\n")
          || commentIndentElement.getTreePrev() == null)) {
          final String whiteSpaceText = commentIndentElement.getText();
          final int lineBreakIndex = whiteSpaceText.lastIndexOf("\n");

          indent = Indent.getSpaceIndent(whiteSpaceText.length() - lineBreakIndex - 1);
        }
      }
      KarateJavaScriptBlock e = new KarateJavaScriptBlock(child, indent);
      e.setAlignment(blockAlignment);
      result.add(e);
    }
    return result;
  }

  @Override
  @Nullable
  public Wrap getWrap() {
    return null;
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
    if (child1 == null) {
      return null;
    }

    ASTBlock block1 = (ASTBlock) child1;
    ASTBlock block2 = (ASTBlock) child2;
    ASTNode node1 = block1.getNode();
    ASTNode node2 = block2.getNode();
    if (node1 == null || node2 == null) {
      return null;
    }
    final IElementType parent1 = node1.getTreeParent() != null ? node1.getTreeParent().getElementType() : null;
    final IElementType elementType1 = node1.getElementType();
    final IElementType elementType2 = node2.getElementType();

    if (READ_ONLY_BLOCKS.contains(elementType2)) {
      return Spacing.getReadOnlySpacing();
    }
    if (KarateTokenTypes.ACTION_KEYWORD == elementType1) {
      String text = node1.getText();
      if (myActionKeywordProvider.isActionKeyword(text)) {
        if (myKeywordProvider.isSpaceRequiredAfterKeyword("en", text)) {
          return Spacing.createSpacing(1, 1, 0, false, 0);
        } else {
          return Spacing.createSpacing(0, 1, 0, false, 0);
        }
      } else {
        return Spacing.createSpacing(1, 1, 0, false, 0);
      }
    }
    if (OPEN_PAREN == elementType1 || CLOSE_PAREN == elementType2) {
      return Spacing.createSpacing(0, 0, 0, false, 0);
    }
    if (IDENTIFIERS.contains(elementType1) || IDENTIFIERS.contains(elementType2)
      || OPERATOR == elementType1 || OPERATOR == elementType2) {
      return Spacing.createSpacing(1, 1, 0, false, 0);
    }
    if ((TEXT_LIKE.contains(elementType1) && KarateTokenTypes.QUOTED_STRING.contains(elementType2)) || (
      TEXT_LIKE.contains(elementType2) && KarateTokenTypes.QUOTED_STRING.contains(elementType1))) {
      if (NON_SPACED_CHARACTERS.stream().anyMatch(s -> node2.getText().startsWith("" + s)) ||
        NON_SPACED_CHARACTERS.stream().anyMatch(s -> StringUtil.endsWith(node1.getText(), s))) {
        return Spacing.createSpacing(0, 1, 0, false, 0);
      }
      return Spacing.createSpacing(1, 1, 0, true, 1);
    }
    if (GherkinElementTypes.SCENARIOS.contains(elementType2) &&
      elementType1 != KarateTokenTypes.COMMENT &&
      parent1 != GherkinElementTypes.RULE) {
      return Spacing.createSpacing(0, 0, 2, true, 2);
    }
    if (elementType1 == KarateTokenTypes.PIPE &&
      elementType2 == GherkinElementTypes.TABLE_CELL) {
      return Spacing.createSpacing(1, 1, 0, false, 0);
    }
    if ((elementType1 == GherkinElementTypes.TABLE_CELL || elementType1 == KarateTokenTypes.PIPE) &&
      elementType2 == KarateTokenTypes.PIPE) {
      final ASTNode tableNode = TreeUtil.findParent(node1, GherkinElementTypes.TABLE);
      if (tableNode != null) {
        int columnIndex = getTableCellColumnIndex(node1);
        int maxWidth = ((GherkinTable) tableNode.getPsi()).getColumnWidth(columnIndex);
        int spacingWidth = (maxWidth - node1.getText().trim().length()) + 1;
        if (elementType1 == KarateTokenTypes.PIPE) {
          spacingWidth += 2;
        }
        return Spacing.createSpacing(spacingWidth, spacingWidth, 0, false, 0);
      }
    }
    if (KarateTokenTypes.KEYWORDS.contains(elementType1) && elementType2 != COLON) {
      boolean keepLineBreaks = elementType2 == STEP;
      return Spacing.createSpacing(1, 1, 0, keepLineBreaks, 0);
    }
    return null;
  }

  private static int getTableCellColumnIndex(ASTNode node) {
    int pipeCount = 0;
    while (node != null) {
      if (node.getElementType() == KarateTokenTypes.PIPE) {
        pipeCount++;
      }
      node = node.getTreePrev();
    }
    return pipeCount - 1;
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

  @Override
  public boolean isIncomplete() {
    ASTNode node = getNode();
    if (node == null) {
      return false;
    }
    if (GherkinElementTypes.SCENARIOS.contains(node.getElementType())) {
      return true;
    }
    if (node.getElementType() == GherkinElementTypes.FEATURE) {
      return node.getChildren(TokenSet.create(GherkinElementTypes.FEATURE_HEADER,
        GherkinElementTypes.SCENARIO,
        GherkinElementTypes.SCENARIO_OUTLINE)).length == 0;
    }
    return false;
  }

  @Override
  public boolean isLeaf() {
    return myLeaf;
  }

  static @NotNull SpacingBuilder createJsonSpacingBuilder(CodeStyleSettings settings) {
    return GherkinBlock.createJsonSpacingBuilder(settings);
  }
}
