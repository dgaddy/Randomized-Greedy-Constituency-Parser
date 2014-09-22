package constituencyParser;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;

import constituencyParser.features.BasicFeatures;
import constituencyParser.features.Feature;
import constituencyParser.features.FeatureParameters;

public class PassiveAgressive {
	CKYDecoder decoder;
	LabelEnumeration labels;
	FeatureParameters parameters;
	
	public PassiveAgressive(LabelEnumeration labels, Rules rules, CKYDecoder decoder) {
		this.labels = labels;
		this.decoder = decoder;
		parameters = new FeatureParameters(labels.getNumberOfLabels(), rules);
	}
	
	public void train(List<SpannedWords> trainingExamples) {
		int count = 0;
		for(SpannedWords sw : trainingExamples) {
			count++;
			if(count % 10 == 0) {
				System.out.println(count + " of " + trainingExamples.size());
			}
			
			List<String> words = sw.getWords();
			List<Span> predicted = decoder.decode(words, parameters);
			
			int loss = computeLoss(predicted, sw.getSpans()); 
			if(loss > 0) {
				HashMap<Feature, Double> features = new HashMap<Feature, Double>();
				for(Span s : sw.getSpans()) {
					for(Feature f : BasicFeatures.getSpanFeatures(words, s, labels)) {
						if(features.containsKey(features)) {
							features.put(f, features.get(f) + 1);
						}
						else {
							features.put(f, 1.0);
						}
					}
				}
				for(Span s : predicted) {
					for(Feature f : BasicFeatures.getSpanFeatures(words, s, labels)) {
						if(features.containsKey(features)) {
							features.put(f, features.get(f) - 1);
						}
						else {
							features.put(f, -1.0);
						}
					}
				}
				
				double l1_norm = 0;
				for(Entry<Feature, Double> entry : features.entrySet()) {
					l1_norm += Math.abs(entry.getValue());
				}
				
				parameters.add(features, loss/l1_norm);
			}
		}
	}
	
	public FeatureParameters getParameters() {
		return parameters;
	}
	
	private int computeLoss(List<Span> predicted, List<Span> gold) {
		HashSet<Span> predictedSpans = new HashSet<>(predicted);
		int loss = 0;
		for(Span s : gold) {
			if(!predictedSpans.contains(s))
				loss++;
		}
		return loss;
	}
}
