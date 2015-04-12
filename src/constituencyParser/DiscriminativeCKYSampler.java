package constituencyParser;

import gnu.trove.list.array.TIntArrayList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import constituencyParser.Rule.Type;
import constituencyParser.features.FirstOrderFeatureHolder;

/**
 * A sampler of parse trees based on DiscriminativeCKYDecoder
 */
public class DiscriminativeCKYSampler {
	private static final double PRUNE_THRESHOLD = 10;
	
	WordEnumeration wordEnum;
	LabelEnumeration labels;
	RuleEnumeration rules;
	
	FirstOrderFeatureHolder firstOrderFeatures;
	
	// stuff set by calculateProbabilities
	int wordsSize;
	List<Word> sentenceWords;
	double[][][] insideLogProbabilitiesBeforeUnaries; // unnormalized probabilities; start, end, label
	double[][][] insideLogProbabilitiesAfterUnaries;
	Pruning prune;
	
	boolean costAugmenting = false;
	double cost = 1.0;
	int[][] goldLabels; // gold span info used for cost augmenting: indices are start and end, value is label, -1 if no span for a start and end
	int[][] goldUnaryLabels;
	
	public double totalMarginal = Double.NEGATIVE_INFINITY;
	
	public DiscriminativeCKYSampler(WordEnumeration words, LabelEnumeration labels, RuleEnumeration rules, FirstOrderFeatureHolder features) {
		this.wordEnum = words;
		this.labels = labels;
		this.rules = rules;
		
		firstOrderFeatures = features;
	}
	
	/**
	 * 
	 * @param costAugmenting
	 * @param gold gold span info used for cost augmenting: indices are start and end, value is label, -1 if no span for a start and end
	 */
	public void setCostAugmenting(boolean costAugmenting, double cost, int[][] gold, int[][] goldUnary) {
		this.costAugmenting = costAugmenting;
		this.cost = cost;
		this.goldLabels = gold;
		this.goldUnaryLabels = goldUnary;
	}
	
