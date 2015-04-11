package constituencyParser;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import constituencyParser.features.FeatureParameters;
import constituencyParser.features.Features;

public class StatisticsCollector {
	public static void printFeatureStats(List<SpannedWords> examples, WordEnumeration words, RuleEnumeration rules, LabelEnumeration labels) {
		HashMap<Long, Integer> featureCounts = new HashMap<>();
		
		for(SpannedWords example : examples) {
			for(Long code : Features.getAllFeatures(example.getSpans(), example.getWords(), true, words, labels, rules)) {
				Integer prev = featureCounts.get(code);
				int num;
				if(prev == null)
					num = 1;
				else
					num = prev + 1;
				
				featureCounts.put(code, num);
			}
		}
		
		int numRare = 0;
		int numSecondOrder = 0;
		int numRareSecondOrder = 0;
		int maxNum = 0;
		long maxFeature = -1;
		for(Entry<Long, Integer> entry : featureCounts.entrySet()) {
			if(entry.getValue() < 2) {
				numRare++;
			}
			if(Features.isSecondOrderFeature(entry.getKey())) {
				numSecondOrder++;
				if(entry.getValue() < 2) {
					numRareSecondOrder++;
				}
			}
			
			int num = entry.getValue();
			if(num > maxNum) {
				maxNum = num;
				maxFeature = entry.getKey();
			}
		}
		
		System.out.println("Number features seen: " + featureCounts.size());
		System.out.println("Most common feature: " + Features.getStringForCode(maxFeature, words, rules, labels) + " " + maxNum);
		System.out.println("Number of rare features: " + numRare);
		System.out.println("Number of second order features: " + numSecondOrder);
		System.out.println("Number of rare second order: " + numRareSecondOrder);
	}
	
	public static void main(String[] args) throws ClassNotFoundException, IOException {
		if(args.length < 2) {
			System.out.println("requires 2 arguments: the model file name, the data directory");
			return;
		}

		String modelFile = args[0];
		String dataDir = args[1];
		
		{
			System.out.println("From data shuffled numberings:");
			WordEnumeration shuffledWords = new WordEnumeration(true);
			LabelEnumeration shuffledLabels = new LabelEnumeration();
			RuleEnumeration shuffledRules = new RuleEnumeration();
			List<SpannedWords> shuffledExamples = PennTreebankReader.loadFromFiles(dataDir, 2, 22, shuffledWords, shuffledLabels, shuffledRules, true, true);
			printFeatureStats(shuffledExamples, shuffledWords, shuffledRules, shuffledLabels);
		} // in brackets for garbage collecting purposes

		SaveObject savedModel = SaveObject.loadSaveObject(modelFile);

		WordEnumeration words = savedModel.getWords();
		LabelEnumeration labels = savedModel.getLabels();
		RuleEnumeration rules = savedModel.getRules();
		FeatureParameters parameters = savedModel.getParameters();
		
		//System.out.println(parameters.getScore(Features.getSecondOrderRuleFeature(labels.getId("DT"), labels.getId("NP"), labels.getId("PP")), false));
		
		List<SpannedWords> examples = PennTreebankReader.loadFromFiles(dataDir, 2,22, words, labels, rules, true);
		
		System.out.println("From data using loaded numberings:");
		printFeatureStats(examples, words, rules, labels);
		
		System.out.println();
		System.out.println("From model:");
		parameters.printStats(words, rules, labels);
	}
}
