package constituencyParser.unlabeled;

import java.util.List;

import constituencyParser.Word;
import constituencyParser.features.FeatureParameters;

public class UnlabeledFirstOrderFeatureHolder {
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
	
	public void fillScoreArrays(List<Word> words, FeatureParameters params) {
		
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
