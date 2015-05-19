package constituencyParser.unlabeled;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import constituencyParser.Decoder;
import constituencyParser.LabelEnumeration;
import constituencyParser.Pruning;
import constituencyParser.Rule;
import constituencyParser.Rule.Type;
import constituencyParser.RuleEnumeration;
import constituencyParser.Span;
import constituencyParser.SpanUtilities;
import constituencyParser.SpannedWords;
import constituencyParser.Word;
import constituencyParser.WordEnumeration;
import constituencyParser.features.FeatureParameters;
import constituencyParser.features.Features;
import constituencyParser.features.FirstOrderFeatureHolder;
import constituencyParser.unlabeled.GreedyChange.ParentedSpans;

/**
 * Samples parse trees then makes greedy updates on them
 */
public class RandomizedGreedyDecoder implements Decoder {
	DiscriminativeCKYSampler sampler;
	
	WordEnumeration wordEnum;
	LabelEnumeration labels;
	RuleEnumeration rules;
	
	GreedyChange greedyChange;
	
	FirstOrderFeatureHolder firstOrderFeatures;
	Pruning pruning;
	
	Set<Set<Span>> alreadySeenSpans;
	
	DecoderTask[] decoderTasks;
	ExecutorService executorService;
	ExecutorCompletionService<List<Span>> completionService;
	
	boolean doSecondOrder = true;
	
	boolean costAugmenting = false;
	boolean[][] goldLabels; // gold span info used for cost augmenting: indices are start and end, value is label, -1 if no span for a start and end
	
	int numberSampleIterations = 50;
	
	public RandomizedGreedyDecoder(WordEnumeration words, LabelEnumeration labels, RuleEnumeration rules, int threads, BinaryHeadPropagation headPropagation) {
		this.wordEnum = words;
		this.labels = labels;
		this.rules = rules;
		
		firstOrderFeatures = new FirstOrderFeatureHolder(words, labels, rules);
		sampler = new DiscriminativeCKYSampler(words, labels, rules, firstOrderFeatures);
		
		this.greedyChange = new GreedyChange(labels, headPropagation);
		
		executorService = Executors.newFixedThreadPool(threads);
		completionService = new ExecutorCompletionService<>(executorService);
		decoderTasks = new DecoderTask[threads];
		for(int i = 0; i < threads; i++) {
			decoderTasks[i] = new DecoderTask();
		}
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
		if(!costAugmenting)
			return;
		int size = gold.getWords().size();
		goldLabels = new boolean[size][size+1];
		for(int i = 0; i < size; i++) {
			for(int j = 0; j < size+1; j++) {
				goldLabels[i][j] = false;
			}
		}
		
		for(Span s : gold.getSpans()) {
			if(s.getRule().getType() != Type.UNARY)
				goldLabels[s.getStart()][s.getEnd()] = true;
		}
	}
	
	/**
	 * Set number of random restarts to do using new sample
	 * @param iterations
	 */
	public void setNumberSampleIterations(int iterations) {
		numberSampleIterations = iterations;
	}
	
	List<Word> words;
	FeatureParameters params;
	int numberIterationsStarted = 0;
	Object lockObject = new Object();
	
	/**
	 * Returns a parse tree in the form of a list of spans
	 */
	public List<Span> decode(List<Word> words, FeatureParameters params) {
		this.words = words;
		this.params = params;
		numberIterationsStarted = 0;
		
		firstOrderFeatures.fillScoreArrays(words, params);
		
		sampler.setCostAugmenting(costAugmenting, goldLabels);
		
		pruning = sampler.calculateProbabilities(words);
		
		//System.out.println("calculated probabilities");
		
		alreadySeenSpans = Collections.newSetFromMap(new ConcurrentHashMap<Set<Span>, Boolean>()); // a set of the spans we have sampled so if we sample the same again, we don't have to greedy update
		
		for(DecoderTask task : decoderTasks) {
			completionService.submit(task);
		}
		
		List<ParentedSpans> bestOptions = new ArrayList<>();
		for(int i = 0; i < decoderTasks.length; i++) {
			try {
				List<Span> result = completionService.take().get();
				bestOptions.add(new ParentedSpans(result, SpanUtilities.getParents(result)));
			}
			catch(ExecutionException e) {
				System.out.println("Exception in decoder worker thread");
				e.getCause().printStackTrace();
				e.printStackTrace();
			}
			catch(InterruptedException e) {}
		}
		
		MaxResult result = getMax(bestOptions, words, params);
		lastScore = result.score;
		
		return result.spans;
	}
	
	double lastScore = 0;
	public double getLastScore() {
		return lastScore;
	}
	
