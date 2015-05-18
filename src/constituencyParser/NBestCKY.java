package constituencyParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import constituencyParser.Rule.Type;
import constituencyParser.features.FeatureParameters;
import constituencyParser.features.FirstOrderFeatureHolder;

public class NBestCKY {
	private static final int N = 10;
	
	WordEnumeration wordEnum;
	LabelEnumeration labels;
	RuleEnumeration rules;
	
	FirstOrderFeatureHolder firstOrderFeatures;
	
	double[][][][] scores;
	Span[][][][] spans;
	
	boolean costAugmenting;
	int[][] goldLabels;
	int[][] goldUnaryLabels;
	
	public NBestCKY(WordEnumeration words, LabelEnumeration labels, RuleEnumeration rules) {
		this.wordEnum = words;
		this.labels = labels;
		this.rules = rules;
		
		firstOrderFeatures = new FirstOrderFeatureHolder(words, labels, rules);
	}
	
	double lastScore = 0;
	
	public List<List<Span>> decode(List<Word> words, FeatureParameters params) {
		firstOrderFeatures.fillScoreArrays(words, params);
		
		int wordsSize = words.size();
		int labelsSize = labels.getNumberOfLabels();
		int rulesSize = rules.getNumberOfBinaryRules();
		
		
		scores = new double[wordsSize][wordsSize+1][labelsSize][N];
		for(int i = 0; i < wordsSize; i++)
			for(int j = 0; j < wordsSize+1; j++)
				for(int k = 0; k < labelsSize; k++)
					for(int l = 0; l < N; l++)
						scores[i][j][k][l] = Double.NEGATIVE_INFINITY;
		spans = new Span[wordsSize][wordsSize+1][labelsSize][N];
		
		for(int i = 0; i < wordsSize; i++) {
			for(int label = 0; label < labelsSize; label++) {
				Span span = new Span(i, label);
				double score = firstOrderFeatures.scoreTerminal(i, label);
				if(costAugmenting && label != goldLabels[i][i+1])
					score += 1;
				scores[i][i+1][label][0] = score;
				spans[i][i+1][label][0] = span;
			}
			
			doUnary(words, i, i+1, Double.POSITIVE_INFINITY, params);
			
			for(int label = 0; label < labelsSize; label++) {
				for(int j = 0; j < N-1; j++) {
					if(spans[i][i+1][label][j] != null && spans[i][i+1][label][j].equalsWithChildren(spans[i][i+1][label][j+1])) {
						System.out.println("terminal after unaries");
					}
				}
			}
		}
		
		double[][] max = new double[wordsSize][wordsSize+1];
		for(int length = 2; length < wordsSize + 1; length++) {
			for(int start = 0; start < wordsSize + 1 - length; start++) {
				int end = start + length;
				max[start][end] = Double.NEGATIVE_INFINITY;
				
				for(int split = 1; split < length; split++) {
					int splitLocation = start + split;

					double[][] leftScore = scores[start][splitLocation];
					double[][] rightScore = scores[splitLocation][end];
					
					for(int r = 0; r < rulesSize; r++) {

						Rule rule = rules.getBinaryRule(r);
						int label = rule.getLabel();
						
						double spanScore = firstOrderFeatures.scoreBinary(start, end, splitLocation, r);
						
						if(costAugmenting && label != goldLabels[start][end])
							spanScore += 1;
						
						for(int i = 0; i < N; i++) {
							for(int j = 0; j < N; j++) {
								double leftChildScore = leftScore[rule.getLeft()][i];
								double rightChildScore = rightScore[rule.getRight()][j];
								double fullScore = spanScore + leftChildScore + rightChildScore;
								if(fullScore > scores[start][end][label][N-1]) {
									Span span = new Span(start, end, splitLocation, rule);
									span.setLeft(spans[start][splitLocation][rule.getLeft()][i]);
									span.setRight(spans[splitLocation][end][rule.getRight()][j]);
									addToTop(span, fullScore, scores[start][end][label], spans[start][end][label]);
									
									if(fullScore > max[start][end]) {
										max[start][end] = fullScore;
									}
								}
								else break;
							}
						}
					}
				}
				
				for(int label = 0; label < labelsSize; label++) {
					for(int j = 0; j < N-1; j++) {
						if(spans[start][end][label][j] != null && spans[start][end][label][j].equalsWithChildren(spans[start][end][label][j+1])) {
							System.out.println("binary before unaries");
						}
					}
				}

				doUnary(words, start, end, max[start][end], params);
				
				for(int label = 0; label < labelsSize; label++) {
					for(int j = 0; j < N-1; j++) {
						if(spans[start][end][label][j] != null && spans[start][end][label][j].equalsWithChildren(spans[start][end][label][j+1])) {
							System.out.println("binary after unaries");
						}
					}
				}
			}
		}
		
		double[] bestScores = new double[N];
		Arrays.fill(bestScores, Double.NEGATIVE_INFINITY);
		Span[] bestSpans = new Span[N];
		for(Integer topLabel : labels.getTopLevelLabelIds()) {
			for(int i = 0; i < N; i++) {
				addToTop(spans[0][wordsSize][topLabel][i], scores[0][wordsSize][topLabel][i], bestScores, bestSpans);
			}
		}
		
		List<List<Span>> result = new ArrayList<>();
		for(int i = 0; i < N; i++) {
			if(bestSpans[i] != null) {
				if(i < N-1 && bestSpans[i].equalsWithChildren(bestSpans[i+1]))
					System.out.println("fjdkfjsfj");
				
				List<Span> usedSpans = new ArrayList<>();
				getUsedSpans(bestSpans[i], usedSpans);
				result.add(usedSpans);
			}
		}
		
		lastScore = bestScores[0];
		
		return result;
	}
	
