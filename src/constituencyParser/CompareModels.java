package constituencyParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import constituencyParser.features.FeatureParameters;
import constituencyParser.features.Features;

public class CompareModels {
	public static void main(String[] args) throws Exception {

		OptionParser parser = new OptionParser("d:s:t:i:");
		OptionSet options = parser.parse(args);
		
		String dataDir = "";
		boolean secondOrder = true;
		int numberOfThreads = 1;
		int greedyIterations = 100;
		
		if(options.has("d")) {
			dataDir = (String)options.valueOf("d");
		}
		if(options.has("s")) {
			secondOrder = "t".equals(options.valueOf("s"));
		}
		if(options.has("t")) {
			numberOfThreads = Integer.parseInt((String)options.valueOf("t"));
		}
		if(options.has("i")) {
			greedyIterations = Integer.parseInt((String)options.valueOf("i"));
		}

		test(dataDir, secondOrder, greedyIterations, 1, numberOfThreads);
	}

	public static void test(String dataFolder, boolean secondOrder, int randomizedGreedyIterations, double fractionOfData, int threads) throws IOException, ClassNotFoundException {
		SaveObject savedModel = SaveObject.loadSaveObject("model_feb_order_1/modelIteration2");
		SaveObject savedModel2 = SaveObject.loadSaveObject("model_feb_order_2/modelIteration2");

		WordEnumeration words = savedModel.getWords();
		LabelEnumeration labels = savedModel.getLabels();
		RuleEnumeration rules = savedModel.getRules();
		FeatureParameters parameters = savedModel.getParameters();
		
		FeatureParameters parameters2 = savedModel2.getParameters();
		
		RandomizedGreedyDecoder randGreedyDecoder = new RandomizedGreedyDecoder(words, labels, rules, threads);
		
		List<SpannedWords> gold = PennTreebankReader.loadFromFiles(dataFolder, 23, 24, words, labels, rules, false);
		int number = (int)(gold.size() * fractionOfData);
		gold = gold.subList(0, number);

		randGreedyDecoder.setNumberSampleIterations(randomizedGreedyIterations);
		
		for(SpannedWords example : gold) {
			randGreedyDecoder.setSecondOrder(secondOrder);

			List<Span> result = randGreedyDecoder.decode(example.getWords(), parameters);
			List<Span> result2 = randGreedyDecoder.decode(example.getWords(), parameters2);

			int commonWith1 = 0;
			for(Span span : result) {
				for(Span goldSpan : example.getSpans()) {
					if(span.getStart() == goldSpan.getStart() && span.getEnd() == goldSpan.getEnd() && span.getRule().getLabel() == goldSpan.getRule().getLabel())
						commonWith1++;
				}
			}
			
			int commonWith2 = 0;
			for(Span span : result2) {
				for(Span goldSpan : example.getSpans()) {
					if(span.getStart() == goldSpan.getStart() && span.getEnd() == goldSpan.getEnd() && span.getRule().getLabel() == goldSpan.getRule().getLabel())
						commonWith2++;
				}
			}
			
			if (!(new HashSet<>(result)).equals(new HashSet<>(result2))) {
				System.out.println("1 has " + commonWith1 + " correct");
				System.out.println("2 has " + commonWith2 + " correct");
				System.out.println(example.getWords());
				SpanUtilities.printSpans(example.getSpans(), example.getWords().size(), labels);
				
				SpanUtilities.printSpans(result, example.getWords().size(), labels);
				SpanUtilities.printSpans(result2, example.getWords().size(), labels);
				
				List<Long> features1 = Features.getAllFeatures(result, example.getWords(), true, words, labels, rules);
				List<Long> features2 = Features.getAllFeatures(result2, example.getWords(), true, words, labels, rules);
				
				double secondOrderParamScoreForFirstOrder = 0;
				double firstOrderParamScoreForFirstOrder = 0;
				for(long f : features1) {
					secondOrderParamScoreForFirstOrder += parameters2.getScore(f);
					firstOrderParamScoreForFirstOrder += parameters.getScore(f);
				}
				
				System.out.println("fo score for fo " + firstOrderParamScoreForFirstOrder + " so score for fo " + secondOrderParamScoreForFirstOrder);
				
				double secondOrderParamScoreForSecondOrder = 0;
				double firstOrderParamScoreForSecondOrder = 0;
				for(long f : features2) {
					secondOrderParamScoreForSecondOrder += parameters2.getScore(f);
					firstOrderParamScoreForSecondOrder += parameters.getScore(f);
				}
				
				System.out.println("fo score for so " + firstOrderParamScoreForSecondOrder + " so score for so " + secondOrderParamScoreForSecondOrder);
				
				List<Long> common = new ArrayList<>(features1);
				common.retainAll(features2);
				System.out.println("model 1 only");
				features1.removeAll(common);
				for(long f : features1) {
					System.out.println(Features.getStringForCode(f, words, rules, labels) + " " + parameters.getScore(f) + " " + parameters2.getScore(f));
				}
				System.out.println("model 2 only");
				features2.removeAll(common);
				for(long f : features2) {
					System.out.println(Features.getStringForCode(f, words, rules, labels) + " " + parameters.getScore(f) + " " + parameters2.getScore(f));
				}
			}
			else {
				System.out.println("same");
			}
		}
	}
}