	public Pruning calculateProbabilities(List<Word> words) {
		prune = new Pruning(words.size(), labels.getNumberOfLabels());
		sentenceWords = words;
		wordsSize = words.size();
		int labelsSize = labels.getNumberOfLabels();
		int rulesSize = rules.getNumberOfBinaryRules();
		insideLogProbabilitiesBeforeUnaries = new double[wordsSize][wordsSize+1][labelsSize];
		insideLogProbabilitiesAfterUnaries = new double[wordsSize][wordsSize+1][labelsSize];
		for(int i = 0; i < wordsSize; i++)
			for(int j = 0; j < wordsSize+1; j++)
				for(int k = 0; k < labelsSize; k++)
					insideLogProbabilitiesBeforeUnaries[i][j][k] = Double.NEGATIVE_INFINITY;
		
		for(int i = 0; i < wordsSize; i++) {
			double maxBeforeUnaries = 0;
			
			for(int label = 0; label < labelsSize; label++) {
				double spanScore = firstOrderFeatures.scoreTerminal(i, label);
				if(costAugmenting && goldLabels[i][i+1] != label) {
					spanScore += cost;
				}
				
				insideLogProbabilitiesBeforeUnaries[i][i+1][label] = spanScore;
				
				if(spanScore > maxBeforeUnaries)
					maxBeforeUnaries = spanScore;
			}
			
			for(int label = 0; label < labelsSize; label++) {
				double p = insideLogProbabilitiesBeforeUnaries[i][i+1][label];
				if (p < maxBeforeUnaries - PRUNE_THRESHOLD) {
					prune.pruneBeforeUnary(i, i + 1, label);
				}
			}
			
			doUnaryProbabilities(words, i, i+1);
			
			double maxAfterUnaries = Double.NEGATIVE_INFINITY;
			for (int label = 0; label < labelsSize; label++) {
				maxAfterUnaries = Math.max(maxAfterUnaries, insideLogProbabilitiesAfterUnaries[i][i+1][label]);
			}
			
			for (int label = 0; label < labelsSize; label++) {
				double p = insideLogProbabilitiesAfterUnaries[i][i+1][label];
				if(p < maxAfterUnaries - PRUNE_THRESHOLD) {
					prune.pruneAfterUnary(i, i + 1, label);
				}
			}
		}
		
		for(int length = 2; length < wordsSize + 1; length++) {
			for(int start = 0; start < wordsSize + 1 - length; start++) {
				double maxBeforeUnaries = 0;
				for(int split = 1; split < length; split++) {
					for(int r = 0; r < rulesSize; r++) {
						
						Rule rule = rules.getBinaryRule(r);
						
						int label = rule.getLabel();
						
						double probability = binaryProbability(start, start+length, start+split, r, rule);
						
						if(probability != Double.NEGATIVE_INFINITY) {
							double fullProbability = addProbabilitiesLog(insideLogProbabilitiesBeforeUnaries[start][start+length][label], probability);  
							//double fullProbability = Math.max(insideLogProbabilitiesBeforeUnaries[start][start+length][label], probability);  
							insideLogProbabilitiesBeforeUnaries[start][start+length][label] = fullProbability;
							
							if(fullProbability > maxBeforeUnaries) {
								maxBeforeUnaries = fullProbability;
							}
						}
					}
				}
				
				for(int label = 0; label < labelsSize; label++) {
					double p = insideLogProbabilitiesBeforeUnaries[start][start+length][label];
					if (p < maxBeforeUnaries - PRUNE_THRESHOLD) {
						prune.pruneBeforeUnary(start, start+length, label);
					}
				}

				doUnaryProbabilities(words, start, start + length);
				
				double maxAfterUnaries = Double.NEGATIVE_INFINITY;
				for (int label = 0; label < labelsSize; label++) {
					maxAfterUnaries = Math.max(maxAfterUnaries, insideLogProbabilitiesAfterUnaries[start][start+length][label]);
				}
				
				for(int l = 0; l < labelsSize; l++) {
					double p = insideLogProbabilitiesAfterUnaries[start][start+length][l];
					if(p < maxAfterUnaries - PRUNE_THRESHOLD) {
						prune.pruneAfterUnary(start, start+length, l);
						//if (goldLabels[start][start + length] == l) {
						//	System.out.println("prune gold 2");
						//}
						//if (goldUnaryLabels[start][start + length] == l) {
						//	System.out.println("prune gold 4");
						//}
					}
				}
			}
		}
		
		//System.out.println("Top prob:");
		//for (int i = 0; i < labelsSize; ++i) {
		//	System.out.println(labels.getLabel(i) + ": " + insideLogProbabilitiesAfterUnaries[0][wordsSize][i] + " " + insideLogProbabilitiesBeforeUnaries[0][wordsSize][i]);			
		//}
		//System.exit(0);
		
		computeTopMarginal();
		
		return prune;
	}
	
	/**
	 * returns ln(exp(x)+exp(y))
	 * @param logProb1
	 * @param logProb2
	 * @return
	 */
	private double addProbabilitiesLog(double x, double y) {
		if(x == Double.NEGATIVE_INFINITY)
			return y;
		if(y == Double.NEGATIVE_INFINITY)
			return x;
		
		double larger;
		double smaller;
		if(x > y) {
			larger = x;
			smaller = y;
		}
		else {
			larger = y;
			smaller = x;
		}
		
		return larger + Math.log(1 + Math.exp(smaller - larger));
	}
	
	private double unaryProbability(int start, int end, int ruleNumber, Rule rule) {
		if (prune.isPrunedBeforeUnary(start, end, rule.getLeft())) 
			return Double.NEGATIVE_INFINITY;
		
		double childProb = insideLogProbabilitiesBeforeUnaries[start][end][rule.getLeft()];
		double spanScore = firstOrderFeatures.scoreUnary(start, end, ruleNumber);
		if(costAugmenting && goldUnaryLabels[start][end] != rule.getLabel()) {
			spanScore += cost;
		}
		
		double probability = spanScore + childProb;
		return probability;
	}
	
