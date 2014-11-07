package constituencyParser;

import constituencyParser.Rule.Type;

public class Span {
	
	private int start;
	private int end;
	
	private Rule rule;
	
	// binary only, not defined for unary features
	private int split;
	
	// these are optional, used by CKY to hold children
	private Span left;
	private Span right;
	
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
	
	public Span(int position, Rule rule) {
		this.rule = rule;
		this.start = position;
		this.end = position + 1;
	}
	
	public Span(int start, int end, int label, int childLabel) {
		this.rule = new Rule(label, childLabel);
		this.start = start;
		this.end = end;
	}
	
	public Span(int start, int end, Rule rule) {
		this.rule = rule;
		this.start = start;
		this.end = end;
	}
	
	public Span(int start, int end, int split, int label, int leftLabel, int rightLabel) {
		this.rule = new Rule(label, leftLabel, rightLabel);
		this.start = start;
		this.end = end;
		this.split = split;
	}
	
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
	
	public Span changeChildLabel(boolean left, int label) {
		Rule newRule;
		switch(this.rule.getType()) {
		case UNARY:
			newRule = new Rule(this.rule.getParent(), label);
			break;
		case TERMINAL:
			newRule = new Rule(this.rule.getParent());
			break;
		case BINARY:
			if(left)
				newRule = new Rule(this.rule.getParent(), label, this.rule.getRight());
			else
				newRule = new Rule(this.rule.getParent(), this.rule.getLeft(), label);
			break;
		default:
			throw new RuntimeException();
		}
		return new Span(start, end, split, newRule);
	}
	
	public String toString() {
		return "Span: " + rule + " " + start + " " + end;
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
