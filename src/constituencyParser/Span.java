package constituencyParser;

import constituencyParser.Rule.Type;

/**
 * Represents a span, which is a production rule and it's position over a sentence
 */
public class Span {
	
	private int start;
	private int end;
	
	private Rule rule;
	
	// binary only, not defined for unary features
	private int split;
	
	// these are optional, used by CKY to hold children
	private Span left;
	private Span right;
	
	public Span(Span other) {
		start = other.start;
		end = other.end;
		rule = other.rule;
		split = other.split;
	}

	/**
	 * Makes a terminal span
	 * @param position
	 * @param label
	 */
	public Span(int position, int label) {
		this.rule = new Rule(label);
		this.start = position;
		this.end = position + 1;
	}
	
	/**
	 * Used for terminals
	 * @param position
	 * @param rule
	 */
	public Span(int position, Rule rule) {
		this.rule = rule;
		this.start = position;
		this.end = position + 1;
	}
	
	/**
	 * Used for unary rules
	 * @param start
	 * @param end
	 * @param label
	 * @param childLabel
	 */
	public Span(int start, int end, int label, int childLabel) {
		this.rule = new Rule(label, childLabel);
		this.start = start;
		this.end = end;
	}
	
	/** 
	 * Used for unary rules
	 * @param start
	 * @param end
	 * @param rule
	 */
	public Span(int start, int end, Rule rule) {
		this.rule = rule;
		this.start = start;
		this.end = end;
	}
	
	/**
	 * Used for binary rules
	 * @param start
	 * @param end
	 * @param split
	 * @param label
	 * @param leftLabel
	 * @param rightLabel
	 */
	public Span(int start, int end, int split, int label, int leftLabel, int rightLabel) {
		this.rule = new Rule(label, leftLabel, rightLabel);
		this.start = start;
		this.end = end;
		this.split = split;
	}
	
	/**
	 * Used for binary rules
	 * @param start
	 * @param end
	 * @param split
	 * @param rule
	 */
	public Span(int start, int end, int split, Rule rule) {
		this.rule = rule;
		this.start = start;
		this.end = end;
		this.split = split;
	}
	
	public void shiftRight(int shift) {
		start += shift;
		end += shift;
		split += shift;
	}
	
	public Rule getRule() {
		return rule;
	}
	
	public int getStart() {
		return start;
	}
	
	public int getEnd() {
		return end;
	}
	
	public int getSplit() {
		return split;
	}
	
	public void setLeft(Span left) {
		this.left = left;
	}
	
	public void setRight(Span right) {
		this.right = right;
	}
	
	public Span getLeft() {
		return left;
	}
	
	public Span getRight() {
		return right;
	}
	
	public Span removeRange(int start, int end) {
		int newStart = this.start;
		int newEnd = this.end;
		if(start == this.start)
			newStart = end;
		if(end == this.end)
			newEnd = start;
		return new Span(newStart, newEnd, this.split, this.rule);
	}
	
	/**
	 * Call when a child has changed size to update values
	 * @param start the new start of the child
	 * @param end the new end of the child
	 * @return
	 */
	public Span childExpanded(int start, int end) {
		int newStart = this.start;
		int newEnd = this.end;
		int newSplit = this.split;
		if(start == this.start)
			newSplit = end;
		else if(end == this.end)
			newSplit = start;
		else if(start == this.split)
			newEnd = end;
		else if(end == this.split)
			newStart = start;
		
		return new Span(newStart, newEnd, newSplit, this.rule);
	}
	
	public Span changeLabel(int label) {
		Rule newRule;
		switch(this.rule.getType()) {
		case UNARY:
			newRule = new Rule(label, this.rule.getLeft());
			break;
		case TERMINAL:
			newRule = new Rule(label);
			break;
		case BINARY:
			newRule = new Rule(label, this.rule.getLeft(), this.rule.getRight());
			break;
		default:
			throw new RuntimeException();
		}
		Span result =  new Span(start, end, split, newRule);
		result.setLeft(left);
		result.setRight(right);
		return result;
	}
	
	public Span changeChildLabel(boolean left, int label) {
		Rule newRule;
		switch(this.rule.getType()) {
		case UNARY:
			newRule = new Rule(this.rule.getLabel(), label);
			break;
		case TERMINAL:
			newRule = new Rule(this.rule.getLabel());
			break;
		case BINARY:
			if(left)
				newRule = new Rule(this.rule.getLabel(), label, this.rule.getRight());
			else
				newRule = new Rule(this.rule.getLabel(), this.rule.getLeft(), label);
			break;
		default:
			throw new RuntimeException();
		}
		Span result =  new Span(start, end, split, newRule);
		result.setLeft(this.left);
		result.setRight(this.right);
		return result;
	}
	
	public String toString() {
		return "Span: " + rule + " " + start + " " + end;
	}
	
	public String toString(LabelEnumeration labels) {
		return "Span: " + rule.toString(labels) + " " + start + " " + end + (rule.getType() == Type.BINARY ? " " + split : "");
	}

	public boolean equals(Object other) {
		if(other instanceof Span) {
			Span otherSpan = (Span)other;
			boolean equal = otherSpan.start == start && otherSpan.end == end && otherSpan.rule.equals(rule);
			if(equal && rule.getType() == Type.BINARY)
				equal &= otherSpan.split == split;
			return equal;
		}
		return false;
	}
	
	public int hashCode() {
		int value = start * 17 + end * 19 + 3 * rule.hashCode();
		if(rule.getType() == Type.BINARY) {
			value += split * 29;
		}
		return value;
	}
}