	/**
	 * 
	 * @param properties
	 * @param start
	 * @param end
	 * @param split absolute position
	 * @param ruleNumber
	 * @param rule
	 * @return
	 */
	private double binaryProbability(int start, int end, int split, int ruleNumber, Rule rule) {
		if(prune.isPrunedAfterUnary(start, split, rule.getLeft()) || prune.isPrunedAfterUnary(split, end, rule.getRight()))
			return Double.NEGATIVE_INFINITY;

		double leftChildProb = insideLogProbabilitiesAfterUnaries[start][split][rule.getLeft()];
		double rightChildProb = insideLogProbabilitiesAfterUnaries[split][end][rule.getRight()];
		
		double spanScore = firstOrderFeatures.scoreBinary(start, end, split, ruleNumber);
		if(costAugmenting && goldLabels[start][end] != rule.getLabel()) {
			spanScore += cost;
		}
		
		double probability = spanScore + leftChildProb + rightChildProb;
		return probability;
	}
	
	private void doUnaryProbabilities(List<Word> words, int start, int end) {
		int numUnaryRules = rules.getNumberOfUnaryRules();
		
		double[] probs = insideLogProbabilitiesAfterUnaries[start][end];
		double[] oldProbs = insideLogProbabilitiesBeforeUnaries[start][end];
		for(int i = 0; i < probs.length; i++) {
			if (!prune.isPrunedBeforeUnary(start, end, i))
				probs[i] = oldProbs[i];
			else
				probs[i] = Double.NEGATIVE_INFINITY;
		}
		
		for(int i = 0; i < numUnaryRules; i++) {
			Rule rule = rules.getUnaryRule(i);
			
			double probability = unaryProbability(start, end, i, rule);
			
			double fullProb = addProbabilitiesLog(probability, probs[rule.getLabel()]);
			//double fullProb = Math.max(probability, probs[rule.getLabel()]);
			probs[rule.getLabel()] = fullProb;
		}
	}
	
	public void printProbs() {
		for(Integer topLabel : labels.getTopLevelLabelIds()) {
			double score = insideLogProbabilitiesAfterUnaries[0][wordsSize][topLabel];
			
			System.out.println(labels.getLabel(topLabel) + " " + score);
		}
	}
	
	public List<Span> sample() {
		List<Double> logProbabilities = new ArrayList<>();
		List<Integer> labelsToSample = new ArrayList<>();
		for(Integer topLabel : labels.getTopLevelLabelIds()) {
			if(prune.isPrunedAfterUnary(0, wordsSize, topLabel))
				continue;
			
			double score = insideLogProbabilitiesAfterUnaries[0][wordsSize][topLabel];
			if(score == Double.NEGATIVE_INFINITY)
				continue;
			
			logProbabilities.add(score);
			labelsToSample.add(topLabel);
			
			//System.out.println(labels.getLabel(topLabel) + " " + score);
		}
		
		if(logProbabilities.size() == 0) {
			System.out.println("top level fail");
			return new ArrayList<>();
		}
		
		int chosenTopLabel = labelsToSample.get(sample(logProbabilities));
		
		List<Span> sample = new ArrayList<>();
		
		sample(0, wordsSize, chosenTopLabel, true, sample);
		
		return sample;
	}
	
