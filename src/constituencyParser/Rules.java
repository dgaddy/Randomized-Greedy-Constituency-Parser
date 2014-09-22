package constituencyParser;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import constituencyParser.Rule.Type;

public class Rules implements Serializable {
	private static final long serialVersionUID = 1L;
	
	private List<Rule> binaryRules = new ArrayList<>();
	HashMap<Rule, Integer> binaryIds = new HashMap<>();
	
	private List<Rule> unaryRules = new ArrayList<>();
	HashMap<Rule, Integer> unaryIds = new HashMap<>();
	
	public Rules() {
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
}
