package constituencyParser.unlabeled;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import constituencyParser.LabelEnumeration;
import constituencyParser.Pruning;
import constituencyParser.Word;
import constituencyParser.WordEnumeration;

/**
 * A sampler of parse trees based on DiscriminativeCKYDecoder
 */
public class DiscriminativeCKYSampler {
	WordEnumeration wordEnum;
	LabelEnumeration labels;
	
	private double pruneThreshold;
	
	UnlabeledFirstOrderFeatureHolder firstOrderFeatures;
	
	// stuff set by calculateProbabilities
	int wordsSize;
	List<Word> sentenceWords;
	double[][][] insideLogProbabilities; // unnormalized probabilities; start, end, label
	double [][] max;
	Pruning prune;
	
	boolean costAugmenting = false;
	boolean[][] goldLabels; // gold span info used for cost augmenting: indices are start and end, value is label, -1 if no span for a start and end
	
	public DiscriminativeCKYSampler(WordEnumeration words, LabelEnumeration labels, UnlabeledFirstOrderFeatureHolder features, double pruneThreshold) {
		this.wordEnum = words;
		this.labels = labels;
		this.pruneThreshold = pruneThreshold;
		
		firstOrderFeatures = features;
	}
	
	/**
	 * 
	 * @param costAugmenting
	 * @param gold gold span info used for cost augmenting: indices are start and end, value is true if gold has a span here
	 */
	public void setCostAugmenting(boolean costAugmenting, boolean[][] gold) {
		this.costAugmenting = costAugmenting;
		this.goldLabels = gold;
	}
	
	public Pruning calculateProbabilities(List<Word> words) {
		prune = new Pruning(words.size(), labels.getNumberOfLabels());
		sentenceWords = words;
		wordsSize = words.size();
		int labelsSize = labels.getNumberOfLabels();
		insideLogProbabilities = new double[wordsSize][wordsSize+1][labelsSize];
		for(int i = 0; i < wordsSize; i++)
			for(int j = 0; j < wordsSize+1; j++)
				for(int k = 0; k < labelsSize; k++)
					insideLogProbabilities[i][j][k] = Double.NEGATIVE_INFINITY;
		
		max = new double[wordsSize][wordsSize+1];
		for(int i = 0; i < wordsSize; i++) {
			max[i][i+1] = Double.NEGATIVE_INFINITY;
			for(int label : wordEnum.getPartsOfSpeech(words.get(i))) {
				double spanScore = firstOrderFeatures.scoreTerminal(i, label);
				if(costAugmenting && !goldLabels[i][i+1]) {
					spanScore += 1;
				}
				
				insideLogProbabilities[i][i+1][label] = spanScore;
				
				if(spanScore > max[i][i+1])
					max[i][i+1] = spanScore;
			}
			
			for(int l = 0; l < labelsSize; l++) {
				double p = insideLogProbabilities[i][i+1][l];
				if(p < max[i][i+1] - pruneThreshold) {
					prune.prune(i, i+1, l);
				}
			}
		}
		
		int[][] uses = new int[wordsSize][wordsSize+1];
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
					
					double[] leftScore = insideLogProbabilities[start][splitLocation];
					double[] rightScore = insideLogProbabilities[splitLocation][end];
					
					for(boolean leftHead : new boolean[]{true, false}) {
						for(int leftLabel = 0; leftLabel < labelsSize; leftLabel++) {
							double leftChildProb = leftScore[leftLabel];
							if(leftChildProb + pruneThreshold < leftMax)
								continue;
							
							for(int rightLabel = 0; rightLabel < labelsSize; rightLabel++) {
								double rightChildProb = rightScore[rightLabel];
								
								if(rightChildProb + pruneThreshold < rightMax
										|| leftChildProb + rightChildProb + pruneThreshold < max[start][end])
									continue;
								
								int label = leftHead ? leftLabel : rightLabel;
								
								double probability = binaryProbability(start, start+length, start+split, leftLabel, rightLabel, leftHead);
								
								if(probability != Double.NEGATIVE_INFINITY) {
									uses[start][splitLocation]++;
									uses[splitLocation][end]++;
									double fullProbability = addProbabilitiesLog(insideLogProbabilities[start][start+length][label], probability);  
									insideLogProbabilities[start][start+length][label] = fullProbability;
									
									if(fullProbability > max[start][end]) {
										max[start][end] = fullProbability;
									}
								}
							}
						}
					}
				}
				
