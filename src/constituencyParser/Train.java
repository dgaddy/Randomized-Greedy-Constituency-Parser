package constituencyParser;

import gnu.trove.map.hash.TLongDoubleHashMap;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import constituencyParser.features.FeatureParameters;
import constituencyParser.features.Features;

/**
 * Trains a parser using Adagrad
 */
public class Train {
	public DiscriminativeCKYDecoder CKYDecoder;
	
	Decoder decoder;
	WordEnumeration wordEnum;
	LabelEnumeration labels;
	RuleEnumeration rules;
	FeatureParameters parameters;
	
	TLongDoubleHashMap predictedFeatureCounts = new TLongDoubleHashMap();
	TLongDoubleHashMap goldFeatureCounts = new TLongDoubleHashMap();
	
	public Train(WordEnumeration words, LabelEnumeration labels, RuleEnumeration rules, Decoder decoder, FeatureParameters parameters) {
		this.wordEnum = words;
		this.labels = labels;
		this.rules = rules;
		this.decoder = decoder;
		this.parameters = parameters;
	}
	
	public void train(List<SpannedWords> trainingExamples, double dropout, boolean doSecondOrder, boolean costAugmenting, int batchSize, int iters, boolean mira) {
		int totalLoss = 0;
		int updateNumber = 0;
		int index = 0;
		int N = trainingExamples.size();
		
		//parameters.resetDropout(dropout);
		System.out.println("Number of training examples: " + trainingExamples.size());
		int matchCKY = 0;
		while(index < trainingExamples.size()) {

			TLongDoubleHashMap features = new TLongDoubleHashMap();
			//parameters.resetDropout(dropout);
			
			double batchPredictedScore = 0;
			double batchGoldScore = 0;
			
			for(int b = 0; b < batchSize && index < trainingExamples.size(); b++, index++) {
				if ((index + 1) % 10 == 0)
					System.out.print((index + 1) + "  ");
				
				SpannedWords sw = trainingExamples.get(index);
				//if(count % 5 == 0) {
					//System.out.println(count + " of " + trainingExamples.size() + "; Average loss: " + (totalLoss / (double) count));
				//}
				
				List<Word> words = sw.getWords();
				
				// CKY
				double CKYPredictedScore = 0.0;
				if (!doSecondOrder) {
					CKYDecoder.setCostAugmenting(costAugmenting, sw);
					List<Span> CKYPredicted = CKYDecoder.decode(words, parameters);
					((RandomizedGreedyDecoder)decoder).ckySpan = CKYPredicted;
					CKYPredictedScore = CKYDecoder.getLastScore();
				}

				decoder.setCostAugmenting(costAugmenting, sw);
				decoder.setSecondOrder(doSecondOrder);
				List<Span> predicted;
				 
				if(parameters.getStoredFeatures().size() == 0) {
					predicted = new ArrayList<>(); // don't run decoder if no features, since it won't be doing anything useful anyway, and runs a lot slower
				}
				else {
					predicted = decoder.decode(words, parameters);
				}
			
				int loss = computeLoss(predicted, sw.getSpans()); 
				totalLoss += loss;
				
				if(loss > 0) {
					List<Span> gold = sw.getSpans();
					
					// positive
					List<Long> goldFeatures = Features.getAllFeatures(gold, words, doSecondOrder, wordEnum, labels, rules);
					double goldScore = 0;
					for(Long code : goldFeatures) {
						features.adjustOrPutValue(code, -1.0, -1.0);
						goldScore += parameters.getScore(code);
						goldFeatureCounts.adjustOrPutValue(code, 1.0, 1.0);
					}
					
					batchGoldScore += goldScore;
					batchPredictedScore += decoder.getLastScore();
					
					// negative
					List<Long> predictedFeatures = Features.getAllFeatures(predicted, words, doSecondOrder, wordEnum, labels, rules);
					double predictedScore = 0;
					for(Long code : predictedFeatures) {
						features.adjustOrPutValue(code, 1.0, 1.0);
						predictedScore += parameters.getScore(code);
						predictedFeatureCounts.adjustOrPutValue(code, 1.0, 1.0);
					}
					
					// used to double check scoring
					int augmentingScore = 0;
					if(costAugmenting) {
						for(Span s : predicted) {
							boolean inGold = false;
							for(Span gs : gold) {
								if(gs.getRule().getLabel() == s.getRule().getLabel() && gs.getStart() == s.getStart() && gs.getEnd() == s.getEnd() && gs.getRule().getType() == s.getRule().getType()) {
									inGold = true;
								}
							}
							if(!inGold) {
								augmentingScore++;
							}
						}
					}
					
					if(Math.abs(predictedScore + augmentingScore - decoder.getLastScore()) > 1e-4 && !Double.isInfinite(decoder.getLastScore())) {
						SpanUtilities.printSpans(predicted, sw.getWords().size(), labels);
						SaveObject so = new SaveObject(wordEnum, labels, rules, parameters);
						try {
							so.save("modelBeforeCrash");
						} catch (IOException e) {
						}
						throw new RuntimeException("" + index + " Decoder score and freshly calculated score don't match: " + (predictedScore + augmentingScore) + " " + decoder.getLastScore());
					}
					
					if(goldScore > predictedScore + augmentingScore) {
						System.out.println("Warning: Gold score greater than predicted score, but decoder didn't find it");
						System.out.println("Gold score: " + goldScore + " predicted: " + predictedScore + " " + augmentingScore);
					}
					
					//System.out.println("gold: " + goldScore + " greed: " + decoder.getLastScore() + " cky: " + CKYPredictedScore);
					//SpanUtilities.print(predicted, labels);
					//SpanUtilities.print(CKYPredicted, labels);
					//try { System.in.read(); } catch (Exception e) {e.printStackTrace();}
					//SpanUtilities.debugSpan(predicted, labels, (RandomizedGreedyDecoder)decoder);
					if (!doSecondOrder && Math.abs(CKYPredictedScore - decoder.getLastScore()) < 1e-6) {
						matchCKY++;
					}
				}
			}
			
			updateNumber++;
			if(mira) {
				int miraCount = (iters * (N / batchSize + (N%batchSize > 0 ? 1 : 0)) + updateNumber);
				//parameters.updateMIRA(features, batchPredictedScore - batchGoldScore, miraCount);
				parameters.updateMIRA(features, batchPredictedScore - batchGoldScore);
			}
			else
				parameters.update(features);
		}
		//checkParameterSanity();
		
		decoder.setCostAugmenting(false, null);
		parameters.resetDropout(0);
		System.out.println("Finished; Average loss: " + (totalLoss / (double)trainingExamples.size()));
		System.out.println("match cky: " + (matchCKY + 0.0) / trainingExamples.size());
	}
	
