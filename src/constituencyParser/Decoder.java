package constituencyParser;

import java.util.List;

import constituencyParser.features.FeatureParameters;

public interface Decoder {
	public List<Span> decode(List<Integer> words, FeatureParameters params, boolean dropout);
}