	private void sample(int start, int end, int label, boolean allowUnaries, List<Span> resultAccumulator) {
		List<Double> logProbabilities = new ArrayList<>();
		List<Span> spans = new ArrayList<>();
		if(allowUnaries) {
			int numUnaryRules = rules.getNumberOfUnaryRules();
			
			for(int r = 0; r < numUnaryRules; r++) {
				Rule rule = rules.getUnaryRule(r);
				
				if(rule.getLabel() == label) {
					//if(prune.isPrunedBeforeUnary(start, end, rule.getLeft()))
					//	continue;
					
					double prob = unaryProbability(start, end, r, rule);
					if(prob == Double.NEGATIVE_INFINITY)
						continue;
					
					logProbabilities.add(prob);
					Span span = new Span(start, end, rule);
					spans.add(span);
				}
			}
		}
		
		if(end - start == 1) { // terminals
			if (!prune.isPrunedBeforeUnary(start, end, label)) {
				double prob = insideLogProbabilitiesBeforeUnaries[start][end][label];
				logProbabilities.add(prob);
				spans.add(new Span(start, label));
			}
		}
		else {
			// binary
			if (!allowUnaries) {
				int numBinRules = rules.getNumberOfBinaryRules();
				for(int split = 1; split < end-start; split++) {
					for(int r = 0; r < numBinRules; r++) {
						Rule rule = rules.getBinaryRule(r);
						
						if(rule.getLabel() == label) {
							//if(prune.isPrunedAfterUnary(start, start+split, rule.getLeft()) || prune.isPrunedAfterUnary(start+split, end, rule.getRight()))
							//	continue;
							
							double prob = binaryProbability(start, end, start+split, r, rule);
							if(prob == Double.NEGATIVE_INFINITY)
								continue;
							
							logProbabilities.add(prob);
							Span span = new Span(start, end, start+split, rule);
							spans.add(span);
						}
					}
				}
			}
			else {
				if (!prune.isPrunedBeforeUnary(start, end, label)) {
					double prob = insideLogProbabilitiesBeforeUnaries[start][end][label];
					logProbabilities.add(prob);
					spans.add(null);
				}
			}
		}
		
		//if (allowUnaries)
		//	checkLogProbability(insideLogProbabilitiesAfterUnaries[start][end][label], logProbabilities);
		//else
		//	checkLogProbability(insideLogProbabilitiesBeforeUnaries[start][end][label], logProbabilities);
		
		int sampleIndex = sample(logProbabilities);
		Span span = spans.get(sampleIndex);
		if(span != null && span.getRule().getType() == Type.UNARY) {
			resultAccumulator.add(span);
			sample(start, end, span.getRule().getLeft(), false, resultAccumulator);
		}
		else if(end - start == 1) {
			SpanUtilities.Assert(span != null);
			resultAccumulator.add(span);
			return;
		}
		else {
			if (!allowUnaries) { 
				SpanUtilities.Assert(span != null && span.getRule().getType() == Type.BINARY);
				resultAccumulator.add(span);
				sample(start, span.getSplit(), span.getRule().getLeft(), true, resultAccumulator);
				sample(span.getSplit(), end, span.getRule().getRight(), true, resultAccumulator);
			}
			else {
				SpanUtilities.Assert(span == null);
				sample(start, end, label, false, resultAccumulator);
			}
		}
	}
	
	public void checkLogProbability(double score, List<Double> logProbabilities) {
		double maxLogP = Collections.max(logProbabilities);
		double sumExp = 0;
		for(double logP : logProbabilities) {
			sumExp += Math.exp(logP - maxLogP);
		}
		double logSumProbabilities = maxLogP + Math.log(sumExp);
		SpanUtilities.Assert(Math.abs(logSumProbabilities - score) < 1e-6);
	}
	
	public List<Double> scale(List<Double> logProbabilities, double scale) {
		List<Double> ret = new ArrayList<Double>();
		for (int i = 0; i < logProbabilities.size(); ++i)
			ret.add(logProbabilities.get(i) * scale);
		return ret;
	}
	
	public void computeTopMarginal() {
		List<Double> p = new ArrayList<Double>();
		for(Integer topLabel : labels.getTopLevelLabelIds()) {
			if(prune.isPrunedAfterUnary(0, wordsSize, topLabel))
				continue;
			
			double score = insideLogProbabilitiesAfterUnaries[0][wordsSize][topLabel];
			if(score == Double.NEGATIVE_INFINITY)
				continue;
			
			p.add(score);
		}
		
		double maxLogP = Collections.max(p);
		double sumExp = 0;
		for(double logP : p) {
			sumExp += Math.exp(logP - maxLogP);
		}
		totalMarginal = maxLogP + Math.log(sumExp);
	}
	
