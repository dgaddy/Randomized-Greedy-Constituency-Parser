package constituencyParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import constituencyParser.Rule.Type;

public class CKYDecoder {
	WordEnumeration words;
	Rules rules;
	LabelEnumeration labels;
	
	int[] labelCounts;
	int[] binaryRuleCounts;
	int[] unaryRuleCounts;
	int[][] terminalCounts; // indexed by terminal then word
	
	double[][][] probabilities;
	Span[][][] spans;
	List<Span> usedSpans;
	
	public CKYDecoder(WordEnumeration words, LabelEnumeration labels, Rules rules) {
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
	
	public List<Span> decode(List<Integer> words) {
		int wordsSize = words.size();
		int numWords = terminalCounts[0].length;
		int numLabels = labelCounts.length;
		int binRulesSize = binaryRuleCounts.length;
		probabilities = new double[wordsSize][wordsSize+1][numLabels];
		spans = new Span[wordsSize][wordsSize+1][numLabels];

		for(int i = 0; i < wordsSize; i++) {
			int word = words.get(i);
			for(int label = 0; label < numLabels; label++) {
				double labelCount = labelCounts[label];
				double wordCount = word >= numWords ? 1 : terminalCounts[label][word]; // the 1 is for the out of vocabulary words, which we count as being seen once with all labels
				if(labelCount == 0)
					continue;
				probabilities[i][i+1][label] = wordCount / labelCount;
				spans[i][i+1][label] = new Span(i, label);
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
						double prob = ruleCount / labelCount * probabilities[start][start+split][rule.getLeft()] * probabilities[start+split][start+length][rule.getRight()];
						
						if(prob > probabilities[start][start+length][label]) {
							probabilities[start][start+length][label] = prob;
							
							Span span = new Span(start, start + length, start + split, rule);
							span.setLeft(spans[start][start+split][rule.getLeft()]);
							span.setRight(spans[start+split][start+length][rule.getRight()]);
							spans[start][start+length][label] = span;
						}
					}
				}
				
				doUnary(start, start+length);
			}
		}
		
		double bestScore = 0;
		Span bestSpan = null;
		for(Integer topLabel : labels.getTopLevelLabelIds()) {
			double score = probabilities[0][wordsSize][topLabel];
			if(score > bestScore) {
				bestScore = score;
				bestSpan = spans[0][wordsSize][topLabel];
			}
		}
		
		usedSpans = new ArrayList<>();
		if(bestSpan != null)
			getUsedSpans(bestSpan);
		
		return usedSpans;
	}
	
	public void doUnary(int start, int end) {
		int numLabels = labelCounts.length;
		double[] unaryProbabilities = new double[numLabels];
		Span[] unarySpans = new Span[numLabels];
		
		int numUnaryRules = unaryRuleCounts.length;
		for(int i = 0; i < numUnaryRules; i++) {
			Rule rule = rules.getUnaryRule(i);
			int label = rule.getParent();
			double labelCount = labelCounts[label];
			double ruleCount = unaryRuleCounts[i];
			double prob = ruleCount / labelCount * probabilities[start][end][rule.getLeft()];
			
			if(prob > unaryProbabilities[label]) {
				unaryProbabilities[label] = prob;
				
				Span span = new Span(start, end, rule);
				span.setLeft(spans[start][end][rule.getLeft()]);
				unarySpans[label] = span;
			}
		}
		
		for(int i = 0; i < numLabels; i++) {
			if(unaryProbabilities[i] > probabilities[start][end][i] && unarySpans[i] != null) {
				probabilities[start][end][i] = unaryProbabilities[i];
				spans[start][end][i] = unarySpans[i];
			}
		}
	}
	
	private void getUsedSpans(Span span) {
		usedSpans.add(span);
		if(span.getRule().getType() == Rule.Type.BINARY) {
			getUsedSpans(span.getLeft());
			getUsedSpans(span.getRight());
		}
		else if(span.getRule().getType() == Rule.Type.UNARY) {
			getUsedSpans(span.getLeft());
		}
	}
}