	private class DecoderTask implements Callable<List<Span>> {
		@Override
		public List<Span> call() {
			List<Span> best = new ArrayList<Span>();
			double bestScore = Double.NEGATIVE_INFINITY;
			
			while(true) {
				synchronized(lockObject) {
					if(numberIterationsStarted < numberSampleIterations) {
						numberIterationsStarted++;
					}
					else {
						break;
					}
				}
				//System.out.println("new iteration");
				List<Span> spans = sampler.sample();
				SpanUtilities.connectChildren(spans);
				if(pruning.containsPruned(spans))
					throw new RuntimeException();
				if(!SpanUtilities.usesOnlyExistingRules(spans, rules))
					throw new RuntimeException();
				
				if(spans.size() > 0) { // sometimes sampler fails to find valid sample and returns empty list
					SpanUtilities.checkCorrectness(spans);
					
					boolean changed = true;
					double lastScore = Double.NEGATIVE_INFINITY;
					while(changed) {
						Set<Span> spansSet = new HashSet<>(spans);
						if(alreadySeenSpans.contains(spansSet))
							break;
						else
							alreadySeenSpans.add(spansSet);
						
						double score = score(words, spans, params);
						//System.out.println("score: " + score);
						if(score <= lastScore) {
							changed = false;
						}
						lastScore = score;
						
						// terminal labels
						for(int i = 0; i < words.size(); i++) {
							List<ParentedSpans> options = greedyChange.makeGreedyTerminalChanges(spans, i, pruning, words);
							
							spans = getMax(options, words, params).spans;
							
							if(pruning.containsPruned(spans))
								throw new RuntimeException();
							if(!SpanUtilities.usesOnlyExistingRules(spans, rules))
								throw new RuntimeException();
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
									List<ParentedSpans> update = greedyChange.makeGreedyChanges(spans, start, start + length, pruning, words);
									
									// occasionally check that parents are correct
									if(update.size() > 0 && Math.random() < .005) {
										ParentedSpans ps = update.get(0);
										if(!Arrays.equals(ps.parents, SpanUtilities.getParents(ps.spans)))
											throw new RuntimeException("Parents incorrect");
									}
									
									spans = getMax(update, words, params).spans;
									
									if(pruning.containsPruned(spans))
										throw new RuntimeException();
									if(!SpanUtilities.usesOnlyExistingRules(spans, rules))
										throw new RuntimeException();
								}
							}
						}
						
						if(pruning.containsPruned(spans))
							throw new RuntimeException();
						if(!SpanUtilities.usesOnlyExistingRules(spans, rules))
							throw new RuntimeException();
					}
				}
				double score = score(words, spans, params);
				if(score > bestScore) {
					best = new ArrayList<>(spans);
					bestScore = score;
				}
			}
			
			return best;
		}
	}
	
	/**
	 * Decodes by only sampling and getting the maximum score without any greedy updates
	 * @param words
	 * @param params
	 * @param dropout
	 * @return
	 */
	public List<Span> decodeNoGreedy(List<Word> words, FeatureParameters params) {
		firstOrderFeatures.fillScoreArrays(words, params);
		sampler.calculateProbabilities(words);
		List<ParentedSpans> options = new ArrayList<>();
		for(int i = 0; i < 100; i++) {
			List<Span> s = sampler.sample();
			options.add(new ParentedSpans(s, SpanUtilities.getParents(s)));
		}
		return getMax(options, words, params).spans;
	}
	
	private class MaxResult {
		List<Span> spans;
		double score;
		public MaxResult(List<Span> spans, double score) {
			this.spans = spans;
			this.score = score;
		}
	}
	
	/**
	 * Choose the maximum list of spans (parse tree) from options
	 * @param options a list of different options where each option has a list of spans (and parents stored so we don't have to recompute them)
	 * @param words
	 * @param params
	 * @param dropout
	 * @return
	 */
	private MaxResult getMax(List<ParentedSpans> options, List<Word> words, FeatureParameters params) {
		double bestScore = Double.NEGATIVE_INFINITY;
		List<Span> best = null;
		if(options.size() == 0)
			throw new RuntimeException("No options given to max");
		for(ParentedSpans option : options) {
			double score = score(words, option.spans, option.parents, params);
			if(score >= bestScore) {
				bestScore = score;
				best = option.spans;
			}
		}
		return new MaxResult(best, bestScore);
	}
	
	private double score(List<Word> words, List<Span> spans, FeatureParameters params) {
		return score(words, spans, SpanUtilities.getParents(spans), params);
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
	double score(List<Word> words, List<Span> spans, int[] parents, FeatureParameters params) {
		double score = 0;
		for(int j = 0; j < spans.size(); j++) {
			Span s = spans.get(j);
			Rule rule = s.getRule();
			if(pruning.isPruned(s.getStart(), s.getEnd(), rule.getLabel()))
				return Double.NEGATIVE_INFINITY;
			
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
			score += spanScore;
			
			if(costAugmenting && !goldLabels[s.getStart()][s.getEnd()]) {
				score += 1;
			}
		}
		
		for(long feature : Features.getLexicalDependencyFeatures(spans, parents)) {
			score += params.getScore(feature);
		}
		
		if(doSecondOrder) {
			for(long feature : Features.getAllHigherOrderFeatures(words, spans, parents, rules, wordEnum, labels)) {
				score += params.getScore(feature);
			}
		}
		
		return score;
	}
}
