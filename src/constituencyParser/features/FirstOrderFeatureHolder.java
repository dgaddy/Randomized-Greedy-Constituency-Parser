package constituencyParser.features;

import gnu.trove.list.TLongList;

import java.util.List;

import constituencyParser.*;
import constituencyParser.Rule.Type;

public class FirstOrderFeatureHolder {
	WordEnumeration wordEnum;
	LabelEnumeration labels;
	RuleEnumeration rules;
	
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
	
	// for terminal rules
	double[][] terminalScores; // by word number then rule
	
	public FirstOrderFeatureHolder(WordEnumeration words, LabelEnumeration labels, RuleEnumeration rules) {
		this.wordEnum = words;
		this.labels = labels;
		this.rules = rules;
	}
	
	public void fillScoreArrays(List<Word> words, FeatureParameters params) {
		int wordsSize = words.size();
		int labelsSize = labels.getNumberOfLabels();
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
		
		terminalScores = new double[wordsSize][labelsSize];
		
		for(int i = 0; i < wordsSize; i++) {
			Word word = words.get(i);
			Word wordBefore = i == 0 ? null : words.get(i-1);
			Word wordAfter = i == wordsSize - 1 ? null : words.get(i+1);
			for(int r = 0; r < binaryRulesSize; r++) {
				int label = rules.getBinaryRule(r).getLabel();
				long ruleCode = RuleEnumeration.getRuleCode(r, Type.BINARY);
				
				// start score
				double score = 0;
				long propertyCode = SpanProperties.getWordPropertyCode(word, SpanProperties.WordPropertyType.FIRST);
				score += scoreProperty(propertyCode, ruleCode, label, params);
				if(wordBefore != null) {
					propertyCode = SpanProperties.getWordPropertyCode(wordBefore, SpanProperties.WordPropertyType.BEFORE);
					score += scoreProperty(propertyCode, ruleCode, label, params);
				}
				startSpanScores[i][r] = score;
				
				// end score
				score = 0;
				propertyCode = SpanProperties.getWordPropertyCode(word, SpanProperties.WordPropertyType.LAST);
				score += scoreProperty(propertyCode, ruleCode, label, params);
				if(wordAfter != null) {
					propertyCode = SpanProperties.getWordPropertyCode(wordAfter, SpanProperties.WordPropertyType.AFTER);
					score += scoreProperty(propertyCode, ruleCode, label, params);
				}
				endSpanScores[i+1][r] = score;
				
				// split score
				score = 0;
				if(wordBefore != null) {
					propertyCode = SpanProperties.getWordPropertyCode(wordBefore, SpanProperties.WordPropertyType.BEFORE_SPLIT);
					score += scoreProperty(propertyCode, ruleCode, label, params);
					propertyCode = SpanProperties.getWordPropertyCode(word, SpanProperties.WordPropertyType.AFTER_SPLIT);
					score += scoreProperty(propertyCode, ruleCode, label, params);
				}
				splitSpanScores[i][r] = score;
				
				// length score
				int length = i+1; // from 1 to wordsSize
				propertyCode = SpanProperties.getLengthPropertyCode(length);
				lengthSpanScores[length][r] = scoreProperty(propertyCode, ruleCode, label, params);
			}
			
			// unaries
			for(int r = 0; r < unaryRulesSize; r++) {
				int label = rules.getUnaryRule(r).getLabel();
				long ruleCode = RuleEnumeration.getRuleCode(r, Type.UNARY);
				
				// start score
				double score = 0;
				long propertyCode = SpanProperties.getWordPropertyCode(word, SpanProperties.WordPropertyType.FIRST);
				score += scoreProperty(propertyCode, ruleCode, label, params);
				if(wordBefore != null) {
					propertyCode = SpanProperties.getWordPropertyCode(wordBefore, SpanProperties.WordPropertyType.BEFORE);
					score += scoreProperty(propertyCode, ruleCode, label, params);
				}
				unaryStartSpanScores[i][r] = score;
				
				// end score
				score = 0;
				propertyCode = SpanProperties.getWordPropertyCode(word, SpanProperties.WordPropertyType.LAST);
				score += scoreProperty(propertyCode, ruleCode, label, params);
				if(wordAfter != null) {
					propertyCode = SpanProperties.getWordPropertyCode(wordAfter, SpanProperties.WordPropertyType.AFTER);
					score += scoreProperty(propertyCode, ruleCode, label, params);
				}
				unaryEndSpanScores[i+1][r] = score;
				
				// length score
				int length = i+1; // from 1 to wordsSize
				propertyCode = SpanProperties.getLengthPropertyCode(length);
				unaryLengthSpanScores[length][r] = scoreProperty(propertyCode, ruleCode, label, params);
			}
		}
		
		for(int r = 0; r < binaryRulesSize; r++) {
			long ruleCode = RuleEnumeration.getRuleCode(r, Type.BINARY);
			binaryRuleScores[r] = params.getScore(Features.getRuleFeature(ruleCode));
		}
		
		for(int r = 0; r < unaryRulesSize; r++) {
			long ruleCode = RuleEnumeration.getRuleCode(r, Type.UNARY);
			unaryRuleScores[r] = params.getScore(Features.getRuleFeature(ruleCode));
		}
		
		for(int i = 0; i < wordsSize; i++) {
			TLongList spanProperties = SpanProperties.getTerminalSpanProperties(words, i, wordEnum);
			for(int label = 0; label < labelsSize; label++) {
				double spanScore = 0;
				final long ruleCode = RuleEnumeration.getTerminalRuleCode(label);
				for(int p = 0; p < spanProperties.size(); p++) {
					spanScore += params.getScore(Features.getSpanPropertyByRuleFeature(spanProperties.get(p), ruleCode));
				}
				spanScore += params.getScore(Features.getRuleFeature(ruleCode));
				terminalScores[i][label] = spanScore;
			}
		}
	}
	
	private double scoreProperty(long spanProperty, long ruleCode, int label, FeatureParameters params) {
		return params.getScore(Features.getSpanPropertyByRuleFeature(spanProperty, ruleCode))
				+ params.getScore(Features.getSpanPropertyByLabelFeature(spanProperty, label));
	}
	
	public double scoreTerminal(int position, int label) {
		return terminalScores[position][label];
	}
	
	public double scoreBinary(int start, int end, int split, int ruleId) {
		return startSpanScores[start][ruleId] + endSpanScores[end][ruleId] + splitSpanScores[split][ruleId] + lengthSpanScores[end-start][ruleId] + binaryRuleScores[ruleId];
	}
	
	public double scoreUnary(int start, int end, int ruleId) {
		return unaryStartSpanScores[start][ruleId] + unaryEndSpanScores[end][ruleId] + unaryLengthSpanScores[end-start][ruleId] + unaryRuleScores[ruleId];
	}
}
