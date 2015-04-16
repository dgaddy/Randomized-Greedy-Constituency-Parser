package constituencyParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import constituencyParser.TreeNode.Bracket;
import constituencyParser.features.FeatureParameters;
import constituencyParser.features.Features;

/**
 * Has methods for testing parsers in comparison to development / test data
 */
public class Test {
	public static void main(String[] args) throws Exception {
		OptionParser parser = new OptionParser("m:d:s:t:i:w:zp:");
		OptionSet options = parser.parse(args);
		
		String modelFile = "";
		String dataDir = "";
		boolean secondOrder = true;
		int numberOfThreads = 1;
		int greedyIterations = 100;
		int section = 0;
		boolean useRandGreedy = true;
		double percentOfData = 1;
		
		if(options.has("m")) {
			modelFile = (String)options.valueOf("m");
		}
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
		if(options.has("w")) {
			section = Integer.parseInt((String)options.valueOf("w"));
		}
		if(options.has("z")) {
			useRandGreedy = false;
			secondOrder = false;
		}
		if(options.has("p")) {
			percentOfData = Double.parseDouble((String)options.valueOf("p"));
			if(percentOfData > 1 || percentOfData < 0)
				throw new RuntimeException("Percent of data should be between 0 and 1");
		}

		SaveObject savedModel = SaveObject.loadSaveObject(modelFile);

		WordEnumeration words = savedModel.getWords();
		LabelEnumeration labels = savedModel.getLabels();
		RuleEnumeration rules = savedModel.getRules();
		FeatureParameters parameters = savedModel.getParameters();

		test(words, labels, rules, parameters, dataDir, secondOrder, greedyIterations, percentOfData, numberOfThreads, useRandGreedy, section, "", "");
	}

	public static void test(WordEnumeration words, LabelEnumeration labels, RuleEnumeration rules, FeatureParameters parameters, 
			String dataFolder, boolean secondOrder, int randomizedGreedyIterations, double fractionOfData, int threads, 
			boolean useRandGreedy, int section, String outputFolder, String runid) throws IOException {
		Decoder decoder;
		//DiscriminativeCKYDecoder CKYDecoder;
		
		if(useRandGreedy) {
			RandomizedGreedyDecoder rg = new RandomizedGreedyDecoder(words, labels, rules, threads);
			rg.setNumberSampleIterations(randomizedGreedyIterations);
			decoder = rg;
		}
		else
			decoder = new DiscriminativeCKYDecoder(words, labels, rules);
		//CKYDecoder = new DiscriminativeCKYDecoder(words, labels, rules);
		
		List<SpannedWords> gold = PennTreebankReader.loadFromFiles(dataFolder, section, section + 1, words, labels, rules, false);
		int number = (int)(gold.size() * fractionOfData);
		gold = gold.subList(0, number);
		
		//parameters.resetDropout(0); // this makes sure any dropout from training isn't used when we are testing

		int numberCorrect = 0;
		int numberGold = 0;
		int numberOutput = 0;
		
		PennTreebankWriter writer = new PennTreebankWriter(outputFolder + "output.tst." + runid, words, labels, false);
		PennTreebankWriter writer_gold = new PennTreebankWriter(outputFolder + "output.gld." + runid, words, labels, false);
		//String goldFile = "./data/wsj." + section + ".txt";
		
		int cnt = 0;
		for(SpannedWords example : gold) {
			cnt++;
			if (cnt % 10 == 0)
				System.out.print("  " + cnt);
			
			//List<Span> CKYPredicted = CKYDecoder.decode(example.getWords(), parameters);
			//((RandomizedGreedyDecoder)decoder).ckySpan = CKYPredicted;

			List<Bracket> goldBrackets = TreeNode.makeTreeFromSpans(example.getSpans(), example.getWords(), words, labels).unbinarize().getAllBrackets();
			
			decoder.setSecondOrder(secondOrder);

			List<Span> result = decoder.decode(example.getWords(), parameters);
			if(result.size() == 0) {
				numberGold += goldBrackets.size();
				continue;
			}
			
			writer.writeTree(new SpannedWords(result, example.getWords()));
			writer_gold.writeTree(example);
			
			List<Bracket> resultBrackets = TreeNode.makeTreeFromSpans(result, example.getWords(), words, labels).unbinarize().getAllBrackets();
			
			for(Bracket b : resultBrackets) {
				for(Bracket g : goldBrackets) {
					if(b.equals(g)) {
						numberCorrect++;
					}
				}
			}
			
			List<Long> goldFeatures = Features.getAllFeatures(example.getSpans(), example.getWords(), secondOrder, words, labels, rules);
			double goldScore = 0;
			for(Long code : goldFeatures) {
				goldScore += parameters.getScore(code);
			}
			
			if(goldScore > decoder.getLastScore() + 1e-5) {
				System.out.println("Gold score higher than predicted, but was not found. " + goldScore + " " + decoder.getLastScore());
			}

			numberGold += goldBrackets.size();
			numberOutput += resultBrackets.size();
		}
		System.out.println();
		writer.close();
		writer_gold.close();
		
		double precision = numberCorrect / (double)numberOutput;
		double recall = numberCorrect / (double)numberGold;

		double score = 2*precision*recall/(precision+recall);
		System.out.println("Development set score: " + score);
		
		// run script
		runEval(outputFolder + "output.gld." + runid, outputFolder + "output.tst." + runid);
		//runEval(goldFile, outputFolder + "output.tst." + runid);
	}
	
