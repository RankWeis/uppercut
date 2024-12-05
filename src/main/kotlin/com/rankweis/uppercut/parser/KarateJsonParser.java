package com.rankweis.uppercut.parser;

import static com.rankweis.uppercut.karate.psi.GherkinElementTypes.JSON;

import com.intellij.json.JsonParser;
import com.intellij.json.psi.JsonParserUtil;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import com.intellij.lang.parser.GeneratedParserUtilBase.Builder;
import com.intellij.lang.parser.GeneratedParserUtilBase.Parser;
import com.intellij.psi.tree.IElementType;
import com.rankweis.uppercut.karate.psi.KarateLanguage;
import java.util.Objects;

public class KarateJsonParser extends JsonParser {
  public static final Parser TOKEN_ADVANCER = (builder, level) -> {
    if (builder.eof() || Objects.requireNonNull(builder.getTokenType()).getLanguage().is(KarateLanguage.INSTANCE)) {
      return false;
    }
    builder.advanceLexer();
    return true;
  };

  @Override public void parseLight(IElementType t, PsiBuilder b) {
    Marker mark = b.mark();
    b = JsonParserUtil.adapt_builder_(t, b, this, EXTENDS_SETS_);
    ((Builder) b).state.tokenAdvancer = TOKEN_ADVANCER;
    PsiBuilder.Marker m = JsonParserUtil.enter_section_(b, 0, 1, null);
    boolean r = this.parse_root_(t, b);
    JsonParserUtil.exit_section_(b, 0, m, t, r, true, TOKEN_ADVANCER);
    mark.done(JSON);
  }
}
