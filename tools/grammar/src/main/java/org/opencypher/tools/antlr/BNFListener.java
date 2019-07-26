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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.opencypher.tools.antlr.bnf.BNFBaseListener;
import org.opencypher.tools.antlr.bnf.BNFLexer;
import org.opencypher.tools.antlr.bnf.BNFParser.AlternativeContext;
import org.opencypher.tools.antlr.bnf.BNFParser.AlternativesContext;
import org.opencypher.tools.antlr.bnf.BNFParser.BnfsymbolsContext;
import org.opencypher.tools.antlr.bnf.BNFParser.CharactersetContext;
import org.opencypher.tools.antlr.bnf.BNFParser.ElementContext;
import org.opencypher.tools.antlr.bnf.BNFParser.ExclusioncharactersetContext;
import org.opencypher.tools.antlr.bnf.BNFParser.IdContext;
import org.opencypher.tools.antlr.bnf.BNFParser.LhsContext;
import org.opencypher.tools.antlr.bnf.BNFParser.ListcharactersetContext;
import org.opencypher.tools.antlr.bnf.BNFParser.NamedcharactersetContext;
import org.opencypher.tools.antlr.bnf.BNFParser.OptionalitemContext;
import org.opencypher.tools.antlr.bnf.BNFParser.RequireditemContext;
import org.opencypher.tools.antlr.bnf.BNFParser.RhsContext;
import org.opencypher.tools.antlr.bnf.BNFParser.Rule_Context;
import org.opencypher.tools.antlr.bnf.BNFParser.RuleidContext;
import org.opencypher.tools.antlr.bnf.BNFParser.RulelistContext;
import org.opencypher.tools.antlr.bnf.BNFParser.RulerefContext;
import org.opencypher.tools.antlr.bnf.BNFParser.TextContext;
import org.opencypher.tools.antlr.tree.BnfSymbolLiteral;
import org.opencypher.tools.antlr.tree.BnfSymbols;
import org.opencypher.tools.antlr.tree.CharacterLiteral;
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
import org.opencypher.tools.antlr.tree.NamedCharacterSet;
import org.opencypher.tools.antlr.tree.NormalText;
import org.opencypher.tools.antlr.tree.OneOrMore;
import org.opencypher.tools.antlr.tree.Rule;
import org.opencypher.tools.antlr.tree.RuleId;
import org.opencypher.tools.antlr.tree.RuleList;
import org.opencypher.tools.antlr.tree.ZeroOrMore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * builds a GrammarItem tree from a SQL-BNF specification 
 */
public class BNFListener extends BNFBaseListener
{
	// build
	private final ParseTreeProperty<GrammarItem> items = new ParseTreeProperty<>();
	private final CommonTokenStream tokens; 
	private GrammarTop treeTop = null;
	
	private static final Logger LOGGER = LoggerFactory.getLogger(BNFListener.class.getName());
	
	public BNFListener(CommonTokenStream tokens)
	{
		super();
		this.tokens = tokens;
	}

	public GrammarTop getTreeTop()
	{
		return treeTop;
	}

	private GrammarItem getItem(ParseTree ctx) {
		return items.get(ctx);
	}
	
	private void setItem(ParseTree ctx, GrammarItem item) {
		LOGGER.debug("setting a {} for {}", item.getClass().getSimpleName(), ctx.getText());
		items.put(ctx, item);
	}

	private void pullUpItem(ParseTree ctx) {

		if (ctx.getChildCount() != 1) {
			throw new IllegalStateException("There should be only one child at " + ctx.getText() + " but there are " +
						ctx.getChildCount());
		}
		GrammarItem movedItem = getItem(ctx.getChild(0));
		if (movedItem != null) {
//			LOGGER.debug("moving a {} upto to ctx {}", 
//				  movedItem.getClass().getSimpleName(), ctx.getClass().getSimpleName());
			items.put(ctx,  movedItem);
		} else {
			throw new IllegalStateException("No item to be moved from " + ctx.getChild(0).getClass().getSimpleName()
					+ " up to " + ctx.getClass().getSimpleName());
		}
	}
	
//	@Override
//	public void exitEveryRule(ParserRuleContext ctx)
//	{
//		LOGGER.debug("exiting a {}", ctx.getClass().getSimpleName().replaceFirst("Context",""));
//		super.exitEveryRule(ctx);
//	}

