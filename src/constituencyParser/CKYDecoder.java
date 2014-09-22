package constituencyParser;

import java.util.ArrayList;
import java.util.List;

import constituencyParser.features.BasicFeatures;
import constituencyParser.features.FeatureParameters;
import constituencyParser.features.FeatureParameters.SpanPropertyParameters;

public class CKYDecoder {
	LabelEnumeration labels;
	Rules rules;
	
	double[][][] scores;
	Span[][][] spans;
	
	List<Span> usedSpans;
	
	public CKYDecoder(LabelEnumeration labels, Rules rules) {
		this.labels = labels;
		this.rules = rules;
	}
	
	public List<Span> decode(List<String> words, FeatureParameters params) {
		int wordsSize = words.size();
		int labelsSize = labels.getNumberOfLabels();
		int rulesSize = rules.getNumberOfBinaryRules();
		scores = new double[wordsSize][wordsSize+1][labelsSize];
		for(int i = 0; i < wordsSize; i++)
			for(int j = 0; j < wordsSize+1; j++)
				for(int k = 0; k < labelsSize; k++)
					scores[i][j][k] = Double.NEGATIVE_INFINITY;
		spans = new Span[wordsSize][wordsSize+1][labelsSize];
		
		for(int i = 0; i < wordsSize; i++) {
			List<SpanPropertyParameters> spanProperties = params.getSpanParameters(BasicFeatures.getTerminalSpanProperties(words, i));
			for(int label = 0; label < labelsSize; label++) {
				Span span = new Span(i, label);
				double spanScore =  FeatureParameters.scoreTerminal(spanProperties, label);
				scores[i][i+1][label] = spanScore;
				spans[i][i+1][label] = span;
			}
			
			doUnary(words, i, i+1, params);
		}
		
		for(int length = 2; length < wordsSize + 1; length++) {
			for(int start = 0; start < wordsSize + 1 - length; start++) {
				for(int split = 1; split < length; split++) {
					List<SpanPropertyParameters> spanProperties = params.getSpanParameters(BasicFeatures.getBinarySpanProperties(words, start, start + length, start + split));
					for(int r = 0; r < rulesSize; r++) {
						Rule rule = rules.getBinaryRule(r);
						int label = rule.getParent();
						
						double childScores = scores[start][start+split][rule.getLeft()] + scores[start+split][start+length][rule.getRight()];
						double spanScore =  FeatureParameters.scoreBinary(spanProperties, r, label);
						double fullScore = spanScore + childScores;
						if(fullScore > scores[start][start+length][label]) {
							scores[start][start+length][label] = fullScore;
							Span span = new Span(start, start + length, start + split, rule);
							spans[start][start+length][label] = span;
						}
					}
				}
				
				doUnary(words, start, start + length, params);
			}
		}
		
		
		usedSpans = new ArrayList<>();
		getUsedSpans(0, wordsSize, labels.getId("S"));
		// TODO: other types of top level (e.g. SINV)
		
		return usedSpans;
	}
	
	private void doUnary(List<String> words, int start, int end, FeatureParameters parameters) {
		boolean changedLast = true;
		int numUnaryRules = rules.getNumberOfUnaryRules();
		List<SpanPropertyParameters> p = parameters.getSpanParameters(BasicFeatures.getUnarySpanProperties(words, start, end));
		int count = 0;
		while(changedLast) {
			count++;
			if(count > 10)
				throw new RuntimeException("Stack of unaries greater than 10");
			changedLast = false;
			for(int i = 0; i < numUnaryRules; i++) {
				Rule rule = rules.getUnaryRule(i);
				int label = rule.getParent();
				double childScore = scores[start][end][rule.getLeft()];
				double spanScore = FeatureParameters.scoreUnary(p, i, label);
				double fullScore = childScore + spanScore;
				if(fullScore > scores[start][end][label] && !checkForUnaryPath(start, end, rule.getLeft(), label)) {
					scores[start][end][label] = fullScore;
					Span span = new Span(start, end, rule);
					spans[start][end][label] = span;
				}
			}
		}
	}
	
	private boolean checkForUnaryPath(int start, int end, int startLabel, int endLabel) {
		int label = startLabel;
		boolean firstIteration = true;
		while(label != -1) {
			if(label == endLabel)
				return true;
			if(label == startLabel && !firstIteration)
				throw new RuntimeException("Cycle found");
			Span span = spans[start][end][label];
			Rule rule = span.getRule();
			if(rule.getType() == Rule.Type.UNARY) {
				label = rule.getLeft();
			}
			else {
				label = -1;
			}
			firstIteration = false;
		}
		return false;
	}
	
	private void getUsedSpans(int start, int end, int label) {
		Span span = spans[start][end][label];
		if(span == null)
			return;
		usedSpans.add(span);
		if(span.getRule().getType() == Rule.Type.BINARY) {
			getUsedSpans(span.getStart(), span.getSplit(), span.getRule().getLeft());
			getUsedSpans(span.getSplit(), span.getEnd(), span.getRule().getRight());
		}
		else if(span.getRule().getType() == Rule.Type.UNARY) {
			getUsedSpans(span.getStart(), span.getEnd(), span.getRule().getLeft());
		}
	}
}
