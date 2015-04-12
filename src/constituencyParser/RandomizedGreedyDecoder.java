package constituencyParser;

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
import java.util.concurrent.Future;

import constituencyParser.GreedyChange.ConstituencyNode;
import constituencyParser.GreedyChange.ParentedSpans;
import constituencyParser.Rule.Type;
import constituencyParser.features.FeatureParameters;
import constituencyParser.features.Features;
import constituencyParser.features.FirstOrderFeatureHolder;

/**
 * Samples parse trees then makes greedy updates on them
 */
public class RandomizedGreedyDecoder implements Decoder {
	DiscriminativeCKYSampler sampler;
	public List<Span> ckySpan;
	
	WordEnumeration wordEnum;
	LabelEnumeration labels;
	RuleEnumeration rules;
	
	GreedyChange greedyChange;
	
	FirstOrderFeatureHolder firstOrderFeatures;
	Pruning pruning;
	
	//Set<Set<Span>> alreadySeenSpans;
	
	DecoderTask[] decoderTasks;
	ExecutorService executorService;
	ExecutorCompletionService<List<Span>> completionService;
	
	boolean doSecondOrder = true;
	
	boolean costAugmenting = false;
	int[][] goldLabels; // gold span info used for cost augmenting: indices are start and end, value is label, -1 if no span for a start and end
	int[][] goldUnaryLabels;
	double cost = 1.0;
	
	int numberSampleIterations = 30;
	
	public MaxResult finalResult;
	
	public RandomizedGreedyDecoder(WordEnumeration words, LabelEnumeration labels, RuleEnumeration rules, int threads) {
		this.wordEnum = words;
		this.labels = labels;
		this.rules = rules;
		
		firstOrderFeatures = new FirstOrderFeatureHolder(words, labels, rules);
		sampler = new DiscriminativeCKYSampler(words, labels, rules, firstOrderFeatures);
		
		this.greedyChange = new GreedyChange(labels, rules);
		
		executorService = Executors.newFixedThreadPool(threads);
		completionService = new ExecutorCompletionService<>(executorService);
		decoderTasks = new DecoderTask[threads];
		for(int i = 0; i < threads; i++) {
			decoderTasks[i] = new DecoderTask(i, this);
		}
	}
	
	/**
	 * For when using CKYSampler instead of DiscriminativeCKYDecoder
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
	public void setCostAugmenting(boolean costAugmenting, SpannedWords gold, double cost) {
		this.costAugmenting = costAugmenting;
		this.cost = cost;
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
	
	@Override
	/**
	 * Returns a parse tree in the form of a list of spans
	 */
	public List<Span> decode(List<Word> words, FeatureParameters params) {
		this.words = words;
		this.params = params;
		numberIterationsStarted = 0;
		finalResult = new MaxResult(null, Double.NEGATIVE_INFINITY);
		
		firstOrderFeatures.fillScoreArrays(words, params);
		
		sampler.setCostAugmenting(costAugmenting, cost, goldLabels, goldUnaryLabels);
		
		pruning = sampler.calculateProbabilities(words);
		
		//System.out.println("calculated probabilities");
		
		//alreadySeenSpans = Collections.newSetFromMap(new ConcurrentHashMap<Set<Span>, Boolean>()); // a set of the spans we have sampled so if we sample the same again, we don't have to greedy update
		
		//System.out.println("aaa");
		
		for(DecoderTask task : decoderTasks) {
			completionService.submit(task);
		}
		
//		List<ParentedSpans> bestOptions = new ArrayList<>();
		for(int i = 0; i < decoderTasks.length; i++) {
			try {
				completionService.take().get();
//				List<Span> result = completionService.take().get();
//				bestOptions.add(new ParentedSpans(result, SpanUtilities.getParents(result)));
			}
			catch(ExecutionException e) {
				System.out.println("Exception in decoder worker thread");
				e.getCause().printStackTrace();
				e.printStackTrace();
			}
			catch(InterruptedException e) {
				e.printStackTrace();
			}
		}
//		
//		MaxResult result = getMax(bestOptions, words, params);
//		System.out.println("bbb");
		lastScore = finalResult.score;
		SpanUtilities.Assert(finalResult.spans != null);
		
//		System.out.println(lastScore);
		
		return finalResult.spans;
	}
	
