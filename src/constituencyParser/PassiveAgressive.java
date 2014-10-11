package constituencyParser;

import gnu.trove.list.TLongList;
import gnu.trove.map.hash.TLongDoubleHashMap;

import java.util.HashSet;
import java.util.List;

import constituencyParser.features.FeatureParameters;
import constituencyParser.features.Features;

public class PassiveAgressive {
	CKYDecoder decoder;
	WordEnumeration wordEnum;
	LabelEnumeration labels;
	Rules rules;
	FeatureParameters parameters;
	
	public PassiveAgressive(WordEnumeration words, LabelEnumeration labels, Rules rules, CKYDecoder decoder) {
		this.wordEnum = words;
		this.labels = labels;
		this.rules = rules;
		this.decoder = decoder;
		parameters = new FeatureParameters();
	}
	
	public PassiveAgressive(WordEnumeration words, LabelEnumeration labels, Rules rules, CKYDecoder decoder, FeatureParameters parameters) {
		this.wordEnum = words;
		this.labels = labels;
		this.rules = rules;
		this.decoder = decoder;
		this.parameters = parameters;
	}
	
	public void train(List<SpannedWords> trainingExamples, double dropout) {
		int count = 0;
		int totalLoss = 0;
		for(SpannedWords sw : trainingExamples) {
			count++;
			/*if(count % 50 == 0) {
				System.out.println(count + " of " + trainingExamples.size() + "; Average loss: " + (totalLoss / (double) count));
			}*/
			
			parameters.resetDropout(dropout);
			
			List<Integer> words = sw.getWords();
			List<Span> predicted = decoder.decode(words, parameters, true);
			
			int loss = computeLoss(predicted, sw.getSpans()); 
			totalLoss += loss;
			
			if(loss > 0) {
				TLongDoubleHashMap features = new TLongDoubleHashMap();
				//System.out.println("positive");
				for(Span s : sw.getSpans()) {
					TLongList featureCodes = Features.getSpanPropertyByRuleFeatures(words, s, rules, wordEnum);
					for(int i = 0; i < featureCodes.size(); i++) {
						features.adjustOrPutValue(featureCodes.get(i), 1, 1);
						//System.out.println(Features.getStringForCode(featureCodes.get(i), wordEnum, rules, labels));
					}
					features.adjustOrPutValue(Features.getRuleFeature(s.getRule(), rules), 1, 1);
					//System.out.println(Features.getStringForCode(Features.getRuleFeature(s.getRule(), rules), wordEnum, rules));
					TLongList propertyByLabelCodes = Features.getSpanPropertyByLabelFeatures(words, s);
					for(int i = 0; i < propertyByLabelCodes.size(); i++) {
						features.adjustOrPutValue(propertyByLabelCodes.get(i), 1, 1);
						//System.out.println(Features.getStringForCode(propertyByLabelCodes.get(i), wordEnum, rules, labels));
					}
				}
				//System.out.println("negative");
				for(Span s : predicted) {
					TLongList featureCodes = Features.getSpanPropertyByRuleFeatures(words, s, rules, wordEnum);
					for(int i = 0; i < featureCodes.size(); i++) {
						features.adjustOrPutValue(featureCodes.get(i), -1, -1);
						//System.out.println(Features.getStringForCode(featureCodes.get(i), wordEnum, rules));
					}
					features.adjustOrPutValue(Features.getRuleFeature(s.getRule(), rules), -1, -1);
					//System.out.println(Features.getStringForCode(Features.getRuleFeature(s.getRule(), rules), wordEnum, rules));
					TLongList propertyByLabelCodes = Features.getSpanPropertyByLabelFeatures(words, s);
					for(int i = 0; i < propertyByLabelCodes.size(); i++) {
						features.adjustOrPutValue(propertyByLabelCodes.get(i), -1, -1);
					}
				}
				
				parameters.update(features, 1);
			}
		}
		
		System.out.println("Finished; Average loss: " + (totalLoss / (double)count));
	}
	
	public FeatureParameters getParameters() {
		return parameters;
	}
	
	static int computeLoss(List<Span> predicted, List<Span> gold) {
		HashSet<Span> predictedSpans = new HashSet<>(predicted);
		int loss = 0;
		for(Span s : gold) {
			if(!predictedSpans.contains(s)) {
				loss++;
			}
		}
		return loss;
	}
}
