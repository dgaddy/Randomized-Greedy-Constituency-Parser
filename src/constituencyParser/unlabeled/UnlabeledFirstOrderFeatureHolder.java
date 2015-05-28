package constituencyParser.unlabeled;

import gnu.trove.list.TLongList;

import java.util.List;

import constituencyParser.*;
import constituencyParser.features.FeatureParameters;
import constituencyParser.features.SpanProperties;

public class UnlabeledFirstOrderFeatureHolder {
	WordEnumeration wordEnum;
	LabelEnumeration labels;

	// for binary rules
	double[][][][] startSpanScores; // indexed by word number in sentence then rule - value is the score of start span property features
	double[][][][] endSpanScores; // indexed by end word number (exclusive) then rule
	double[][][][] splitSpanScores;
	double[][][][] lengthSpanScores; // indexed by length then rule
	double[][][] binaryRuleScores;
	double[][][][] dependencyScores; // parent index, child index, parent POS, childPOS

	// for terminal rules
	double[][] terminalScores; // by word number then rule

	public UnlabeledFirstOrderFeatureHolder(WordEnumeration words, LabelEnumeration labels) {
		this.wordEnum = words;
		this.labels = labels;
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
		
		dependencyScores = new double[wordsSize][wordsSize][labelsSize][labelsSize];

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
		
		for(int i = 0; i < wordsSize; i++) {
			TLongList spanProperties = SpanProperties.getTerminalSpanProperties(words, i, wordEnum);
			
			for(int label = 0; label < labelsSize; label++) {
				double spanScore = 0;
				for(int p = 0; p < spanProperties.size(); p++) {
					long code = UnlabeledFeatures.getSpanPropertyByTerminalRuleFeature(spanProperties.get(p), label);
					spanScore += params.getScore(code);
				}
				spanScore += params.getScore(UnlabeledFeatures.getTerminalRuleFeature(label));
				terminalScores[i][label] = spanScore;
			}
		}
		
		for(int p = 0; p < wordsSize; p++) {
			for(int c = 0; c < wordsSize; c++) {
				for(int parentLabel = 0; parentLabel < labelsSize; parentLabel++) {
					for(int childLabel = 0; childLabel < labelsSize; childLabel++) {
						int parentWord = words.get(p).getId();
						int childWord = words.get(c).getId();
						double score = params.getScore(UnlabeledFeatures.getLexicalDependencyFeature(parentWord, childWord));
						score += params.getScore(UnlabeledFeatures.getLexPosDependencyFeature(parentWord, childLabel));
						score += params.getScore(UnlabeledFeatures.getPosLexDependencyFeature(parentLabel, childWord));
						dependencyScores[p][c][parentLabel][childLabel] = score;
					}
				}
			}
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
	
	public double scoreDependencyFeature(int parentWordPosition, int childWordPosition, int parentPOS, int childPOS) {
		return dependencyScores[parentWordPosition][childWordPosition][parentPOS][childPOS];
	}
}
