package constituencyParser.unlabeled;

import gnu.trove.list.TLongList;

import java.util.ArrayList;
import java.util.List;

import constituencyParser.*;
import constituencyParser.features.FeatureParameters;
import constituencyParser.features.SpanProperties;

public class UnlabeledFirstOrderFeatureHolder {
	WordEnumeration wordEnum;
	LabelEnumeration labels;
	RuleEnumeration rules;

	// for binary rules
	double[][][][] startSpanScores; // indexed by word number in sentence then rule - value is the score of start span property features
	double[][][][] endSpanScores; // indexed by end word number (exclusive) then rule
	double[][][][] splitSpanScores;
	double[][][][] lengthSpanScores; // indexed by length then rule
	double[][][] binaryRuleScores;

	// for terminal rules
	double[][] terminalScores; // by word number then rule
	
	List<List<List<Long>>> terminalProperties;

	public UnlabeledFirstOrderFeatureHolder(WordEnumeration words, LabelEnumeration labels, RuleEnumeration rules) {
		this.wordEnum = words;
		this.labels = labels;
		this.rules = rules;
	}

	public void fillScoreArrays(List<Word> words, FeatureParameters params) {
		int wordsSize = words.size();
		int labelsSize = labels.getNumberOfLabels();
		startSpanScores = new double[wordsSize][labelsSize][labelsSize][2];
		endSpanScores = new double[wordsSize+1][labelsSize][labelsSize][2];
		splitSpanScores = new double[wordsSize][labelsSize][labelsSize][2];
		lengthSpanScores = new double[wordsSize+1][labelsSize][labelsSize][2];
		binaryRuleScores = new double[labelsSize][labelsSize][2];

		terminalScores = new double[wordsSize][labelsSize];

		for(int i = 0; i < wordsSize; i++) {
			Word word = words.get(i);
			Word wordBefore = i == 0 ? null : words.get(i-1);
			Word wordAfter = i == wordsSize - 1 ? null : words.get(i+1);
			for(boolean leftHead : new boolean[]{true, false}) {
				for(int leftLabel = 0; leftLabel < labelsSize; leftLabel++) {
					for(int rightLabel = 0; rightLabel < labelsSize; rightLabel++) {

						// start score
						double score = 0;
						long propertyCode = SpanProperties.getWordPropertyCode(word, SpanProperties.WordPropertyType.FIRST);
						score += scoreBinaryProperty(propertyCode, leftLabel, rightLabel, leftHead, params);
						if(wordBefore != null) {
							propertyCode = SpanProperties.getWordPropertyCode(wordBefore, SpanProperties.WordPropertyType.BEFORE);
							score += scoreBinaryProperty(propertyCode, leftLabel, rightLabel, leftHead, params);
						}
						startSpanScores[i][leftLabel][rightLabel][leftHead ? 1 : 0] = score;

						// end score
						score = 0;
						propertyCode = SpanProperties.getWordPropertyCode(word, SpanProperties.WordPropertyType.LAST);
						score += scoreBinaryProperty(propertyCode, leftLabel, rightLabel, leftHead, params);
						if(wordAfter != null) {
							propertyCode = SpanProperties.getWordPropertyCode(wordAfter, SpanProperties.WordPropertyType.AFTER);
							score += scoreBinaryProperty(propertyCode, leftLabel, rightLabel, leftHead, params);
						}
						endSpanScores[i+1][leftLabel][rightLabel][leftHead ? 1 : 0] = score;

						// split score
						score = 0;
						if(wordBefore != null) {
							propertyCode = SpanProperties.getWordPropertyCode(wordBefore, SpanProperties.WordPropertyType.BEFORE_SPLIT);
							score += scoreBinaryProperty(propertyCode, leftLabel, rightLabel, leftHead, params);
							propertyCode = SpanProperties.getWordPropertyCode(word, SpanProperties.WordPropertyType.AFTER_SPLIT);
							score += scoreBinaryProperty(propertyCode, leftLabel, rightLabel, leftHead, params);
						}
						splitSpanScores[i][leftLabel][rightLabel][leftHead ? 1 : 0] = score;

						// length score
						int length = i+1; // from 1 to wordsSize
						propertyCode = SpanProperties.getLengthPropertyCode(length);
						lengthSpanScores[length][leftLabel][rightLabel][leftHead ? 1 : 0] = scoreBinaryProperty(propertyCode, leftLabel, rightLabel, leftHead, params);
					}
				}
			}
		}

		for(boolean leftHead : new boolean[]{true, false}) {
			for(int leftLabel = 0; leftLabel < labelsSize; leftLabel++) {
				for(int rightLabel = 0; rightLabel < labelsSize; rightLabel++) {
					binaryRuleScores[leftLabel][rightLabel][leftHead ? 1 : 0] = params.getScore(UnlabeledFeatures.getBinaryRuleFeature(leftLabel, rightLabel, leftHead));
				}
			}
		}

		terminalProperties = new ArrayList<>();
		for(int i = 0; i < wordsSize; i++) {
			TLongList spanProperties = SpanProperties.getTerminalSpanProperties(words, i, wordEnum);
			
			List<List<Long>> a = new ArrayList<>();
			for(int label = 0; label < labelsSize; label++) {
				double spanScore = 0;
				ArrayList<Long> prop = new ArrayList<>();
				for(int p = 0; p < spanProperties.size(); p++) {
					long code = UnlabeledFeatures.getSpanPropertyByTerminalRuleFeature(spanProperties.get(p), label);
					spanScore += params.getScore(code);
					prop.add(code);
				}
				prop.add(UnlabeledFeatures.getTerminalRuleFeature(label));
				a.add(prop);
				spanScore += params.getScore(UnlabeledFeatures.getTerminalRuleFeature(label));
				terminalScores[i][label] = spanScore;
			}
			terminalProperties.add(a);
		}
	}

	private double scoreBinaryProperty(long spanProperty, long leftLabel, long rightLabel, boolean leftHead, FeatureParameters params) {
		return params.getScore(UnlabeledFeatures.getSpanPropertyByBinaryRuleFeature(spanProperty, leftLabel, rightLabel, leftHead))
				+ params.getScore(UnlabeledFeatures.getSpanPropertyByLabelFeature(spanProperty, leftHead ? leftLabel : rightLabel));
	}

	public double scoreTerminal(int position, int label) {
		return terminalScores[position][label];
	}

	public double scoreBinary(int start, int end, int split, int leftLabel, int rightLabel, boolean leftHead) {
		return startSpanScores[start][leftLabel][rightLabel][leftHead ? 1 : 0] + endSpanScores[end][leftLabel][rightLabel][leftHead ? 1 : 0]  + splitSpanScores[split][leftLabel][rightLabel][leftHead ? 1 : 0]  + lengthSpanScores[end-start][leftLabel][rightLabel][leftHead ? 1 : 0]  + binaryRuleScores[leftLabel][rightLabel][leftHead ? 1 : 0] ;
	}
}
