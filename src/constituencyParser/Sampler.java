package constituencyParser;

import gnu.trove.list.TLongList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import constituencyParser.Rule.Type;
import constituencyParser.features.FeatureParameters;
import constituencyParser.features.Features;
import constituencyParser.features.SpanProperties;

public class Sampler {
	private static final double PRUNE_THRESHOLD = .01;
	
	WordEnumeration wordEnum;
	LabelEnumeration labels;
	Rules rules;
	
	// stuff set by calculateProbabilities
	int wordsSize;
	List<Integer> sentenceWords;
	FeatureParameters parameters;
	double[][][] insideProbabilitiesBeforeUnaries; // unnormalized probabilities; start, end, label
	double[][][] insideProbabilitiesAfterUnaries;
	double[][] maxBeforeUnaries; // used for pruning
	//double[][][][] ruleProbabilities; // start, end, rule, split (relative to start)
	//double[][][] unaryRuleProbabilities;
	
	public Sampler(WordEnumeration words, LabelEnumeration labels, Rules rules) {
		this.wordEnum = words;
		this.labels = labels;
		this.rules = rules;
	}
	
	public void calculateProbabilities(List<Integer> words, FeatureParameters params) {
		parameters = params;
		sentenceWords = words;
		wordsSize = words.size();
		int labelsSize = labels.getNumberOfLabels();
		int rulesSize = rules.getNumberOfBinaryRules();
		insideProbabilitiesBeforeUnaries = new double[wordsSize][wordsSize+1][labelsSize];
		insideProbabilitiesAfterUnaries = new double[wordsSize][wordsSize+1][labelsSize];
		//ruleProbabilities = new double[wordsSize][wordsSize+1][rulesSize][wordsSize];
		//unaryRuleProbabilities = new double[wordsSize][wordsSize+1][rules.getNumberOfUnaryRules()];
		for(int i = 0; i < wordsSize; i++)
			for(int j = 0; j < wordsSize+1; j++)
				for(int k = 0; k < labelsSize; k++)
					insideProbabilitiesBeforeUnaries[i][j][k] = 0;
		
		for(int i = 0; i < wordsSize; i++) {
			TLongList spanProperties = SpanProperties.getTerminalSpanProperties(words, i, wordEnum);
			for(int label = 0; label < labelsSize; label++) {
				double spanScore = 0;
				final long ruleCode = Rules.getTerminalRuleCode(label);
				for(int p = 0; p < spanProperties.size(); p++) {
					spanScore += params.getScore(Features.getSpanPropertyByRuleFeature(spanProperties.get(p), ruleCode), false);
				}
				spanScore += params.getScore(Features.getRuleFeature(ruleCode), false);
				insideProbabilitiesBeforeUnaries[i][i+1][label] = Math.exp(spanScore);
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
						//ruleProbabilities[start][start+length][r][split] = probability;
						
						double fullProbability = insideProbabilitiesBeforeUnaries[start][start+length][label] + probability;  
						insideProbabilitiesBeforeUnaries[start][start+length][label] = fullProbability;
						
						if(fullProbability > maxBeforeUnaries[start][start+length]) {
							maxBeforeUnaries[start][start+length] = fullProbability;
						}
					}
				}
				
				doUnaryProbabilities(words, start, start + length);
			}
		}
	}
	
	private double unaryProbability(TLongList properties, int start, int end, int ruleNumber, Rule rule) {
		double childProb = insideProbabilitiesBeforeUnaries[start][end][rule.getLeft()];
		double spanScore = 0;
		final long ruleCode = Rules.getRuleCode(ruleNumber, Type.UNARY);
		for(int p = 0; p < properties.size(); p++) {
			spanScore += parameters.getScore(Features.getSpanPropertyByRuleFeature(properties.get(p), ruleCode), false);
			spanScore += parameters.getScore(Features.getSpanPropertyByLabelFeature(properties.get(p), rule.getParent()), false);
		}
		spanScore += parameters.getScore(Features.getRuleFeature(ruleCode), false);
		
		double probability = Math.exp(spanScore) * childProb;
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
		double leftChildProb = insideProbabilitiesAfterUnaries[start][split][rule.getLeft()];
		double rightChildProb = insideProbabilitiesAfterUnaries[split][end][rule.getRight()];
		if(leftChildProb < maxBeforeUnaries[start][split] * PRUNE_THRESHOLD || rightChildProb < maxBeforeUnaries[split][end] * PRUNE_THRESHOLD)
			return 0;
		
		double spanScore =  0;
		final long ruleCode = Rules.getRuleCode(ruleNumber, Type.BINARY);
		for(int p = 0; p < spanProperties.size(); p++) {
			spanScore += parameters.getScore(Features.getSpanPropertyByRuleFeature(spanProperties.get(p), ruleCode), false);
			spanScore += parameters.getScore(Features.getSpanPropertyByLabelFeature(spanProperties.get(p), rule.getParent()), false);
		}
		spanScore += parameters.getScore(Features.getRuleFeature(ruleCode), false);
		
		double probability = Math.exp(spanScore) * leftChildProb * rightChildProb;
		return probability;
	}
	
	private void doUnaryProbabilities(List<Integer> words, int start, int end) {
		int numUnaryRules = rules.getNumberOfUnaryRules();
		TLongList properties = SpanProperties.getUnarySpanProperties(words, start, end);
		
		List<Span> toAdd = new ArrayList<>();
		List<Double> probabilitiesToAdd = new ArrayList<>();
		for(int i = 0; i < numUnaryRules; i++) {
			Rule rule = rules.getUnaryRule(i);
			
			//unaryRuleProbabilities[start][end][i] = probabilityToAdd;
			
			double probability = unaryProbability(properties, start, end, i, rule);
			
			Span span = new Span(start, end, rule);
			toAdd.add(span);
			probabilitiesToAdd.add(probability);
		}
		
		double[] probs = insideProbabilitiesAfterUnaries[start][end];
		double[] oldProbs = insideProbabilitiesBeforeUnaries[start][end];
		for(int i = 0; i < probs.length; i++) {
			probs[i] = oldProbs[i];
		}
		
		for(int i = 0; i < toAdd.size(); i++) {
			Span sp = toAdd.get(i);
			double sc = probabilitiesToAdd.get(i);
			int label = sp.getRule().getParent();
			
			insideProbabilitiesAfterUnaries[start][end][label] += sc;
		}
	}
	
	public List<Span> sample() {
		double cumulative = 0;
		List<Double> cumulativeValues = new ArrayList<>();
		List<Integer> labelsToSample = new ArrayList<>();
		for(Integer topLabel : labels.getTopLevelLabelIds()) {
			double score = insideProbabilitiesAfterUnaries[0][wordsSize][topLabel];
			cumulative += score;
			cumulativeValues.add(cumulative);
			labelsToSample.add(topLabel);
		}
		
		int chosenTopLabel = labelsToSample.get(sample(cumulativeValues, cumulative));
		
		List<Span> sample = new ArrayList<>();
		sample(0, wordsSize, chosenTopLabel, true, sample);
		
		return sample;
	}
	
	private void sample(int start, int end, int label, boolean allowUnaries, List<Span> resultAccumulator) {
		//double[][] probabilities = ruleProbabilities[start][end];
		//double [] unaryProbabilities = unaryRuleProbabilities[start][end];
		double cumulative = 0;
		List<Double> cumulativeValues = new ArrayList<>();
		List<Span> spans = new ArrayList<>();
		if(allowUnaries) {
			int numUnaryRules = rules.getNumberOfUnaryRules();
			TLongList properties = SpanProperties.getUnarySpanProperties(sentenceWords, start, end);
			
			for(int r = 0; r < numUnaryRules; r++) {
				Rule rule = rules.getUnaryRule(r);
				if(rule.getParent() == label) {
					double prob = unaryProbability(properties, start, end, r, rule);
					cumulative += prob;
					cumulativeValues.add(cumulative);
					Span span = new Span(start, end, rule);
					spans.add(span);
				}
			}
		}
		
		if(end - start == 1) { // terminals
			double prob = insideProbabilitiesBeforeUnaries[start][end][label];
			cumulative += prob;
			cumulativeValues.add(cumulative);
			spans.add(new Span(start, label));
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
						if(prob == 0)
							continue;
						cumulative += prob;
						cumulativeValues.add(cumulative);
						Span span = new Span(start, end, start+split, rule);
						spans.add(span);
					}
				}
			}
		}
		
		//System.out.println(cumulativeValues);
		//System.out.println(spans);
		
		Span span = spans.get(sample(cumulativeValues, cumulative));
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
	
	private int sample(List<Double> cumulativeValues, double cumulative) {
		double sample = Math.random() * cumulative;
		int sampleIndex = Collections.binarySearch(cumulativeValues, sample);
		if(sampleIndex < 0) { // usually true, this is what binarySearch does when it does not find the exact value
			sampleIndex = -(sampleIndex + 1);
		}
		if(sampleIndex >= cumulativeValues.size()) { // because evidenty this happens somehow
			sampleIndex = cumulativeValues.size() - 1;
		}
		return sampleIndex;
	}
}
