package constituencyParser.features;

import gnu.trove.map.TLongDoubleMap;
import gnu.trove.map.hash.TLongDoubleHashMap;
import gnu.trove.procedure.TLongDoubleProcedure;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

public class FeatureParameters implements Serializable {
	private static final long serialVersionUID = 1L;
	
	TLongDoubleHashMap featureValues = new TLongDoubleHashMap();
	
	public double getScore(long code) {
		return featureValues.get(code);
	}
	
	/**
	 * adds scale*featureUpdates to the parameters
	 * @param featureUpdates
	 * @param scale
	 */
	public void add(TLongDoubleMap featureUpdates, final double scale) {
		featureUpdates.forEachEntry(new TLongDoubleProcedure() {

			@Override
			public boolean execute(long key, double value) {
				double adjustment = scale * value;
				featureValues.adjustOrPutValue(key, adjustment, adjustment);
				return true;
			}
			
		});
	}
	
	public static FeatureParameters average(List<FeatureParameters> toAverage) {
		final double factor = 1.0 / toAverage.size();
		FeatureParameters average = new FeatureParameters();
		for(FeatureParameters params : toAverage) {
			average.add(params.featureValues, factor);
		}
		return average;
	}
	
	public static FeatureParameters copy(FeatureParameters toCopy) {
		return average(Arrays.asList(toCopy));
	}
	
	public String toString() {
		return featureValues.toString();
	}
}
