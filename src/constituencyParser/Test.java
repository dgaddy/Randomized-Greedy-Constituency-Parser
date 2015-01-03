package constituencyParser;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import constituencyParser.features.FeatureParameters;

public class Test {
	public static void main(String[] args) throws Exception {
		if(args.length < 3) {
			System.out.println("requires 3 arguments: the model file name, the data directory, and t/f second order ");
			System.out.println("optional argument: number of iterations for randomized greedy restart");
			return;
		}
		
		String modelFile = args[0];
		String dataDir = args[1];
		boolean secondOrder = args[2].equals("t");
		if(!secondOrder && !args[2].equals("f")) {
			System.out.println("second order must be t or f");
		}
		
		int greedyIterations = 100;
		if(args.length > 3) {
			greedyIterations = Integer.parseInt(args[3]);
		}
		
		//testPassiveAggressive();
		
		SaveObject savedModel = SaveObject.loadSaveObject(modelFile);
		
		WordEnumeration words = savedModel.getWords();
		LabelEnumeration labels = savedModel.getLabels();
		Rules rules = savedModel.getRules();
		FeatureParameters parameters = savedModel.getParameters();
		
		//sample(words, labels, rules, parameters);
		
		
		test(words, labels, rules, parameters, dataDir, secondOrder, greedyIterations);
		
		//decodeSentence();
	}
	
	public static void test(WordEnumeration words, LabelEnumeration labels, Rules rules, FeatureParameters parameters, String dataFolder, boolean secondOrder) throws IOException {
		test(words, labels, rules, parameters, dataFolder, secondOrder, 100);
	}
	
	public static void test(WordEnumeration words, LabelEnumeration labels, Rules rules, FeatureParameters parameters, String dataFolder, boolean secondOrder, int randomizedGreedyIterations) throws IOException {
		DiscriminitiveCKYDecoder decoder = new DiscriminitiveCKYDecoder(words, labels, rules);
		
		RandomizedGreedyDecoder randGreedyDecoder = new RandomizedGreedyDecoder(words, labels, rules);
		//randGreedyDecoder.samplerDoCounts(PennTreebankReader.loadFromFiles(dataFolder, 2, 22, words, labels, rules)); for non-discriminitive
		
		List<SpannedWords> gold = PennTreebankReader.loadFromFiles(dataFolder, 0, 1, words, labels, rules);
		
		randGreedyDecoder.setNumberSampleIterations(randomizedGreedyIterations);
		
		int numberCorrect = 0;
		int numberGold = 0;
		int numberOutput = 0;
		for(SpannedWords example : gold) {
			//randGreedyDecoder.setSecondOrder(secondOrder);
			randGreedyDecoder.setSecondOrder(false);
			
			List<Span> ckyResult = decoder.decode(example.getWords(), parameters, false);
			List<Span> result = randGreedyDecoder.decode(example.getWords(), parameters, false);
			
			boolean different = false;
			for(Span span : result) {
				for(Span goldSpan : example.getSpans()) {
					if(span.getStart() == goldSpan.getStart() && span.getEnd() == goldSpan.getEnd() && span.getRule().getParent() == goldSpan.getRule().getParent())
						numberCorrect++;
				}
				
				boolean found = false;
				for(Span ckySpan : ckyResult) {
					if(span.getStart() == ckySpan.getStart() && span.getEnd() == ckySpan.getEnd() && span.getRule().getParent() == ckySpan.getRule().getParent())
						found = true;
				}
				if(!found)
					different = true;
			}
			if(different) {
				HashSet<Span> common = new HashSet<Span>(ckyResult);
				common.retainAll(result);
				HashSet<Span> ckySpecific = new HashSet<Span>(ckyResult);
				ckySpecific.removeAll(common);
				HashSet<Span> rgSpecific = new HashSet<Span>(result);
				rgSpecific.removeAll(common);
				System.out.println("Difference.");
				System.out.println("CKY: " + ckySpecific + " score: " + decoder.getLastScore());
				SpanUtilities.printSpans(ckyResult, example.getWords().size(), labels);
				System.out.println("RandGreedy: " + rgSpecific + " score: " + randGreedyDecoder.getLastScore());
				SpanUtilities.printSpans(result, example.getWords().size(), labels);
			}
			else {
				System.out.println("Same.");
			}
			
			numberGold += example.getSpans().size();
			numberOutput += result.size();
		}
		double precision = numberCorrect / (double)numberOutput;
		double recall = numberCorrect / (double)numberGold;
		
		double score = 2*precision*recall/(precision+recall);
		System.out.println("Development set score: " + score);
	}
	
