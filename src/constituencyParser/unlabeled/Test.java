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
import constituencyParser.SpanUtilities;
import constituencyParser.SpannedWords;
import constituencyParser.TestOptions;
import constituencyParser.TreeNode;
import constituencyParser.WordEnumeration;
import constituencyParser.features.FeatureParameters;

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
		BinaryHeadPropagation prop = BinaryHeadPropagation.getBinaryHeadPropagation(trees, labels);
		List<SpannedWords> gold = prop.getExamples(false, words, rules); // TODO: change first parameter to false
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
		
		int POSCorrect = 0;
		int POSTotal = 0;
		int numberUnlabeledSpanCorrectGoldPOS = 0;
		
		int cnt = 0;
		for(SpannedWords example : gold) {
			cnt++;
			if (cnt % 10 == 0)
				System.out.print("  " + cnt);
			
			List<Span> result = decoder.decode(example.getWords(), parameters);
			// TODO: fix
			//List<Span> result = ((UnlabeledCKY)decoder).decodeWithPOS(example.getWords(), SpanUtilities.getPOS(example.getWords().size(), example.getSpans()), parameters);
			
			List<Long> goldFeatures = UnlabeledFeatures.getAllFeatures(example.getSpans(), example.getWords(), options.secondOrder, words, labels, rules, options.useRandGreedy);
			double goldScore = 0;
			for(Long code : goldFeatures) {
				goldScore += parameters.getScore(code);
			}
			if(goldScore > decoder.getLastScore() + 1e-4) {
				System.out.println("gold score higher than predicted " + goldScore + " "  + decoder.getLastScore());
				//SpanUtilities.printSpans(example.getSpans(), example.getWords().size(), labels);
				//SpanUtilities.printSpans(result, example.getWords().size(), labels);
			}
			
			List<Long> features = UnlabeledFeatures.getAllFeatures(result, example.getWords(), options.secondOrder, words, labels, rules, options.useRandGreedy);
			double score = 0;
			for(Long code : features) {
				score += parameters.getScore(code);
			}
			if(Math.abs(score - decoder.getLastScore()) > 1e-4)
				throw new RuntimeException("Decoder score doesn't match.");
			
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
			
			List<Integer> goldPOS = SpanUtilities.getPOS(example.getWords().size(), example.getSpans());
			//System.out.println(example.getWords());
			//System.out.println(goldPOS);
			List<Integer> resultPOS = SpanUtilities.getPOS(example.getWords().size(), result);
			//System.out.println(resultPOS);
			//UnlabeledCKY d = (UnlabeledCKY)decoder;
			//double goldPOSScore = 0;
			//double resultPOSScore = 0;
			for(int i = 0; i < goldPOS.size(); i++) {
				int gp = goldPOS.get(i);
				int rp = resultPOS.get(i);
				if(gp == rp)
					POSCorrect++;
				//goldPOSScore += d.scores[i][i+1][gp];
				//resultPOSScore += d.scores[i][i+1][rp];
			}
			//System.out.println("POS scores gold " + goldPOSScore + " result " + resultPOSScore);
			POSTotal += goldPOS.size();
			
			List<Integer> posLabels = labels.getPOSLabels();
			for(Integer pos : goldPOS)
				if(!posLabels.contains(pos))
					throw new RuntimeException("POS not known: " + pos);
			
			/*for(Span s : example.getSpans()) {
				boolean good = false;
				for(int r = 0; r < rules.getNumberOfBinaryRules(); r++) {
					if(rules.getBinaryRule(r).equals(s.getRule()))
						good = true;
				}
				if(!good)
					System.out.println("Head propagated differently in gold");
			}*/
			// TODO: this happens a lot
			
			if(!options.useRandGreedy) {
				List<Span> resultPOSGold = ((UnlabeledCKY)decoder).decodeWithPOS(example.getWords(), goldPOS, parameters);
				//SpanUtilities.printSpans(resultPOSGold, example.getWords().size(), labels);
				//System.out.println("POS gold score " + decoder.getLastScore());
				
				features = UnlabeledFeatures.getAllFeatures(resultPOSGold, example.getWords(), options.secondOrder, words, labels, rules, options.useRandGreedy);
				score = 0;
				for(Long code : features) {
					score += parameters.getScore(code);
				}
				if(Math.abs(score - decoder.getLastScore()) > 1e-4)
					throw new RuntimeException("Decoder score doesn't match.");
				
				for(Span span : resultPOSGold) {
					if(span.getRule().getType() != Type.BINARY)
						continue;
					
					for(Span goldSpan : example.getSpans()) {
						if(goldSpan.getRule().getType() != Type.BINARY)
							continue;
						
						if(span.getStart() == goldSpan.getStart() && span.getEnd() == goldSpan.getEnd())
							numberUnlabeledSpanCorrectGoldPOS++;
					}
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
		System.out.println("POS accuracy: " + (POSCorrect / (double)POSTotal));
		precision = numberUnlabeledSpanCorrectGoldPOS / (double)numberOutputUnlabeledSpan;
		recall = numberUnlabeledSpanCorrectGoldPOS / (double) numberGoldUnlabeledSpan;
		score = 2*precision*recall/(precision+recall);
		System.out.println("Span score with gold POS: " + score);
	}
}
