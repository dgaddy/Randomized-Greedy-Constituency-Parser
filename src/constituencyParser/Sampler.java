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
	
	int wordsSize;
	double[][][] insideProbabilities; // unnormalized probabilities; start, end, label
	double[][][][] ruleProbabilities; // start, end, rule, split (relative to start)
	double[][][] unaryRuleProbabilities;
	
	public Sampler(WordEnumeration words, LabelEnumeration labels, Rules rules) {
		this.wordEnum = words;
		this.labels = labels;
		this.rules = rules;
	}
	
	public void calculateProbabilities(List<Integer> words, FeatureParameters params) {
		wordsSize = words.size();
		int labelsSize = labels.getNumberOfLabels();
		int rulesSize = rules.getNumberOfBinaryRules();
		insideProbabilities = new double[wordsSize][wordsSize+1][labelsSize];
		ruleProbabilities = new double[wordsSize][wordsSize+1][rulesSize][wordsSize];
		unaryRuleProbabilities = new double[wordsSize][wordsSize+1][rules.getNumberOfUnaryRules()];
		for(int i = 0; i < wordsSize; i++)
			for(int j = 0; j < wordsSize+1; j++)
				for(int k = 0; k < labelsSize; k++)
					insideProbabilities[i][j][k] = 0;
		
		for(int i = 0; i < wordsSize; i++) {
			TLongList spanProperties = SpanProperties.getTerminalSpanProperties(words, i, wordEnum);
			for(int label = 0; label < labelsSize; label++) {
				double spanScore = 0;
				final long ruleCode = Rules.getTerminalRuleCode(label);
				for(int p = 0; p < spanProperties.size(); p++) {
					spanScore += params.getScore(Features.getSpanPropertyByRuleFeature(spanProperties.get(p), ruleCode), false);
				}
				spanScore += params.getScore(Features.getRuleFeature(ruleCode), false);
				insideProbabilities[i][i+1][label] = Math.exp(spanScore);
			}
			
			doUnaryProbabilities(words, i, i+1, params);
		}
		
		double[][] max = new double[wordsSize][wordsSize+1];
		for(int length = 2; length < wordsSize + 1; length++) {
			for(int start = 0; start < wordsSize + 1 - length; start++) {
				max[start][start+length] = 0;
				for(int split = 1; split < length; split++) {
					TLongList spanProperties = SpanProperties.getBinarySpanProperties(words, start, start + length, start + split);
					for(int r = 0; r < rulesSize; r++) {
						
						Rule rule = rules.getBinaryRule(r);
						int label = rule.getParent();
						
						double leftChildProb = insideProbabilities[start][start+split][rule.getLeft()];
						double rightChildProb = insideProbabilities[start+split][start+length][rule.getRight()];
						//if(leftChildProb < max[start][start+split] * PRUNE_THRESHOLD || rightChildProb < max[start+split][start+length] * PRUNE_THRESHOLD)
						//	continue;
						
						double spanScore =  0;
						final long ruleCode = Rules.getRuleCode(r, Type.BINARY);
						for(int p = 0; p < spanProperties.size(); p++) {
							spanScore += params.getScore(Features.getSpanPropertyByRuleFeature(spanProperties.get(p), ruleCode), false);
							spanScore += params.getScore(Features.getSpanPropertyByLabelFeature(spanProperties.get(p), label), false);
						}
						spanScore += params.getScore(Features.getRuleFeature(ruleCode), false);
						
						double probability = Math.exp(spanScore) * leftChildProb * rightChildProb;
						ruleProbabilities[start][start+length][r][split] = probability;
						
						double fullProbability = insideProbabilities[start][start+length][label] + probability;  
						insideProbabilities[start][start+length][label] = fullProbability;
						
						if(fullProbability > max[start][start+length]) {
							max[start][start+length] = fullProbability;
						}
					}
				}
				
				doUnaryProbabilities(words, start, start + length, params);
			}
		}
	}
	
	private void doUnaryProbabilities(List<Integer> words, int start, int end, FeatureParameters parameters) {
		int numUnaryRules = rules.getNumberOfUnaryRules();
		TLongList properties = SpanProperties.getUnarySpanProperties(words, start, end);
		
		List<Span> toAdd = new ArrayList<>();
		List<Double> probabilitiesToAdd = new ArrayList<>();
		for(int i = 0; i < numUnaryRules; i++) {
			Rule rule = rules.getUnaryRule(i);
			int label = rule.getParent();
			double childProb = insideProbabilities[start][end][rule.getLeft()];
			double spanScore = 0;
			final long ruleCode = Rules.getRuleCode(i, Type.UNARY);
			for(int p = 0; p < properties.size(); p++) {
				spanScore += parameters.getScore(Features.getSpanPropertyByRuleFeature(properties.get(p), ruleCode), false);
				spanScore += parameters.getScore(Features.getSpanPropertyByLabelFeature(properties.get(p), label), false);
			}
			spanScore += parameters.getScore(Features.getRuleFeature(ruleCode), false);
			
			double probabilityToAdd = Math.exp(spanScore) * childProb;
			unaryRuleProbabilities[start][end][i] = probabilityToAdd;
			
			Span span = new Span(start, end, rule);
			toAdd.add(span);
			probabilitiesToAdd.add(probabilityToAdd);
		}
		
		for(int i = 0; i < toAdd.size(); i++) {
			Span sp = toAdd.get(i);
			double sc = probabilitiesToAdd.get(i);
			int label = sp.getRule().getParent();
			
			insideProbabilities[start][end][label] += sc;
		}
	}
	
	public List<Span> sample() {
		double cumulative = 0;
		List<Double> cumulativeValues = new ArrayList<>();
		List<Integer> labelsToSample = new ArrayList<>();
		for(Integer topLabel : labels.getTopLevelLabelIds()) {
			double score = insideProbabilities[0][wordsSize][topLabel];
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
		if(end - start == 1) {
			resultAccumulator.add(new Span(start, label));
			return;
		}
		
		double[][] probabilities = ruleProbabilities[start][end];
		double [] unaryProbabilities = unaryRuleProbabilities[start][end];
		double cumulative = 0;
		List<Double> cumulativeValues = new ArrayList<>();
		List<Span> spans = new ArrayList<>();
		if(allowUnaries) {
			for(int r = 0; r < unaryProbabilities.length; r++) {
				Rule rule = rules.getUnaryRule(r);
				if(rule.getParent() == label) {
					double prob = unaryProbabilities[r];
					cumulative += prob;
					cumulativeValues.add(cumulative);
					Span span = new Span(start, end, rule);
					spans.add(span);
				}
			}
		}
		// binary
		for(int r = 0; r < probabilities.length; r++) {
			Rule rule = rules.getBinaryRule(r);
			if(rule.getParent() == label) {
				for(int split = 1; split < end-start; split++) {
					double prob = probabilities[r][split];
					cumulative += prob;
					cumulativeValues.add(cumulative);
					Span span = new Span(start, end, start+split, rule);
					spans.add(span);
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