	public static void decodeSentence() throws Exception {
		SaveObject savedModel = SaveObject.loadSaveObject("modelIteration1");
		
		WordEnumeration words = savedModel.getWords();
		LabelEnumeration labels = savedModel.getLabels();
		Rules rules = savedModel.getRules();
		FeatureParameters parameters = savedModel.getParameters();
		
		System.out.println(rules.getNumberOfUnaryRules());
		
		DiscriminitiveCKYDecoder decoder = new DiscriminitiveCKYDecoder(words, labels, rules);
		
		List<Span> result = decoder.decode(words.getIds(Arrays.asList("I", "go", "to", "the", "supermarket", ".")), parameters, false);
		
		RandomizedGreedyDecoder decoder2 = new RandomizedGreedyDecoder(words, labels, rules);
		
		List<Span> result2 = decoder2.decode(words.getIds(Arrays.asList("I", "go", "to", "the", "supermarket", ".")), parameters, false);
		
		List<Span> result3 = decoder2.decodeNoGreedy(words.getIds(Arrays.asList("I", "go", "to", "the", "supermarket", ".")), parameters, false);
		
		System.out.println(result);
		System.out.println(result2);
		System.out.println(result3);
	}
	
	public static void testPassiveAggressive() throws Exception {
		WordEnumeration words = new WordEnumeration();
		LabelEnumeration labels = new LabelEnumeration();
		Rules rules = new Rules();
		List<SpannedWords> examples = PennTreebankReader.loadFromFiles("../WSJ data/", 2, 3, words, labels, rules);
		
		int loss = 1;
		FeatureParameters params = new FeatureParameters();
		
		while(loss > 0) {
			// run passive aggressive on the first example
			DiscriminitiveCKYDecoder decoder = new DiscriminitiveCKYDecoder(words, labels, rules);
			PassiveAgressive pa = new PassiveAgressive(words, labels, rules, decoder, params);
			pa.train(examples.subList(0, 1), .05, false, false);
			
			// the first example should now classify correctly
			//List<Span> result = decoder.decode(examples.get(0).getWords(), params);
			//loss = PassiveAgressive.computeLoss(result, examples.get(0).getSpans());
			//System.out.println("loss is "+ loss);
		}
	}
	
	public static void sample(WordEnumeration words, LabelEnumeration labels, Rules rules, FeatureParameters parameters) {
		DiscriminitiveCKYSampler sampler = new DiscriminitiveCKYSampler(words, labels, rules);
		sampler.calculateProbabilities(words.getIds(Arrays.asList("I", "go", "to", "the", "supermarket")), parameters);
		for(int i = 0; i < 100; i++) {
			List<Span> spans = sampler.sample();
			System.out.println(spans);
		}
	}
	
	public static void testCKY() throws IOException {
		String dataFolder = "../WSJ data/";
		WordEnumeration words = new WordEnumeration();
		LabelEnumeration labels = new LabelEnumeration();
		Rules rules = new Rules();
		CKYDecoder decoder = new CKYDecoder(words, labels, rules);
		decoder.doCounts(PennTreebankReader.loadFromFiles(dataFolder, 2, 22, words, labels, rules));
		List<SpannedWords> gold = PennTreebankReader.loadFromFiles(dataFolder, 0, 1, words, labels, rules);
		List<Span> result = decoder.decode(gold.get(0).getWords());
		System.out.println(result);
	}
}