	@SuppressWarnings("unused")
	private void checkParameterSanity() {
		try {
			FileWriter writer = new FileWriter("second_order_features");
			parameters.resetDropout(0);
			for(Long feature : parameters.getStoredFeatures()) {
				if(!Features.isSecondOrderFeature(feature))
					continue;
				
				double score = parameters.getScore(feature);
				double predictedCount = predictedFeatureCounts.get(feature);
				double goldCount = goldFeatureCounts.get(feature);
				writer.write(Features.getStringForCode(feature, wordEnum, rules, labels) + " " + score + " " + predictedCount + " " + goldCount);
				if(score > 1 && predictedCount > goldCount) {
					System.out.println("Feature " + Features.getStringForCode(feature, wordEnum, rules, labels) + " has positive score,");
					System.out.println("but it appears " + predictedCount + " in predicted and " + goldCount + " in gold");
				}
				if(score < -1 && goldCount > predictedCount) {
					System.out.println("Feature " + Features.getStringForCode(feature, wordEnum, rules, labels) + " has negative score,");
					System.out.println("but it appears " + predictedCount + " in predicted and " + goldCount + " in gold");
				}
			}
			writer.close();
		}
		catch(IOException ex) {}
	}
	
	public FeatureParameters getParameters() {
		return parameters;
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
