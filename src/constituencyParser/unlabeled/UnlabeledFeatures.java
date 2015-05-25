package constituencyParser.unlabeled;

import java.util.ArrayList;
import java.util.List;

import gnu.trove.list.TLongList;
import constituencyParser.LabelEnumeration;
import constituencyParser.Rule;
import constituencyParser.Rule.Type;
import constituencyParser.RuleEnumeration;
import constituencyParser.Span;
import constituencyParser.SpanUtilities;
import constituencyParser.Word;
import constituencyParser.WordEnumeration;
import constituencyParser.features.GlobalFeatures;
import constituencyParser.features.SpanProperties;

/**
 * This class is used for making codes of long type to represent features
 */
public class UnlabeledFeatures {
	
	enum FeatureType {
		NONE, // placeholder for model backwards compatibility
		SPAN_PROPERY_BY_BINARY_RULE,
		SPAN_PROPERTY_BY_TERMINAL_RULE,
		BINARY_RULE,
		TERMINAL_RULE,
		SPAN_PROPERTY_BY_LABEL,
		SECOND_ORDER_RULE,
		CO_PAR,
		CO_LEN_PAR,
		SECOND_ORDER_PROPERTY_BY_RULE,
		LEXICAL_DEPENDENCY
	}
	
	public static long getCodeBase(FeatureType type) {
		return ((long) type.ordinal()) << 52L;
	}
	
	/**
	 * Combines a span property (such as words at certain positions, length, prefixes and suffixes etc.) with a rule
	 * @param spanPropertyCode
	 * @param ruleCode
	 * @return
	 */
	public static long getSpanPropertyByBinaryRuleFeature(long spanPropertyCode, long leftLabel, long rightLabel, boolean leftHead) {
		return (getCodeBase(FeatureType.SPAN_PROPERY_BY_BINARY_RULE) | ((leftHead ? 1L : 0L) << 50L) | (rightLabel << 41L) | (leftLabel << 32L)) | spanPropertyCode;
	}
	
	public static long getSpanPropertyByTerminalRuleFeature(long spanPropertyCode, long label) {
		return getCodeBase(FeatureType.SPAN_PROPERTY_BY_TERMINAL_RULE) | (label << 32L) | spanPropertyCode;
	}
	
	/**
	 * A feature for a production rule
	 * @param ruleCode the code for a rule gotten from RuleEnumeration
	 * @return
	 */
	public static long getBinaryRuleFeature(long leftLabel, long rightLabel, boolean leftHead) {
		return getCodeBase(FeatureType.BINARY_RULE) | ((leftHead ? 1L : 0L) << 50L) | (rightLabel << 41L) | (leftLabel << 32L);
	}
	
	public static long getTerminalRuleFeature(long label) {
		return getCodeBase(FeatureType.TERMINAL_RULE) | label;
	}
	
	public static long getRuleFeature(Rule r) {
		if(r.getType() == Type.BINARY)
			return getBinaryRuleFeature(r.getLeft(), r.getRight(), r.getLeftPropagateHead());
		else if(r.getType() == Type.TERMINAL)
			return getTerminalRuleFeature(r.getLabel());
		else throw new RuntimeException("Rule not valid here " + r);
	}
	
	/**
	 * Like spanPropertyByRule but only with the label
	 * @param spanPropertyCode
	 * @param label
	 * @return
	 */
	public static long getSpanPropertyByLabelFeature(long spanPropertyCode, long label) {
		return (getCodeBase(FeatureType.SPAN_PROPERTY_BY_LABEL) | (label << 32L)) | spanPropertyCode;
	}
	
	/**
	 * A feature for a label, it's parent, and one of it's children
	 * @param childLabel
	 * @param label
	 * @param parentLabel
	 * @return
	 */
	public static long getSecondOrderRuleFeature(long ruleCode, long parentLabel) {
		return (getCodeBase(FeatureType.SECOND_ORDER_RULE) | (parentLabel << 20L)) | ruleCode;
	}
	
	public static long getSecondOrderSpanPropertyByRuleFeature(long spanPropertyCode, long ruleCode, long parentLabel) {
		return ((getCodeBase(FeatureType.SECOND_ORDER_PROPERTY_BY_RULE) | (ruleCode << 41L)) | (parentLabel << 32)) | spanPropertyCode;
	}
	
