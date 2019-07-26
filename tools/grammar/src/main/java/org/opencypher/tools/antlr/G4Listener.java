/*
 * Copyright (c) 2015-2019 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Attribution Notice under the terms of the Apache License 2.0
 *
 * This work was created by the collective efforts of the openCypher community.
 * Without limiting the terms of Section 6, any Derivative Work that is not
 * approved by the public consensus process of the openCypher Implementers Group
 * should not be described as “Cypher” (and Cypher® is a registered trademark of
 * Neo4j Inc.) or as "openCypher". Extensions by implementers or prototypes or
 * proposals for change that have been documented or implemented should only be
 * described as "implementation extensions to Cypher" or as "proposed changes to
 * Cypher that are not yet approved by the openCypher community".
 */
  package org.opencypher.tools.antlr;

import java.util.List;

import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.opencypher.tools.antlr.g4.Gee4BaseListener;
import org.opencypher.tools.antlr.g4.Gee4Lexer;
import org.opencypher.tools.antlr.g4.Gee4Parser.CardinalityContext;
import org.opencypher.tools.antlr.g4.Gee4Parser.CharSetContext;
import org.opencypher.tools.antlr.g4.Gee4Parser.DotPatternContext;
import org.opencypher.tools.antlr.g4.Gee4Parser.FragmentRuleContext;
import org.opencypher.tools.antlr.g4.Gee4Parser.GrammardefContext;
import org.opencypher.tools.antlr.g4.Gee4Parser.LiteralContext;
import org.opencypher.tools.antlr.g4.Gee4Parser.NegatedCharSetContext;
import org.opencypher.tools.antlr.g4.Gee4Parser.NegatedQuotedStringContext;
import org.opencypher.tools.antlr.g4.Gee4Parser.QuotedStringContext;
import org.opencypher.tools.antlr.g4.Gee4Parser.RuleAlternativeContext;
import org.opencypher.tools.antlr.g4.Gee4Parser.RuleComponentContext;
import org.opencypher.tools.antlr.g4.Gee4Parser.RuleElementsContext;
import org.opencypher.tools.antlr.g4.Gee4Parser.RuleItemContext;
import org.opencypher.tools.antlr.g4.Gee4Parser.RuleNameContext;
import org.opencypher.tools.antlr.g4.Gee4Parser.RuleReferenceContext;
import org.opencypher.tools.antlr.g4.Gee4Parser.Rule_Context;
import org.opencypher.tools.antlr.g4.Gee4Parser.RulelistContext;
import org.opencypher.tools.antlr.g4.Gee4Parser.SpecialRuleContext;
import org.opencypher.tools.antlr.g4.Gee4Parser.WholegrammarContext;
import org.opencypher.tools.antlr.tree.EOFreference;
import org.opencypher.tools.antlr.tree.ExclusionCharacterSet;
import org.opencypher.tools.antlr.tree.GrammarItem;
import org.opencypher.tools.antlr.tree.GrammarName;
import org.opencypher.tools.antlr.tree.GrammarTop;
import org.opencypher.tools.antlr.tree.Group;
import org.opencypher.tools.antlr.tree.InAlternative;
import org.opencypher.tools.antlr.tree.InAlternatives;
import org.opencypher.tools.antlr.tree.InLiteral;
import org.opencypher.tools.antlr.tree.InOptional;
import org.opencypher.tools.antlr.tree.ListedCharacterSet;
import org.opencypher.tools.antlr.tree.NormalText;
import org.opencypher.tools.antlr.tree.OneOrMore;
import org.opencypher.tools.antlr.tree.Rule;
import org.opencypher.tools.antlr.tree.Rule.RuleType;
import org.opencypher.tools.antlr.tree.RuleId;
import org.opencypher.tools.antlr.tree.RuleList;
import org.opencypher.tools.antlr.tree.ZeroOrMore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class G4Listener extends Gee4BaseListener
{
	private final ParseTreeProperty<GrammarItem> items = new ParseTreeProperty<>();
	private GrammarTop treeTop = null;
	private final CommonTokenStream tokens;
	// nasty stateful field
	private boolean lexerRule = false;
	
	private static final Logger LOGGER = LoggerFactory.getLogger(G4Listener.class.getName());

	public G4Listener(CommonTokenStream tokens)
	{
		super();
		this.tokens = tokens;
	}

	public GrammarTop getTreeTop()
	{
		return treeTop;
	}

	@Override
	public void exitWholegrammar(WholegrammarContext ctx) {
		GrammarName name = (GrammarName) getItem(ctx.grammardef());
		RuleList rules = (RuleList) getItem(ctx.rulelist());
		final String headerText;
		headerText = null;
		treeTop = new GrammarTop(name, rules, headerText);
		
	}

	@Override
	public void exitGrammardef(GrammardefContext ctx) {
		String name = ctx.IDENTIFIER().getText();
		setItem(ctx, new GrammarName(name));
	}

	@Override
	public void exitRulelist(RulelistContext ctx)
	{
		
		RuleList rules = new RuleList();
		for (Rule_Context ruleCtx : ctx.rule_())
		{
			GrammarItem grammarItem = getItem(ruleCtx);
			LOGGER.debug("adding an item for {}", ruleCtx.getText());
			rules.addItem(grammarItem);
		}
		for (FragmentRuleContext ruleCtx : ctx.fragmentRule())
		{
			GrammarItem grammarItem = getItem(ruleCtx);
			LOGGER.debug("adding a fragment item for {}", ruleCtx.getText());
			rules.addItem(grammarItem);
		}
		setItem(ctx, rules);
	}
	
	@Override
	public void exitRule_(Rule_Context ctx)
	{
		final Rule rule;
		GrammarItem item = getItem(ctx.ruleElements());
		String ruleName = getItem(ctx.ruleName()).toString().replaceFirst("^L_", "");
		// this is juggled in the normaliser
//		if (lexerRule && ruleName.matches("[A-Z]+") && item.isKeywordPart()) {
//			rule = new Rule(getItem(ctx.ruleName()), item, true, RuleType.NORMAL);
//		} else {
			rule = new Rule(getItem(ctx.ruleName()), item);
//		}
		setItem(ctx, rule);
	}

	@Override
	public void exitFragmentRule(FragmentRuleContext ctx)
	{
		final Rule rule;
		GrammarItem item = getItem(ctx.literal());
		String ruleName = getItem(ctx.ruleName()).toString().replaceFirst("^L_", "");
		if (lexerRule && ruleName.matches("[A-Z]+") && item.isKeywordPart()) {
			rule = new Rule(getItem(ctx.ruleName()), item, true, RuleType.KEYWORD);
		} else {
			rule = new Rule(getItem(ctx.ruleName()), item, false, RuleType.FRAGMENT);
		}
		setItem(ctx, rule);
	}


	@Override
	public void exitSpecialRule(SpecialRuleContext ctx) {

		LOGGER.warn("Ignoring special rule {}", ctx.getText());
	}


	@Override
	public void exitRuleName(RuleNameContext ctx)
	{
		String ruleName = ctx.getText();
		// flip a flag that will last for the processing of the rule
		lexerRule = ruleName.equals(ruleName.toUpperCase());
		// special rule to undo the marking of reserved words
		setItem(ctx, new RuleId(ruleName.replaceFirst("^L_", "")));
	}

	@Override
	public void exitRuleElements(RuleElementsContext ctx)
	{
		InAlternatives alts = new InAlternatives();
		for (RuleAlternativeContext element : ctx.ruleAlternative())
		{
			alts.addItem(getItem(element));
		}
		setItem(ctx, alts);
	}

	@Override
	public void exitRuleAlternative(RuleAlternativeContext ctx)
	{
		InAlternative alt = new InAlternative();
		for (RuleItemContext element : ctx.ruleItem())
		{
			alt.addItem(getItem(element));
		}
		NormalText normal = findNormalText(ctx);
		if (normal != null) {
			alt.addItem(normal);
		}
		setItem(ctx, alt);

	}

	private NormalText findNormalText(ParserRuleContext ctx)
	{
		Token endAlt = ctx.getStop();
		int i = endAlt.getTokenIndex();
		List<Token> normalTextChannel =
					tokens.getHiddenTokensToRight(i, Gee4Lexer.HIDDEN);
		if (normalTextChannel != null) {
			Token normalToken = normalTextChannel.get(0);
			if (normalToken != null) {
				String txt = normalToken.getText().replaceFirst("//!!\\s*", "");
				return new NormalText(txt);
			}
		}
		return null;
	}

	
	@Override
	public void exitRuleItem(RuleItemContext ctx)
	{
		final ParserRuleContext contentCtx;
		final boolean singular;
		if (ctx.ruleComponent() != null) {
			contentCtx = ctx.ruleComponent();
			singular = true;
		} else {
			contentCtx = ctx.ruleElements();
			singular = false;
		}
		GrammarItem contentItem = getItem(contentCtx);
		final GrammarItem ourItem;
		CardinalityContext cardinCtx = ctx.cardinality();
		if (cardinCtx == null) {
			if (singular) {
				ourItem = contentItem;
			} else {
				ourItem = new Group(contentItem);
			}
		} else if (cardinCtx.QUESTION() != null) {
			ourItem = new InOptional(contentItem);
		} else if (cardinCtx.PLUS() != null ) {
			ourItem = new OneOrMore(contentItem);
		} else if (cardinCtx.STAR() != null) {
			ourItem = new ZeroOrMore(contentItem);
		} else {
			throw new IllegalStateException("Cannot find content for RuleItem: " + ctx.getText());
		}
		setItem(ctx, ourItem);
		
	}

	@Override
	public void exitRuleComponent(RuleComponentContext ctx)
	{
		pullUpItem(ctx);
	}

	
	@Override
	public void exitQuotedString(QuotedStringContext ctx) {
		String text = ctx.getText().replaceFirst("^'","").replaceFirst("'$", "");
		// does this cope with mixtures ?
		setItem(ctx, new InLiteral(text));
	}

	@Override
	public void exitNegatedQuotedString(NegatedQuotedStringContext ctx) {
		setItem(ctx, new ExclusionCharacterSet(ctx.getText().replaceFirst("^~'","").replaceFirst("'$", "")));
	}

	@Override
	public void exitCharSet(CharSetContext ctx) {
		String charSetString = ctx.getText().replaceFirst("^\\[","").replaceFirst("\\]$", "");
		if (charSetString.contains("\\")) {
			charSetString = interpret(charSetString);
		}
		setItem(ctx, new ListedCharacterSet(charSetString));
	}

	private String interpret(String charSetString) {
		// to cope with punctuation, especially backslash, the syntax has text+,
		// but we want them together again
		LOGGER.warn("interpreting {}", charSetString);
		boolean escaped = false;
		StringBuilder b = new StringBuilder();
		for (int i = 0, end = charSetString.length() - 1; i < end; i++ ) {
			int cp = charSetString.codePointAt(i);
			switch (cp) {
			case '\\':
				if (escaped) {
					b.append(cp);
					escaped = false;
				} else {
					escaped = true;
				}
				break;

			default:
				if (escaped) {
					escaped = false;
					switch (cp) {
					case 'r':
						b.append('\r');
						break;
					case 'n':
						b.append("\n");
						break;
					case 't':
						b.append("\t");
						break;
					case 'b':
						b.append("\b");
						break;
					case 'f':
						b.append("\f");
						break;
					case '\\':
						b.append("\\");
						break;
					case 'u':
						if (i + 4 > charSetString.length()) {
							throw new IllegalArgumentException("unicode escape requires 4 hex digits");
						}
						String hexchars = charSetString.substring(i+1, i + 5);
						LOGGER.warn("at {}, hex {}", i, hexchars);
						int ch = Integer.parseInt(hexchars, 16);
						b.append((char) ch);
						i += 4;
						break;
					default:
						throw new IllegalArgumentException("Don't know how to interpret escaped character " + cp);
					}
				} else {
					b.append(cp);
				}

			}
		}
		String answer = b.toString();
		LOGGER.warn("became {}, len {}", answer, answer.length());

		return answer;

	}
	
	@Override
	public void exitNegatedCharSet(NegatedCharSetContext ctx) {
		setItem(ctx, new ExclusionCharacterSet(ctx.getText().replaceFirst("^~\\[","").replaceFirst("\\]$", "")));
	}

	@Override
	public void exitDotPattern(DotPatternContext ctx) {
		throw new UnsupportedOperationException("Cannot handle lexer dot pattern: " + ctx.getText());
	}

	@Override
	public void exitLiteral(LiteralContext ctx)
	{
		pullUpItem(ctx);
	}

	
	
	private String respaceString(String original) {
		return original.replaceAll("_", " ");
	}
	
	@Override
	public void exitRuleReference(RuleReferenceContext ctx)
	{
		// special case keywords thata have been escaped by Antlr4 write
		String ruleName = ctx.getText();
		if (ruleName.startsWith("L_")) {
			setItem(ctx, new InLiteral(ruleName.substring(2)));
//		} else if (ruleName.matches("[A-Z]+")) {
//			// special case keyword from oC bnf
//			setItem(ctx, new InLiteral(ruleName));
		} else if (ruleName.equals("EOF")) {
			setItem(ctx, new EOFreference());
		} else {
			setItem(ctx, new RuleId(ruleName));
		}
	}

	@Override
	public void exitEveryRule(ParserRuleContext ctx)
	{
//		LOGGER.debug("   exiting a {} : {}", ctx.getClass().getSimpleName().replaceFirst("Context",""), ctx.getText());
		super.exitEveryRule(ctx);
	}

	private GrammarItem getItem(ParseTree ctx) {
		return items.get(ctx);
	}
	
	private void setItem(ParseTree ctx, GrammarItem item) {
		LOGGER.debug("setting a {} for {}", item.getClass().getSimpleName(), ctx.getText());
		items.put(ctx, item);
	}
	
	private void pullUpItem(ParseTree ctx) {
		LOGGER.debug("promoting a {} from {}", ctx.getClass().getSimpleName(),  ctx.getChild(0).getClass().getSimpleName());
		items.put(ctx,  getItem(ctx.getChild(0)));
	}
	

}