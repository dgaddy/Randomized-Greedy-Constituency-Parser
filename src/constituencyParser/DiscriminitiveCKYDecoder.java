package constituencyParser;

import gnu.trove.list.TLongList;

import java.util.ArrayList;
import java.util.List;

import constituencyParser.Rule.Type;
import constituencyParser.features.FeatureParameters;
import constituencyParser.features.Features;
import constituencyParser.features.SpanProperties;

/**
 * A discriminitive CKY decoder based on Less Grammar, More Features, David Hall, Greg Durrett and Dan
Klein, ACL 14 (http://www.cs.berkeley.edu/ dlwh/papers/spanparser.pdf)
 */
public class DiscriminitiveCKYDecoder implements Decoder {
	private static final double PRUNE_THRESHOLD = 10;
	
	WordEnumeration wordEnum;
	LabelEnumeration labels;
	RuleEnumeration rules;
	
	double[][][] scores;
	Span[][][] spans;
	
	// for binary rules
	double[][] startSpanScores; // indexed by word number in sentence then rule - value is the score of start span property features
	double[][] endSpanScores; // indexed by end word number (exclusive) then rule
	double[][] splitSpanScores;
	double[][] lengthSpanScores; // indexed by length then rule
	double[] binaryRuleScores;
	
	// for unary rules
	double[][] unaryStartSpanScores;
	double[][] unaryEndSpanScores;
	double[][] unaryLengthSpanScores;
	double[] unaryRuleScores;
	
	List<Span> usedSpans;
	
	public DiscriminitiveCKYDecoder(WordEnumeration words, LabelEnumeration labels, RuleEnumeration rules) {
		this.wordEnum = words;
		this.labels = labels;
		this.rules = rules;
	}
	
	void fillScoreArrays(List<Integer> words, FeatureParameters params, boolean dropout) {
		int wordsSize = words.size();
		int binaryRulesSize = rules.getNumberOfBinaryRules();
		int unaryRulesSize = rules.getNumberOfUnaryRules();
		startSpanScores = new double[wordsSize][binaryRulesSize];
		endSpanScores = new double[wordsSize+1][binaryRulesSize];
		splitSpanScores = new double[wordsSize][binaryRulesSize];
		lengthSpanScores = new double[wordsSize+1][binaryRulesSize];
		binaryRuleScores = new double[binaryRulesSize];
		
		unaryStartSpanScores = new double[wordsSize][unaryRulesSize];
		unaryEndSpanScores = new double[wordsSize+1][unaryRulesSize];
		unaryLengthSpanScores = new double[wordsSize+1][unaryRulesSize];
		unaryRuleScores = new double[unaryRulesSize];
		
		for(int i = 0; i < wordsSize; i++) {
			int word = words.get(i);
			int wordBefore = i == 0 ? -1 : words.get(i-1);
			int wordAfter = i == wordsSize - 1 ? -1 : words.get(i+1);
			for(int r = 0; r < binaryRulesSize; r++) {
				int label = rules.getBinaryRule(r).getLabel();
				long ruleCode = RuleEnumeration.getRuleCode(r, Type.BINARY);
				
				// start score
				double score = 0;
				long propertyCode = SpanProperties.getWordPropertyCode(word, SpanProperties.WordPropertyType.FIRST);
				score += scoreProperty(propertyCode, ruleCode, label, params, dropout);
				if(wordBefore != -1) {
					propertyCode = SpanProperties.getWordPropertyCode(wordBefore, SpanProperties.WordPropertyType.BEFORE);
					score += scoreProperty(propertyCode, ruleCode, label, params, dropout);
				}
				startSpanScores[i][r] = score;
				
				// end score
				score = 0;
				propertyCode = SpanProperties.getWordPropertyCode(word, SpanProperties.WordPropertyType.LAST);
				score += scoreProperty(propertyCode, ruleCode, label, params, dropout);
				if(wordAfter != -1) {
					propertyCode = SpanProperties.getWordPropertyCode(wordAfter, SpanProperties.WordPropertyType.AFTER);
					score += scoreProperty(propertyCode, ruleCode, label, params, dropout);
				}
				endSpanScores[i+1][r] = score;
				
				// split score
				score = 0;
				if(wordBefore != -1) {
					propertyCode = SpanProperties.getWordPropertyCode(wordBefore, SpanProperties.WordPropertyType.BEFORE_SPLIT);
					score += scoreProperty(propertyCode, ruleCode, label, params, dropout);
					propertyCode = SpanProperties.getWordPropertyCode(word, SpanProperties.WordPropertyType.AFTER_SPLIT);
					score += scoreProperty(propertyCode, ruleCode, label, params, dropout);
				}
				splitSpanScores[i][r] = score;
				
				// length score
				int length = i+1; // from 1 to wordsSize
				propertyCode = SpanProperties.getLengthPropertyCode(length);
				lengthSpanScores[length][r] = scoreProperty(propertyCode, ruleCode, label, params, dropout);
			}
			
			// unaries
			for(int r = 0; r < unaryRulesSize; r++) {
				int label = rules.getUnaryRule(r).getLabel();
				long ruleCode = RuleEnumeration.getRuleCode(r, Type.UNARY);
				
				// start score
				double score = 0;
				long propertyCode = SpanProperties.getWordPropertyCode(word, SpanProperties.WordPropertyType.FIRST);
				score += scoreProperty(propertyCode, ruleCode, label, params, dropout);
				if(wordBefore != -1) {
					propertyCode = SpanProperties.getWordPropertyCode(wordBefore, SpanProperties.WordPropertyType.BEFORE);
					score += scoreProperty(propertyCode, ruleCode, label, params, dropout);
				}
				unaryStartSpanScores[i][r] = score;
				
				// end score
				score = 0;
				propertyCode = SpanProperties.getWordPropertyCode(word, SpanProperties.WordPropertyType.LAST);
				score += scoreProperty(propertyCode, ruleCode, label, params, dropout);
				if(wordAfter != -1) {
					propertyCode = SpanProperties.getWordPropertyCode(wordAfter, SpanProperties.WordPropertyType.AFTER);
					score += scoreProperty(propertyCode, ruleCode, label, params, dropout);
				}
				unaryEndSpanScores[i+1][r] = score;
				
				// length score
				int length = i+1; // from 1 to wordsSize
				propertyCode = SpanProperties.getLengthPropertyCode(length);
				unaryLengthSpanScores[length][r] = scoreProperty(propertyCode, ruleCode, label, params, dropout);
			}
		}
		
		for(int r = 0; r < binaryRulesSize; r++) {
			long ruleCode = RuleEnumeration.getRuleCode(r, Type.BINARY);
			binaryRuleScores[r] = params.getScore(Features.getRuleFeature(ruleCode), dropout);
		}
		
		for(int r = 0; r < unaryRulesSize; r++) {
			long ruleCode = RuleEnumeration.getRuleCode(r, Type.UNARY);
			unaryRuleScores[r] = params.getScore(Features.getRuleFeature(ruleCode), dropout);
		}
	}
	
