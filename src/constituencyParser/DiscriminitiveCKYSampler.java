package constituencyParser;

import gnu.trove.list.TLongList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import constituencyParser.Rule.Type;
import constituencyParser.features.FeatureParameters;
import constituencyParser.features.Features;
import constituencyParser.features.SpanProperties;

public class DiscriminitiveCKYSampler {
	private static final double PRUNE_THRESHOLD = 10;
	
	WordEnumeration wordEnum;
	LabelEnumeration labels;
	Rules rules;
	
	// stuff set by calculateProbabilities
	int wordsSize;
	List<Integer> sentenceWords;
	FeatureParameters parameters;
	double[][][] insideLogProbabilitiesBeforeUnaries; // unnormalized probabilities; start, end, label
	double[][][] insideLogProbabilitiesAfterUnaries;
	double[][] maxBeforeUnaries; // used for pruning
	
	boolean costAugmenting = false;
	int[][] goldLabels; // gold span info used for cost augmenting: indices are start and end, value is label, -1 if no span for a start and end
	
	public DiscriminitiveCKYSampler(WordEnumeration words, LabelEnumeration labels, Rules rules) {
		this.wordEnum = words;
		this.labels = labels;
		this.rules = rules;
	}
	
	/**
	 * 
	 * @param costAugmenting
	 * @param gold gold span info used for cost augmenting: indices are start and end, value is label, -1 if no span for a start and end
	 */
	public void setCostAugmenting(boolean costAugmenting, int[][] gold) {
		this.costAugmenting = costAugmenting;
		this.goldLabels = gold;
	}
	
	public void calculateProbabilities(List<Integer> words, FeatureParameters params) {
		parameters = params;
		sentenceWords = words;
		wordsSize = words.size();
		int labelsSize = labels.getNumberOfLabels();
		int rulesSize = rules.getNumberOfBinaryRules();
		insideLogProbabilitiesBeforeUnaries = new double[wordsSize][wordsSize+1][labelsSize];
		insideLogProbabilitiesAfterUnaries = new double[wordsSize][wordsSize+1][labelsSize];
		for(int i = 0; i < wordsSize; i++)
			for(int j = 0; j < wordsSize+1; j++)
				for(int k = 0; k < labelsSize; k++)
					insideLogProbabilitiesBeforeUnaries[i][j][k] = Double.NEGATIVE_INFINITY;
		
		for(int i = 0; i < wordsSize; i++) {
			TLongList spanProperties = SpanProperties.getTerminalSpanProperties(words, i, wordEnum);
			for(int label = 0; label < labelsSize; label++) {
				double spanScore = 0;
				final long ruleCode = Rules.getTerminalRuleCode(label);
				for(int p = 0; p < spanProperties.size(); p++) {
					spanScore += params.getScore(Features.getSpanPropertyByRuleFeature(spanProperties.get(p), ruleCode), false);
				}
				spanScore += params.getScore(Features.getRuleFeature(ruleCode), false);
				
				if(costAugmenting && goldLabels[i][i+1] != label) {
					spanScore += 1;
				}
				
				insideLogProbabilitiesBeforeUnaries[i][i+1][label] = spanScore;
			}
			
			doUnaryProbabilities(words, i, i+1);
		}
		
		maxBeforeUnaries = new double[wordsSize][wordsSize+1];
		for(int length = 2; length < wordsSize + 1; length++) {
			for(int start = 0; start < wordsSize + 1 - length; start++) {
				maxBeforeUnaries[start][start+length] = 0;
				for(int split = 1; split < length; split++) {
					TLongList spanProperties = SpanProperties.getBinarySpanProperties(words, start, start + length, start + split);
					for(int r = 0; r < rulesSize; r++) {
						
						Rule rule = rules.getBinaryRule(r);
						
						int label = rule.getParent();
						
						double probability = binaryProbability(spanProperties, start, start+length, start+split, r, rule);
						
						double fullProbability = addProbabilitiesLog(insideLogProbabilitiesBeforeUnaries[start][start+length][label], probability);  
						insideLogProbabilitiesBeforeUnaries[start][start+length][label] = fullProbability;
						
						if(fullProbability > maxBeforeUnaries[start][start+length]) {
							maxBeforeUnaries[start][start+length] = fullProbability;
						}
					}
				}
				
				doUnaryProbabilities(words, start, start + length);
			}
		}
	}
	
