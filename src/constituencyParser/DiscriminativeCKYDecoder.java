package constituencyParser;

import java.util.ArrayList;
import java.util.List;

import constituencyParser.features.FeatureParameters;
import constituencyParser.features.FirstOrderFeatureHolder;

/**
 * A discriminative CKY decoder based on Less Grammar, More Features, David Hall, Greg Durrett and Dan
Klein, ACL 14 (http://www.cs.berkeley.edu/ dlwh/papers/spanparser.pdf)
 */
public class DiscriminativeCKYDecoder implements Decoder {
	private static final double PRUNE_THRESHOLD = 10;
	
	WordEnumeration wordEnum;
	LabelEnumeration labels;
	RuleEnumeration rules;
	
	FirstOrderFeatureHolder firstOrderFeatures;
	
	double[][][] scores;
	Span[][][] spans;
	
	List<Span> usedSpans;
	
	public DiscriminativeCKYDecoder(WordEnumeration words, LabelEnumeration labels, RuleEnumeration rules) {
		this.wordEnum = words;
		this.labels = labels;
		this.rules = rules;
		
		firstOrderFeatures = new FirstOrderFeatureHolder(words, labels, rules);
	}
	
	double lastScore = 0;
	
	public List<Span> decode(List<Word> words, FeatureParameters params) {
		firstOrderFeatures.fillScoreArrays(words, params);
		
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
			for(int label = 0; label < labelsSize; label++) {
				Span span = new Span(i, label);
				scores[i][i+1][label] = firstOrderFeatures.scoreTerminal(i, label);
				spans[i][i+1][label] = span;
			}
			
			doUnary(words, i, i+1, params);
		}
		
		double[][] max = new double[wordsSize][wordsSize+1];
		for(int length = 2; length < wordsSize + 1; length++) {
			for(int start = 0; start < wordsSize + 1 - length; start++) {
				max[start][start+length] = Double.NEGATIVE_INFINITY;
				for(int split = 1; split < length; split++) {
					int end = start + length;
					int splitLocation = start + split;
					
					for(int r = 0; r < rulesSize; r++) {

						Rule rule = rules.getBinaryRule(r);
						int label = rule.getLabel();
						
						double leftChildScore = scores[start][splitLocation][rule.getLeft()];
						double rightChildScore = scores[splitLocation][end][rule.getRight()];
						if(leftChildScore < max[start][splitLocation] - PRUNE_THRESHOLD || rightChildScore < max[splitLocation][end] - PRUNE_THRESHOLD)
							continue;
						
						double spanScore = firstOrderFeatures.scoreBinary(start, end, splitLocation, r);
						
						double fullScore = spanScore + leftChildScore + rightChildScore;
						if(fullScore > scores[start][end][label]) {
							scores[start][end][label] = fullScore;
							Span span = new Span(start, end, splitLocation, rule);
							span.setLeft(spans[start][splitLocation][rule.getLeft()]);
							span.setRight(spans[splitLocation][end][rule.getRight()]);
							spans[start][end][label] = span;
							
							if(fullScore > max[start][end]) {
								max[start][end] = fullScore;
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
		
		lastScore = bestScore;
		
		return usedSpans;
	}
	
	public double getLastScore() {
		return lastScore;
	}
	
	private void doUnary(List<Word> words, int start, int end, FeatureParameters parameters) {
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
			double spanScore = firstOrderFeatures.scoreUnary(start, end, i);
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
		if(costAugmenting)
			throw new UnsupportedOperationException();
	}

	@Override
	public void setSecondOrder(boolean secondOrder) {
		if(secondOrder)
			throw new UnsupportedOperationException();
	}
}
