package constituencyParser.features;

import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TLongDoubleMap;
import gnu.trove.map.hash.TLongIntHashMap;
import gnu.trove.procedure.TLongDoubleProcedure;
import gnu.trove.procedure.TLongIntProcedure;
import gnu.trove.procedure.TLongProcedure;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import constituencyParser.LabelEnumeration;
import constituencyParser.RuleEnumeration;
import constituencyParser.WordEnumeration;

public class FeatureParameters implements Serializable {
	private static final long serialVersionUID = 2L;
	private static final double DELTA = 1e-5;
	
	TLongIntHashMap featureIndices;
	
	double learningRate;
	double regularization;
	TDoubleArrayList featureValues = new TDoubleArrayList();
	TDoubleArrayList gradientsSquared = new TDoubleArrayList();
	transient TIntArrayList dropout; // 1 if should drop, 0 if should keep
	boolean dontMakeNewFeatures = false;
	
	int numberMiraUpdates = 0;
	TDoubleArrayList featureValuesTotal = new TDoubleArrayList();
	TDoubleArrayList featureValuesBak = new TDoubleArrayList();
	
	public FeatureParameters(double learningRate, double regularization) {
		featureIndices = new TLongIntHashMap(500, 0.2f, 0, -1);
		this.learningRate = learningRate;
		this.regularization = regularization;
	}

	public FeatureParameters(FeatureParameters other) {
		learningRate = other.learningRate;
		regularization = other.regularization;
		featureIndices = new TLongIntHashMap(other.featureIndices.size(), 0.2f, 0, -1);
		other.featureIndices.forEachEntry(new TLongIntProcedure() {
			@Override
			public boolean execute(long arg0, int arg1) {
				featureIndices.put(arg0, arg1);
				return true;
			}
		});
		featureValues = new TDoubleArrayList(other.featureValues);
		gradientsSquared = new TDoubleArrayList(other.gradientsSquared);
		featureValuesTotal = new TDoubleArrayList(other.featureValuesTotal);
	}
	
	/**
	 * This should be called every iteration so we drop a new set of parameters
	 */
	public void resetDropout(double probability) {
		int featureSize = featureValues.size();
		dropout = new TIntArrayList(featureSize);
		for(int i = 0; i < featureSize; i++) {
			int value = Math.random() < probability ? 1 : 0;
			dropout.add(value);
		}
	}
	
	private boolean getDropout(int index) {
		if(dropout == null)
			return false;
		if(index >= dropout.size())
			return false; // for features that are created after we made the dropout array, just keep them
		
		return dropout.get(index) > 0;
	}
	
	public double getScore(long code) {
		int index = featureIndices.get(code);
		if(index == -1) // default value, so feature isn't in map
			return 0;
		else {
			if(getDropout(index))
				return 0;
			else
				return featureValues.getQuick(index);
		}
	}
	
	/**
	 * updates parameters with featureUpdates using adagrad
	 * @param featureUpdates difference counts between gold and predicted, negative is in gold but not predicted and positive is in predicted but not gold
	 */
	public void update(TLongDoubleMap featureUpdates) {
		final TDoubleArrayList updates = new TDoubleArrayList((int)(featureValues.size() * 1.5));
		for(int i = 0; i < featureValues.size(); i++) {
			updates.add(0);
		}
		featureUpdates.forEachEntry(new TLongDoubleProcedure() {

			@Override
			public boolean execute(long key, double value) {
				if(value < 1e-5 && value > -1e-5)
					return true;
				
				int index = getOrMakeIndex(key);
				if(index == -1)
					return true;
				
				if(index == updates.size()) {
					updates.add(0);
				}
				if(getDropout(index))
					return true;
				
				updates.set(index, value);
				return true;
			}
			
		});
		
		for(int i = 0; i < featureValues.size(); i++) {
			double oldVal = featureValues.getQuick(i);
			double adjustment = updates.get(i) + regularization * oldVal;
			double newGradSquared = gradientsSquared.getQuick(i) + adjustment*adjustment;
			gradientsSquared.setQuick(i, newGradSquared);
			
			double s = Math.sqrt(newGradSquared + DELTA);
			//double newVal = (s * oldVal - learningRate * adjustment) / (learningRate * regularization + DELTA + s);
			double newVal = oldVal - learningRate * adjustment / s;
			
			featureValues.setQuick(i, newVal);
		}
	}
	