	public double getLastScore() {
		return lastScore;
	}
	
	private void doUnary(List<Word> words, int start, int end, double thresh, FeatureParameters parameters) {
		int numUnaryRules = rules.getNumberOfUnaryRules();
		int numLabels = labels.getNumberOfLabels();
		
		double[][] unaryScores = new double[numLabels][N];
		for(int i = 0; i < numLabels; i++)
			Arrays.fill(unaryScores[i], Double.NEGATIVE_INFINITY);
		Span[][] unarySpans = new Span[numLabels][N];
		
		for(int i = 0; i < numUnaryRules; i++) {
			Rule rule = rules.getUnaryRule(i);
			int label = rule.getLabel();
			
			double spanScore = firstOrderFeatures.scoreUnary(start, end, i);
			if(costAugmenting && label != goldUnaryLabels[start][end])
				spanScore += 1;
			
			for(int c = 0; c < N; c++) {
				double childScore = scores[start][end][rule.getLeft()][c];
				double fullScore = childScore + spanScore;
				
				if(fullScore > unaryScores[label][N-1]) {
					Span span = new Span(start, end, rule);
					span.setLeft(spans[start][end][rule.getLeft()][c]);
					addToTop(span, fullScore, unaryScores[label], unarySpans[label]);
				}
				else break;
			}
		}
		
		for(int i = 0; i < numLabels; i++) {
			for(int j = 0; j < N; j++) {
				if(!addToTop(unarySpans[i][j], unaryScores[i][j], scores[start][end][i], spans[start][end][i]))
					break;
			}
		}
	}
	
	private boolean addToTop(Span span, double score, double[] previousScores, Span[] previousSpans) {
		boolean changed = false;
		for(int i = 0; i < N; i++) {
			if(score > previousScores[i]) {
				double s = previousScores[i];
				previousScores[i] = score;
				score = s;
				Span t = previousSpans[i];
				previousSpans[i] = span;
				span = t;
				changed = true;
			}
		}
		return changed;
	}
	
	private void getUsedSpans(Span span, List<Span> usedSpans) {
		usedSpans.add(span);
		if(span.getRule().getType() == Rule.Type.BINARY) {
			getUsedSpans(span.getLeft(), usedSpans);
			getUsedSpans(span.getRight(), usedSpans);
		}
		else if(span.getRule().getType() == Rule.Type.UNARY) {
			getUsedSpans(span.getLeft(), usedSpans);
		}
	}

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
}
