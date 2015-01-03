package constituencyParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import constituencyParser.Rule.Type;

public class CKYSampler {
	WordEnumeration words;
	Rules rules;
	LabelEnumeration labels;
	
	int[] labelCounts;
	int[] binaryRuleCounts;
	int[] unaryRuleCounts;
	int[][] terminalCounts; // indexed by terminal then word
	
	double[][][] insideProbabilitiesBeforeUnaries;
	double[][][] insideProbabilitiesAfterUnaries;
	List<Span> usedSpans;
	
	public CKYSampler(WordEnumeration words, LabelEnumeration labels, Rules rules) {
		this.words = words;
		this.labels = labels;
		this.rules = rules;
	}
	
	public void doCounts(List<SpannedWords> examples) {
		int numLabels = labels.getNumberOfLabels();
		labelCounts = new int[numLabels];
		binaryRuleCounts = new int[rules.getNumberOfBinaryRules()];
		unaryRuleCounts = new int[rules.getNumberOfUnaryRules()];
		terminalCounts = new int[numLabels][words.getNumberOfWords()];
		for(int i = 0; i < numLabels; i++) {
			labelCounts[i]++; // Add one to each terminal label count for the out of vocabulary words
		}
		
		for(SpannedWords sw : examples) {
			List<Integer> words = sw.getWords();
			for(Span s : sw.getSpans()) {
				Rule rule = s.getRule();
				labelCounts[rule.getParent()]++;
				if(rule.getType() == Type.BINARY) {
					binaryRuleCounts[rules.getBinaryId(rule)]++;
				}
				else if(rule.getType() == Type.UNARY) {
					unaryRuleCounts[rules.getUnaryId(rule)]++;
				}
				else if(rule.getType() == Type.TERMINAL) {
					terminalCounts[rule.getParent()][words.get(s.getStart())]++;
				}
				else {
					throw new RuntimeException("Invalid type.");
				}
			}
		}
	}
	
	public void calculateProbabilities(List<Integer> words) {
		int wordsSize = words.size();
		int numWords = terminalCounts[0].length;
		int numLabels = labelCounts.length;
		int binRulesSize = binaryRuleCounts.length;
		insideProbabilitiesBeforeUnaries = new double[wordsSize][wordsSize+1][numLabels];
		insideProbabilitiesAfterUnaries = new double[wordsSize][wordsSize+1][numLabels];
		
		for(int i = 0; i < wordsSize; i++) {
			int word = words.get(i);
			for(int label = 0; label < numLabels; label++) {
				double labelCount = labelCounts[label];
				double wordCount = word >= numWords ? 1 : terminalCounts[label][word]; // the 1 is for the out of vocabulary words, which we count as being seen once with all labels
				if(labelCount == 0)
					continue;
				insideProbabilitiesBeforeUnaries[i][i+1][label] = wordCount / labelCount;
			}
			
			doUnary(i, i+1);
		}
		
		for(int length = 2; length < wordsSize + 1; length++) {
			for(int start = 0; start < wordsSize + 1 - length; start++) {
				for(int split = 1; split < length; split++) {
					for(int r = 0; r < binRulesSize; r++) {
						Rule rule = rules.getBinaryRule(r);
						int label = rule.getParent();
						
						double labelCount = labelCounts[label];
						double ruleCount = binaryRuleCounts[r];
						double prob = ruleCount / labelCount * insideProbabilitiesAfterUnaries[start][start+split][rule.getLeft()] * insideProbabilitiesAfterUnaries[start+split][start+length][rule.getRight()];
						
						insideProbabilitiesBeforeUnaries[start][start+length][label] += prob;
					}
				}
				
				doUnary(start, start+length);
			}
		}
	}
	
	public void doUnary(int start, int end) {
		int numLabels = labelCounts.length;
		
		for(int i = 0; i < numLabels; i++) {
			insideProbabilitiesAfterUnaries[start][end][i] = insideProbabilitiesBeforeUnaries[start][end][i];
		}
		
		int numUnaryRules = unaryRuleCounts.length;
		for(int i = 0; i < numUnaryRules; i++) {
			Rule rule = rules.getUnaryRule(i);
			int label = rule.getParent();
			double labelCount = labelCounts[label];
			double ruleCount = unaryRuleCounts[i];
			double prob = ruleCount / labelCount * insideProbabilitiesBeforeUnaries[start][end][rule.getLeft()];
			
			insideProbabilitiesAfterUnaries[start][end][label] += prob;
		}
	}
	
	public List<Span> sample() {
		int wordsSize = insideProbabilitiesAfterUnaries.length;
		List<Span> sample = new ArrayList<>();
		double cumulative = 0;
		List<Double> cumulativeValues = new ArrayList<>();
		List<Integer> labelsToSample = new ArrayList<>();
		for(Integer topLabel : labels.getTopLevelLabelIds()) {
			double score = insideProbabilitiesAfterUnaries[0][wordsSize][topLabel];
			if(score <= 0)
				continue;
			
			cumulative += score;
			cumulativeValues.add(cumulative);
			labelsToSample.add(topLabel);
		}
		
		if(cumulativeValues.size() == 0) {
			System.out.println("top level fail");
			return new ArrayList<>();
		}
		
		int chosenTopLabel = labelsToSample.get(sample(cumulativeValues, cumulative));
		
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
			int numUnaryRules = unaryRuleCounts.length;
			
			for(int r = 0; r < numUnaryRules; r++) {
				Rule rule = rules.getUnaryRule(r);
				if(rule.getParent() == label) {
					double labelCount = labelCounts[label];
					double ruleCount = unaryRuleCounts[r];
					double prob = ruleCount / labelCount * insideProbabilitiesBeforeUnaries[start][end][rule.getLeft()];
					if(prob <= 0)
						continue;
					
					cumulative += prob;
					cumulativeValues.add(cumulative);
					Span span = new Span(start, end, rule);
					spans.add(span);
				}
			}
		}
		
		if(end - start == 1) { // terminals
			double prob = insideProbabilitiesBeforeUnaries[start][end][label];
			if(prob > 0) {
				cumulative += prob;
				cumulativeValues.add(cumulative);
				spans.add(new Span(start, label));
			}
		}
		else {
			// binary
			int numBinRules = binaryRuleCounts.length;
			for(int split = 1; split < end-start; split++) {
				for(int r = 0; r < numBinRules; r++) {
					Rule rule = rules.getBinaryRule(r);
					
					if(rule.getParent() == label) {
						double labelCount = labelCounts[label];
						double ruleCount = binaryRuleCounts[r];
						double prob = ruleCount / labelCount * insideProbabilitiesAfterUnaries[start][start+split][rule.getLeft()] * insideProbabilitiesAfterUnaries[start+split][end][rule.getRight()];
						if(prob <= 0)
							continue;
						
						cumulative += prob;
						cumulativeValues.add(cumulative);
						Span span = new Span(start, end, start+split, rule);
						spans.add(span);
					}
				}
			}
		}
		
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
		return sampleIndex;
	}

}
