package constituencyParser;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import constituencyParser.features.FeatureParameters;

public class Test {
	public static void main(String[] args) throws Exception {
		SaveObject savedModel = SaveObject.loadSaveObject("parallelModel2");
		
		WordEnumeration words = savedModel.getWords();
		LabelEnumeration labels = savedModel.getLabels();
		Rules rules = savedModel.getRules();
		FeatureParameters parameters = savedModel.getParameters();
		
		sample(words, labels, rules, parameters);
		//test(words, labels, rules, parameters, "../WSJ Data/");
	}
	
	public static void test(WordEnumeration words, LabelEnumeration labels, Rules rules, FeatureParameters parameters, String dataFolder) throws IOException {
		
		List<SpannedWords> gold = PennTreebankReader.loadFromFiles(dataFolder, 0, 1, words, labels, rules);
		gold = gold.subList(0, 50);
		
		CKYDecoder decoder = new CKYDecoder(words, labels, rules);
		
		int numberCorrect = 0;
		int numberGold = 0;
		int numberOutput = 0;
		for(SpannedWords example : gold) {
			List<Span> result = decoder.decode(example.getWords(), parameters, false);
			for(Span span : result) {
				for(Span goldSpan : example.getSpans()) {
					if(span.getStart() == goldSpan.getStart() && span.getEnd() == goldSpan.getEnd() && span.getRule().getParent() == goldSpan.getRule().getParent())
						numberCorrect++;
				}
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
		SaveObject savedModel = SaveObject.loadSaveObject("parallelModel0");
		
		WordEnumeration words = savedModel.getWords();
		LabelEnumeration labels = savedModel.getLabels();
		Rules rules = savedModel.getRules();
		FeatureParameters parameters = savedModel.getParameters();
		
		CKYDecoder decoder = new CKYDecoder(words, labels, rules);
		
		List<Span> result = decoder.decode(Arrays.asList(words.getId("I"), words.getId("like"), words.getId("computers"), words.getId(".")), parameters, false);
		
		System.out.println(result);
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
			CKYDecoder decoder = new CKYDecoder(words, labels, rules);
			PassiveAgressive pa = new PassiveAgressive(words, labels, rules, decoder, params);
			pa.train(examples.subList(0, 1), .05);
			
			// the first example should now classify correctly
			//List<Span> result = decoder.decode(examples.get(0).getWords(), params);
			//loss = PassiveAgressive.computeLoss(result, examples.get(0).getSpans());
			//System.out.println("loss is "+ loss);
		}
	}
	
	public static void sample(WordEnumeration words, LabelEnumeration labels, Rules rules, FeatureParameters parameters) {
		Sampler sampler = new Sampler(words, labels, rules);
		List<Span> spans = sampler.sample(words.getIds(Arrays.asList("I", "go", "to", "the", "supermarket")), parameters);
		System.out.println(spans);
	}
}
