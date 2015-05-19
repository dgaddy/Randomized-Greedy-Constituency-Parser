package constituencyParser.unlabeled;

import java.io.IOException;
import java.util.List;

import constituencyParser.Decoder;
import constituencyParser.LabelEnumeration;
import constituencyParser.Rule.Type;
import constituencyParser.PennTreebankReader;
import constituencyParser.RuleEnumeration;
import constituencyParser.SaveObject;
import constituencyParser.Span;
import constituencyParser.SpannedWords;
import constituencyParser.TestOptions;
import constituencyParser.TreeNode;
import constituencyParser.WordEnumeration;
import constituencyParser.features.FeatureParameters;
import constituencyParser.features.Features;

/**
 * Has methods for testing parsers in comparison to development / test data
 */
public class Test {
	public static void main(String[] args) throws Exception {
		TestOptions options = new TestOptions(args);
		
		SaveObject savedModel = SaveObject.loadSaveObject(options.modelFile);

		WordEnumeration words = savedModel.getWords();
		LabelEnumeration labels = savedModel.getLabels();
		RuleEnumeration rules = savedModel.getRules();
		FeatureParameters parameters = savedModel.getParameters();

		test(words, labels, rules, parameters, options);
	}

	public static void test(WordEnumeration words, LabelEnumeration labels, RuleEnumeration rules, FeatureParameters parameters, TestOptions options) throws IOException {
		List<TreeNode> trees = PennTreebankReader.loadTreeNodesFromFiles(PennTreebankReader.getWSJFiles(options.dataDir, options.section, options.section + 1));
		BinaryHeadPropagation prop = BinaryHeadPropagation.getBinaryHeadPropagation(trees);
		List<SpannedWords> gold = prop.getExamples(false, words, rules);
		labels = prop.getLabels();
		int number = (int)(gold.size() * options.percentOfData);
		gold = gold.subList(0, number);
		
		Decoder decoder;
		if(options.secondOrder)
			decoder = new RandomizedGreedyDecoder(words, labels, rules, options.numberOfThreads, prop);
		else
			decoder = new UnlabeledCKY(words, labels, rules);
		
		parameters.resetDropout(0); // this makes sure any dropout from training isn't used when we are testing
		
		int numberUnlabeledSpanCorrect = 0;
		int numberGoldUnlabeledSpan = 0;
		int numberOutputUnlabeledSpan = 0;
		
		int cnt = 0;
		for(SpannedWords example : gold) {
			cnt++;
			if (cnt % 10 == 0)
				System.out.print("  " + cnt);
			
			List<Span> result = decoder.decode(example.getWords(), parameters);
			
			List<Long> goldFeatures = Features.getAllFeatures(example.getSpans(), example.getWords(), options.secondOrder, words, labels, rules, options.useRandGreedy);
			double goldScore = 0;
			for(Long code : goldFeatures) {
				goldScore += parameters.getScore(code);
			}
			if(goldScore > decoder.getLastScore() + 1e-4) {
				System.out.println("gold score higher than predicted " + goldScore + " "  + decoder.getLastScore());
			}
			
			for(Span span : result) {
				if(span.getRule().getType() != Type.BINARY)
					continue;
				
				numberOutputUnlabeledSpan++;
				for(Span goldSpan : example.getSpans()) {
					if(goldSpan.getRule().getType() != Type.BINARY)
						continue;
					
					if(span.getStart() == goldSpan.getStart() && span.getEnd() == goldSpan.getEnd())
						numberUnlabeledSpanCorrect++;
				}
			}
			for(Span goldSpan : example.getSpans()) {
				if(goldSpan.getRule().getType() == Type.BINARY)
					numberGoldUnlabeledSpan++;
			}
		}
		System.out.println();
		
		double precision = numberUnlabeledSpanCorrect / (double)numberOutputUnlabeledSpan;
		double recall = numberUnlabeledSpanCorrect / (double) numberGoldUnlabeledSpan;
		double score = 2*precision*recall/(precision+recall);
		System.out.println("Unlabeled spans score: " + score);
	}
}