	private double scoreProperty(long spanProperty, long ruleCode, int label, FeatureParameters params, boolean dropout) {
		return params.getScore(Features.getSpanPropertyByRuleFeature(spanProperty, ruleCode), dropout)
				+ params.getScore(Features.getSpanPropertyByLabelFeature(spanProperty, label), dropout);
	}
	
	double lastScore = 0;
	
	public List<Span> decode(List<Integer> words, FeatureParameters params, boolean dropout) {
		fillScoreArrays(words, params, dropout);
		
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
				final long ruleCode = RuleEnumeration.getTerminalRuleCode(label);
				for(int p = 0; p < spanProperties.size(); p++) {
					spanScore += params.getScore(Features.getSpanPropertyByRuleFeature(spanProperties.get(p), ruleCode), dropout);
				}
				spanScore += params.getScore(Features.getRuleFeature(ruleCode), dropout);
				scores[i][i+1][label] = spanScore;
				spans[i][i+1][label] = span;
			}
			
			doUnary(words, i, i+1, params, dropout);
		}
		
		double[][] max = new double[wordsSize][wordsSize+1];
		for(int length = 2; length < wordsSize + 1; length++) {
			for(int start = 0; start < wordsSize + 1 - length; start++) {
				max[start][start+length] = Double.NEGATIVE_INFINITY;
				for(int split = 1; split < length; split++) {
					for(int r = 0; r < rulesSize; r++) {
						
						Rule rule = rules.getBinaryRule(r);
						int label = rule.getLabel();
						
						double leftChildScore = scores[start][start+split][rule.getLeft()];
						double rightChildScore = scores[start+split][start+length][rule.getRight()];
						if(leftChildScore < max[start][start+split] - PRUNE_THRESHOLD || rightChildScore < max[start+split][start+length] - PRUNE_THRESHOLD)
							continue;
						
						double childScores = scores[start][start+split][rule.getLeft()] + scores[start+split][start+length][rule.getRight()];
						
						double spanScore = startSpanScores[start][r] + splitSpanScores[start+split][r] + endSpanScores[start+length][r] + lengthSpanScores[length][r] + binaryRuleScores[r];
						
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
				
				doUnary(words, start, start + length, params, dropout);
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
		
		lastScore = bestScore;
		
		return usedSpans;
	}
	
	public double getLastScore() {
		return lastScore;
	}
	
	private void doUnary(List<Integer> words, int start, int end, FeatureParameters parameters, boolean dropout) {
		int numUnaryRules = rules.getNumberOfUnaryRules();
		int numLabels = labels.getNumberOfLabels();
		
		double[] unaryScores = new double[numLabels];
		for(int i = 0; i < numLabels; i++)
			unaryScores[i] = Double.NEGATIVE_INFINITY;
		Span[] unarySpans = new Span[numLabels];
		
		for(int i = 0; i < numUnaryRules; i++) {
			Rule rule = rules.getUnaryRule(i);
			int label = rule.getLabel();
			
			double childScore = scores[start][end][rule.getLeft()];
			double spanScore = unaryStartSpanScores[start][i] + unaryEndSpanScores[end][i] + unaryLengthSpanScores[end-start][i] + unaryRuleScores[i];
			double fullScore = childScore + spanScore;
			if(fullScore > unaryScores[label]) {
				Span span = new Span(start, end, rule);
				span.setLeft(spans[start][end][rule.getLeft()]);
				unaryScores[label] = fullScore;
				unarySpans[label] = span;
			}
		}
		
		for(int i = 0; i < numLabels; i++) {
			if(unaryScores[i] > scores[start][end][i] && unarySpans[i] != null) {
				scores[start][end][i] = unaryScores[i];
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

	@Override
	public void setCostAugmenting(boolean costAugmenting, SpannedWords gold) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setSecondOrder(boolean secondOrder) {
		throw new UnsupportedOperationException();
	}
}