	@Override
	public void exitRulelist(RulelistContext ctx)
	{
		// this is the top level for bnf, since that doesn't have a global declaration
		RuleList rules = new RuleList();
		GrammarName name = null;
		for (Rule_Context ruleCtx : ctx.rule_())
		{
			GrammarItem grammarItem = getItem(ruleCtx);
			if (name == null) {
				// first rule is also the grammar name
				name = new GrammarName(((Rule) grammarItem).getRuleName());
			}
			rules.addItem(grammarItem);
		}
//		HeaderContext headerCtx = ctx.header();
		final String headerText;
//		if (headerCtx != null) {
//			InHeader header = (InHeader) getItem(headerCtx);
//			headerText = header.getContent();
//		} else {
			headerText = null;
//		}
		treeTop = new GrammarTop(name, rules, headerText);
	}

	
//	@Override
//	public void exitHeader(HeaderContext ctx) {
//		
//		StringBuilder b = new StringBuilder();
//		ctx.headerline().stream().map(l -> b.append(l).append("\n")).count();
//		
//		setItem(ctx, new InHeader(b.toString()));
//	}
//
	@Override
	public void exitRule_(Rule_Context ctx)
	{
		Rule rule = new Rule(getItem(ctx.lhs()), getItem(ctx.rhs()));
		setItem(ctx, rule);
	}

	@Override
	public void exitLhs(LhsContext ctx)
	{
		// lhs is now < ruleid >, so it's the middle bit we want
		setItem(ctx, getItem(ctx.getChild(1)));
	}

	@Override
	public void exitBnfsymbols(BnfsymbolsContext ctx)
	{
		setItem(ctx, new InLiteral(ctx.getText()));
	}

	@Override
	public void exitRhs(RhsContext ctx)
	{
		pullUpItem(ctx);
//		// is rhs a bunch of bnf symbols
//		// which will currently be normal literals
//		// if one is, they all are
//		// only important if there is more than one
//		if (ctx.getChildCount() > 1) {
//			// we need to combine them, i think
//			SpecialSeqLiteral item = new SpecialSeqLiteral(ctx.children.stream()
//					.map(c -> ((SpecialLiteral) getItem(c)).getCharLit()).collect(Collectors.toList()));
//			setItem(ctx, item);
//		} else {
////			LOGGER.warn("promoting a {} from {}", ctx.getClass().getSimpleName(),  ctx.getChild(0).getClass().getSimpleName());
//			GrammarItem item = getItem(ctx.getChild(0));
//			// is this a punctuation definition
//			if (item.getType() == ItemType.ALTERNATIVES) {
//				List<GrammarItem> children = item.getChildren();
//				if (children.size() == 1) {
//					if (children.get(0).getType() == ItemType.ALTERNATIVE) {
//						List<GrammarItem> grandChildren = children.get(0).getChildren();
//						if (grandChildren.stream().allMatch(gc -> gc.getType() == ItemType.SPECIAL)) {
////							LOGGER.warn("Consider {}", grandChildren.stream()
////									.map(gc -> gc.getClass().getSimpleName()).collect(Collectors.joining(" ")));
//							if (grandChildren.size() == 1) {
//								setItem(ctx, grandChildren.get(0));
//							} else {
//								setItem(ctx, new SpecialSeqLiteral(grandChildren.stream()
//										.map(gc -> (((SpecialLiteral) gc).getCharLit())).collect(Collectors.toList())));
//							}
//							return;
//						}
//					}
//				}
//			}
//				
//			setItem(ctx,  item);
//		}
	}

	@Override
	public void exitAlternatives(AlternativesContext ctx)
	{
		InAlternatives alts = new InAlternatives();
		for (AlternativeContext element : ctx.alternative())
		{
			alts.addItem(getItem(element));
		}
		setItem(ctx, alts);
	}

	@Override
	public void exitAlternative(AlternativeContext ctx)
	{
		InAlternative alt = new InAlternative();
		for (ElementContext element : ctx.element())
		{
			alt.addItem(getItem(element));
		}
		GrammarItem normalText = findNormalText(ctx);
		if (normalText != null) {
			alt.addItem(normalText);
		}
		setItem(ctx, alt);
	}

