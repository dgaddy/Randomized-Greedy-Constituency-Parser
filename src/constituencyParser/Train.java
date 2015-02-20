package constituencyParser;

import gnu.trove.map.hash.TLongDoubleHashMap;

import java.io.IOException;
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
	
	TLongDoubleHashMap predictedFeatureCounts = new TLongDoubleHashMap();
	TLongDoubleHashMap goldFeatureCounts = new TLongDoubleHashMap();
	
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
				List<Long> goldFeatures = Features.getAllFeatures(gold, words, doSecondOrder, wordEnum, labels, rules);
				double goldScore = 0;
				for(Long code : goldFeatures) {
					features.adjustOrPutValue(code, 1, 1);
					goldScore += parameters.getScore(code, true);
					goldFeatureCounts.adjustOrPutValue(code, 1, 1);
				}
				
				// negative
				List<Long> predictedFeatures = Features.getAllFeatures(predicted, words, doSecondOrder, wordEnum, labels, rules);
				double predictedScore = 0;
				for(Long code : predictedFeatures) {
					features.adjustOrPutValue(code, -1, -1);
					predictedScore += parameters.getScore(code, true);
					predictedFeatureCounts.adjustOrPutValue(code, 1, 1);
				}
				
				// used to double check scoring
				int augmentingScore = 0;
				if(costAugmenting) {
					for(Span s : predicted) {
						boolean inGold = false;
						for(Span gs : gold) {
							if(gs.getRule().getLabel() == s.getRule().getLabel() && gs.getStart() == s.getStart() && gs.getEnd() == s.getEnd()) {
								inGold = true;
							}
						}
						if(!inGold) {
							augmentingScore++;
						}
					}
				}
				
				if(Math.abs(predictedScore + augmentingScore - decoder.getLastScore()) > 1e-4) {
					SpanUtilities.printSpans(predicted, sw.getWords().size(), labels);
					SaveObject so = new SaveObject(wordEnum, labels, rules, parameters);
					try {
						so.save("modelBeforeCrash");
					} catch (IOException e) {
					}
					throw new RuntimeException("" + exampleNumber + " Decoder score and freshly calculated score don't match: " + (predictedScore + augmentingScore) + " " + decoder.getLastScore());
				}
				
				if(goldScore > predictedScore + augmentingScore) {
					System.out.println("Warning: Gold score greater than predicted score, but decoder didn't find it");
				}
				
				parameters.update(features, exampleNumber);
			}
		}
		checkParameterSanity();
		
		System.out.println("Finished; Average loss: " + (totalLoss / (double)exampleNumber));
	}
	
	private void checkParameterSanity() {
		for(Long feature : parameters.getStoredFeatures()) {
			if(!Features.isSecondOrderFeature(feature))
				continue;
			
			double score = parameters.getScore(feature, false);
			double predictedCount = predictedFeatureCounts.get(feature);
			double goldCount = goldFeatureCounts.get(feature);
			if(score > 1 && predictedCount > goldCount) {
				System.out.println("Feature " + Features.getStringForCode(feature, wordEnum, rules, labels) + " has positive score,");
				System.out.println("but it appears " + predictedCount + " in predicted and " + goldCount + " in gold");
			}
			if(score < -1 && goldCount > predictedCount) {
				System.out.println("Feature " + Features.getStringForCode(feature, wordEnum, rules, labels) + " has negative score,");
				System.out.println("but it appears " + predictedCount + " in predicted and " + goldCount + " in gold");
			}
		}
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
