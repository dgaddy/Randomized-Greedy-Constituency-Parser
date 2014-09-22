package constituencyParser.features;

import gnu.trove.map.hash.TIntDoubleHashMap;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import constituencyParser.Rule;
import constituencyParser.Rules;
import constituencyParser.features.Feature.SpanProperty;

public class FeatureParameters implements Serializable {
	public class SpanPropertyParameters implements Serializable {
		private static final long serialVersionUID = 2L;
		
		private TIntDoubleHashMap labelValues;
		private TIntDoubleHashMap unaryRuleValues;
		private TIntDoubleHashMap binaryRuleValues;
		
		private SpanPropertyParameters(int numLabels, int numBinaryRules, int numUnaryRules) {
			labelValues = new TIntDoubleHashMap();
			binaryRuleValues = new TIntDoubleHashMap();
			unaryRuleValues = new TIntDoubleHashMap();
		}
	}
	
	private static final long serialVersionUID = 1L;
	
	HashMap<SpanProperty, SpanPropertyParameters> featureValues = new HashMap<>();
	int numberOfLabels;
	
	Rules rules;
	
	public FeatureParameters(int numLabels, Rules rules) {
		numberOfLabels = numLabels;
		this.rules = rules;
	}
	
	public void resize(int numLabels, Rules rules) {
		this.numberOfLabels = numLabels;
		this.rules = rules;
	}
	
	/**
	 * Returns the non-empty SpanPropertyParameters for a list of SpanProperties
	 * @param properties
	 * @return
	 */
	public List<SpanPropertyParameters> getSpanParameters(List<SpanProperty> properties) {
		List<SpanPropertyParameters> result = new ArrayList<>();
		for(SpanProperty sp : properties) {
			if(featureValues.containsKey(sp))
				result.add(featureValues.get(sp));
		}
		return result;
	}
	
	public static double scoreBinary(List<SpanPropertyParameters> properties, int ruleNumber, int labelNumber) {
		double score = 0;
		for(SpanPropertyParameters params : properties) {
			score += params.labelValues.get(labelNumber) + params.binaryRuleValues.get(ruleNumber); 
		}
		return score;
	}
	
	public static double scoreUnary(List<SpanPropertyParameters> properties, int ruleNumber, int labelNumber) {
		double score = 0;
		for(SpanPropertyParameters params : properties) {
			score += params.labelValues.get(labelNumber) + params.unaryRuleValues.get(ruleNumber); 
		}
		return score;
	}
	
	public static double scoreTerminal(List<SpanPropertyParameters> properties, int labelNumber) {
		double score = 0;
		for(SpanPropertyParameters params : properties) {
			score += params.labelValues.get(labelNumber);
		}
		return score;
	}
	
	/**
	 * adds scale*featureUpdates to the parameters
	 * @param featureUpdates
	 * @param scale
	 */
	public void add(Map<Feature, Double> featureUpdates, double scale) {
		for(Entry<Feature, Double> entry : featureUpdates.entrySet()) {
			Feature feature = entry.getKey();
			SpanProperty property = feature.getProperty();
			Rule rule = feature.getRule();
			
			SpanPropertyParameters params;
			if(featureValues.containsKey(property)) {
				params = featureValues.get(property);
			}
			else {
				params = new SpanPropertyParameters(numberOfLabels, rules.getNumberOfBinaryRules(), rules.getNumberOfUnaryRules());
				featureValues.put(property, params);
			}
			
			double adjustment = scale * entry.getValue();
			params.labelValues.adjustOrPutValue(rule.getParent(), adjustment, adjustment);
			if(rule.getType() == Rule.Type.BINARY) {
				params.binaryRuleValues.adjustOrPutValue(rules.getBinaryId(rule), adjustment, adjustment);
			}
			if(rule.getType() == Rule.Type.UNARY) {
				params.unaryRuleValues.adjustOrPutValue(rules.getUnaryId(rule), adjustment, adjustment);
			}
		}
	}
}