	public static void runEval(String gold, String test) {
		System.out.println("gold: " + gold + " test: " + test);
		try {
			Runtime r = Runtime.getRuntime();
			Process p;
			p = r.exec("./EVALB/evalb -p EVALB/new.prm " + gold + " " + test);
			BufferedReader b = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String line = b.readLine();
			while (line != null && !line.startsWith("=== Summary ==="))
				line = b.readLine();

			System.out.println("summary");
			while (line != null) {
			  System.out.println(line);
			  line = b.readLine();
			}
			p.waitFor();
			System.out.println("evalutaion done");

			b.close();		
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Basic metrics from testing a group of sentences used to pass results by parallel testing
	 */
	public static class TestResult {
		int numberCorrect;
		int numberGold;
		int numberOutput;
		
		public TestResult(int numCorrect, int numGold, int numOutput) {
			numberCorrect = numCorrect;
			numberGold = numGold;
			numberOutput = numOutput;
		}
	}

	/**
	 * Tests sentences and returns a TestResult.  Used for parallel testing.
	 */
	public static class TestPortion implements Callable<TestResult> {
		WordEnumeration words;
		LabelEnumeration labels;
		RuleEnumeration rules;
		List<SpannedWords> gold;
		FeatureParameters parameters;
		boolean secondOrder;
		int randomizedGreedyIterations;
		
		public TestPortion(WordEnumeration words, LabelEnumeration labels, RuleEnumeration rules, FeatureParameters parameters, List<SpannedWords> gold, boolean secondOrder, int randomizedGreedyIterations) {
			this.words = words;
			this.labels = labels;
			this.rules = rules;
			this.gold = gold;
			this.parameters = parameters;
			this.secondOrder = secondOrder;
			this.randomizedGreedyIterations = randomizedGreedyIterations;
		}
		
		@Override
		public TestResult call() throws Exception {
			RandomizedGreedyDecoder randGreedyDecoder = new RandomizedGreedyDecoder(words, labels, rules, 1);
			randGreedyDecoder.setNumberSampleIterations(randomizedGreedyIterations);

			randGreedyDecoder.setSecondOrder(secondOrder);
			
			int numberCorrect = 0;
			int numberGold = 0;
			int numberOutput = 0;
			for(SpannedWords example : gold) {
				List<Span> result = randGreedyDecoder.decode(example.getWords(), parameters);

				for(Span span : result) {
					for(Span goldSpan : example.getSpans()) {
						if(span.getStart() == goldSpan.getStart() && span.getEnd() == goldSpan.getEnd() && span.getRule().getLabel() == goldSpan.getRule().getLabel())
							numberCorrect++;
					}
				}

				numberGold += example.getSpans().size();
				numberOutput += result.size();
			}
			return new TestResult(numberCorrect, numberGold, numberOutput);
		}
	}

	/**
	 * Test on development set running on multiple threads
	 */
	public static void testParallel(WordEnumeration words, LabelEnumeration labels, RuleEnumeration rules, FeatureParameters parameters, String dataFolder, boolean secondOrder, int randomizedGreedyIterations, double fractionOfData, int numberThreads) throws IOException, InterruptedException, ExecutionException {
		Random random = new Random();
		
		List<SpannedWords> gold = PennTreebankReader.loadFromFiles(dataFolder, 0, 1, words, labels, rules, false);
		int number = (int)(gold.size() * fractionOfData);
		gold = gold.subList(0, number);

		List<List<SpannedWords>> data = new ArrayList<>();
		for(int t = 0; t < numberThreads; t++) {
			data.add(new ArrayList<SpannedWords>());
		}
		for(SpannedWords example : gold) {
			data.get(random.nextInt(numberThreads)).add(example);
		}
		
		ExecutorService pool = Executors.newFixedThreadPool(numberThreads);
		
		List<Future<TestResult>> futures = new ArrayList<>();
		for(List<SpannedWords> d : data) {
			Future<TestResult> future = pool.submit(new TestPortion(words, labels, rules, parameters, d, secondOrder, randomizedGreedyIterations));
			futures.add(future);
		}
		
		int totalCorrect = 0;
		int totalGold = 0;
		int totalOutput = 0;
		for(Future<TestResult> future : futures) {
			TestResult result = future.get();
			totalCorrect += result.numberCorrect;
			totalGold += result.numberGold;
			totalOutput += result.numberOutput;
		}
		
		pool.shutdown();
		
		double precision = totalCorrect / (double)totalOutput;
		double recall = totalCorrect / (double)totalGold;

		double score = 2*precision*recall/(precision+recall);
		System.out.println("Development set score: " + score);
	}

	///////////////////////////////////////////////////
	// Various methods for testing different components
	
	public static void decodeSentence() throws Exception {
		SaveObject savedModel = SaveObject.loadSaveObject("modelIteration1");

		WordEnumeration words = savedModel.getWords();
		LabelEnumeration labels = savedModel.getLabels();
		RuleEnumeration rules = savedModel.getRules();
		FeatureParameters parameters = savedModel.getParameters();

		System.out.println(rules.getNumberOfUnaryRules());

		DiscriminativeCKYDecoder decoder = new DiscriminativeCKYDecoder(words, labels, rules);

		List<Span> result = decoder.decode(words.getWords(Arrays.asList("I", "go", "to", "the", "supermarket", ".")), parameters);

		RandomizedGreedyDecoder decoder2 = new RandomizedGreedyDecoder(words, labels, rules, 1);

		List<Span> result2 = decoder2.decode(words.getWords(Arrays.asList("I", "go", "to", "the", "supermarket", ".")), parameters);

		List<Span> result3 = decoder2.decodeNoGreedy(words.getWords(Arrays.asList("I", "go", "to", "the", "supermarket", ".")), parameters);

		System.out.println(result);
		System.out.println(result2);
		System.out.println(result3);
	}

	public static void testPassiveAggressive() throws Exception {
		WordEnumeration words = new WordEnumeration(true, 100);
		LabelEnumeration labels = new LabelEnumeration();
		RuleEnumeration rules = new RuleEnumeration();
		List<SpannedWords> examples = PennTreebankReader.loadFromFiles("../WSJ data/", 2, 3, words, labels, rules, false);

		int loss = 1;
		FeatureParameters params = new FeatureParameters(1, 0);

		while(loss > 0) {
			// run passive aggressive on the first example
			DiscriminativeCKYDecoder decoder = new DiscriminativeCKYDecoder(words, labels, rules);
			Train pa = new Train(words, labels, rules, decoder, params);
			pa.train(examples.subList(0, 1), .05, false, false, 1, 0, false);

			// the first example should now classify correctly
			//List<Span> result = decoder.decode(examples.get(0).getWords(), params);
			//loss = PassiveAgressive.computeLoss(result, examples.get(0).getSpans());
			//System.out.println("loss is "+ loss);
		}
	}

	public static void testCKY() throws IOException {
		String dataFolder = "../WSJ data/";
		WordEnumeration words = new WordEnumeration(true, 100);
		LabelEnumeration labels = new LabelEnumeration();
		RuleEnumeration rules = new RuleEnumeration();
		CKYDecoder decoder = new CKYDecoder(words, labels, rules);
		decoder.doCounts(PennTreebankReader.loadFromFiles(dataFolder, 2, 22, words, labels, rules, false));
		List<SpannedWords> gold = PennTreebankReader.loadFromFiles(dataFolder, 0, 1, words, labels, rules, false);
		List<Span> result = decoder.decode(gold.get(0).getWords());
		System.out.println(result);
	}
}