	double lastScore = 0;
	public double getLastScore() {
		return lastScore;
	}
	
	private class DecoderTask implements Callable<List<Span>> {
		int id;
		RandomizedGreedyDecoder decoder;
		
		public DecoderTask(int id, RandomizedGreedyDecoder decoder) {
			this.id = id;
			this.decoder = decoder;
		}
		
		@Override
		public List<Span> call() {
			List<Span> best = new ArrayList<Span>();
			double bestScore = Double.NEGATIVE_INFINITY;
			boolean useCKY = false;
			
			//double ckyScore = score(words, ckySpan, params);
			
			while(true) {
				//if (!costAugmenting)
				//	System.out.println("new iteration: " + id);
				List<Span> spans = null;
				if (useCKY) {
					spans = sampler.sample();
				}
				else {
					// add CKY result as initial tree
					spans = new ArrayList<Span>();
					for (int z = 0; z < ckySpan.size(); ++z)
						spans.add(new Span(ckySpan.get(z)));
					useCKY = true;
				}
				SpanUtilities.connectChildren(spans);
				
				//double sampleScore = score(words, spans, params);
				//System.out.println("score: " + sampleScore + " " + ckyScore + " " + sampler.totalMarginal);
				//SpanUtilities.print(spans, labels);
				//SpanUtilities.print(ckySpan, labels);
				//SpanUtilities.debugSpan(spans, labels, decoder);
				
				//if(pruning.containsPruned(spans))
				//	throw new RuntimeException();
				//if(!SpanUtilities.usesOnlyExistingRules(spans, rules))
				//	throw new RuntimeException();
				
				if(spans.size() > 0) { // sometimes sampler fails to find valid sample and returns empty list
					//if (!costAugmenting)
					//	System.out.println("1111: " + id);
					SpanUtilities.checkCorrectness(spans);
					
					boolean changed = true;
					double lastScore = Double.NEGATIVE_INFINITY;
					while(changed) {
						//Set<Span> spansSet = new HashSet<>(spans);
						//if(alreadySeenSpans.contains(spansSet))
						//	break;
						//else
						//	alreadySeenSpans.add(spansSet);
						
						//if (!costAugmenting)
						//	System.out.println("2222: " + id);

						double score = score(words, spans, params);

						//if (words.size() < 20) {
						//	System.out.println("score: " + score + " " + ckyScore);
						//	SpanUtilities.print(spans, labels);
						//	SpanUtilities.print(ckySpan, labels);
						//	try { System.in.read(); } catch (Exception e) { e.printStackTrace(); }
						//}
						//if (!costAugmenting)
						//	System.out.println("4444: " + id);

						if(score < lastScore + 1e-6) {
							changed = false;
							break;
						}
						lastScore = score;
						
						// terminal labels
						/*
						for(int i = 0; i < words.size(); i++) {
							List<ParentedSpans> options = greedyChange.makeGreedyLabelChanges(spans, i, i+1, false, pruning);
							
							MaxResult max = getMax(options, words, params, 1); 
							spans = max.spans;
							
							if(pruning.containsPruned(spans))
								throw new RuntimeException();
							if(!SpanUtilities.usesOnlyExistingRules(spans, rules))
								throw new RuntimeException();
						}
						*/
						
						// udpate labels of all spans
						boolean[][] existSpan = new boolean[words.size()][words.size() + 1];
						for (int i = 0; i < spans.size(); ++i) {
							Span span = spans.get(i);
							existSpan[span.getStart()][span.getEnd()] = true;
						}
						for(int length = 1; length < words.size() + 1; length++) {
							for(int start = 0; start < words.size() - length + 1; start++) {
								if (!existSpan[start][start + length])
									continue;
								List<ParentedSpans> options = greedyChange.makeGreedyLabelChanges(spans, start, start + length, length == words.size(), pruning);
								if(options.size() == 0) {
									break; // no valid option
								}
								spans = getMax(options, words, params, 3).spans;
								
							}
						}
						
						//if(pruning.containsPruned(spans))
						//	throw new RuntimeException();
						//if(!SpanUtilities.usesOnlyExistingRules(spans, rules))
						//	throw new RuntimeException();
						
						//if (!costAugmenting)
						//	System.out.println("5555: " + id);

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
									List<ParentedSpans> update = greedyChange.makeGreedyChanges(spans, start, start + length, words.size(), pruning);
									//SpanUtilities.Assert(containsOriginal(update, spans));
									
									// occasionally check that parents are correct
									//if(update.size() > 0 && Math.random() < .005) {
									//	ParentedSpans ps = update.get(0);
									//	if(!Arrays.equals(ps.parents, SpanUtilities.getParents(ps.spans)))
									//		throw new RuntimeException("Parents incorrect");
									//}
									/*
									double currScore = score(words, spans, params);
									if (currScore > 0.0 && words.size() > 5 && words.size() < 20) {
										System.out.println("current score: " + currScore);
										for (int z = 0; z < spans.size(); ++z)
											System.out.print(spans.get(z).toString(labels) + ", ");
										System.out.println();
										//System.out.println(spans);
									}*/

									//SpanUtilities.print(spans, labels);
									//System.out.println("score " + score(words, spans, params));

									MaxResult max = getMax(update, words, params, 2);
									spans = max.spans;
									/*
									if ((currScore > 0.0 || max.score > 0.0) && words.size() > 7 && words.size() < 20) {
										System.out.println("change span: " + start + " " + (start + length));
										System.out.println("after update score: " + max.score);
										for (int z = 0; z < spans.size(); ++z)
											System.out.print(spans.get(z).toString(labels) + ", ");
										System.out.println();
										//System.out.println(spans);
										try { System.in.read(); } catch (Exception e) { e.printStackTrace(); }
									}*/

									if (spans == null) {
										System.out.println(update.size() + " " + start + " " + (start + length));
										for (int z = 0; z < update.size(); ++z) {
											SpanUtilities.print(update.get(z).spans, labels);
											ParentedSpans option = update.get(z);
											double tmpscore = score(words, option.spans, option.parents, params);
											System.out.println("score " + tmpscore);
										}
									}
									SpanUtilities.Assert(spans != null);
									//if(pruning.containsPruned(spans))
									//	throw new RuntimeException();
									//if(!SpanUtilities.usesOnlyExistingRules(spans, rules))
									//	throw new RuntimeException();
								}
							}
						}
						
						//if (!costAugmenting)
						//	System.out.println("6666: " + id);

						// iterate top level again with constraint to top level label in training set
						/*
						List<ParentedSpans> options = greedyChange.makeGreedyLabelChanges(spans, 0, words.size(), true, pruning);
						if(options.size() == 0) {
							break; // no valid option that uses top level labels
						}
						spans = getMax(options, words, params, 3).spans;
						
						if(pruning.containsPruned(spans))
							throw new RuntimeException();
						if(!SpanUtilities.usesOnlyExistingRules(spans, rules))
							throw new RuntimeException();
						*/
						//if (!costAugmenting)
						//	System.out.println("3333: " + id);

					}
				}
				else {
					System.out.println("failed sampling: " + id);
				}
				
