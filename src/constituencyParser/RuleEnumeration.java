package constituencyParser;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import constituencyParser.Rule.Type;

/**
 * Used to turn rules into a unique id and vice versa
 */
public class RuleEnumeration implements Serializable {
	private static final long serialVersionUID = 1L;
	
	private List<Rule> binaryRules = new ArrayList<>();
	HashMap<Rule, Integer> binaryIds = new HashMap<>();
	
	private List<Rule> unaryRules = new ArrayList<>();
	HashMap<Rule, Integer> unaryIds = new HashMap<>();
	
	public RuleEnumeration() {
	}
	
	public RuleEnumeration(RuleEnumeration rules) {
		this.binaryRules = new ArrayList<>(rules.binaryRules);
		this.unaryRules = new ArrayList<>(rules.unaryRules);
		this.binaryIds = new HashMap<>(rules.binaryIds);
		this.unaryIds = new HashMap<>(rules.unaryIds);
	}
	
	private void addBinaryRule(Rule rule) {
		if(binaryIds.containsKey(rule))
			return;
		else {
			int id = binaryRules.size();
			binaryRules.add(rule);
			binaryIds.put(rule, id);
		}
	}
	
	private void addUnaryRule(Rule rule) {
		if(unaryIds.containsKey(rule))
			return;
		else {
			int id = unaryRules.size();
			unaryRules.add(rule);
			unaryIds.put(rule, id);
		}
	}
	
	public void addAllRules(List<SpannedWords> spannedWords) {
		for(SpannedWords sw : spannedWords) {
			for(Span s : sw.getSpans()) {
				if(s.getRule().getType() == Type.BINARY)
					addBinaryRule(s.getRule());
				else if(s.getRule().getType() == Type.UNARY)
					addUnaryRule(s.getRule());
			}
		}
	}
	
	public Rule getBinaryRule(int index) {
		return binaryRules.get(index);
	}
	
	public int getBinaryId(Rule rule) {
		return binaryIds.get(rule);
	}
	
	public int getNumberOfBinaryRules() {
		return binaryRules.size();
	}
	
	public Rule getUnaryRule(int index) {
		return unaryRules.get(index);
	}
	
	public int getUnaryId(Rule rule) {
		return unaryIds.get(rule);
	}
	
	public int getNumberOfUnaryRules() {
		return unaryRules.size();
	}
	
	public Rule getRule(int id, Type type) {
		if(type == Type.BINARY)
			return getBinaryRule(id);
		else if(type == Type.UNARY)
			return getUnaryRule(id);
		else if(type == Type.TERMINAL)
			return new Rule(id);
		else return null;
	}
	
	public boolean isExistingRule(Rule rule) {
		if(rule.getType() == Type.BINARY)
			return binaryIds.containsKey(rule);
		else if(rule.getType() == Type.UNARY)
			return unaryIds.containsKey(rule);
		else // all terminals are existing
			return true;
	}
	
	/**
	 * The rule code is a 20 bit code that encodes the rule type and the rule id
	 * @param ruleId
	 * @param type
	 * @return
	 */
	public static long getRuleCode(int ruleId, Rule.Type type) {
		return (((long)type.ordinal()) << 16L) + ruleId;
	}
	
	public static long getTerminalRuleCode(int labelId) {
		return getRuleCode(labelId, Type.TERMINAL);
	}
	
	/**
	 * The inverse of getRuleCode
	 * @param code
	 * @return
	 */
	public Rule getRuleFromCode(long code) {
		int ruleId = (int) (code % (1L << 16L));
		int typeOrdinal = (int) (code >> 16L);
		Type type = Type.values()[typeOrdinal];
		return getRule(ruleId, type);
	}
}