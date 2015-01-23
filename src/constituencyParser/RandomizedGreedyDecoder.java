package constituencyParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import constituencyParser.GreedyChange.ParentedSpans;
import constituencyParser.Rule.Type;
import constituencyParser.features.FeatureParameters;
import constituencyParser.features.Features;
import constituencyParser.features.FirstOrderFeatureHolder;

/**
 * Samples parse trees then makes greedy updates on them
 */
public class RandomizedGreedyDecoder implements Decoder {
	DiscriminitiveCKYSampler sampler;
	
	WordEnumeration wordEnum;
	LabelEnumeration labels;
	RuleEnumeration rules;
	
	GreedyChange greedyChange;
	
	FirstOrderFeatureHolder firstOrderFeatures;
	
	/**
	 * Holds a span and its parent label.  Used for cacheing
	 *
	 */
	class SpanAndParent {
		Span span;
		int parentLabel;
		
		public SpanAndParent(Span s, int parentLabel) {
			this.span = s;
			this.parentLabel = parentLabel;
		}
		
		@Override
		public int hashCode() {
			return parentLabel + span.hashCode();
		}
		
		@Override
		public boolean equals(Object other) {
			if(other instanceof SpanAndParent) {
				SpanAndParent sp = (SpanAndParent)other;
				return sp.span.equals(this.span) && sp.parentLabel == this.parentLabel; 
			}
			return false;
		}
	}
	
	HashMap<Span, Double> firstOrderSpanScoreCache;
	
	boolean doSecondOrder = true;
	
	boolean costAugmenting = false;
	int[][] goldLabels; // gold span info used for cost augmenting: indices are start and end, value is label, -1 if no span for a start and end
	
	int numberSampleIterations = 50;
	
	public RandomizedGreedyDecoder(WordEnumeration words, LabelEnumeration labels, RuleEnumeration rules) {
		this.wordEnum = words;
		this.labels = labels;
		this.rules = rules;
		
		firstOrderFeatures = new FirstOrderFeatureHolder(words, labels, rules);
		sampler = new DiscriminitiveCKYSampler(words, labels, rules, firstOrderFeatures);
		
		this.greedyChange = new GreedyChange(labels, rules);
	}
	
	/**
	 * For when using CKYSampler instead of DiscriminitiveCKYDecoder
	 * @param trainingData
	 */
	public void samplerDoCounts(List<SpannedWords> trainingData) {
		//sampler.doCounts(trainingData);
	}
	
	/**
	 * Do second order features
	 */
	public void setSecondOrder(boolean secondOrder) {
		this.doSecondOrder = secondOrder;
	}
	
	/**
	 * If set to true, adds one to score for each incorrect span.  Used only during training.
	 */
	public void setCostAugmenting(boolean costAugmenting, SpannedWords gold) {
		this.costAugmenting = costAugmenting;
		int size = gold.getWords().size();
		goldLabels = new int[size][size+1];
		for(int i = 0; i < size; i++)
			for(int j = 0; j < size+1; j++)
				goldLabels[i][j] = -1;
		
		for(Span s : gold.getSpans()) {
			goldLabels[s.getStart()][s.getEnd()] = s.getRule().getLabel();
		}
	}
	
	/**
	 * Set number of random restarts to do using new sample
	 * @param iterations
	 */
	public void setNumberSampleIterations(int iterations) {
		numberSampleIterations = iterations;
	}
	
	double lastScore = 0;
	/**
	 * Returns a parse tree in the form of a list of spans
	 */
	public List<Span> decode(List<Integer> words, FeatureParameters params, boolean dropout) {
		firstOrderSpanScoreCache = new HashMap<Span, Double>(30000, .5f); // usually holds less than 15000 items
		
		firstOrderFeatures.fillScoreArrays(words, params, dropout);
		
		sampler.setCostAugmenting(costAugmenting, goldLabels);
		
		sampler.calculateProbabilities(words);
		
		//System.out.println("calculated probabilities");
		
		Set<Set<Span>> alreadyDone = new HashSet<>(); // a set of the spans we have sampled so if we sample the same again, we don't have to greedy update
		
		List<Span> best = new ArrayList<Span>();
		double bestScore = Double.NEGATIVE_INFINITY;
		int numberOfUpdates = 0;
		
		for(int iteration = 0; iteration < numberSampleIterations && numberOfUpdates < numberSampleIterations; iteration++) {
			//System.out.println("new iteration");
			
			List<Span> spans = sampler.sample();
			
			if(spans.size() > 0) { // sometimes sampler fails to find valid sample and returns empty list
				SpanUtilities.checkCorrectness(spans);
				
				boolean changed = true;
				double lastScore = Double.NEGATIVE_INFINITY;
				while(changed) {
					Set<Span> spansSet = new HashSet<>(spans);
					if(alreadyDone.contains(spansSet))
						break;
					else
						alreadyDone.add(spansSet);
					
					numberOfUpdates++;
					
					double score = score(words, spans, params, dropout);
					//System.out.println("score: " + score);
					if(score <= lastScore) {
						changed = false;
					}
					lastScore = score;
					
					// terminal labels
					for(int i = 0; i < words.size(); i++) {
						List<ParentedSpans> options = greedyChange.makeGreedyLabelChanges(spans, i, i+1, false);
						
						spans = getMax(options, words, params, dropout);
					}
					
					// other spans
					for(int length = 1; length < words.size() + 1; length++) {
						for(int start = 0; start < words.size() - length + 1; start++) {
							boolean exists = false; // if there is actually a span with this start and length
							for(int i = 0; i < spans.size(); i++) {
								Span s = spans.get(i);
								if(s.getStart() == start && s.getEnd() == start + length) {
									exists = true;
								}
							}
							if(exists) {
								List<ParentedSpans> update = greedyChange.makeGreedyChanges(spans, start, start + length);
								
								spans = getMax(update, words, params, dropout);
							}
						}
					}
					
					// iterate top level again with constraint to top level label in training set
					List<ParentedSpans> options = greedyChange.makeGreedyLabelChanges(spans, 0, words.size(), true);
					spans = getMax(options, words, params, dropout);
				}
			}
			
			double score = score(words, spans, params, dropout);
			if(score > bestScore) {
				best = new ArrayList<>(spans);
				bestScore = score;
			}
		}
		lastScore = bestScore;
		return best;
	}
	
