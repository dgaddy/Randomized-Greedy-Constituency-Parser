package constituencyParser;

import java.io.Serializable;

public class Rule implements Serializable {
	private static final long serialVersionUID = 1L;

	public enum Type {
		BINARY, // of the form A->B C
		UNARY, // of the form A->B
		TERMINAL // of the form A->word
	}
	
	public Rule(int p, int l, int r) {
		label = p;
		left = l;
		right = r;
		type = Type.BINARY;
	}
	
	public Rule(int label, int child) {
		this.label = label;
		left = child;
		type = Type.UNARY;
	}
	
	public Rule(int label) {
		this.label = label;
		type = Type.TERMINAL;
	}
	
	public Rule(Rule other) {
		this.type = other.type;
		this.label = other.label;
		this.left = other.left;
		this.right = other.right;
	}
	
	private volatile Type type;
	private volatile int label;
	private volatile int left;
	private volatile int right;
	
	public int getLabel() {
		return label;
	}
	
	public int getLeft() {
		return left;
	}
	
	public int getRight() {
		return right;
	}
	
	public Type getType() {
		return type;
	}
	
	public Rule changeLabel(int label) {
		Rule newRule = new Rule(this);
		newRule.label = label;
		return newRule;
	}
	
	public boolean equals(Object other) {
		if(other instanceof Rule) {
			Rule otherR = (Rule)other;
			return type.equals(otherR.type) && otherR.label == label && otherR.left == left && otherR.right == right;
		}
		return false;
	}
	
	public int hashCode() {
		return type.hashCode() * 11 + label * 13 + left * 17 + right * 19;
	}
	
	public String toString(LabelEnumeration labels) {
		if(type == Type.TERMINAL)
			return "{" + labels.getLabel(label) + "}";
		else if(type == Type.UNARY)
			return "{" + labels.getLabel(label) + "->" + labels.getLabel(left) + "}";
		else
			return "{" + labels.getLabel(label) + "->" + labels.getLabel(left) + " " + labels.getLabel(right) + "}";
	}
	
	public String toString() {
		if(type == Type.TERMINAL)
			return "{" + label + "}";
		else if(type == Type.UNARY)
			return "{" + label + "->" + left + "}";
		else
			return "{" + label + "->" + left + " " + right + "}";
	}
	
	public static Rule getRule(String label, LabelEnumeration labelEnum) {
		return new Rule(labelEnum.getId(label));
	}
	
	public static Rule getRule(String parent, String child, LabelEnumeration labelEnum) {
		return new Rule(labelEnum.getId(parent), labelEnum.getId(child));
	}
	
	public static Rule getRule(String parent, String left, String right, LabelEnumeration labelEnum) {
		return new Rule(labelEnum.getId(parent), labelEnum.getId(left), labelEnum.getId(right));
	}
}
