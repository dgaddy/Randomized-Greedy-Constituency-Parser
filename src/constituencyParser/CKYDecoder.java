package constituencyParser;

import gnu.trove.list.TLongList;

import java.util.ArrayList;
import java.util.List;

import constituencyParser.Rule.Type;
import constituencyParser.features.FeatureParameters;
import constituencyParser.features.Features;
import constituencyParser.features.SpanProperties;

public class CKYDecoder {
	private static final double PRUNE_THRESHOLD = 10;
	
	WordEnumeration wordEnum;
	LabelEnumeration labels;
	Rules rules;
	
	double[][][] scores;
	Span[][][] spans;
	
	List<Span> usedSpans;
	
	public CKYDecoder(WordEnumeration words, LabelEnumeration labels, Rules rules) {
		this.wordEnum = words;
		this.labels = labels;
		this.rules = rules;
	}
	
	public List<Span> decode(List<Integer> words, FeatureParameters params) {
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
			TLongList spanProperties = SpanProperties.getTerminalSpanProperties(words, i, wordEnum);
			for(int label = 0; label < labelsSize; label++) {
				Span span = new Span(i, label);
				double spanScore = 0;
				final long ruleCode = Rules.getTerminalRuleCode(label);
				for(int p = 0; p < spanProperties.size(); p++) {
					spanScore += params.getScore(Features.getSpanPropertyByRuleFeature(spanProperties.get(p), ruleCode));
				}
				spanScore += params.getScore(Features.getRuleFeature(ruleCode));
				scores[i][i+1][label] = spanScore;
				spans[i][i+1][label] = span;
			}
			
			doUnary(words, i, i+1, params);
		}
		
		double[][] max = new double[wordsSize][wordsSize+1];
		for(int length = 2; length < wordsSize + 1; length++) {
			for(int start = 0; start < wordsSize + 1 - length; start++) {
				max[start][start+length] = Double.NEGATIVE_INFINITY;
				for(int split = 1; split < length; split++) {
					TLongList spanProperties = SpanProperties.getBinarySpanProperties(words, start, start + length, start + split);
					for(int r = 0; r < rulesSize; r++) {
						
						Rule rule = rules.getBinaryRule(r);
						int label = rule.getParent();
						
						double leftChildScore = scores[start][start+split][rule.getLeft()];
						double rightChildScore = scores[start+split][start+length][rule.getRight()];
						if(leftChildScore < max[start][start+split] - PRUNE_THRESHOLD || rightChildScore < max[start+split][start+length] - PRUNE_THRESHOLD)
							continue;
						
						double childScores = scores[start][start+split][rule.getLeft()] + scores[start+split][start+length][rule.getRight()];
						
						double spanScore =  0;
						final long ruleCode = Rules.getRuleCode(r, Type.BINARY);
						for(int p = 0; p < spanProperties.size(); p++) {
							spanScore += params.getScore(Features.getSpanPropertyByRuleFeature(spanProperties.get(p), ruleCode));
							spanScore += params.getScore(Features.getSpanPropertyByLabelFeature(spanProperties.get(p), label));
						}
						spanScore += params.getScore(Features.getRuleFeature(ruleCode));
						
						double fullScore = spanScore + childScores;
						if(fullScore > scores[start][start+length][label]) {
							scores[start][start+length][label] = fullScore;
							Span span = new Span(start, start + length, start + split, rule);
							span.setLeft(spans[start][start+split][rule.getLeft()]);
							span.setRight(spans[start+split][start+length][rule.getRight()]);
							spans[start][start+length][label] = span;
							
							if(fullScore > max[start][start+length]) {
								max[start][start+length] = fullScore;
							}
						}
						
						
					}
				}
				
				doUnary(words, start, start + length, params);
			}
		}
		
		double bestScore = Double.NEGATIVE_INFINITY;
		Span bestSpan = null;
		for(Integer topLabel : labels.getTopLevelLabelIds()) {
			double score = scores[0][wordsSize][topLabel];
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
	
	private void doUnary(List<Integer> words, int start, int end, FeatureParameters parameters) {
		int numUnaryRules = rules.getNumberOfUnaryRules();
		TLongList properties = SpanProperties.getUnarySpanProperties(words, start, end);
		
		List<Span> toAdd = new ArrayList<>();
		List<Double> scoresToAdd = new ArrayList<>();
		for(int i = 0; i < numUnaryRules; i++) {
			Rule rule = rules.getUnaryRule(i);
			int label = rule.getParent();
			double childScore = scores[start][end][rule.getLeft()];
			double spanScore = 0;
			final long ruleCode = Rules.getRuleCode(i, Type.UNARY);
			for(int p = 0; p < properties.size(); p++) {
				spanScore += parameters.getScore(Features.getSpanPropertyByRuleFeature(properties.get(p), ruleCode));
				spanScore += parameters.getScore(Features.getSpanPropertyByLabelFeature(properties.get(p), label));
			}
			spanScore += parameters.getScore(Features.getRuleFeature(ruleCode));
			double fullScore = childScore + spanScore;
			if(fullScore > scores[start][end][label]) {
				Span span = new Span(start, end, rule);
				span.setLeft(spans[start][end][rule.getLeft()]);
				toAdd.add(span);
				scoresToAdd.add(fullScore);
			}
		}
		
		for(int i = 0; i < toAdd.size(); i++) {
			Span sp = toAdd.get(i);
			double sc = scoresToAdd.get(i);
			int label = sp.getRule().getParent();
			scores[start][end][label] = sc;
			spans[start][end][label] = sp;
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
