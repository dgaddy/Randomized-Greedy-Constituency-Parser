package constituencyParser.features;

import java.io.Serializable;

import constituencyParser.Rule;

public class Feature implements Serializable {
	private static final long serialVersionUID = 1L;

	public static class SpanProperty{}
	
	private Rule rule;
	private SpanProperty property;
	
	public Feature(Rule rule2, SpanProperty spanProperty) {
		this.rule = rule2;
		this.property = spanProperty;
	}
	
	public Rule getRule() {
		return rule;
	}
	
	public SpanProperty getProperty() {
		return property;
	}
	
	public boolean equals(Object other) {
		if(other instanceof Feature) {
			Feature f = (Feature)other;
			return f.rule.equals(rule) && f.property.equals(property);
		}
		return false;
	}
	
	public int hashCode() {
		return rule.hashCode() + property.hashCode();
	}
}