	/**
	 * Gets the score of the last result returned by decode
	 * @return
	 */
	public double getLastScore() {
		return lastScore;
	}
	
	/**
	 * Decodes by only sampling and getting the maximum score without any greedy updates
	 * @param words
	 * @param params
	 * @param dropout
	 * @return
	 */
	public List<Span> decodeNoGreedy(List<Integer> words, FeatureParameters params, boolean dropout) {
		firstOrderFeatures.fillScoreArrays(words, params, dropout);
		sampler.calculateProbabilities(words);
		List<ParentedSpans> options = new ArrayList<>();
		for(int i = 0; i < 100; i++) {
			List<Span> s = sampler.sample();
			options.add(new ParentedSpans(s, SpanUtilities.getParents(s)));
		}
		return getMax(options, words, params, dropout);
	}
	
	/**
	 * Choose the maximum list of spans (parse tree) from options
	 * @param options a list of different options where each option has a list of spans (and parents stored so we don't have to recompute them)
	 * @param words
	 * @param params
	 * @param dropout
	 * @return
	 */
	private List<Span> getMax(List<ParentedSpans> options, List<Integer> words, FeatureParameters params, boolean dropout) {
		double bestScore = Double.NEGATIVE_INFINITY;
		List<Span> best = null;
		for(ParentedSpans option : options) {
			double score = score(words, option.spans, option.parents, params, dropout);
			if(score >= bestScore) {
				bestScore = score;
				best = option.spans;
			}
		}
		return best;
	}
	
	private double score(List<Integer> words, List<Span> spans, FeatureParameters params, boolean dropout) {
		return score(words, spans, SpanUtilities.getParents(spans), params, dropout);
	}
	
	/**
	 * Score a parse tree (in the form of a list of Spans)
	 * @param words
	 * @param spans
	 * @param parents
	 * @param params
	 * @param dropout
	 * @return
	 */
	double score(List<Integer> words, List<Span> spans, int[] parents, FeatureParameters params, boolean dropout) {
		double score = 0;
		
		for(int j = 0; j < spans.size(); j++) {
			Span s = spans.get(j);
			Rule rule = s.getRule();
			if(firstOrderSpanScoreCache.containsKey(s)) {
				score += firstOrderSpanScoreCache.get(s);
				continue;
			}
			else {
				double spanScore = 0;
				if(rule.getType() == Type.BINARY) {
					int ruleId = rules.getBinaryId(rule);
					if(ruleId == -1)
						return Double.NEGATIVE_INFINITY;
					spanScore = firstOrderFeatures.scoreBinary(s.getStart(), s.getEnd(), s.getSplit(), ruleId);
				}
				else if(rule.getType() == Type.UNARY) {
					int ruleId = rules.getUnaryId(rule);
					if(ruleId == -1)
						return Double.NEGATIVE_INFINITY;
					spanScore = firstOrderFeatures.scoreUnary(s.getStart(), s.getEnd(), ruleId);
				}
				else {// terminal
					spanScore = firstOrderFeatures.scoreTerminal(s.getStart(), rule.getLabel());
				}
				firstOrderSpanScoreCache.put(s, spanScore);
				score += spanScore;
			}
			
			if(costAugmenting && goldLabels[s.getStart()][s.getEnd()] != s.getRule().getLabel()) {
				score += 1;
			}
			
			// second order
			if(doSecondOrder && parents[j] != -1) {
				double spanScore2 = 0;
				
				Rule parentRule = spans.get(parents[j]).getRule();

				if(rule.getType() == Rule.Type.UNARY) {
					long code = Features.getSecondOrderRuleFeature(rule.getLeft(), rule.getLabel(), parentRule.getLabel());
					spanScore2 += params.getScore(code, dropout);
				}
				else if(rule.getType() == Rule.Type.BINARY) {
					long code = Features.getSecondOrderRuleFeature(rule.getLeft(), rule.getLabel(), parentRule.getLabel());
					spanScore2 += params.getScore(code, dropout);
					code = Features.getSecondOrderRuleFeature(rule.getRight(), rule.getLabel(), parentRule.getLabel());
					spanScore2 += params.getScore(code, dropout);
				}
				
				score += spanScore2;
			}
		}
		return score;
	}
}
