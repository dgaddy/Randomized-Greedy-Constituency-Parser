package constituencyParser.features;

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

/**
 * This class is used for making codes of long type to represent features
 */
public class Features {
	
	enum FeatureType {
		NONE, // placeholder for model backwards compatibility
		SPAN_PROPERY_BY_RULE,
		RULE,
		SPAN_PROPERTY_BY_LABEL,
		SECOND_ORDER_RULE,
		CO_PAR,
		CO_LEN_PAR,
		SECOND_ORDER_PROPERTY_BY_RULE,
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
	public static long getSpanPropertyByRuleFeature(long spanPropertyCode, long ruleCode) {
		return (getCodeBase(FeatureType.SPAN_PROPERY_BY_RULE) | (ruleCode << 32L)) | spanPropertyCode;
	}
	
	/**
	 * A feature for a production rule
	 * @param ruleCode the code for a rule gotten from RuleEnumeration
	 * @return
	 */
	public static long getRuleFeature(long ruleCode) {
		return getCodeBase(FeatureType.RULE) | ruleCode;
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
		if(type == FeatureType.SPAN_PROPERY_BY_RULE) {
			int ruleCode = extractBits(code, 52, 32);
			int spanPropertyCode = extractBits(code, 32, 0);
			return rules.getRuleFromCode(ruleCode).toString(labels) + " " + SpanProperties.getSpanPropertyCodeString(spanPropertyCode, words);
		}
		else if (type == FeatureType.RULE) {
			return "" + rules.getRuleFromCode(extractBits(code, 52, 0)).toString(labels);
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
		if(span.getRule().getType() == Type.UNARY) {
			long ruleCode = RuleEnumeration.getRuleCode(rules.getUnaryId(span.getRule()), Type.UNARY);
			TLongList propertyCodes = SpanProperties.getUnarySpanProperties(words, span.getStart(), span.getEnd());
			for(int i = 0; i < propertyCodes.size(); i++) {
				codes.add(getSpanPropertyByRuleFeature(propertyCodes.get(i), ruleCode));
			}
		}
		else if(span.getRule().getType() == Type.BINARY) {
			long ruleCode = RuleEnumeration.getRuleCode(rules.getBinaryId(span.getRule()), Type.BINARY);
			TLongList propertyCodes = SpanProperties.getBinarySpanProperties(words, span.getStart(), span.getEnd(), span.getSplit());
			for(int i = 0; i < propertyCodes.size(); i++) {
				codes.add(getSpanPropertyByRuleFeature(propertyCodes.get(i), ruleCode));
			}
		}
		else {
			long ruleCode = RuleEnumeration.getRuleCode(span.getRule().getLabel(), Type.TERMINAL);
			TLongList propertyCodes = SpanProperties.getTerminalSpanProperties(words, span.getStart(), wordEnum);
			for(int i = 0; i < propertyCodes.size(); i++) {
				codes.add(getSpanPropertyByRuleFeature(propertyCodes.get(i), ruleCode));
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
		if(span.getRule().getType() == Type.UNARY) {
			int label = span.getRule().getLabel();
			TLongList propertyCodes = SpanProperties.getUnarySpanProperties(words, span.getStart(), span.getEnd());
			for(int i = 0; i < propertyCodes.size(); i++) {
				codes.add(getSpanPropertyByLabelFeature(propertyCodes.get(i), label));
			}
		}
		else if(span.getRule().getType() == Type.BINARY) {
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
		if(span.getRule().getType() == Type.UNARY) {
			long ruleCode = RuleEnumeration.getRuleCode(rules.getUnaryId(span.getRule()), Type.UNARY);
			TLongList propertyCodes = SpanProperties.getUnarySpanProperties(words, span.getStart(), span.getEnd());
			for(int i = 0; i < propertyCodes.size(); i++) {
				codes.add(getSecondOrderSpanPropertyByRuleFeature(propertyCodes.get(i), ruleCode, parentLabel));
			}
		}
		else if(span.getRule().getType() == Type.BINARY) {
			long ruleCode = RuleEnumeration.getRuleCode(rules.getBinaryId(span.getRule()), Type.BINARY);
			TLongList propertyCodes = SpanProperties.getBinarySpanProperties(words, span.getStart(), span.getEnd(), span.getSplit());
			for(int i = 0; i < propertyCodes.size(); i++) {
				codes.add(getSecondOrderSpanPropertyByRuleFeature(propertyCodes.get(i), ruleCode, parentLabel));
			}
		}
		else {
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
	
				features.add(Features.getSecondOrderRuleFeature(rules.getRuleCode(rule), parentRule.getLabel()));
			}
		}
		new GlobalFeatures(labels).getAll(spans, features);
		return features;
	}
	
	public static List<Long> getAllFeatures(List<Span> spans, List<Word> words, boolean doSecondOrder, WordEnumeration wordEnum, LabelEnumeration labels, RuleEnumeration rules) {
		List<Long> features = new ArrayList<>();
		for(int j = 0; j < spans.size(); j++) {
			Span s = spans.get(j);
			features.addAll(Features.getSpanPropertyByRuleFeatures(words, s, rules, wordEnum));
			features.add(Features.getRuleFeature(rules.getRuleCode(s.getRule())));
			features.addAll(Features.getSpanPropertyByLabelFeatures(words, s));
		}
		
		if(doSecondOrder) {
			int[] parents = SpanUtilities.getParents(spans);
			features.addAll(Features.getAllHigherOrderFeatures(words, spans, parents, rules, wordEnum, labels));
		}
		return features;
	}
}
