package constituencyParser;

import constituencyParser.Rule.Type;

public class Span {
	
	private int start;
	private int end;
	
	private Rule rule;
	
	// binary only, not defined for unary features
	private int split;
	
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
