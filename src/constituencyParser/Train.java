package constituencyParser;

import gnu.trove.list.TLongList;
import gnu.trove.map.hash.TLongDoubleHashMap;

import java.util.HashSet;
import java.util.List;

import constituencyParser.features.FeatureParameters;
import constituencyParser.features.Features;

/**
 * Trains a parser using Adagrad
 */
public class Train {
	Decoder decoder;
	WordEnumeration wordEnum;
	LabelEnumeration labels;
	RuleEnumeration rules;
	FeatureParameters parameters;
	
	int numberExamples = 0;
	
	public Train(WordEnumeration words, LabelEnumeration labels, RuleEnumeration rules, Decoder decoder) {
		this.wordEnum = words;
		this.labels = labels;
		this.rules = rules;
		this.decoder = decoder;
		parameters = new FeatureParameters();
	}
	
	public Train(WordEnumeration words, LabelEnumeration labels, RuleEnumeration rules, Decoder decoder, FeatureParameters parameters) {
		this.wordEnum = words;
		this.labels = labels;
		this.rules = rules;
		this.decoder = decoder;
		this.parameters = parameters;
	}
	
	public void train(List<SpannedWords> trainingExamples, double dropout, boolean doSecondOrder, boolean costAugmenting) {
		numberExamples = trainingExamples.size();
		
		int exampleNumber = 0; // first example is 1
		int totalLoss = 0;
		for(SpannedWords sw : trainingExamples) {
			exampleNumber++;
			//if(count % 5 == 0) {
				//System.out.println(count + " of " + trainingExamples.size() + "; Average loss: " + (totalLoss / (double) count));
			//}
			
			parameters.resetDropout(dropout);
			
			List<Integer> words = sw.getWords();
			
			decoder.setCostAugmenting(costAugmenting, sw);
			decoder.setSecondOrder(doSecondOrder);
			List<Span> predicted = decoder.decode(words, parameters, true);
			
			int loss = computeLoss(predicted, sw.getSpans()); 
			totalLoss += loss;
			
			if(loss > 0) {
				TLongDoubleHashMap features = new TLongDoubleHashMap();
				//System.out.println("positive");
				List<Span> gold = sw.getSpans();
				int[] goldParents = SpanUtilities.getParents(gold);
				int[] predictedParents = SpanUtilities.getParents(predicted);
				for(int j = 0; j < gold.size(); j++) {
					Span s = gold.get(j);
					TLongList featureCodes = Features.getSpanPropertyByRuleFeatures(words, s, rules, wordEnum);
					for(int i = 0; i < featureCodes.size(); i++) {
						features.adjustOrPutValue(featureCodes.get(i), 1, 1);
						//System.out.println(Features.getStringForCode(featureCodes.get(i), wordEnum, rules, labels));
					}
					features.adjustOrPutValue(Features.getRuleFeature(s.getRule(), rules), 1, 1);
					//System.out.println(Features.getStringForCode(Features.getRuleFeature(s.getRule(), rules), wordEnum, rules));
					TLongList propertyByLabelCodes = Features.getSpanPropertyByLabelFeatures(words, s);
					for(int i = 0; i < propertyByLabelCodes.size(); i++) {
						features.adjustOrPutValue(propertyByLabelCodes.get(i), 1, 1);
						//System.out.println(Features.getStringForCode(propertyByLabelCodes.get(i), wordEnum, rules, labels));
					}
					
					if(doSecondOrder) {
						if(goldParents[j] != -1) {
							Rule rule = s.getRule();
							Rule parentRule = gold.get(goldParents[j]).getRule();

							if(rule.getType() == Rule.Type.UNARY) {
								long code = Features.getSecondOrderRuleFeature(rule.getLeft(), rule.getLabel(), parentRule.getLabel());
								features.adjustOrPutValue(code, 1, 1);
							}
							else if(rule.getType() == Rule.Type.BINARY) {
								long code = Features.getSecondOrderRuleFeature(rule.getLeft(), rule.getLabel(), parentRule.getLabel());
								features.adjustOrPutValue(code, 1, 1);
								code = Features.getSecondOrderRuleFeature(rule.getRight(), rule.getLabel(), parentRule.getLabel());
								features.adjustOrPutValue(code, 1, 1);
							}
						}
					}
				}
				//System.out.println("negative");
				for(int j = 0; j < predicted.size(); j++) {
					Span s = predicted.get(j);
					TLongList featureCodes = Features.getSpanPropertyByRuleFeatures(words, s, rules, wordEnum);
					for(int i = 0; i < featureCodes.size(); i++) {
						features.adjustOrPutValue(featureCodes.get(i), -1, -1);
						//System.out.println(Features.getStringForCode(featureCodes.get(i), wordEnum, rules));
					}
					features.adjustOrPutValue(Features.getRuleFeature(s.getRule(), rules), -1, -1);
					//System.out.println(Features.getStringForCode(Features.getRuleFeature(s.getRule(), rules), wordEnum, rules));
					TLongList propertyByLabelCodes = Features.getSpanPropertyByLabelFeatures(words, s);
					for(int i = 0; i < propertyByLabelCodes.size(); i++) {
						features.adjustOrPutValue(propertyByLabelCodes.get(i), -1, -1);
					}
					
					if(doSecondOrder) {
						if(predictedParents[j] != -1) {
							Rule rule = s.getRule();
							Rule parentRule = predicted.get(predictedParents[j]).getRule();

							if(rule.getType() == Rule.Type.UNARY) {
								long code = Features.getSecondOrderRuleFeature(rule.getLeft(), rule.getLabel(), parentRule.getLabel());
								features.adjustOrPutValue(code, -1, -1);
							}
							else if(rule.getType() == Rule.Type.BINARY) {
								long code = Features.getSecondOrderRuleFeature(rule.getLeft(), rule.getLabel(), parentRule.getLabel());
								features.adjustOrPutValue(code, -1, -1);
								code = Features.getSecondOrderRuleFeature(rule.getRight(), rule.getLabel(), parentRule.getLabel());
								features.adjustOrPutValue(code, -1, -1);
							}
						}
					}
				}
				
				parameters.update(features, exampleNumber);
			}
		}
		
		System.out.println("Finished; Average loss: " + (totalLoss / (double)exampleNumber));
	}
	
	public FeatureParameters getParameters() {
		return parameters;
	}
	
	public FeatureParameters getAverageParametersOverAllIterations() {
		return parameters.averageOverIterations(numberExamples);
	}
	
	static int computeLoss(List<Span> predicted, List<Span> gold) {
		HashSet<Span> goldSpans = new HashSet<>(gold);
		
		int common = 0;
		for(Span s : predicted) {
			if(goldSpans.contains(s))
				common++;
		}
		
		return predicted.size() + gold.size() - 2*common;
	}
}
