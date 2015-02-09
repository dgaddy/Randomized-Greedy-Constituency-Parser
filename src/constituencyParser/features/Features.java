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
import constituencyParser.WordEnumeration;

/**
 * This class is used for making codes of long type to represent features
 */
public class Features {
	/**
	 * Combines a span property (such as words at certain positions, length, prefixes and suffixes etc.) with a rule
	 * @param spanPropertyCode
	 * @param ruleCode
	 * @return
	 */
	public static long getSpanPropertyByRuleFeature(long spanPropertyCode, long ruleCode) {
		return (1L << 52L) + (ruleCode << 32L) + spanPropertyCode;
	}
	
	/**
	 * A feature for a production rule
	 * @param ruleCode the code for a rule gotten from RuleEnumeration
	 * @return
	 */
	public static long getRuleFeature(long ruleCode) {
		return (2L << 52L) + ruleCode;
	}
	
	/**
	 * Like spanPropertyByRule but only with the label
	 * @param spanPropertyCode
	 * @param label
	 * @return
	 */
	public static long getSpanPropertyByLabelFeature(long spanPropertyCode, long label) {
		return (3L << 52L) + (label << 32L) + spanPropertyCode;
	}
	
	/**
	 * A feature for a label, it's parent, and one of it's children
	 * @param childLabel
	 * @param label
	 * @param parentLabel
	 * @return
	 */
	public static long getSecondOrderRuleFeature(long childLabel, long label, long parentLabel) {
		return (4L << 52L) + (childLabel << 32L) + (label << 16L) + parentLabel;
	}
	
	public static boolean isSecondOrderFeature(long code) {
		return (code >> 52L) == 4;
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
		int type = (int) (code >> 52L);
		if(type == 1) {
			int ruleCode = extractBits(code, 52, 32);
			int spanPropertyCode = extractBits(code, 32, 0);
			return rules.getRuleFromCode(ruleCode) + " " + SpanProperties.getSpanPropertyCodeString(spanPropertyCode, words);
		}
		else if (type == 2) {
			return "" + rules.getRuleFromCode(extractBits(code, 52, 0));
		}
		else if(type == 3) {
			int label = extractBits(code, 52,32);
			int spanPropertyCode = extractBits(code, 32, 0);
			return label + " " + SpanProperties.getSpanPropertyCodeString(spanPropertyCode, words);
		}
		else if(type == 4) {
			int childLabel = extractBits(code, 52, 32);
			int label = extractBits(code, 32, 16);
			int parentLabel = extractBits(code, 16, 0);
			return labels.getLabel(parentLabel) + " " + labels.getLabel(label) + " " + labels.getLabel(childLabel);
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
	public static List<Long> getSpanPropertyByRuleFeatures(List<Integer> words, Span span, RuleEnumeration rules, WordEnumeration wordEnum) {
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
	public static List<Long> getSpanPropertyByLabelFeatures(List<Integer> words, Span span) {
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
	
	/**
	 * A helper method for getRuleFeature above that handles getting the ruleCode, so you can just pass a Rule
	 * @param rule
	 * @param rules
	 * @return
	 */
	public static long getRuleFeature(Rule rule, RuleEnumeration rules) {
		if(rule.getType() == Type.UNARY) {
			int id = rules.getUnaryId(rule);
			return getRuleFeature(RuleEnumeration.getRuleCode(id, Type.UNARY));
		}
		else if(rule.getType() == Type.BINARY) {
			int id = rules.getBinaryId(rule);
			return getRuleFeature(RuleEnumeration.getRuleCode(id, Type.BINARY));
		}
		else {
			return getRuleFeature(RuleEnumeration.getTerminalRuleCode(rule.getLabel()));
		}
	}
	
	public static List<Long> getAllFeatures(List<Span> spans, List<Integer> words, boolean doSecondOrder, WordEnumeration wordEnum, LabelEnumeration labels, RuleEnumeration rules) {
		List<Long> features = new ArrayList<>();
		int[] parents = SpanUtilities.getParents(spans);
		for(int j = 0; j < spans.size(); j++) {
			Span s = spans.get(j);
			features.addAll(Features.getSpanPropertyByRuleFeatures(words, s, rules, wordEnum));
			features.add(Features.getRuleFeature(s.getRule(), rules));
			features.addAll(Features.getSpanPropertyByLabelFeatures(words, s));
			
			if(doSecondOrder) {
				if(parents[j] != -1) {
					Rule rule = s.getRule();
					Rule parentRule = spans.get(parents[j]).getRule();

					if(rule.getType() == Rule.Type.UNARY) {
						long code = Features.getSecondOrderRuleFeature(rule.getLeft(), rule.getLabel(), parentRule.getLabel());
						features.add(code);
					}
					else if(rule.getType() == Rule.Type.BINARY) {
						long code = Features.getSecondOrderRuleFeature(rule.getLeft(), rule.getLabel(), parentRule.getLabel());
						features.add(code);
						code = Features.getSecondOrderRuleFeature(rule.getRight(), rule.getLabel(), parentRule.getLabel());
						features.add(code);
					}
				}
			}
		}
		return features;
	}
}
