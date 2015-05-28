package constituencyParser.unlabeled;

import java.util.ArrayList;
import java.util.List;

import constituencyParser.Rule.Type;
import constituencyParser.*;
import constituencyParser.features.FeatureParameters;

/**
 * Labels are replaced by propagated POS labels
 */
public class LexicalizedUnlabeledCKY implements Decoder {
	WordEnumeration wordEnum;
	LabelEnumeration labels;
	
	double pruneThreshold;
	
	UnlabeledFirstOrderFeatureHolder firstOrderFeatures;
	
	double[][][][] scores; // start, stop, head, label
	Span[][][][] spans;
	
	List<Span> usedSpans;
	
	boolean costAugmenting;
	boolean[][] goldSpans;
	
	public LexicalizedUnlabeledCKY(WordEnumeration words, LabelEnumeration labels, double pruneThreshold) {
		this.wordEnum = words;
		this.labels = labels;
		this.pruneThreshold = pruneThreshold;
		
		firstOrderFeatures = new UnlabeledFirstOrderFeatureHolder(words, labels);
	}
	
	double lastScore = 0;
	
	public List<Span> decode(List<Word> words, FeatureParameters params) {
		firstOrderFeatures.fillScoreArrays(words, params);
		
		int wordsSize = words.size();
		int labelsSize = labels.getNumberOfLabels();
		
		scores = new double[wordsSize][wordsSize+1][wordsSize][labelsSize];
		for(int i = 0; i < wordsSize; i++)
			for(int j = 0; j < wordsSize+1; j++)
				for(int k = 0; k < wordsSize; k++)
					for(int l = 0; l < labelsSize; l++)
						scores[i][j][k][l] = Double.NEGATIVE_INFINITY;
		spans = new Span[wordsSize][wordsSize+1][wordsSize][labelsSize];
		
		
		List<List<Integer>> partsOfSpeechOptions = new ArrayList<>();
		for(int i = 0; i < wordsSize; i++) {
			List<Integer> poss = wordEnum.getPartsOfSpeech(words.get(i));
			partsOfSpeechOptions.add(poss);
			for(int label : poss) { // TODO decide if the pos speech thing helps
				Span span = new Span(i, label);
				span.setHeadWord(words.get(i).getId());
				double score = firstOrderFeatures.scoreTerminal(i, label);
				if(costAugmenting && !goldSpans[i][i+1])
					score += 1;
				scores[i][i+1][i][label] = score;
				spans[i][i+1][i][label] = span;
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
					
					for(int leftHead = start; leftHead < splitLocation; leftHead++) {
						for(int rightHead = splitLocation; rightHead < end; rightHead++) {
							double[] leftScore = scores[start][splitLocation][leftHead];
							double[] rightScore = scores[splitLocation][end][rightHead];

							for(boolean propLeft : new boolean[]{true, false}) {
								for(int leftLabel : partsOfSpeechOptions.get(leftHead)) {
									double leftChildScore = leftScore[leftLabel];
									if(leftChildScore + pruneThreshold < leftMax)
										continue;
									
									for(int rightLabel : partsOfSpeechOptions.get(rightHead)) {
										double rightChildScore = rightScore[rightLabel];
										if(rightChildScore + pruneThreshold < rightMax
												|| leftChildScore + rightChildScore + pruneThreshold < max[start][end])
											continue;
										
										int label = propLeft ? leftLabel : rightLabel;
										int childLabel = propLeft ? rightLabel : leftLabel;
										int head = propLeft ? leftHead : rightHead;
										int child = propLeft ? rightHead : leftHead;
										
										double spanScore = firstOrderFeatures.scoreBinary(start, end, splitLocation, leftLabel, rightLabel, propLeft);
										double depScore = firstOrderFeatures.scoreDependencyFeature(head, child, label, childLabel);
										spanScore += depScore;
										
										if(costAugmenting && !goldSpans[start][end])
											spanScore += 1;
										
										double fullScore = spanScore + leftChildScore + rightChildScore;
										if(fullScore > scores[start][end][head][label]) {
											scores[start][end][head][label] = fullScore;
											Span span = new Span(start, end, splitLocation, new Rule(label, leftLabel, rightLabel, propLeft));
											span.setHeadWord(words.get(head).getId());
											span.setLeft(spans[start][splitLocation][leftHead][leftLabel]);
											span.setRight(spans[splitLocation][end][rightHead][rightLabel]);
											spans[start][end][head][label] = span;
											
											if(fullScore > max[start][end]) {
												max[start][end] = fullScore;
											}
										}
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
		for(int head = 0; head < wordsSize; head++) {
			for(int topLabel = 0; topLabel < labelsSize; topLabel++) {
				double score = scores[0][wordsSize][head][topLabel];
				if(score > bestScore) {
					bestScore = score;
					bestSpan = spans[0][wordsSize][head][topLabel];
				}
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
