package constituencyParser;

import gnu.trove.map.hash.TLongDoubleHashMap;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import constituencyParser.Rule.Type;
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
	
	public void train(List<SpannedWords> trainingExamples, double dropout, boolean doSecondOrder, boolean costAugmenting, double cost, int batchSize, int iters) {
		int totalLoss = 0;
		int index = 0;
		int N = trainingExamples.size();
		
		//parameters.resetDropout(dropout);
		System.out.println(trainingExamples.size());
		while(index < trainingExamples.size()) {

			//TLongDoubleHashMap features = new TLongDoubleHashMap();
			
			for(int b = 0; b < batchSize && index < trainingExamples.size(); b++, index++) {
				if ((index + 1) % 10 == 0)
					System.out.print((index + 1) + "  ");
				
				SpannedWords sw = trainingExamples.get(index);
				//if(count % 5 == 0) {
					//System.out.println(count + " of " + trainingExamples.size() + "; Average loss: " + (totalLoss / (double) count));
				//}
				
				List<Word> words = sw.getWords();
				
				// CKY
				CKYDecoder.setCostAugmenting(costAugmenting, sw, cost);
				List<Span> CKYPredicted = CKYDecoder.decode(words, parameters);
				((RandomizedGreedyDecoder)decoder).ckySpan = CKYPredicted;
				//double CKYPredictedScore = CKYDecoder.getLastScore();
				//System.out.println(" cky: " + CKYPredictedScore);

				decoder.setCostAugmenting(costAugmenting, sw, cost);
				decoder.setSecondOrder(doSecondOrder);
				List<Span> predicted;
				//System.out.println("aaa");
				//int tmp = parameters.getStoredFeatures().size(); 
				//if(tmp == 0) {
				//	System.out.println("???");
				//	predicted = new ArrayList<>();
				//}
				//else {
				//	System.out.println(tmp);
					predicted = decoder.decode(words, parameters);
				//}
					
				//System.out.println("bbb");
				int loss = computeLoss(predicted, sw.getSpans()); 
				totalLoss += loss;
				
				if(loss > 0) {
					TLongDoubleHashMap features = new TLongDoubleHashMap();
					
					//System.out.println("ccc");
					List<Span> gold = sw.getSpans();
					
					// positive
					List<Long> goldFeatures = Features.getAllFeatures(gold, words, doSecondOrder, wordEnum, labels, rules);
					double goldScore = 0;
					for(Long code : goldFeatures) {
						features.adjustOrPutValue(code, -1.0, -1.0);
						goldScore += parameters.getScore(code);
						goldFeatureCounts.adjustOrPutValue(code, 1.0, 1.0);
					}
					
					// negative
					List<Long> predictedFeatures = Features.getAllFeatures(predicted, words, doSecondOrder, wordEnum, labels, rules);
					double predictedScore = 0;
					for(Long code : predictedFeatures) {
						features.adjustOrPutValue(code, 1.0, 1.0);
						predictedScore += parameters.getScore(code);
						predictedFeatureCounts.adjustOrPutValue(code, 1.0, 1.0);
					}
					
					// used to double check scoring
					/*
					int augmentingScore = 0;
					for(Span s : predicted) {
						boolean inGold = false;
						if (s.getRule().getType() == Type.UNARY) {
							for(Span gs : gold) {
								if (gs.getRule().getType() == Type.UNARY
										&& gs.getRule().getLabel() == s.getRule().getLabel() && gs.getStart() == s.getStart() && gs.getEnd() == s.getEnd()) {
									inGold = true;
								}
							}

						}
						if(!inGold) {
							augmentingScore += cost;
						}
					}
					*/
					double augmentingScore = computeCostAugment(words.size(), gold, predicted, cost);
					
					if(Math.abs(predictedScore + (costAugmenting ? augmentingScore : 0.0) - decoder.getLastScore()) > 1e-4) {
						SpanUtilities.printSpans(predicted, sw.getWords().size(), labels);
						SaveObject so = new SaveObject(wordEnum, labels, rules, parameters);
						try {
							so.save("modelBeforeCrash");
						} catch (IOException e) {
						}
						throw new RuntimeException("" + index + " Decoder score and freshly calculated score don't match: " + (predictedScore + augmentingScore) + " " + decoder.getLastScore());
					}
					
					if(goldScore > predictedScore + (costAugmenting ? augmentingScore : 0.0)) {
						System.out.println("Warning: Gold score greater than predicted score, but decoder didn't find it");
						System.out.println("Gold score: " + goldScore + " predicted: " + predictedScore + " " + augmentingScore);
						//SpanUtilities.printSpans(gold, sw.getWords().size(), labels);
						//SpanUtilities.printSpans(predicted, sw.getWords().size(), labels);
						//try { System.in.read(); } catch (Exception e) {}
					}
					
					//System.out.println("gold: " + goldScore + " greed: " + decoder.getLastScore() + " cky: " + CKYPredictedScore);
					//SpanUtilities.print(predicted, labels);
					//SpanUtilities.print(CKYPredicted, labels);
					//try { System.in.read(); } catch (Exception e) {e.printStackTrace();}
					//SpanUtilities.debugSpan(predicted, labels, (RandomizedGreedyDecoder)decoder);
					
					if (augmentingScore + predictedScore - goldScore > 0)
						parameters.updateMIRA(features, augmentingScore + predictedScore - goldScore, iters * N + index + 1);
				}
				//System.out.println("ddd");
			}
			
			//parameters.update(features);
			//System.out.println("eee");
		}
		//checkParameterSanity();
		
		System.out.println("Finished; Average loss: " + (totalLoss / (double)trainingExamples.size()));
	}
	
	public double computeCostAugment(int size, List<Span> gold, List<Span> pred, double cost) {
		int[][] goldLabels = new int[size][size+1];
		int[][] goldUnaryLabels = new int[size][size+1];
		for(int i = 0; i < size; i++) {
			for(int j = 0; j < size+1; j++) {
				goldLabels[i][j] = -1;
				goldUnaryLabels[i][j] = -1;
			}
		}

		for (Span s : gold) {
			if(s.getRule().getType() == Type.UNARY)
				goldUnaryLabels[s.getStart()][s.getEnd()] = s.getRule().getLabel();
			else
				goldLabels[s.getStart()][s.getEnd()] = s.getRule().getLabel();
		}

		double ret = 0.0;
		for (Span s : pred) {
			if (s.getRule().getType() == Type.UNARY) {
				if (s.getRule().getLabel() != goldUnaryLabels[s.getStart()][s.getEnd()])
					ret += cost;
			}
			else {
				if (s.getRule().getLabel() != goldLabels[s.getStart()][s.getEnd()])
					ret += cost;
			}
		}
		return ret;
	}
	
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
