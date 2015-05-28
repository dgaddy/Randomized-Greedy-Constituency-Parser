package constituencyParser.unlabeled;

import java.util.ArrayList;
import java.util.List;

import constituencyParser.Rule.Type;
import constituencyParser.*;
import constituencyParser.features.FeatureParameters;

/**
 * Labels are replaced by propagated POS labels
 */
public class UnlabeledCKY implements Decoder {
	WordEnumeration wordEnum;
	LabelEnumeration labels;
	
	private double pruneThreshold;
	
	UnlabeledFirstOrderFeatureHolder firstOrderFeatures;
	
	BinaryHeadPropagation propagation;
	
	double[][][] scores;
	Span[][][] spans;
	
	List<Span> usedSpans;
	
	boolean costAugmenting;
	boolean[][] goldSpans;
	
	public UnlabeledCKY(WordEnumeration words, LabelEnumeration labels, double pruneThreshold, BinaryHeadPropagation prop) {
		this.wordEnum = words;
		this.labels = labels;
		this.pruneThreshold = pruneThreshold;
		this.propagation = prop;
		
		firstOrderFeatures = new UnlabeledFirstOrderFeatureHolder(words, labels);
	}
	
	double lastScore = 0;
	
	public List<Span> decode(List<Word> words, FeatureParameters params) {
		firstOrderFeatures.fillScoreArrays(words, params);
		
		int wordsSize = words.size();
		int labelsSize = labels.getNumberOfLabels();
		
		scores = new double[wordsSize][wordsSize+1][labelsSize];
		for(int i = 0; i < wordsSize; i++)
			for(int j = 0; j < wordsSize+1; j++)
				for(int k = 0; k < labelsSize; k++)
					scores[i][j][k] = Double.NEGATIVE_INFINITY;
		spans = new Span[wordsSize][wordsSize+1][labelsSize];
		
		for(int i = 0; i < wordsSize; i++) {
			for(int label : wordEnum.getPartsOfSpeech(words.get(i))) { // TODO decide if the pos speech thing helps
				Span span = new Span(i, label);
				double score = firstOrderFeatures.scoreTerminal(i, label);
				if(costAugmenting && !goldSpans[i][i+1])
					score += 1;
				scores[i][i+1][label] = score;
				spans[i][i+1][label] = span;
			}
			
			//doUnary(words, i, i+1, Double.POSITIVE_INFINITY, params);
		}
		
		double[][] max = new double[wordsSize][wordsSize+1];
		for(int length = 2; length < wordsSize + 1; length++) {
			for(int start = 0; start < wordsSize + 1 - length; start++) {
				int end = start + length;
				max[start][end] = Double.NEGATIVE_INFINITY;
				
				for(int split = 1; split < length; split++) {
					int splitLocation = start + split;
					
					double leftMax = max[start][splitLocation];
					double rightMax = max[splitLocation][end];
					
					if (leftMax + rightMax + pruneThreshold < max[start][end])
						continue;
					
					double[] leftScore = scores[start][splitLocation];
					double[] rightScore = scores[splitLocation][end];

					for(boolean leftHead : new boolean[]{true, false}) {
						for(int leftLabel = 0; leftLabel < labelsSize; leftLabel++) {
							double leftChildScore = leftScore[leftLabel];
							if(leftChildScore + pruneThreshold < leftMax)
								continue;
							
							for(int rightLabel = 0; rightLabel < labelsSize; rightLabel++) {
								double rightChildScore = rightScore[rightLabel];
								//boolean leftHead = propagation.getPropagateLeft(leftLabel, rightLabel);
								if(rightChildScore + pruneThreshold < rightMax
										|| leftChildScore + rightChildScore + pruneThreshold < max[start][end])
									continue;
								
								int label = leftHead ? leftLabel : rightLabel;
								
								double spanScore = firstOrderFeatures.scoreBinary(start, end, splitLocation, leftLabel, rightLabel, leftHead);
								
								if(costAugmenting && !goldSpans[start][end])
									spanScore += 1;
								
								double fullScore = spanScore + leftChildScore + rightChildScore;
								if(fullScore > scores[start][end][label]) {
									scores[start][end][label] = fullScore;
									Span span = new Span(start, end, splitLocation, new Rule(label, leftLabel, rightLabel, leftHead));
									span.setLeft(spans[start][splitLocation][leftLabel]);
									span.setRight(spans[splitLocation][end][rightLabel]);
									spans[start][end][label] = span;
									
									if(fullScore > max[start][end]) {
										max[start][end] = fullScore;
									}
								}
							}
						}
					}
				}

				//doUnary(words, start, end, max[start][end], params);
			}
		}
		
		double bestScore = Double.NEGATIVE_INFINITY;
		Span bestSpan = null;
		for(int topLabel = 0; topLabel < labelsSize; topLabel++) {
			double score = scores[0][wordsSize][topLabel];
			if(score > bestScore) {
				bestScore = score;
				bestSpan = spans[0][wordsSize][topLabel];
			}
		}
		
		usedSpans = new ArrayList<>();
		if(bestSpan != null)
			getUsedSpans(bestSpan);
		
		lastScore = bestScore;
		
		return usedSpans;
	}
	
