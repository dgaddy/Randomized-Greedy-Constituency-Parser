package constituencyParser.features;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import constituencyParser.Rule;
import constituencyParser.Rules;
import constituencyParser.features.Feature.SpanProperty;

public class FeatureParameters implements Serializable {
	public class SpanPropertyParameters implements Serializable {
		private static final long serialVersionUID = 1L;
		
		private double[] labelValues;
		private double[] unaryRuleValues;
		private double[] binaryRuleValues;
		
		private SpanPropertyParameters(int numLabels, int numBinaryRules, int numUnaryRules) {
			labelValues = new double[numLabels];
			binaryRuleValues = new double[numBinaryRules];
			unaryRuleValues = new double[numUnaryRules];
		}
		
		void resize(int numLabels, int numBinaryRules, int numUnaryRules) {
			labelValues = Arrays.copyOf(labelValues, numLabels);
			unaryRuleValues = Arrays.copyOf(unaryRuleValues, numUnaryRules);
			binaryRuleValues = Arrays.copyOf(binaryRuleValues, numBinaryRules);
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
		int numBin = rules.getNumberOfBinaryRules();
		int numUn = rules.getNumberOfUnaryRules();
		for(Entry<SpanProperty, SpanPropertyParameters> entry : featureValues.entrySet()) {
			entry.getValue().resize(numLabels, numBin, numUn);
		}
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
			score += params.labelValues[labelNumber] + params.binaryRuleValues[ruleNumber]; 
		}
		return score;
	}
	
	public static double scoreUnary(List<SpanPropertyParameters> properties, int ruleNumber, int labelNumber) {
		double score = 0;
		for(SpanPropertyParameters params : properties) {
			score += params.labelValues[labelNumber] + params.unaryRuleValues[ruleNumber]; 
		}
		return score;
	}
	
	public static double scoreTerminal(List<SpanPropertyParameters> properties, int labelNumber) {
		double score = 0;
		for(SpanPropertyParameters params : properties) {
			score += params.labelValues[labelNumber];
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
			
			params.labelValues[rule.getParent()] += scale * entry.getValue();
			if(rule.getType() == Rule.Type.BINARY)
				params.binaryRuleValues[rules.getBinaryId(rule)] += scale * entry.getValue();
			if(rule.getType() == Rule.Type.UNARY)
				params.unaryRuleValues[rules.getUnaryId(rule)] += scale * entry.getValue();
		}
	}
}