	/**
	 * returns ln(exp(x)+exp(y))
	 * @param logProb1
	 * @param logProb2
	 * @return
	 */
	private double addProbabilitiesLog(double x, double y) {
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
	
	private double unaryProbability(TLongList properties, int start, int end, int ruleNumber, Rule rule) {
		double childProb = insideLogProbabilitiesBeforeUnaries[start][end][rule.getLeft()];
		double spanScore = 0;
		final long ruleCode = Rules.getRuleCode(ruleNumber, Type.UNARY);
		for(int p = 0; p < properties.size(); p++) {
			spanScore += parameters.getScore(Features.getSpanPropertyByRuleFeature(properties.get(p), ruleCode), false);
			spanScore += parameters.getScore(Features.getSpanPropertyByLabelFeature(properties.get(p), rule.getParent()), false);
		}
		spanScore += parameters.getScore(Features.getRuleFeature(ruleCode), false);
		
		if(costAugmenting && goldLabels[start][end] != rule.getParent()) {
			spanScore += 1;
		}
		
		double probability = spanScore + childProb;
		return probability;
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
	private double binaryProbability(TLongList spanProperties, int start, int end, int split, int ruleNumber, Rule rule) {
		double leftChildProb = insideLogProbabilitiesAfterUnaries[start][split][rule.getLeft()];
		double rightChildProb = insideLogProbabilitiesAfterUnaries[split][end][rule.getRight()];
		if(leftChildProb < maxBeforeUnaries[start][split] - PRUNE_THRESHOLD || rightChildProb < maxBeforeUnaries[split][end] - PRUNE_THRESHOLD)
			return 0;
		
		double spanScore =  0;
		final long ruleCode = Rules.getRuleCode(ruleNumber, Type.BINARY);
		for(int p = 0; p < spanProperties.size(); p++) {
			spanScore += parameters.getScore(Features.getSpanPropertyByRuleFeature(spanProperties.get(p), ruleCode), false);
			spanScore += parameters.getScore(Features.getSpanPropertyByLabelFeature(spanProperties.get(p), rule.getParent()), false);
		}
		spanScore += parameters.getScore(Features.getRuleFeature(ruleCode), false);
		
		if(costAugmenting && goldLabels[start][end] != rule.getParent()) {
			spanScore += 1;
		}
		
		double probability = spanScore + leftChildProb + rightChildProb;
		return probability;
	}
	
	private void doUnaryProbabilities(List<Integer> words, int start, int end) {
		int numUnaryRules = rules.getNumberOfUnaryRules();
		TLongList properties = SpanProperties.getUnarySpanProperties(words, start, end);
		
		double[] probs = insideLogProbabilitiesAfterUnaries[start][end];
		double[] oldProbs = insideLogProbabilitiesBeforeUnaries[start][end];
		for(int i = 0; i < probs.length; i++) {
			probs[i] = oldProbs[i];
		}
		
		for(int i = 0; i < numUnaryRules; i++) {
			Rule rule = rules.getUnaryRule(i);
			
			double probability = unaryProbability(properties, start, end, i, rule);
			
			double fullProb = addProbabilitiesLog(probability, insideLogProbabilitiesAfterUnaries[start][end][rule.getParent()]);
			insideLogProbabilitiesAfterUnaries[start][end][rule.getParent()] = fullProb;
		}
	}
	
	public void printProbs() {
		for(Integer topLabel : labels.getTopLevelLabelIds()) {
			double score = insideLogProbabilitiesAfterUnaries[0][wordsSize][topLabel];
			
			System.out.println(labels.getLabel(topLabel) + " " + score);
		}
	}
	
	public List<Span> sample() {
		List<Span> sample = null;
		boolean success = false;
		int count = 0;
		while(!success) {
			try {
				List<Double> logProbabilities = new ArrayList<>();
				List<Integer> labelsToSample = new ArrayList<>();
				for(Integer topLabel : labels.getTopLevelLabelIds()) {
					double score = insideLogProbabilitiesAfterUnaries[0][wordsSize][topLabel];
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
				
				sample = new ArrayList<>();
				
				sample(0, wordsSize, chosenTopLabel, true, sample);
				success = true;
			}
			catch(NoOptionsException ex) {
				count++;
				if(count > 50) {
					System.out.println("Sampler not finding any valid options.");
					return new ArrayList<>();
				}
			}
		}
		
		return sample;
	}
	
	private void sample(int start, int end, int label, boolean allowUnaries, List<Span> resultAccumulator) throws NoOptionsException {
		//double[][] probabilities = ruleProbabilities[start][end];
		//double [] unaryProbabilities = unaryRuleProbabilities[start][end];
		List<Double> logProbabilities = new ArrayList<>();
		List<Span> spans = new ArrayList<>();
		if(allowUnaries) {
			int numUnaryRules = rules.getNumberOfUnaryRules();
			TLongList properties = SpanProperties.getUnarySpanProperties(sentenceWords, start, end);
			
			for(int r = 0; r < numUnaryRules; r++) {
				Rule rule = rules.getUnaryRule(r);
				if(rule.getParent() == label) {
					double prob = unaryProbability(properties, start, end, r, rule);
					if(prob == Double.NEGATIVE_INFINITY)
						continue;
					
					logProbabilities.add(prob);
					Span span = new Span(start, end, rule);
					spans.add(span);
				}
			}
		}
		
		if(end - start == 1) { // terminals
			double prob = insideLogProbabilitiesBeforeUnaries[start][end][label];
			if(prob != Double.NEGATIVE_INFINITY) {
				logProbabilities.add(prob);
				spans.add(new Span(start, label));
			}
		}
		else {
			// binary
			int numBinRules = rules.getNumberOfBinaryRules();
			for(int split = 1; split < end-start; split++) {
				TLongList spanProperties = SpanProperties.getBinarySpanProperties(sentenceWords, start, end, start + split);
				for(int r = 0; r < numBinRules; r++) {
					Rule rule = rules.getBinaryRule(r);
					
					if(rule.getParent() == label) {
						double prob = binaryProbability(spanProperties, start, end, start+split, r, rule);
						if(prob == Double.NEGATIVE_INFINITY)
							continue;
						
						logProbabilities.add(prob);
						Span span = new Span(start, end, start+split, rule);
						spans.add(span);
					}
				}
			}
		}
		
		//System.out.println(cumulativeValues);
		//System.out.println(spans);
		
		if(logProbabilities.size() == 0) {
			System.out.println("no options s:"+start+",e:"+end+",l:"+label+",u:"+allowUnaries+",r:"+resultAccumulator+",s:");
			for(Integer i : sentenceWords) {
				System.out.print(wordEnum.getWord(i));
				System.out.print(" ");
			}
			throw new NoOptionsException(label);
		}
		
		Span span = spans.get(sample(logProbabilities));
		resultAccumulator.add(span);
		if(span.getRule().getType() == Type.UNARY) {
			sample(start, end, span.getRule().getLeft(), false, resultAccumulator);
		}
		else if(end - start == 1)
			return;
		else {
			sample(start, span.getSplit(), span.getRule().getLeft(), true, resultAccumulator);
			sample(span.getSplit(), end, span.getRule().getRight(), true, resultAccumulator);
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
	
	/**
	 * Thrown when a sample was made that leads to a dead end - a label for a constituent where we can't put a rule under it
	 */
	private class NoOptionsException extends Exception {
		private static final long serialVersionUID = 1L;

		public NoOptionsException(int label) {
			super("No options with label " + label);
		}
	}
}
