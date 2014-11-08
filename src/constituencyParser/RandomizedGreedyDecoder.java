package constituencyParser;

import gnu.trove.list.TLongList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import constituencyParser.Rule.Type;
import constituencyParser.features.FeatureParameters;
import constituencyParser.features.Features;

public class RandomizedGreedyDecoder {
	Sampler sampler;
	
	WordEnumeration wordEnum;
	LabelEnumeration labels;
	Rules rules;
	
	GreedyChange greedyChange;
	
	HashMap<Span, Double> spanScoreCache;
	
	public RandomizedGreedyDecoder(WordEnumeration words, LabelEnumeration labels, Rules rules) {
		sampler = new Sampler(words, labels, rules);
		this.wordEnum = words;
		this.labels = labels;
		this.rules = rules;
		
		this.greedyChange = new GreedyChange(labels, rules);
	}
	
	public List<Span> decode(List<Integer> words, FeatureParameters params, boolean dropout) {
		spanScoreCache = new HashMap<Span, Double>();
		
		sampler.calculateProbabilities(words, params);
		
		Set<Set<Span>> alreadyDone = new HashSet<>(); // a set of the spans we have sampled so if we sample the same again, we don't have to greedy update
		
		List<Span> best = new ArrayList<Span>();
		double bestScore = Double.NEGATIVE_INFINITY;
		for(int iteration = 0; iteration < 100; iteration++) {
			System.out.println("new iteration");
			
			List<Span> spans = sampler.sample();
			
			SpanUtilities.checkCorrectness(spans);
			
			boolean changed = true;
			double lastScore = Double.NEGATIVE_INFINITY;
			while(changed) {
				Set<Span> spansSet = new HashSet<>(spans);
				if(alreadyDone.contains(spansSet))
					break;
				else
					alreadyDone.add(spansSet);
				
				double score = score(words, spans, params, dropout);
				System.out.println("score: " + score);
				if(score <= lastScore) {
					changed = false;
				}
				lastScore = score;
				
				for(int i = 0; i < words.size(); i++) {
					List<List<Span>> options = greedyChange.makeGreedyTerminalLabelChanges(spans, i);
					
					spans = getMax(options, words, params, dropout);
				}
				
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
							List<List<Span>> update = greedyChange.makeGreedyChanges(spans, start, start + length);
							
							spans = getMax(update, words, params, dropout);
						}
					}
				}
			}
			
			double score = score(words, spans, params, dropout);
			if(score > bestScore) {
				best = new ArrayList<>(spans);
				bestScore = score;
			}
		}
		return best;
	}
	
	public List<Span> decodeNoGreedy(List<Integer> words, FeatureParameters params, boolean dropout) {
		sampler.calculateProbabilities(words, params);
		List<List<Span>> options = new ArrayList<>();
		for(int i = 0; i < 100; i++) {
			options.add(sampler.sample());
		}
		return getMax(options, words, params, dropout);
	}
	
	private List<Span> getMax(List<List<Span>> options, List<Integer> words, FeatureParameters params, boolean dropout) {
		double bestScore = Double.NEGATIVE_INFINITY;
		List<Span> best = null;
		for(List<Span> option : options) {
			double score = score(words, option, params, dropout);
			if(score > bestScore) {
				bestScore = score;
				best = option;
			}
		}
		return best;
	}
	
	private double score(List<Integer> words, List<Span> spans, FeatureParameters params, boolean dropout) {
		double score = 0;
		for(Span s : spans) {
			if(!rules.isExistingRule(s.getRule()))
				return Double.NEGATIVE_INFINITY;
		}
		for(Span s : spans) {
			if(spanScoreCache.containsKey(s)) {
				score += spanScoreCache.get(s);
				continue;
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
			
			spanScoreCache.put(s, spanScore);
			score += spanScore;
		}
		return score;
	}
}
