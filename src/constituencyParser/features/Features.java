package constituencyParser.features;

import java.util.List;

import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import constituencyParser.LabelEnumeration;
import constituencyParser.Rule;
import constituencyParser.Rule.Type;
import constituencyParser.Rules;
import constituencyParser.Span;
import constituencyParser.WordEnumeration;


public class Features {
	public static long getSpanPropertyByRuleFeature(long spanPropertyCode, long ruleCode) {
		return (1L << 52L) + (ruleCode << 32L) + spanPropertyCode;
	}
	
	public static long getRuleFeature(long ruleCode) {
		return (2L << 52L) + ruleCode;
	}
	
	public static long getSpanPropertyByLabelFeature(long spanPropertyCode, int label) {
		return (3L << 52L) + (label << 32L) + spanPropertyCode;
	}
	
	public static String getStringForCode(long code, WordEnumeration words, Rules rules, LabelEnumeration labels) {
		int type = (int) (code >> 52L);
		if(type == 1) {
			int ruleCode = (int) ((code % (1L<<52L)) >> 32L);
			int spanPropertyCode = (int) (code % (1L<<32L));
			return rules.getRuleFromCode(ruleCode) + " " + SpanProperties.getSpanPropertyCodeString(spanPropertyCode, words);
		}
		else if (type == 2) {
			return "" + rules.getRuleFromCode(code % (1L<<52L));
		}
		else if(type == 3) {
			int label = (int) ((code % (1L<<52L)) >> 32L);
			int spanPropertyCode = (int) (code % (1L<<32L));
			return labels.getLabel(label) + " " + SpanProperties.getSpanPropertyCodeString(spanPropertyCode, words);
		}
		else
			return "type " + type +" is invalid";
	}
	
	public static TLongList getSpanPropertyByRuleFeatures(List<Integer> words, Span span, Rules rules, WordEnumeration wordEnum) {
		TLongList codes = new TLongArrayList();
		if(span.getRule().getType() == Type.UNARY) {
			long ruleCode = Rules.getRuleCode(rules.getUnaryId(span.getRule()), Type.UNARY);
			TLongList propertyCodes = SpanProperties.getUnarySpanProperties(words, span.getStart(), span.getEnd());
			for(int i = 0; i < propertyCodes.size(); i++) {
				codes.add(getSpanPropertyByRuleFeature(propertyCodes.get(i), ruleCode));
			}
		}
		else if(span.getRule().getType() == Type.BINARY) {
			long ruleCode = Rules.getRuleCode(rules.getBinaryId(span.getRule()), Type.BINARY);
			TLongList propertyCodes = SpanProperties.getBinarySpanProperties(words, span.getStart(), span.getEnd(), span.getSplit());
			for(int i = 0; i < propertyCodes.size(); i++) {
				codes.add(getSpanPropertyByRuleFeature(propertyCodes.get(i), ruleCode));
			}
		}
		else {
			long ruleCode = Rules.getRuleCode(span.getRule().getParent(), Type.TERMINAL);
			TLongList propertyCodes = SpanProperties.getTerminalSpanProperties(words, span.getStart(), wordEnum);
			for(int i = 0; i < propertyCodes.size(); i++) {
				codes.add(getSpanPropertyByRuleFeature(propertyCodes.get(i), ruleCode));
			}
		}
		return codes;
	}
	
	public static TLongList getSpanPropertyByLabelFeatures(List<Integer> words, Span span) {
		TLongList codes = new TLongArrayList();
		if(span.getRule().getType() == Type.UNARY) {
			int label = span.getRule().getParent();
			TLongList propertyCodes = SpanProperties.getUnarySpanProperties(words, span.getStart(), span.getEnd());
			for(int i = 0; i < propertyCodes.size(); i++) {
				codes.add(getSpanPropertyByLabelFeature(propertyCodes.get(i), label));
			}
		}
		else if(span.getRule().getType() == Type.BINARY) {
			int label = span.getRule().getParent();
			TLongList propertyCodes = SpanProperties.getBinarySpanProperties(words, span.getStart(), span.getEnd(), span.getSplit());
			for(int i = 0; i < propertyCodes.size(); i++) {
				codes.add(getSpanPropertyByLabelFeature(propertyCodes.get(i), label));
			}
		}
		// no need to add to terminals, their rule by property features only have labels
		return codes;
	}
	
	public static long getRuleFeature(Rule rule, Rules rules) {
		if(rule.getType() == Type.UNARY) {
			int id = rules.getUnaryId(rule);
			return getRuleFeature(Rules.getRuleCode(id, Type.UNARY));
		}
		else if(rule.getType() == Type.BINARY) {
			int id = rules.getBinaryId(rule);
			return getRuleFeature(Rules.getRuleCode(id, Type.BINARY));
		}
		else {
			return getRuleFeature(Rules.getTerminalRuleCode(rule.getParent()));
		}
	}
}
