package constituencyParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import constituencyParser.Train.TrainOneIteration;
import constituencyParser.features.FeatureParameters;

public class Test {
	public static void main(String[] args) throws Exception {
		if(args.length < 4) {
			System.out.println("requires 4 arguments: the model file name, the data directory, t/f second order, number of threads");
			System.out.println("optional argument: number of iterations for randomized greedy restart");
			return;
		}

		String modelFile = args[0];
		String dataDir = args[1];
		boolean secondOrder = args[2].equals("t");
		if(!secondOrder && !args[2].equals("f")) {
			System.out.println("second order must be t or f");
		}
		
		int numberOfThreads = Integer.parseInt(args[3]);

		int greedyIterations = 100;
		if(args.length > 4) {
			greedyIterations = Integer.parseInt(args[4]);
		}

		//testPassiveAggressive();

		SaveObject savedModel = SaveObject.loadSaveObject(modelFile);

		WordEnumeration words = savedModel.getWords();
		LabelEnumeration labels = savedModel.getLabels();
		Rules rules = savedModel.getRules();
		FeatureParameters parameters = savedModel.getParameters();

		//sample(words, labels, rules, parameters);


		testParallel(words, labels, rules, parameters, dataDir, secondOrder, greedyIterations, 1, numberOfThreads);

		//decodeSentence();
	}

	public static void test(WordEnumeration words, LabelEnumeration labels, Rules rules, FeatureParameters parameters, String dataFolder, boolean secondOrder) throws IOException {
		test(words, labels, rules, parameters, dataFolder, secondOrder, 100, 1);
	}

	public static void test(WordEnumeration words, LabelEnumeration labels, Rules rules, FeatureParameters parameters, String dataFolder, boolean secondOrder, int randomizedGreedyIterations, double fractionOfData) throws IOException {
		RandomizedGreedyDecoder randGreedyDecoder = new RandomizedGreedyDecoder(words, labels, rules);
		//randGreedyDecoder.samplerDoCounts(PennTreebankReader.loadFromFiles(dataFolder, 2, 22, words, labels, rules)); for non-discriminitive

		List<SpannedWords> gold = PennTreebankReader.loadFromFiles(dataFolder, 0, 1, words, labels, rules);
		int number = (int)(gold.size() * fractionOfData);
		gold = gold.subList(0, number);

		randGreedyDecoder.setNumberSampleIterations(randomizedGreedyIterations);

		int numberCorrect = 0;
		int numberGold = 0;
		int numberOutput = 0;
		for(SpannedWords example : gold) {
			randGreedyDecoder.setSecondOrder(secondOrder);

			List<Span> result = randGreedyDecoder.decode(example.getWords(), parameters, false);

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

	public static class TestPortion implements Callable<TestResult> {
		WordEnumeration words;
		LabelEnumeration labels;
		Rules rules;
		List<SpannedWords> gold;
		FeatureParameters parameters;
		boolean secondOrder;
		int randomizedGreedyIterations;
		
		public TestPortion(WordEnumeration words, LabelEnumeration labels, Rules rules, FeatureParameters parameters, List<SpannedWords> gold, boolean secondOrder, int randomizedGreedyIterations) {
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
			RandomizedGreedyDecoder randGreedyDecoder = new RandomizedGreedyDecoder(words, labels, rules);
			randGreedyDecoder.setNumberSampleIterations(randomizedGreedyIterations);

			randGreedyDecoder.setSecondOrder(secondOrder);
			
			int numberCorrect = 0;
			int numberGold = 0;
			int numberOutput = 0;
			for(SpannedWords example : gold) {
				List<Span> result = randGreedyDecoder.decode(example.getWords(), parameters, false);

				for(Span span : result) {
					for(Span goldSpan : example.getSpans()) {
						if(span.getStart() == goldSpan.getStart() && span.getEnd() == goldSpan.getEnd() && span.getRule().getParent() == goldSpan.getRule().getParent())
							numberCorrect++;
					}
				}

				numberGold += example.getSpans().size();
				numberOutput += result.size();
			}
			return new TestResult(numberCorrect, numberGold, numberOutput);
		}
	}

	public static void testParallel(WordEnumeration words, LabelEnumeration labels, Rules rules, FeatureParameters parameters, String dataFolder, boolean secondOrder, int randomizedGreedyIterations, double fractionOfData, int numberThreads) throws IOException, InterruptedException, ExecutionException {
		Random random = new Random();
		
		List<SpannedWords> gold = PennTreebankReader.loadFromFiles(dataFolder, 0, 1, words, labels, rules);
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
		
		double precision = totalCorrect / (double)totalOutput;
		double recall = totalCorrect / (double)totalGold;

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
