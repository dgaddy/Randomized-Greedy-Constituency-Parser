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
		parent = p;
		left = l;
		right = r;
		type = Type.BINARY;
	}
	
	public Rule(int label, int child) {
		parent = label;
		left = child;
		type = Type.UNARY;
	}
	
	public Rule(int label) {
		parent = label;
		type = Type.TERMINAL;
	}
	
	public Rule(Rule other) {
		this.type = other.type;
		this.parent = other.parent;
		this.left = other.left;
		this.right = other.right;
	}
	
	private volatile Type type;
	private volatile int parent;
	private volatile int left;
	private volatile int right;
	
	public int getParent() {
		return parent;
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
		newRule.parent = label;
		return newRule;
	}
	
	public boolean equals(Object other) {
		if(other instanceof Rule) {
			Rule otherR = (Rule)other;
			return type.equals(otherR.type) && otherR.parent == parent && otherR.left == left && otherR.right == right;
		}
		return false;
	}
	
	public int hashCode() {
		return type.hashCode() * 11 + parent * 13 + left * 17 + right * 19;
	}
	
	public String toString() {
		if(type == Type.TERMINAL)
			return "{" + parent + "}";
		else if(type == Type.UNARY)
			return "{" + parent + "->" + left + "}";
		else
			return "{" + parent + "->" + left + " " + right + "}";
	}
}