	public void updateMIRA(TLongDoubleMap featureUpdates, double loss) {
		numberMiraUpdates++;
		int upd = numberMiraUpdates;
		
		final TIntArrayList idx = new TIntArrayList(featureUpdates.size());
		final TDoubleArrayList val = new TDoubleArrayList(featureUpdates.size());
		/*
		featureUpdates.forEachEntry(new TLongDoubleProcedure() {
			@Override
			public boolean execute(long key, double value) {
				if (Math.abs(value) < 1e-6)
					return true;
				
				int index = getOrMakeIndex(key);
				if(index == -1)
					return true;
				
				idx.add(index);
				val.add(value);

				return true;
			}
		});*/
		for (long k : featureUpdates.keys()) {
			double v = featureUpdates.get(k);
			if (Math.abs(v) < 1e-6)
				continue;
			
			int index = getOrMakeIndex(k);
			if (index == -1)
				continue;
			
			idx.add(index);
			val.add(v);
		}
		
		//System.out.println("loss: " + loss);
		double norm = 0.0;
		for (int i = 0, L = val.size(); i < L; ++i) {
			double v = val.getQuick(i);
			norm += v * v;
		}
		double alpha = loss / norm;
		alpha = Math.min(0.1, alpha);
		if (alpha > 0) {
			//System.out.println("alpha: " + norm + " " + alpha);
			for (int i = 0, L = val.size(); i < L; ++i) {
				int index = idx.getQuick(i);
				double oldVal = featureValues.getQuick(index);
				double delta = alpha * val.getQuick(i);
				featureValues.setQuick(index, oldVal - delta);
				
				oldVal = featureValuesTotal.getQuick(index);
				delta = delta * upd;
				featureValuesTotal.setQuick(index, oldVal - delta);
			}
		}
	}
	
	public void averageParameters() 
	{
		int T = numberMiraUpdates;
		featureValuesBak = featureValues;
		int size = featureValues.size();
		TDoubleArrayList avg = new TDoubleArrayList(size);
		
		for (int i = 0; i < size; ++i) {
			avg.add((featureValues.getQuick(i) * (T+1) - featureValuesTotal.getQuick(i))/T);			
		}		
		featureValues = avg;
	}
	
	public void unaverageParameters() 
	{
		featureValues = featureValuesBak;
	}
	
	private int getOrMakeIndex(long key) {
		int index;
		if(!featureIndices.containsKey(key)) {
			if(dontMakeNewFeatures)
				return -1;
			
			index = featureValues.size();
			featureValues.add(0);
			gradientsSquared.add(0);
			featureValuesTotal.add(0);
			featureIndices.put(key, index);
		}
		else
			index = featureIndices.get(key);
		return index;
	}
	
	public void ensureContainsFeatures(List<Long> features) {
		for(long l : features) {
			getOrMakeIndex(l);
		}
	}
	
	public void stopAddingFeatures() {
		dontMakeNewFeatures = true;
	}
	
