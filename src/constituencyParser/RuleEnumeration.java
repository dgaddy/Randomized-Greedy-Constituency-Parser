package constituencyParser;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import constituencyParser.Rule.Type;

/**
 * Used to turn rules into a unique id and vice versa
 */
public class RuleEnumeration implements Serializable {
	private static final long serialVersionUID = 1L;
	
	private List<Rule> binaryRules = new ArrayList<>();
	int[][][] binaryIds;
	int[][][] binaryCnt;
	
	private List<Rule> unaryRules = new ArrayList<>();
	int[][] unaryIds;
	int[][] unaryCnt;
	
	static final int NUMBER_LABELS = 100;
	
	public RuleEnumeration() {
		 binaryIds = new int[NUMBER_LABELS][NUMBER_LABELS][NUMBER_LABELS];
		 unaryIds = new int[NUMBER_LABELS][NUMBER_LABELS];
		 
		 binaryCnt = new int[NUMBER_LABELS][NUMBER_LABELS][NUMBER_LABELS];
		 unaryCnt = new int[NUMBER_LABELS][NUMBER_LABELS];
		 
		 for(int i = 0; i < NUMBER_LABELS; i++)
			 for(int j = 0; j < NUMBER_LABELS; j++)
				 for(int k = 0; k < NUMBER_LABELS; k++)
					 binaryIds[i][j][k] = -1;
		 for(int i = 0; i < NUMBER_LABELS; i++)
			 for(int j = 0; j < NUMBER_LABELS; j++)
				 unaryIds[i][j] = -1;
	}
	
	public RuleEnumeration(RuleEnumeration other) {
		this.binaryRules = new ArrayList<>(other.binaryRules);
		this.unaryRules = new ArrayList<>(other.unaryRules);
		
		binaryIds = new int[NUMBER_LABELS][NUMBER_LABELS][NUMBER_LABELS];
		unaryIds = new int[NUMBER_LABELS][NUMBER_LABELS];
		for(int i = 0; i < NUMBER_LABELS; i++)
			 for(int j = 0; j < NUMBER_LABELS; j++)
				 for(int k = 0; k < NUMBER_LABELS; k++)
					 binaryIds[i][j][k] = other.binaryIds[i][j][k];
		 for(int i = 0; i < NUMBER_LABELS; i++)
			 for(int j = 0; j < NUMBER_LABELS; j++)
				 unaryIds[i][j] = other.unaryIds[i][j];
	}
	
	public void printUnaryRuls(LabelEnumeration labels) {
		for (int i = 0; i < unaryRules.size(); ++i) {
			Rule rule = unaryRules.get(i);
			System.out.println(rule.toString(labels) + " " + unaryCnt[rule.getLabel()][rule.getLeft()]);
		}
	}
	
	private void addBinaryRule(Rule rule) {
		int label = rule.getLabel(), left = rule.getLeft(), right = rule.getRight();
		if(binaryIds[label][left][right] == -1) {
			int id = binaryRules.size();
			binaryRules.add(rule);
			binaryIds[label][left][right] = id;
			binaryCnt[label][left][right] = 1;
		}
		else {
			binaryCnt[label][left][right]++;
		}
	}
	
	private void addUnaryRule(Rule rule) {
		int label = rule.getLabel(), left = rule.getLeft();
		if(unaryIds[label][left] == -1) {
			int id = unaryRules.size();
			unaryRules.add(rule);
			unaryIds[label][left] = id;
			unaryCnt[label][left] = 1;
		}
		else {
			unaryCnt[label][left]++;
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
	
	public void countMissingRules(List<SpannedWords> spannedWords) {
		int badSentenceCount = 0;
		HashSet<Rule> rulesMissing = new HashSet<>();
		for(SpannedWords sw : spannedWords) {
			boolean missing = false;
			for(Span s : sw.getSpans()) {
				if(!isExistingRule(s.getRule())) {
					rulesMissing.add(s.getRule());
					missing = true;
				}
			}
			if(missing)
				badSentenceCount++;
		}
		System.out.println("Number sentences with missing rules: " + badSentenceCount);
		System.out.println("Number of missing rules: " + rulesMissing.size());
	}
	
	public Rule getBinaryRule(int index) {
		return binaryRules.get(index);
	}
	
	public int getBinaryId(Rule rule) {
		return binaryIds[rule.getLabel()][rule.getLeft()][rule.getRight()];
	}
	
	public int getNumberOfBinaryRules() {
		return binaryRules.size();
	}
	
	public Rule getUnaryRule(int index) {
		return unaryRules.get(index);
	}
	
	public int getUnaryId(Rule rule) {
		return unaryIds[rule.getLabel()][rule.getLeft()];
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
			return getBinaryId(rule) != -1;
		else if(rule.getType() == Type.UNARY)
			return getUnaryId(rule) != -1;
		else // all terminals are existing
			return true;
	}
	
	public boolean isExistingUnaryRule(int label, int left) {
		return unaryIds[label][left] != -1;
	}
	
	public boolean isExistingBinaryRule(int label, int left, int right) {
		return binaryIds[label][left][right] != -1;
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
	
	public long getRuleCode(Rule rule) {
		switch(rule.getType()) {
		case BINARY:
			return RuleEnumeration.getRuleCode(getBinaryId(rule), Type.BINARY);
		case UNARY:
			return RuleEnumeration.getRuleCode(getUnaryId(rule), Type.UNARY);
		case TERMINAL:
			return RuleEnumeration.getTerminalRuleCode(rule.getLabel());
		default:
			throw new RuntimeException("Bad rule type");
		}
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
	
	/**
	 * At one point there was a problem with having multiple of the same rule, this checks for that
	 */
	void checkUniqueness() {
		int numBinRules = getNumberOfBinaryRules();
		for(int r = 0; r < numBinRules; r++) {
			if(getBinaryId(getBinaryRule(r)) != r)
				throw new RuntimeException("Bad RuleEnumeration");
		}
	}
}