	public List<Span> decodeWithPOS(List<Word> words, List<Integer> pos, FeatureParameters params) {
		firstOrderFeatures.fillScoreArrays(words, params);
		
		int wordsSize = pos.size();
		int labelsSize = labels.getNumberOfLabels();
		
		scores = new double[wordsSize][wordsSize+1][labelsSize];
		for(int i = 0; i < wordsSize; i++)
			for(int j = 0; j < wordsSize+1; j++)
				for(int k = 0; k < labelsSize; k++)
					scores[i][j][k] = Double.NEGATIVE_INFINITY;
		spans = new Span[wordsSize][wordsSize+1][labelsSize];
		
		for(int i = 0; i < wordsSize; i++) {
			int label = pos.get(i);
			Span span = new Span(i, label);
			double score = firstOrderFeatures.scoreTerminal(i, label);
			scores[i][i+1][label] = score;
			spans[i][i+1][label] = span;
			
			//doUnary(words, i, i+1, Double.POSITIVE_INFINITY, params);
		}
		
		double[][] max = new double[wordsSize][wordsSize+1];
		for(int length = 2; length < wordsSize + 1; length++) {
			for(int start = 0; start < wordsSize + 1 - length; start++) {
				int end = start + length;
				max[start][end] = Double.NEGATIVE_INFINITY;
				
				for(int split = 1; split < length; split++) {
					int splitLocation = start + split;
					
					double leftMax = max[start][splitLocation];
					double rightMax = max[splitLocation][end];
					
					if (leftMax + rightMax + pruneThreshold < max[start][end])
						continue;
					
					double[] leftScore = scores[start][splitLocation];
					double[] rightScore = scores[splitLocation][end];

					for(boolean leftHead : new boolean[]{true, false}) {
						for(int leftLabel = 0; leftLabel < labelsSize; leftLabel++) {
							double leftChildScore = leftScore[leftLabel];
							if(leftChildScore + pruneThreshold < leftMax)
								continue;
							
							for(int rightLabel = 0; rightLabel < labelsSize; rightLabel++) {
								double rightChildScore = rightScore[rightLabel];
								if(rightChildScore + pruneThreshold < rightMax
										|| leftChildScore + rightChildScore + pruneThreshold < max[start][end])
									continue;
								
								int label = leftHead ? leftLabel : rightLabel;
								
								double spanScore = firstOrderFeatures.scoreBinary(start, end, splitLocation, leftLabel, rightLabel, leftHead);
								
								if(costAugmenting && !goldSpans[start][end])
									spanScore += 1;
								
								double fullScore = spanScore + leftChildScore + rightChildScore;
								if(fullScore > scores[start][end][label]) {
									scores[start][end][label] = fullScore;
									Span span = new Span(start, end, splitLocation, new Rule(label, leftLabel, rightLabel, leftHead));
									span.setLeft(spans[start][splitLocation][leftLabel]);
									span.setRight(spans[splitLocation][end][rightLabel]);
									spans[start][end][label] = span;
									
									if(fullScore > max[start][end]) {
										max[start][end] = fullScore;
									}
								}
							}
						}
					}
				}

				//doUnary(words, start, end, max[start][end], params);
			}
		}
		
		double bestScore = Double.NEGATIVE_INFINITY;
		Span bestSpan = null;
		for(int topLabel = 0; topLabel < labelsSize; topLabel++) {
			double score = scores[0][wordsSize][topLabel];
			if(score > bestScore) {
				bestScore = score;
				bestSpan = spans[0][wordsSize][topLabel];
			}
		}
		
		usedSpans = new ArrayList<>();
		if(bestSpan != null)
			getUsedSpans(bestSpan);
		
		lastScore = bestScore;
		
		return usedSpans;
	}
	
	public double getLastScore() {
		return lastScore;
	}
	
	private void getUsedSpans(Span span) {
		usedSpans.add(span);
		if(span.getRule().getType() == Rule.Type.BINARY) {
			getUsedSpans(span.getLeft());
			getUsedSpans(span.getRight());
		}
	}

	public void setCostAugmenting(boolean costAugmenting, SpannedWords gold) {
		this.costAugmenting = costAugmenting;
		if(!costAugmenting)
			return;
		int size = gold.getWords().size();
		goldSpans = new boolean[size][size+1];
		for(int i = 0; i < size; i++) {
			for(int j = 0; j < size+1; j++) {
				goldSpans[i][j] = false;
			}
		}
		
		for(Span s : gold.getSpans()) {
			if(s.getRule().getType() != Type.UNARY)
				goldSpans[s.getStart()][s.getEnd()] = true;
		}
	}

	@Override
	public void setSecondOrder(boolean secondOrder) {
		if(secondOrder)
			throw new UnsupportedOperationException();
	}
	
	public double increasePruneThreshold() {
		pruneThreshold *= 1.5;
		return pruneThreshold;
	}
	
	public double decreasePruneThreshold() {
		pruneThreshold -= .001;
		return pruneThreshold;
	}
}