				double score = score(words, spans, params);
				if(score > bestScore + 1e-6) {
					best = new ArrayList<>(spans);
					bestScore = score;
				}

				//if (!costAugmenting)
				//	System.out.println("end iteration: " + id + " " + bestScore);
				
				synchronized(lockObject) {
					//System.out.println(bestScore);
					if (bestScore > finalResult.score + 1e-6) {
						// update
						finalResult = new MaxResult(new ArrayList<>(spans), bestScore);
						numberIterationsStarted = 0;
					}
					else {
						numberIterationsStarted++;
						if(numberIterationsStarted >= numberSampleIterations) {
							break;
						}
					}
				}
			}
			
			return best;
		}
		
		public boolean containsOriginal(List<ParentedSpans> options, List<Span> spans) {
			for (int i = 0; i < options.size(); ++i) {
				if (sameSpans(options.get(i).spans, spans)) 
					return true;
			}
			return false;
		}
		
		public boolean sameSpans(List<Span> span1, List<Span> span2) {
			if (span1.size() != span2.size())
				return false;
			for (int i = 0; i < span1.size(); ++i) {
				Span span = span1.get(i);
				boolean find = false;
				for (int j = 0; j < span2.size(); ++j)
					if (span2.get(j).equals(span)) {
						find = true;
						break;
					}
				if (!find)
					return false;
			}
			return true;
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
		return getMax(options, words, params, -1).spans;
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
	private MaxResult getMax(List<ParentedSpans> options, List<Word> words, FeatureParameters params, int id) {
		double bestScore = Double.NEGATIVE_INFINITY;
		List<Span> best = null;
		if(options.size() == 0)
			throw new RuntimeException("No options given to max");
		for(ParentedSpans option : options) {
			//SpanUtilities.printSpans(option.spans, words.size(), labels);
			//if (id == 2 && words.size() < 10)
			//	System.out.println(option.spans);
			double score = score(words, option.spans, option.parents, params);
			if(score > bestScore + 1e-6) {
				bestScore = score;
				best = option.spans;
			}
			else if (Math.abs(score - bestScore) < 1e-6 && option.spans.size() < best.size()) {
				bestScore = score;
				best = option.spans;
			}
		}
		//try { System.in.read(); } catch (Exception e) { e.printStackTrace(); }
		//if (id == 2 && words.size() < 10) {
		//	System.out.flush();
		//	System.exit(0);
		//}
		return new MaxResult(best, bestScore);
	}
	
	double score(List<Word> words, List<Span> spans, FeatureParameters params) {
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
		//if (!costAugmenting)
		//	System.out.println("aaa");

		double score = 0;
		for(int j = 0; j < spans.size(); j++) {
			//if (!costAugmenting)
			//	System.out.println("bbb " + j);
			
			Span s = spans.get(j);
			Rule rule = s.getRule();
			if (rule.getType() == Type.UNARY && pruning.isPrunedAfterUnary(s.getStart(), s.getEnd(), rule.getLabel())) {
				//System.out.println("pruned: " + rule.toString(labels));
				return Double.NEGATIVE_INFINITY;
			}
			if(rule.getType() != Type.UNARY && pruning.isPrunedBeforeUnary(s.getStart(), s.getEnd(), rule.getLabel())) {
				//System.out.println("pruned: " + rule.toString(labels));
				return Double.NEGATIVE_INFINITY;
			}
			
			//if (!costAugmenting)
			//	System.out.println("ccc " + j + " " + rule.getType());

			double spanScore = 0;
			boolean equalGold = true;
			if(rule.getType() == Type.BINARY) {
				int ruleId = rules.getBinaryId(rule);
				if(ruleId == -1) {
					//System.out.println("unseen: " + rule.toString(labels) + " " + s.toString(labels));
					return Double.NEGATIVE_INFINITY;
				}
				spanScore = firstOrderFeatures.scoreBinary(s.getStart(), s.getEnd(), s.getSplit(), ruleId);
				if (costAugmenting && goldLabels[s.getStart()][s.getEnd()] != rule.getLabel())
					equalGold = false;
			}
			else if(rule.getType() == Type.UNARY) {
				int ruleId = rules.getUnaryId(rule);
				if(ruleId == -1) {
					//System.out.println("unseen: " + rule.toString(labels));
					return Double.NEGATIVE_INFINITY;
				}
				spanScore = firstOrderFeatures.scoreUnary(s.getStart(), s.getEnd(), ruleId);
				if (costAugmenting && goldUnaryLabels[s.getStart()][s.getEnd()] != rule.getLabel())
					equalGold = false;
			}
			else {// terminal
				spanScore = firstOrderFeatures.scoreTerminal(s.getStart(), rule.getLabel());
				if (costAugmenting && goldLabels[s.getStart()][s.getEnd()] != rule.getLabel())
					equalGold = false;
			}
			score += spanScore;
			
			//if (!costAugmenting)
			//	System.out.println("ddd " + j);

			if(costAugmenting && !equalGold) {
				score += cost;
			}
			
			//if (parents[j] == -1) {
			//	long code = Features.getTopRuleFeature(rules.getRuleCode(spans.get(j).getRule()));
			//	score += params.getScore(code);
			//}
		}
		
		if(doSecondOrder) {
			for(long feature : Features.getAllHigherOrderFeatures(words, spans, parents, rules, wordEnum, labels)) {
				score += params.getScore(feature);
			}
		}
		
		return score;
	}
}
