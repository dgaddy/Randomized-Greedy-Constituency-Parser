package constituencyParser.unlabeled;

import gnu.trove.map.hash.TLongDoubleHashMap;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import constituencyParser.Decoder;
import constituencyParser.LabelEnumeration;
import constituencyParser.RuleEnumeration;
import constituencyParser.Span;
import constituencyParser.SpanUtilities;
import constituencyParser.SpannedWords;
import constituencyParser.TrainOptions;
import constituencyParser.Word;
import constituencyParser.WordEnumeration;
import constituencyParser.Rule.Type;
import constituencyParser.features.FeatureParameters;

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
	
	public Train(WordEnumeration words, LabelEnumeration labels, RuleEnumeration rules, Decoder decoder, FeatureParameters parameters) {
		this.wordEnum = words;
		this.labels = labels;
		this.rules = rules;
		this.decoder = decoder;
		this.parameters = parameters;
	}
	
	public void train(List<SpannedWords> trainingExamples, TrainOptions options) {
		int totalLoss = 0;
		int index = 0;
		
		decoder.setSecondOrder(options.secondOrder);
		
		//parameters.resetDropout(dropout);
		System.out.println("Number of training examples: " + trainingExamples.size());
		while(index < trainingExamples.size()) {

			TLongDoubleHashMap features = new TLongDoubleHashMap();
			parameters.resetDropout(options.dropout);
			
			double batchPredictedScore = 0;
			double batchGoldScore = 0;
			
			for(int b = 0; b < options.batchSize && index < trainingExamples.size(); b++, index++) {
				if ((index + 1) % 10 == 0)
					System.out.print((index + 1) + "  ");
				
				SpannedWords sw = trainingExamples.get(index);
				//if(count % 5 == 0) {
					//System.out.println(count + " of " + trainingExamples.size() + "; Average loss: " + (totalLoss / (double) count));
				//}
				
				List<Word> words = sw.getWords();
				
				decoder.setCostAugmenting(options.costAugmenting, sw);
				List<Span> predicted;
				 
				if(parameters.getStoredFeatures().size() == 0) {
					predicted = new ArrayList<>(); // don't run decoder if no features, since it won't be doing anything useful anyway, and runs a lot slower
				}
				else {
					predicted = decoder.decode(words, parameters);
					// TODO fix
					//predicted = ((UnlabeledCKY)decoder).decodeWithPOS(sw.getWords(), SpanUtilities.getPOS(sw.getWords().size(), sw.getSpans()), parameters);
				}
				//System.out.println(predicted);
			
				int loss = computeLoss(predicted, sw.getSpans()); 
				totalLoss += loss;
				
				if(loss > 0) {
					List<Span> gold = sw.getSpans();
					
					// positive
					List<Long> goldFeatures = UnlabeledFeatures.getAllFeatures(gold, words, options.secondOrder, wordEnum, labels, rules, options.randGreedy);
					double goldScore = 0;
					for(Long code : goldFeatures) {
						goldScore += parameters.getScore(code);
					}
					
					if(goldScore > decoder.getLastScore()) {
						System.out.println("skipping param update because gold score higher");
						continue; // don't update score if gold score higher
					}
					
					for(Long code : goldFeatures) {
						features.adjustOrPutValue(code, -1.0, -1.0);
						goldFeatureCounts.adjustOrPutValue(code, 1.0, 1.0);
					}
					
					batchGoldScore += goldScore;
					batchPredictedScore += decoder.getLastScore();
					
					
					
					// negative
					List<Long> predictedFeatures = UnlabeledFeatures.getAllFeatures(predicted, words, options.secondOrder, wordEnum, labels, rules, options.randGreedy);
					
					/*List<List<List<Long>>> a = ((UnlabeledCKY)decoder).firstOrderFeatures.terminalProperties;
					UnlabeledFirstOrderFeatureHolder holder = ((UnlabeledCKY)decoder).firstOrderFeatures;
					UnlabeledCKY cky = (UnlabeledCKY)decoder;
					double total1 = 0;
					double total3 = 0;
					
					double scoreFromHolder = 0;
					for(Span s : predicted) {
						if(s.getRule().getType() == Type.TERMINAL) {
							scoreFromHolder += holder.scoreTerminal(s.getStart(), s.getRule().getLabel());
							
							List<Long> f = a.get(s.getStart()).get(s.getRule().getLabel());
							for(Long l : f) {
								if(!predictedFeatures.contains(l))
									throw new RuntimeException("" + l);
								System.out.println(l + " " + parameters.getScore(l));
								total1 += parameters.getScore(l);
							}
							
							total3 += holder.terminalScores[s.getStart()][s.getRule().getLabel()];
						}
						else {
							double spanScore = holder.scoreBinary(s.getStart(), s.getEnd(), s.getSplit(), s.getRule().getLeft(), s.getRule().getRight(), s.getRule().getLeftPropagateHead());
							scoreFromHolder += spanScore;
							double spanScore2 = cky.scores[s.getStart()][s.getEnd()][s.getRule().getLabel()]-cky.scores[s.getStart()][s.getSplit()][s.getRule().getLeft()]-cky.scores[s.getSplit()][s.getEnd()][s.getRule().getRight()];
							if(options.costAugmenting && !cky.goldSpans[s.getStart()][s.getEnd()])
								spanScore += 1;
							if(Math.abs(spanScore - spanScore2) > 1e-6)
								throw new RuntimeException(spanScore + " " + spanScore2 + " " + s.getRule() + " " + s.getRule().getLeftPropagateHead());
						}
					}
					System.out.println();*/
					
					double predictedScore = 0;
					for(Long code : predictedFeatures) {
						features.adjustOrPutValue(code, 1.0, 1.0);
						predictedScore += parameters.getScore(code);
						predictedFeatureCounts.adjustOrPutValue(code, 1.0, 1.0);
					}
					
					
					// used to double check scoring
					int augmentingScore = 0;
					if(options.costAugmenting) {
						for(Span s : predicted) {
							if(s.getRule().getType() == Type.UNARY)
								continue;
							
							boolean inGold = false;
							for(Span gs : gold) {
								if(gs.getStart() == s.getStart() && gs.getEnd() == s.getEnd()) {
									inGold = true;
								}
							}
							if(!inGold) {
								augmentingScore++;
							}
						}
					}
					//System.out.println(predictedScore + " " + (decoder.getLastScore() - augmentingScore));
					
					
					if(Math.abs(predictedScore + augmentingScore - decoder.getLastScore()) > 1e-4 && !Double.isInfinite(decoder.getLastScore())) {
						SpanUtilities.printSpans(predicted, sw.getWords().size(), labels);
						System.out.println("augmenting " + augmentingScore);
						throw new RuntimeException("" + index + " Decoder score and freshly calculated score don't match: " + (predictedScore + augmentingScore) + " " + decoder.getLastScore() + " " + (predictedScore + augmentingScore - decoder.getLastScore()));
					}
					
					/*if(goldScore > predictedScore + augmentingScore) {
						System.out.println("Warning: Gold score greater than predicted score, but decoder didn't find it");
						System.out.println("Gold score: " + goldScore + " predicted: " + predictedScore + " " + augmentingScore);
					}*/
				}
			}
			
			if(options.mira) {
				parameters.updateMIRA(features, batchPredictedScore - batchGoldScore);
			}
			else
				parameters.update(features);
		}
		//checkParameterSanity();
		
		decoder.setCostAugmenting(false, null);
		parameters.resetDropout(0);
		System.out.println("Finished; Average loss: " + (totalLoss / (double)trainingExamples.size()));
	}
	
	@SuppressWarnings("unused")
	private void checkParameterSanity() {
		try {
			FileWriter writer = new FileWriter("second_order_features");
			parameters.resetDropout(0);
			for(Long feature : parameters.getStoredFeatures()) {
				if(!UnlabeledFeatures.isSecondOrderFeature(feature))
					continue;
				
				double score = parameters.getScore(feature);
				double predictedCount = predictedFeatureCounts.get(feature);
				double goldCount = goldFeatureCounts.get(feature);
				writer.write(UnlabeledFeatures.getStringForCode(feature, wordEnum, rules, labels) + " " + score + " " + predictedCount + " " + goldCount);
				if(score > 1 && predictedCount > goldCount) {
					System.out.println("Feature " + UnlabeledFeatures.getStringForCode(feature, wordEnum, rules, labels) + " has positive score,");
					System.out.println("but it appears " + predictedCount + " in predicted and " + goldCount + " in gold");
				}
				if(score < -1 && goldCount > predictedCount) {
					System.out.println("Feature " + UnlabeledFeatures.getStringForCode(feature, wordEnum, rules, labels) + " has negative score,");
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