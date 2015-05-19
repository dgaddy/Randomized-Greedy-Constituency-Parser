package constituencyParser.unlabeled;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import constituencyParser.LabelEnumeration;
import constituencyParser.Pruning;
import constituencyParser.Rule;
import constituencyParser.RuleEnumeration;
import constituencyParser.Span;
import constituencyParser.Word;
import constituencyParser.WordEnumeration;
import constituencyParser.features.FirstOrderFeatureHolder;

/**
 * A sampler of parse trees based on DiscriminativeCKYDecoder
 */
public class DiscriminativeCKYSampler {
	private static final double PRUNE_THRESHOLD = 10;
	
	WordEnumeration wordEnum;
	LabelEnumeration labels;
	RuleEnumeration rules;
	
	FirstOrderFeatureHolder firstOrderFeatures;
	
	// stuff set by calculateProbabilities
	int wordsSize;
	List<Word> sentenceWords;
	double[][][] insideLogProbabilities; // unnormalized probabilities; start, end, label
	double [][] max;
	Pruning prune;
	
	boolean costAugmenting = false;
	boolean[][] goldLabels; // gold span info used for cost augmenting: indices are start and end, value is label, -1 if no span for a start and end
	
	public DiscriminativeCKYSampler(WordEnumeration words, LabelEnumeration labels, RuleEnumeration rules, FirstOrderFeatureHolder features) {
		this.wordEnum = words;
		this.labels = labels;
		this.rules = rules;
		
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
		int rulesSize = rules.getNumberOfBinaryRules();
		insideLogProbabilities = new double[wordsSize][wordsSize+1][labelsSize];
		for(int i = 0; i < wordsSize; i++)
			for(int j = 0; j < wordsSize+1; j++)
				for(int k = 0; k < labelsSize; k++)
					insideLogProbabilities[i][j][k] = Double.NEGATIVE_INFINITY;
		
		max = new double[wordsSize][wordsSize+1];
		for(int i = 0; i < wordsSize; i++) {
			max[i][i+1] = Double.NEGATIVE_INFINITY;
			for(int label : labels.getPOSLabels()) {
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
				if(p < max[i][i+1] - PRUNE_THRESHOLD) {
					prune.prune(i, i+1, l);
				}
			}
		}
		
		for(int length = 2; length < wordsSize + 1; length++) {
			for(int start = 0; start < wordsSize + 1 - length; start++) {
				int end = start + length;
				max[start][end] = Double.NEGATIVE_INFINITY;
				for(int split = 1; split < length; split++) {
					int splitLocation = start + split;
					
					double leftMax = max[start][splitLocation];
					double rightMax = max[splitLocation][end];
					
					if (leftMax + rightMax + PRUNE_THRESHOLD < max[start][end])
						continue;
					
					for(int r = 0; r < rulesSize; r++) {
						
						Rule rule = rules.getBinaryRule(r);
						
						int label = rule.getLabel();
						
						double leftChildProb = insideLogProbabilities[start][split][rule.getLeft()];
						double rightChildProb = insideLogProbabilities[split][end][rule.getRight()];
						
						if(leftChildProb + rightChildProb + PRUNE_THRESHOLD < max[start][end])
							continue;
						
						double probability = binaryProbability(start, start+length, start+split, r, rule);
						
						if(probability != Double.NEGATIVE_INFINITY) {
							double fullProbability = addProbabilitiesLog(insideLogProbabilities[start][start+length][label], probability);  
							insideLogProbabilities[start][start+length][label] = fullProbability;
							
							if(fullProbability > max[start][end]) {
								max[start][end] = fullProbability;
							}
						}
					}
				}
				
				for(int l = 0; l < labelsSize; l++) {
					double p = insideLogProbabilities[start][start+length][l];
					if(p < max[start][end] - PRUNE_THRESHOLD) {
						prune.prune(start, start+length, l);
					}
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
	private double binaryProbability(int start, int end, int split, int ruleNumber, Rule rule) {
		if(prune.isPruned(start, split, rule.getLeft()) || prune.isPruned(split, end, rule.getRight()))
			return Double.NEGATIVE_INFINITY;

		double leftChildProb = insideLogProbabilities[start][split][rule.getLeft()];
		double rightChildProb = insideLogProbabilities[split][end][rule.getRight()];
		
		double spanScore = firstOrderFeatures.scoreBinary(start, end, split, ruleNumber);
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
	
	public List<Span> sample() {
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
			return new ArrayList<>();
		}
		
		int chosenTopLabel = labelsToSample.get(sample(logProbabilities));
		
		List<Span> sample = new ArrayList<>();
		
		sample(0, wordsSize, chosenTopLabel, sample);
		
		return sample;
	}
	
	private void sample(int start, int end, int label, List<Span> resultAccumulator) {
		if(end - start == 1) { // terminals
			resultAccumulator.add(new Span(start, label));
			return;
		}
		else {
			// binary
			List<Double> logProbabilities = new ArrayList<>();
			List<Span> spans = new ArrayList<>();
			
			int numBinRules = rules.getNumberOfBinaryRules();
			for(int split = 1; split < end-start; split++) {
				for(int r = 0; r < numBinRules; r++) {
					Rule rule = rules.getBinaryRule(r);
					
					if(rule.getLabel() == label) {
						if(prune.isPruned(start, start+split, rule.getLeft()) || prune.isPruned(start+split, end, rule.getRight()))
							continue;
						
						double prob = binaryProbability(start, end, start+split, r, rule);
						if(prob == Double.NEGATIVE_INFINITY)
							continue;
						
						logProbabilities.add(prob);
						Span span = new Span(start, end, start+split, rule);
						spans.add(span);
					}
				}
			}
			
			int sampleIndex = sample(logProbabilities);
			Span span = spans.get(sampleIndex);
			resultAccumulator.add(span);
			sample(start, span.getSplit(), span.getRule().getLeft(), resultAccumulator);
			sample(span.getSplit(), end, span.getRule().getRight(), resultAccumulator);
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
}
