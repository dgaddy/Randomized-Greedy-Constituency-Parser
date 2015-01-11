package constituencyParser;

import gnu.trove.list.TLongList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import constituencyParser.GreedyChange.ParentedSpans;
import constituencyParser.features.FeatureParameters;
import constituencyParser.features.Features;

public class RandomizedGreedyDecoder implements Decoder {
	DiscriminitiveCKYSampler sampler;
	
	WordEnumeration wordEnum;
	LabelEnumeration labels;
	Rules rules;
	
	GreedyChange greedyChange;
	
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
	
	HashMap<SpanAndParent, Double> spanScoreCache;
	
	boolean doSecondOrder = true;
	
	boolean costAugmenting = false;
	int[][] goldLabels; // gold span info used for cost augmenting: indices are start and end, value is label, -1 if no span for a start and end
	
	int numberSampleIterations = 50;
	
	public RandomizedGreedyDecoder(WordEnumeration words, LabelEnumeration labels, Rules rules) {
		sampler = new DiscriminitiveCKYSampler(words, labels, rules);
		this.wordEnum = words;
		this.labels = labels;
		this.rules = rules;
		
		this.greedyChange = new GreedyChange(labels, rules);
	}
	
	public void samplerDoCounts(List<SpannedWords> trainingData) {
		//sampler.doCounts(trainingData);
	}
	
	public void setSecondOrder(boolean secondOrder) {
		this.doSecondOrder = secondOrder;
	}
	
	public void setCostAugmenting(boolean costAugmenting, SpannedWords gold) {
		this.costAugmenting = costAugmenting;
		int size = gold.getWords().size();
		goldLabels = new int[size][size+1];
		for(int i = 0; i < size; i++)
			for(int j = 0; j < size+1; j++)
				goldLabels[i][j] = -1;
		
		for(Span s : gold.getSpans()) {
			goldLabels[s.getStart()][s.getEnd()] = s.getRule().getParent();
		}
	}
	
	public void setNumberSampleIterations(int iterations) {
		numberSampleIterations = iterations;
	}
	
	double lastScore = 0;
	public List<Span> decode(List<Integer> words, FeatureParameters params, boolean dropout) {
		spanScoreCache = new HashMap<SpanAndParent, Double>();
		
		sampler.setCostAugmenting(costAugmenting, goldLabels);
		
		sampler.calculateProbabilities(words, params);
		
		//System.out.println("calculated probabilities");
		
		Set<Set<Span>> alreadyDone = new HashSet<>(); // a set of the spans we have sampled so if we sample the same again, we don't have to greedy update
		
		List<Span> best = new ArrayList<Span>();
		double bestScore = Double.NEGATIVE_INFINITY;
		int numberOfUpdates = 0;
		
		for(int iteration = 0; iteration < numberSampleIterations && numberOfUpdates < numberSampleIterations; iteration++) {
			//System.out.println("new iteration");
			
			List<Span> spans = sampler.sample();
			
			/*for (Span s : spans) {
				if(s.getStart() == 0 && s.getEnd() == words.size()) {
					if(!labels.getTopLevelLabelIds().contains(s.getRule().getParent())) {
						System.out.println("sampler error");
					}
				}
			}*/
			
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
	
	public double getLastScore() {
		return lastScore;
	}
	
	public List<Span> decodeNoGreedy(List<Integer> words, FeatureParameters params, boolean dropout) {
		sampler.calculateProbabilities(words, params);
		List<ParentedSpans> options = new ArrayList<>();
		for(int i = 0; i < 100; i++) {
			List<Span> s = sampler.sample();
			options.add(new ParentedSpans(s, SpanUtilities.getParents(s)));
		}
		return getMax(options, words, params, dropout);
	}
	
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
	
	double score(List<Integer> words, List<Span> spans, int[] parents, FeatureParameters params, boolean dropout) {
		double score = 0;
		for(Span s : spans) {
			if(!rules.isExistingRule(s.getRule()))
				return Double.NEGATIVE_INFINITY;
		}
		
		for(int j = 0; j < spans.size(); j++) {
			Span s = spans.get(j);
			SpanAndParent sp = new SpanAndParent(s,parents[j]);
			if(spanScoreCache.containsKey(sp)) {
				score += spanScoreCache.get(sp);
				continue;
			}
			
			if(costAugmenting && goldLabels[s.getStart()][s.getEnd()] != s.getRule().getParent()) {
				score += 1;
			}
			
			double spanScore = 0;
			TLongList featureCodes = Features.getSpanPropertyByRuleFeatures(words, s, rules, wordEnum);
			for(int i = 0; i < featureCodes.size(); i++) {
				spanScore += params.getScore(featureCodes.get(i), dropout);
			}
			spanScore += params.getScore(Features.getRuleFeature(s.getRule(), rules), dropout);
			TLongList propertyByLabelCodes = Features.getSpanPropertyByLabelFeatures(words, s);
			for(int i = 0; i < propertyByLabelCodes.size(); i++) {
				spanScore += params.getScore(propertyByLabelCodes.get(i), dropout);
			}
			
			// second order
			if(doSecondOrder && parents[j] != -1) {
				Rule rule = s.getRule();
				Rule parentRule = spans.get(parents[j]).getRule();

				if(rule.getType() == Rule.Type.UNARY) {
					long code = Features.getSecondOrderRuleFeature(rule.getLeft(), rule.getParent(), parentRule.getParent());
					spanScore += params.getScore(code, dropout);
				}
				else if(rule.getType() == Rule.Type.BINARY) {
					long code = Features.getSecondOrderRuleFeature(rule.getLeft(), rule.getParent(), parentRule.getParent());
					spanScore += params.getScore(code, dropout);
					code = Features.getSecondOrderRuleFeature(rule.getRight(), rule.getParent(), parentRule.getParent());
					spanScore += params.getScore(code, dropout);
				}
			}
			
			spanScoreCache.put(sp, spanScore);
			score += spanScore;
		}
		return score;
	}
}