	public static boolean isSecondOrderFeature(long code) {
		return (code >> 52L) == FeatureType.SECOND_ORDER_RULE.ordinal();
	}
	
	public static long getLexicalDependencyFeature(long parentWord, long childWord) {
		return getCodeBase(FeatureType.LEXICAL_DEPENDENCY) | (parentWord << 25L) | childWord;
	}
	
	/**
	 * Gets a string that represents a feature code.  Mostly for debugging.
	 * @param code
	 * @param words
	 * @param rules
	 * @param labels
	 * @return
	 */
	public static String getStringForCode(long code, WordEnumeration words, RuleEnumeration rules, LabelEnumeration labels) {
		FeatureType type = FeatureType.values()[(int)(code >> 52L)];
		if(type == FeatureType.SPAN_PROPERY_BY_BINARY_RULE) {
			//int ruleCode = extractBits(code, 52, 32);
			int spanPropertyCode = extractBits(code, 32, 0);
			return "Span_property_by_binary_rule " + SpanProperties.getSpanPropertyCodeString(spanPropertyCode, words);
		}
		else if(type == FeatureType.SPAN_PROPERTY_BY_TERMINAL_RULE) {
			return "Span property by terminal rule";
		}
		else if (type == FeatureType.BINARY_RULE) {
			return "Binary_rule";// + rules.getRuleFromCode(extractBits(code, 52, 0)).toString(labels);
		}
		else if (type == FeatureType.TERMINAL_RULE) {
			return "Terminal_rule";
		}
		else if(type == FeatureType.SPAN_PROPERTY_BY_LABEL) {
			int label = extractBits(code, 52,32);
			int spanPropertyCode = extractBits(code, 32, 0);
			return labels.getLabel(label) + " " + SpanProperties.getSpanPropertyCodeString(spanPropertyCode, words);
		}
		else if(type == FeatureType.SECOND_ORDER_RULE) {
			int ruleCode = extractBits(code, 20, 0);
			int parentLabel = extractBits(code, 52, 20);
			return labels.getLabel(parentLabel) + " " + rules.getRuleFromCode(ruleCode);
		}
		else
			return "type " + type +" is invalid";
	}
	
	/**
	 * Extract the number represented by bits high (exclusive) to low (inclusive)
	 * @param code to extract from
	 * @param high
	 * @param low
	 * @return
	 */
	private static int extractBits(long code, long high, long low) {
		return (int)((code % (1L << high)) >> low);
	}
	
	/**
	 * Get all spanPropertyByRule features for a span
	 * @param words the full list of words in a sentence
	 * @param span
	 * @param rules
	 * @param wordEnum
	 * @return
	 */
	public static List<Long> getSpanPropertyByRuleFeatures(List<Word> words, Span span, RuleEnumeration rules, WordEnumeration wordEnum) {
		List<Long> codes = new ArrayList<>();
		Rule rule = span.getRule();
		if(rule.getType() == Type.BINARY) {
			TLongList propertyCodes = SpanProperties.getBinarySpanProperties(words, span.getStart(), span.getEnd(), span.getSplit());
			for(int i = 0; i < propertyCodes.size(); i++) {
				codes.add(getSpanPropertyByBinaryRuleFeature(propertyCodes.get(i), rule.getLeft(), rule.getRight(), rule.getLeftPropagateHead()));
			}
		}
		else if(rule.getType() == Type.TERMINAL){
			TLongList propertyCodes = SpanProperties.getTerminalSpanProperties(words, span.getStart(), wordEnum);
			for(int i = 0; i < propertyCodes.size(); i++) {
				long code = getSpanPropertyByTerminalRuleFeature(propertyCodes.get(i), rule.getLabel());
				codes.add(code);
			}
		}
		return codes;
	}
	
	/**
	 * Get all spanPropertyByLabel features for a span
	 * @param words
	 * @param span
	 * @return
	 */
	public static List<Long> getSpanPropertyByLabelFeatures(List<Word> words, Span span) {
		List<Long> codes = new ArrayList<>();
		if(span.getRule().getType() == Type.BINARY) {
			int label = span.getRule().getLabel();
			TLongList propertyCodes = SpanProperties.getBinarySpanProperties(words, span.getStart(), span.getEnd(), span.getSplit());
			for(int i = 0; i < propertyCodes.size(); i++) {
				codes.add(getSpanPropertyByLabelFeature(propertyCodes.get(i), label));
			}
		}
		// no need to add to terminals, their rule by property features only have labels
		return codes;
	}
	
