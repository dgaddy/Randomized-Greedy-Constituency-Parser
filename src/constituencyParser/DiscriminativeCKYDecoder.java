package constituencyParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import constituencyParser.Rule.Type;
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
	
	boolean costAugmenting;
	int[][] goldLabels;
	int[][] goldUnaryLabels;
	
	public DiscriminativeCKYDecoder(WordEnumeration words, LabelEnumeration labels, RuleEnumeration rules) {
		this.wordEnum = words;
		this.labels = labels;
		this.rules = rules;
		
		firstOrderFeatures = new FirstOrderFeatureHolder(words, labels, rules);
	}
	
	double lastScore = 0;
	
	public List<Span> decode(List<Word> words, FeatureParameters params) {
		//System.out.println("Check 1");
		firstOrderFeatures.fillScoreArrays(words, params);
		//System.out.println("Check 2");
		
		int wordsSize = words.size();
		int labelsSize = labels.getNumberOfLabels();
		int rulesSize = rules.getNumberOfBinaryRules();
		
		//System.out.println(labelsSize + " " + rulesSize);
		
		scores = new double[wordsSize][wordsSize+1][labelsSize];
		for(int i = 0; i < wordsSize; i++)
			for(int j = 0; j < wordsSize+1; j++)
				for(int k = 0; k < labelsSize; k++)
					scores[i][j][k] = Double.NEGATIVE_INFINITY;
		spans = new Span[wordsSize][wordsSize+1][labelsSize];
		
		for(int i = 0; i < wordsSize; i++) {
			for(int label = 0; label < labelsSize; label++) {
				Span span = new Span(i, label);
				double score = firstOrderFeatures.scoreTerminal(i, label);
				if(costAugmenting && label != goldLabels[i][i+1])
					score += 1;
				scores[i][i+1][label] = score;
				spans[i][i+1][label] = span;
			}
			
			doUnary(words, i, i+1, Double.POSITIVE_INFINITY, params);
		}
		
		//System.out.println("Check 3");
		double[][] max = new double[wordsSize][wordsSize+1];
		for(int length = 2; length < wordsSize + 1; length++) {
			for(int start = 0; start < wordsSize + 1 - length; start++) {
				int end = start + length;
				max[start][end] = Double.NEGATIVE_INFINITY;
				//int cnt = 0;
				
				for(int split = 1; split < length; split++) {
					int splitLocation = start + split;
					
					double leftMax = max[start][splitLocation];
					double rightMax = max[splitLocation][end];
					
					if (leftMax + rightMax + PRUNE_THRESHOLD < max[start][end])
						continue;
					
					double[] leftScore = scores[start][splitLocation];
					double[] rightScore = scores[splitLocation][end];

					for(int r = 0; r < rulesSize; r++) {

						Rule rule = rules.getBinaryRule(r);
						int label = rule.getLabel();
						
						double leftChildScore = leftScore[rule.getLeft()];
						double rightChildScore = rightScore[rule.getRight()];
						if(leftChildScore + PRUNE_THRESHOLD < leftMax || rightChildScore + PRUNE_THRESHOLD < rightMax
								//|| leftChildScore + rightChildScore + PRUNE_THRESHOLD < scores[start][end][label])
								|| leftChildScore + rightChildScore + PRUNE_THRESHOLD < max[start][end])
							continue;
						
						//cnt++;
						
						double spanScore = firstOrderFeatures.scoreBinary(start, end, splitLocation, r);
						
						if(costAugmenting && label != goldLabels[start][end])
							spanScore += 1;
						
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
				
				//System.out.println((cnt + 0.0) / (length - 1) / rulesSize);

				doUnary(words, start, end, max[start][end], params);
			}
		}
		//System.out.println("Check 4");
		
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
		//System.out.println("Check 5");
		//System.out.println(lastScore);
		
		return usedSpans;
	}
	
	public double getLastScore() {
		return lastScore;
	}
	
	private void doUnary(List<Word> words, int start, int end, double thresh, FeatureParameters parameters) {
		int numUnaryRules = rules.getNumberOfUnaryRules();
		int numLabels = labels.getNumberOfLabels();
		
		double[] unaryScores = new double[numLabels];
		//for(int i = 0; i < numLabels; i++)
		//	unaryScores[i] = Double.NEGATIVE_INFINITY;
		Arrays.fill(unaryScores, Double.NEGATIVE_INFINITY);
		Span[] unarySpans = new Span[numLabels];
		
		for(int i = 0; i < numUnaryRules; i++) {
			Rule rule = rules.getUnaryRule(i);
			int label = rule.getLabel();
			
			double childScore = scores[start][end][rule.getLeft()];
			
			if (childScore < scores[start][end][label] - PRUNE_THRESHOLD)
			//if (childScore + PRUNE_THRESHOLD < thresh)
				continue;
			
			double spanScore = firstOrderFeatures.scoreUnary(start, end, i);
			if(costAugmenting && label != goldUnaryLabels[start][end])
				spanScore += 1;
			
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
		this.costAugmenting = costAugmenting;
		if(!costAugmenting)
			return;
		int size = gold.getWords().size();
		goldLabels = new int[size][size+1];
		goldUnaryLabels = new int[size][size+1];
		for(int i = 0; i < size; i++) {
			for(int j = 0; j < size+1; j++) {
				goldLabels[i][j] = -1;
				goldUnaryLabels[i][j] = -1;
			}
		}
		
		for(Span s : gold.getSpans()) {
			if(s.getRule().getType() == Type.UNARY)
				goldUnaryLabels[s.getStart()][s.getEnd()] = s.getRule().getLabel();
			else
				goldLabels[s.getStart()][s.getEnd()] = s.getRule().getLabel();
		}
	}

	@Override
	public void setSecondOrder(boolean secondOrder) {
		if(secondOrder)
			throw new UnsupportedOperationException();
	}
}
