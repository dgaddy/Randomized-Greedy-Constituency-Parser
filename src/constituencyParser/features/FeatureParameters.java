package constituencyParser.features;

import gnu.trove.list.array.TDoubleArrayList;
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
	
	public FeatureParameters() {
		featureIndices = new TLongIntHashMap(500, 0.5f, 0, -1);
	}

	public FeatureParameters(FeatureParameters other) {
		featureIndices = new TLongIntHashMap(other.featureIndices);
		featureValues = new TDoubleArrayList(other.featureValues);
		gradientsSquared = new TDoubleArrayList(other.gradientsSquared);
	}
	
	public double getScore(long code) {
		int index = featureIndices.get(code);
		if(index == -1) // default value, so feature isn't in map
			return 0;
		else
			return featureValues.getQuick(index);
	}
	
	/**
	 * updates parameters with negative gradient scale*featureUpdates
	 * @param featureUpdates
	 * @param scale
	 */
	public void update(TLongDoubleMap featureUpdates, final double scale) {
		featureUpdates.forEachEntry(new TLongDoubleProcedure() {

			@Override
			public boolean execute(long key, double value) {
				if(value < 1e-5 && value > -1e-5)
					return true;
				
				double adjustment = value * scale;
				int index = getOrMakeIndex(key);
				
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
	
	public static FeatureParameters average(List<FeatureParameters> toAverage) {
		final FeatureParameters average = new FeatureParameters();
		for(FeatureParameters params : toAverage) {
			final FeatureParameters parameters = params;
			params.featureIndices.forEachEntry(new TLongIntProcedure() {

				@Override
				public boolean execute(long key, int index) {
					int averageIndex = average.getOrMakeIndex(key);
					average.featureValues.setQuick(averageIndex, parameters.featureValues.getQuick(index) + average.featureValues.getQuick(averageIndex));
					average.gradientsSquared.setQuick(averageIndex, parameters.gradientsSquared.getQuick(index) + average.gradientsSquared.getQuick(averageIndex));
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