	public static List<Long> getSecondOrderSpanPropertyByRuleFeatures(List<Word> words, Span span, long parentLabel, RuleEnumeration rules, WordEnumeration wordEnum) {
		List<Long> codes = new ArrayList<>();
		if(span.getRule().getType() == Type.BINARY) {
			long ruleCode = RuleEnumeration.getRuleCode(rules.getBinaryId(span.getRule()), Type.BINARY);
			TLongList propertyCodes = SpanProperties.getBinarySpanProperties(words, span.getStart(), span.getEnd(), span.getSplit());
			for(int i = 0; i < propertyCodes.size(); i++) {
				codes.add(getSecondOrderSpanPropertyByRuleFeature(propertyCodes.get(i), ruleCode, parentLabel));
			}
		}
		else if(span.getRule().getType() == Type.TERMINAL){
			long ruleCode = RuleEnumeration.getRuleCode(span.getRule().getLabel(), Type.TERMINAL);
			TLongList propertyCodes = SpanProperties.getTerminalSpanProperties(words, span.getStart(), wordEnum);
			for(int i = 0; i < propertyCodes.size(); i++) {
				codes.add(getSecondOrderSpanPropertyByRuleFeature(propertyCodes.get(i), ruleCode, parentLabel));
			}
		}
		return codes;
	}
	
	public static List<Long> getAllHigherOrderFeatures(List<Word> words, List<Span> spans, int[] parents, RuleEnumeration rules, WordEnumeration wordEnum, LabelEnumeration labels) {
		List<Long> features = new ArrayList<>();
		for(int j = 0; j < spans.size(); j++) {
			Span s = spans.get(j);
			if(parents[j] != -1) {
				Rule rule = s.getRule();
				Rule parentRule = spans.get(parents[j]).getRule();
	
				features.add(UnlabeledFeatures.getSecondOrderRuleFeature(rules.getRuleCode(rule), parentRule.getLabel()));
			}
		}
		new GlobalFeatures(labels).getAll(spans, features);
		return features;
	}
	
	public static List<Long> getAllFeatures(List<Span> spans, List<Word> words, boolean doSecondOrder, WordEnumeration wordEnum, LabelEnumeration labels, RuleEnumeration rules) {
		return getAllFeatures(spans, words, doSecondOrder, wordEnum, labels, rules, false);
	}
	
	public static List<Long> getAllFeatures(List<Span> spans, List<Word> words, boolean doSecondOrder, WordEnumeration wordEnum, LabelEnumeration labels, RuleEnumeration rules, boolean doLexicalFeatures) {
		List<Long> features = new ArrayList<>();
		for(int j = 0; j < spans.size(); j++) {
			Span s = spans.get(j);
			//if(s.getRule().getType() != Type.TERMINAL)
				features.addAll(UnlabeledFeatures.getSpanPropertyByRuleFeatures(words, s, rules, wordEnum));
			features.add(UnlabeledFeatures.getRuleFeature(s.getRule()));
			features.addAll(UnlabeledFeatures.getSpanPropertyByLabelFeatures(words, s));
		}
		
		if(doLexicalFeatures) {
			int[] parents = SpanUtilities.getParents(spans);
			features.addAll(getLexicalDependencyFeatures(spans, parents));
		}
		
		if(doSecondOrder) {
			int[] parents = SpanUtilities.getParents(spans);
			features.addAll(UnlabeledFeatures.getAllHigherOrderFeatures(words, spans, parents, rules, wordEnum, labels));
		}
		return features;
	}
	
	public static List<Long> getLexicalDependencyFeatures(List<Span> spans, int[] parents) {
		List<Long> features = new ArrayList<>();
		for(int s = 0; s < spans.size(); s++) {
			Span span = spans.get(s);
			int parentIndex = parents[s];
			if(parentIndex != -1) {
				int parentWord = spans.get(parentIndex).getHeadWord();
				int childWord = span.getHeadWord();
				if(parentWord != childWord)
					features.add(getLexicalDependencyFeature(parentWord, childWord));
			}
		}
		return features;
	}
}