	/**
	 * Get the average of a list of featureParameters
	 * @param toAverage
	 * @return
	 */
	public static FeatureParameters average(List<FeatureParameters> toAverage) {
		final FeatureParameters average = new FeatureParameters(toAverage.get(0).learningRate, toAverage.get(0).regularization);
		final double factor = 1.0/toAverage.size();
		for(FeatureParameters params : toAverage) {
			final FeatureParameters parameters = params;
			params.featureIndices.forEachEntry(new TLongIntProcedure() {

				@Override
				public boolean execute(long key, int index) {
					int averageIndex = average.getOrMakeIndex(key);
					average.featureValues.setQuick(averageIndex, parameters.featureValues.getQuick(index) * factor + average.featureValues.getQuick(averageIndex));
					average.gradientsSquared.setQuick(averageIndex, parameters.gradientsSquared.getQuick(index) * factor + average.gradientsSquared.getQuick(averageIndex));
					return true;
				}
				
			});
		}
		return average;
	}
	
	class StatCollector implements TLongIntProcedure {
		long maxValuedFeature = -1;
		double maxFeatureValue = Double.NEGATIVE_INFINITY;
		long minValuedFeature = -1;
		double minFeatureValue = Double.POSITIVE_INFINITY;
		double totalScore = 0;
		double totalAbsScore = 0;
		
		int numberSecondOrder = 0;
		long maxSecondOrder = -1;
		double maxValueSecOrder = Double.NEGATIVE_INFINITY;
		long minSecondOrder = -1;
		double minValueSecOrder = Double.POSITIVE_INFINITY;
		double totalSecondOrder = 0;
		double totalAbsSecondOrder = 0;
		
		@Override
		public boolean execute(long key, int index) {
			double value = featureValues.get(index);
			if(value > maxFeatureValue) {
				maxFeatureValue = value;
				maxValuedFeature = key;
			}
			if(value < minFeatureValue) {
				minFeatureValue = value;
				minValuedFeature = key;
			}
			totalScore += value;
			totalAbsScore += Math.abs(value);
			
			if(Features.isSecondOrderFeature(key)) {
				numberSecondOrder++;
				if(value > maxValueSecOrder) {
					maxValueSecOrder = value;
					maxSecondOrder = key;
				}
				if(value < minValueSecOrder) {
					minValueSecOrder = value;
					minSecondOrder = key;
				}
				totalSecondOrder += value;
				totalAbsSecondOrder += Math.abs(value);
			}
			return true;
		}
	}
	
	public void printStats(WordEnumeration words, RuleEnumeration rules, LabelEnumeration labels) {
		StatCollector collector = new StatCollector();
		
		System.out.println("Number features: " + featureIndices.size());
		if(featureIndices.size() > 0) {
			featureIndices.forEachEntry(collector);
			System.out.println("Max valued feature: " + Features.getStringForCode(collector.maxValuedFeature, words, rules, labels) + " " + collector.maxFeatureValue);
			System.out.println("Min valued feature: " + Features.getStringForCode(collector.minValuedFeature, words, rules, labels) + " " + collector.minFeatureValue);
			System.out.println("Average score: " + collector.totalScore / featureIndices.size());
			System.out.println("Average absolute value: " + collector.totalAbsScore / featureIndices.size());
		}
		
		if(collector.numberSecondOrder > 0) {
			System.out.println("Number second order features: " + collector.numberSecondOrder);
			System.out.println("Max valued second order feature: " + Features.getStringForCode(collector.maxSecondOrder, words, rules, labels) + " " + collector.maxValueSecOrder);
			System.out.println("Min valued second order feature: " + Features.getStringForCode(collector.minSecondOrder, words, rules, labels) + " " + collector.minValueSecOrder);
			System.out.println("Average second order score: " + collector.totalSecondOrder / collector.numberSecondOrder);
			System.out.println("Average second order absolute value: " + collector.totalAbsSecondOrder / collector.numberSecondOrder);
		}
	}
	
	public List<Long> getStoredFeatures() {
		final List<Long> result = new ArrayList<>();
		featureIndices.forEach(new TLongProcedure() {
			@Override
			public boolean execute(long arg0) {
				result.add(arg0);
				return true;
			}
		});
		return result;
	}
	
	public String toString() {
		return featureValues.toString();
	}
}