				for(int l = 0; l < labelsSize; l++) {
					double p = insideLogProbabilities[start][start+length][l];
					if(p < max[start][end] - pruneThreshold) { // TODO does this need to change?
						prune.prune(start, start+length, l);
					}
				}
			}
		}
		
		// TODO see if this pruning works
		/*int[][] uses = new int[wordsSize][wordsSize+1];
		for(int length = 2; length < wordsSize + 1; length++) {
			for(int start = 0; start < wordsSize + 1 - length; start++) {
				int end = start + length;
				for(int split = 1; split < length; split++) {
					int splitLocation = start + split;
					
					double leftMax = max[start][splitLocation];
					double rightMax = max[splitLocation][end];
					
					if (leftMax + rightMax + pruneThreshold < max[start][end]) {
						uses[start][splitLocation]++;
						uses[splitLocation][end]++;
					}
				}
			}
		}*/
		for(int length = 2; length < wordsSize; length++) {
			for(int start = 0; start < wordsSize + 1 - length; start++) {
				int end = start + length;
				if(uses[start][end] == 0) {
					for(int label = 0; label < labelsSize; label++)
						prune.prune(start, end, label);
				}
			}
		}
		
		return prune;
	}
	
	/**
	 * returns ln(exp(x)+exp(y))
	 * @param logProb1
	 * @param logProb2
	 * @return
	 */
	private double addProbabilitiesLog(double x, double y) {
		if(x == Double.NEGATIVE_INFINITY)
			return y;
		if(y == Double.NEGATIVE_INFINITY)
			return x;
		
		double larger;
		double smaller;
		if(x > y) {
			larger = x;
			smaller = y;
		}
		else {
			larger = y;
			smaller = x;
		}
		
		return larger + Math.log(1 + Math.exp(smaller - larger));
	}
	
	/**
	 * 
	 * @param properties
	 * @param start
	 * @param end
	 * @param split absolute position
	 * @param ruleNumber
	 * @param rule
	 * @return
	 */
	private double binaryProbability(int start, int end, int split, int leftLabel, int rightLabel, boolean propagateLeft) {
		if(prune.isPruned(start, split, leftLabel) || prune.isPruned(split, end, rightLabel))
			return Double.NEGATIVE_INFINITY;

		double leftChildProb = insideLogProbabilities[start][split][leftLabel];
		double rightChildProb = insideLogProbabilities[split][end][rightLabel];
		
		double spanScore = firstOrderFeatures.scoreBinary(start, end, split, leftLabel, rightLabel, propagateLeft);
		if(costAugmenting && !goldLabels[start][end]) {
			spanScore += 1;
		}
		
		double probability = spanScore + leftChildProb + rightChildProb;
		return probability;
	}
	
	public void printProbs() {
		for(int topLabel = 0; topLabel < labels.getNumberOfLabels(); topLabel++) {
			double score = insideLogProbabilities[0][wordsSize][topLabel];
			
			System.out.println(labels.getLabel(topLabel) + " " + score);
		}
	}
	
	public ConstituencyNode sample() {
		List<Double> logProbabilities = new ArrayList<>();
		List<Integer> labelsToSample = new ArrayList<>();
		for(int topLabel = 0; topLabel < labels.getNumberOfLabels(); topLabel++) {
			if(prune.isPruned(0, wordsSize, topLabel))
				continue;
			
			double score = insideLogProbabilities[0][wordsSize][topLabel];
			if(score == Double.NEGATIVE_INFINITY)
				continue;
			
			logProbabilities.add(score);
			labelsToSample.add(topLabel);
		}
		
		if(logProbabilities.size() == 0) {
			System.out.println("top level fail");
			return null;
		}
		
		int chosenTopLabel = labelsToSample.get(sample(logProbabilities));
		
		ConstituencyNode sample = new ConstituencyNode();
		sample.label = chosenTopLabel;
		sample.start = 0;
		sample.end = wordsSize;
		
		sample(sample);
		
		return sample;
	}
	
	private static class Option {
		boolean propagateLeft;
		int otherLabel;
		int splitLocation;
		public Option(boolean p, int o, int s) {
			this.propagateLeft = p;
			this.otherLabel = o;
			this.splitLocation = s;
		}
	}
	
	private void sample(ConstituencyNode resultAccumulator) {
		int label = resultAccumulator.label;
		int start = resultAccumulator.start;
		int end = resultAccumulator.end;
		if(end - start == 1) { // terminals
			resultAccumulator.index = start;
			resultAccumulator.terminal = true;
			return;
		}
		else {
			// binary
			List<Double> logProbabilities = new ArrayList<>();
			List<Option> options = new ArrayList<>();
			
			int labelsSize = labels.getNumberOfLabels();
			for(int split = 1; split < end-start; split++) {
				// left propagating - iterate over right label
				for(int rightLabel = 0; rightLabel < labelsSize; rightLabel++) {
					double prob = binaryProbability(start, end, start+split, label, rightLabel, true);
					if(prob == Double.NEGATIVE_INFINITY)
						continue;
					
					logProbabilities.add(prob);
					Option option = new Option(true, rightLabel, start+split);
					options.add(option);
				}
				
				// right propagating = iterate over left label
				for(int leftLabel = 0; leftLabel < labelsSize; leftLabel++) {
					double prob = binaryProbability(start, end, start+split, leftLabel, label, false);
					if(prob == Double.NEGATIVE_INFINITY)
						continue;
					
					logProbabilities.add(prob);
					Option option = new Option(false, leftLabel, start+split);
					options.add(option);
				}
			}
			
			int sampleIndex = sample(logProbabilities);
			Option chosen = options.get(sampleIndex);
			ConstituencyNode left = new ConstituencyNode();
			left.start = start;
			left.end = chosen.splitLocation;
			left.parent = resultAccumulator;
			resultAccumulator.left = left;
			ConstituencyNode right = new ConstituencyNode();
			right.start = chosen.splitLocation;
			right.end = end;
			right.parent = resultAccumulator;
			resultAccumulator.right = right;
			if(chosen.propagateLeft) {
				left.label = label;
				right.label = chosen.otherLabel;
			}
			else {
				left.label = chosen.otherLabel;
				right.label = label;
			}
			sample(left);
			sample(right);
		}
	}
	
	/**
	 * Samples a set of things based on log probabilities
	 * @param cumulativeValues
	 * @param cumulative
	 * @return the index of the sampled item
	 */
	private int sample(List<Double> logProbabilities) {
		double maxLogP = Collections.max(logProbabilities);
		double sumExp = 0;
		for(double logP : logProbabilities) {
			sumExp += Math.exp(logP - maxLogP);
		}
		double logSumProbabilities = maxLogP + Math.log(sumExp);
		
		double cumulative = 0;
		List<Double> cumulativeProbabilities = new ArrayList<>();
		for(double logP : logProbabilities) {
			double prob = Math.exp(logP - logSumProbabilities);
			cumulative += prob;
			cumulativeProbabilities.add(cumulative);
		}
		
		double sample = Math.random();
		int sampleIndex = Collections.binarySearch(cumulativeProbabilities, sample);
		if(sampleIndex < 0) { // usually true, this is what binarySearch does when it does not find the exact value
			sampleIndex = -(sampleIndex + 1);
		}
		if(sampleIndex > logProbabilities.size()) {
			// probably a rounding error
			System.out.println("Warning: rounding error");
			sampleIndex = logProbabilities.size() - 1;
		}
		
		return sampleIndex;
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
