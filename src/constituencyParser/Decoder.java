package constituencyParser;

import java.util.List;

import constituencyParser.features.FeatureParameters;

public interface Decoder {
	/**
	 * Parse the sentence words using params
	 * @param words
	 * @param params
	 * @param dropout
	 * @return
	 */
	public List<Span> decode(List<Word> words, FeatureParameters params, boolean dropout);
	
	public void setCostAugmenting(boolean costAugmenting, SpannedWords gold);
	public void setSecondOrder(boolean secondOrder);
	public double getLastScore();
}