	private NormalText findNormalText(ParserRuleContext ctx)
	{
		Token endAlt = ctx.getStop();
		int i = endAlt.getTokenIndex();
		List<Token> normalTextChannel =
					tokens.getHiddenTokensToRight(i, BNFLexer.HIDDEN);
		if (normalTextChannel != null) {
			Token normalToken = normalTextChannel.get(0);
			if (normalToken != null) {
				String txt = normalToken.getText().replaceFirst("!!\\s*", "");
				return new NormalText(txt);
			}
		}
		return null;
	}

	@Override
	public void exitElement(ElementContext ctx)
	{
		pullUpItem(ctx);
	}

	@Override
	public void exitOptionalitem(OptionalitemContext ctx)
	{
		// if there are a lot of these, it's actually a zero or more
		// required item is known to be plural, as it has braces round something
		final GrammarItem contentItem = getItem(ctx.alternatives());
		final GrammarItem ourItem ;
		if (ctx.ELLIPSIS() == null) {
			
			// convert [ { a b } ... ] from optional(oneOrMore) to zeroOrMore
			GrammarItem simplifiedContent = contentItem.reachThrough();
			if (simplifiedContent instanceof OneOrMore) {
				ourItem = new ZeroOrMore(((OneOrMore) simplifiedContent).getContent());
			} else {
				ourItem = new InOptional(contentItem);
			}
		} else {
			ourItem = new ZeroOrMore(contentItem);
		}
		setItem(ctx, ourItem);
	}

	@Override
	public void exitRequireditem(RequireditemContext ctx)
	{
		// required item is known to be plural, as it has braces round something
		final GrammarItem contentItem = getItem(ctx.alternatives());
		final GrammarItem ourItem ;
		if (ctx.ELLIPSIS() == null) {
			// this is just a group
			if (contentItem.isPlural()) {
				ourItem = new Group(contentItem);
			} else {
				// unnecessary braces, i think
				ourItem = contentItem;
			}
			
		} else {
			ourItem = new OneOrMore(contentItem);
		}
		setItem(ctx, ourItem);
	}

	private static final Pattern UCODE_PATTERN = Pattern.compile("\\\\u([0-9a-fA-F]+)");
	
	@Override
	public void exitText(TextContext ctx)
	{
		String text = ctx.getText().trim();
		// text is UNICODE_LITERAL | WORD | CHARACTER_LITERAL | INTEGER_LITERAL
		if (ctx.UNICODE_LITERAL() != null) {
			 // translate it
			setItem(ctx, new InLiteral(((Character) ((char) Integer.parseInt(text.substring(2), 16))).toString() ));
		} else if (ctx.CHARACTER_LITERAL() != null) {
			setItem(ctx, new CharacterLiteral(text));
		} else {
			// i think everything else is just a regular literal
			setItem(ctx, new InLiteral(text));
		}
//		if (CharLit.allPunctuation(text)) {
//			// is it a known construct
//			CharLit lit = CharLit.getByValue(text);
//			if (lit != null) {
//				setItem(ctx, new SpecialLiteral(text));
//			} else {
//				// split into individual characters
//				List<CharLit> charLits = Arrays.asList(text.split("")).stream().map(ch -> CharLit.getByValue(ch)).collect(Collectors.toList());
//				setItem(ctx, new SpecialSeqLiteral(charLits));
//			}
//		} else {
//			Matcher um = UCODE_PATTERN.matcher(text);
//			if (um.matches()) {
//				// special case space
//				 if (text.equals("\\u0020")) {
////					 setItem(ctx, new ListedCharacterSet(" "));
////				 } else {
//					 setItem(ctx, new InLiteral(((Character) ((char) Integer.parseInt(text.substring(2), 16))).toString() ));
//				 }
//			} else {
//    			// does this cope with mixtures ?
//    			setItem(ctx, new InLiteral(text));
//			}
//		}
	}

