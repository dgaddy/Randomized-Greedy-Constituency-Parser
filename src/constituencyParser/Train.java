package constituencyParser;

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
			
			List<Word> words = sw.getWords();
			
			decoder.setCostAugmenting(costAugmenting, sw);
			decoder.setSecondOrder(doSecondOrder);
			List<Span> predicted = decoder.decode(words, parameters, true);
			
			int loss = computeLoss(predicted, sw.getSpans()); 
			totalLoss += loss;
			
			if(loss > 0) {
				TLongDoubleHashMap features = new TLongDoubleHashMap();
				List<Span> gold = sw.getSpans();
				
				// positive
				for(Long code : Features.getAllFeatures(gold, words, doSecondOrder, wordEnum, labels, rules)) {
					features.adjustOrPutValue(code, 1, 1);
				}
				
				// negative
				for(Long code : Features.getAllFeatures(predicted, words, doSecondOrder, wordEnum, labels, rules)) {
					features.adjustOrPutValue(code, -1, -1);
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