	private double getNthLargestValue(List<Double> logProbabilities, int n) {
		if (logProbabilities.size() < n)
			return Collections.min(logProbabilities);
		
		double[] v = new double[logProbabilities.size()];
		int len = v.length;
		for (int i = 0; i < len; ++i)
			v[i] = logProbabilities.get(i);
		Arrays.sort(v);
		return v[len - n];
	}
	
	private List<Double> getNthLargestValue(List<Double> logProbabilities, double thresh, int n, TIntArrayList index) {
		List<Double> ret = new ArrayList<Double>();
		if (logProbabilities.size() > n) {
			double[] v = new double[logProbabilities.size()];
			int len = v.length;
			for (int i = 0; i < len; ++i)
				v[i] = logProbabilities.get(i);
			Arrays.sort(v);
			for (int i = len - 1; i >= len - n; --i) {
				if (v[i] < thresh)
					break;
				else
					ret.add(v[i]);
			}
		}
		else {
			int len = logProbabilities.size();
			for (int i = 0; i < len; ++i) {
				double v = logProbabilities.get(i);
				if (v >= thresh)
					ret.add(v);
			}
		}
		return ret;
	}
	/**
	 * Samples a set of things based on log probabilities
	 * @param cumulativeValues
	 * @param cumulative
	 * @return the index of the sampled item
	 */
	private int sample(List<Double> logProbabilities) {
		//logProbabilities = scale(logProbabilities, 30);
		/*
		double maxLogP = Collections.max(logProbabilities);
		int sampleIndex = -1;
		for (int i = 0; i < logProbabilities.size(); ++i)
			if (Math.abs(logProbabilities.get(i) - maxLogP) < 1e-6) {
				sampleIndex = i;
				break;
			}
			*/
		
		double maxLogP = Collections.max(logProbabilities);
		/*
		double minThresh = getNthLargestValue(logProbabilities, 3);
		minThresh = Math.max(minThresh, maxLogP - 5) - 1e-6;
		
		List<Double> tmp = new ArrayList<Double>();
		TIntArrayList index = new TIntArrayList();
		int cnt = 0;
		for(double logP : logProbabilities) {
			if (logP > minThresh) {
				tmp.add(logP);
				index.add(cnt);
			}
			cnt++;
		}
		logProbabilities = tmp;
		*/
		
		double sumExp = 0;
		for(double logP : logProbabilities) {
			sumExp += Math.exp(logP - maxLogP);
		}
		double logSumProbabilities = maxLogP + Math.log(sumExp);
		
		double cumulative = 0;
		List<Double> cumulativeProbabilities = new ArrayList<>();
		for(double logP : logProbabilities) {
			double prob = Math.exp(logP - logSumProbabilities);
			cumulative += prob;
			cumulativeProbabilities.add(cumulative);
		}
		
		double sample = Math.random();
		int sampleIndex = Collections.binarySearch(cumulativeProbabilities, sample);
		if(sampleIndex < 0) { // usually true, this is what binarySearch does when it does not find the exact value
			sampleIndex = -(sampleIndex + 1);
		}
		if(sampleIndex > logProbabilities.size()) {
			// probably a rounding error
			System.out.println("Warning: rounding error");
			sampleIndex = logProbabilities.size() - 1;
		}
		SpanUtilities.Assert(Math.abs(cumulativeProbabilities.get(logProbabilities.size() - 1) - 1.0) < 1e-6);
		SpanUtilities.Assert(sample < cumulativeProbabilities.get(sampleIndex) + 1e-6 &&
				(sampleIndex == 0 || sample > cumulativeProbabilities.get(sampleIndex - 1) - 1e-6));
				
		return sampleIndex;
		//return index.get(sampleIndex);
	}
}