	@Override
	public void exitCharacterset(CharactersetContext ctx) {
		if (ctx.getChildCount() == 3) {
    		GrammarItem movedItem = getItem(ctx.getChild(1));
    		if (movedItem != null) {
//    			LOGGER.warn("moving a {} upto to ctx {}", 
//    				  movedItem.getClass().getSimpleName(), ctx.getClass().getSimpleName());
    			items.put(ctx,  movedItem);
    		} else {
    			throw new IllegalStateException("No item to be moved from " + ctx.getChild(1).getClass().getSimpleName()
    					+ " up to " + ctx.getClass().getSimpleName());
    		}
		} else {
			throw new IllegalStateException("child count for character set is " + ctx.getChildCount() + ". expected 3");
		}
	}
	
	
	@Override
	public void exitNamedcharacterset(NamedcharactersetContext ctx) {
		setItem(ctx, new NamedCharacterSet(ctx.ID().getText()));
	}

	@Override
	public void exitExclusioncharacterset(ExclusioncharactersetContext ctx) {
		ListcharactersetContext listCtx = ctx.listcharacterset();
		setItem(ctx, new ExclusionCharacterSet(interpret(listCtx)));
	}

	@Override
	public void exitListcharacterset(ListcharactersetContext ctx) {
		setItem(ctx, new ListedCharacterSet(interpret(ctx)));
	}
	
	private String interpret(ListcharactersetContext listCtx) {
		// to cope with punctuation, especially backslash, the syntax has text+,
		// but we want them together again
		
		List<String> bnfString = listCtx.text().stream().map(TextContext::getText).collect(Collectors.toList());
		StringBuilder b = new StringBuilder();
		while (! bnfString.isEmpty()) {
			String piece = bnfString.remove(0);
			LOGGER.debug("piece is [{}]", piece);
			if (! piece.equals("\\")) {
				b.append(piece);
			} else {
				String next = bnfString.remove(0);
				char first = next.charAt(0);
				String rest = next.substring(1);
				switch (first) {
                    case 'r': b.append('\r');  break;
                    case 'n': b.append("\n");  break;
                    case 't': b.append("\t");  break;
                    case 'b': b.append("\b");  break;
                    case 'f': b.append("\f");  break;
                    case '\\': b.append("\\"); break;
                    case '-':  b.append("-");  break;
                    case ']':  b.append("]");  break;
                    case '$':  b.append("$");  break;
                    // have to escape bnf comment characters !
                    case '/':  b.append("/");  break;
                    case 'u':  
                    	if (rest.length() < 4) {
                    		throw new IllegalArgumentException("unicode escape requires 4 hex digits");
                    	}
                    	int cp = Integer.parseInt(rest.substring(1,4), 16);
                    	b.append((char) cp);
                    	rest = rest.substring(4);
                    	break;
				default:
					throw new IllegalArgumentException("Don't know how to interpret escaped character " + first);
				}
				if (rest.length() > 0) {
					b.append(rest);
				}
			}
			
		}
		String answer = b.toString();
//		LOGGER.warn("became {}, len {}", answer, answer.length());
		
		return answer;
	}


	@Override
	public void exitId(IdContext ctx)
	{
//		GrammarItem item = MappedLiterals.getFromBNFid(ctx.ruleref().getText());
		String ruleName = ctx.ruleref().getText();
		if ( ruleName.equals(EOFreference.BNF_NAME)) {
			setItem(ctx, new EOFreference());
		} else {
			GrammarItem item = new RuleId(ruleName);

    		if (ctx.ELLIPSIS() != null) {
    			setItem(ctx, new OneOrMore(item));
    		} else {
    			setItem(ctx, item);
    		}
		}
	}

	@Override
	public void exitRuleref(RulerefContext ctx) {
		String referencedName = ctx.getText();
		BnfSymbols bnfSymbol = BnfSymbols.getByName(referencedName);
		if (bnfSymbol != null) {
			// if this is only here because bnf can't put bnf symbols in normal rules, 
			// reverse it
			setItem(ctx, new BnfSymbolLiteral(bnfSymbol));
		} else {
			setItem(ctx, new RuleId(referencedName));
		}
	}

	@Override
	public void exitRuleid(RuleidContext ctx)
	{
		setItem(ctx, new RuleId(ctx.getText()));

	}

	
}