package constituencyParser.features;

import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TLongDoubleMap;
import gnu.trove.map.hash.TLongIntHashMap;
import gnu.trove.procedure.TLongDoubleProcedure;
import gnu.trove.procedure.TLongIntProcedure;

import java.io.Serializable;
import java.util.List;

public class FeatureParameters implements Serializable {
	private static final long serialVersionUID = 1L;
	
	TLongIntHashMap featureIndices;
	
	TDoubleArrayList featureValues = new TDoubleArrayList();
	TDoubleArrayList gradientsSquared = new TDoubleArrayList();
	transient TIntArrayList dropout; // 1 if should drop, 0 if should keep
	
	public FeatureParameters() {
		featureIndices = new TLongIntHashMap(500, 0.2f, 0, -1);
	}

	public FeatureParameters(FeatureParameters other) {
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
		if(index >= dropout.size())
			return false; // for features that are created after we made the dropout array, just keep them
		
		return dropout.get(index) > 0;
	}
	
	public double getScore(long code, boolean doDropout) {
		int index = featureIndices.get(code);
		if(index == -1) // default value, so feature isn't in map
			return 0;
		else {
			if(doDropout && getDropout(index))
				return 0;
			else
				return featureValues.getQuick(index);
		}
	}
	
	/**
	 * updates parameters with featureUpdates using adagrad
	 * @param featureUpdates difference counts between gold and predicted, positive is in gold but not predicted and negative is in predicted but not gold
	 * @param scale
	 */
	public void update(TLongDoubleMap featureUpdates) {
		featureUpdates.forEachEntry(new TLongDoubleProcedure() {

			@Override
			public boolean execute(long key, double value) {
				if(value < 1e-5 && value > -1e-5)
					return true;
				
				double adjustment = value;
				int index = getOrMakeIndex(key);
				if(getDropout(index))
					return true;
				
				double newGradSquared = gradientsSquared.getQuick(index) + adjustment*adjustment;
				gradientsSquared.setQuick(index, newGradSquared);
				featureValues.setQuick(index, featureValues.getQuick(index) + adjustment/Math.sqrt(newGradSquared));
				return true;
			}
			
		});
	}
	
	private int getOrMakeIndex(long key) {
		int index;
		if(!featureIndices.containsKey(key)) {
			index = featureValues.size();
			featureValues.add(0);
			gradientsSquared.add(0);
			featureIndices.put(key, index);
		}
		else
			index = featureIndices.get(key);
		return index;
	}
	
	/**
	 * Get the average of a list of featureParameters
	 * @param toAverage
	 * @return
	 */
	public static FeatureParameters average(List<FeatureParameters> toAverage) {
		final FeatureParameters average = new FeatureParameters();
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
	
	public String toString() {
		return featureValues.toString();
	}
}
