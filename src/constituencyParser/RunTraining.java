package constituencyParser;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import constituencyParser.features.FeatureParameters;
import constituencyParser.features.Features;


public class RunTraining {
	public static void main(String[] args) throws Exception {
		OptionParser parser = new OptionParser("t:o:c:i:s:a:p:m:l:b:r:d:zn");
		OptionSet options = parser.parse(args);
		
		String dataFolder = "";
		String outputFolder = "";
		int cores = 1;
		int iterations = 1;
		boolean secondOrder = true;
		boolean costAugmenting = true;
		String startModel = null;
		double percentOfData = 1;
		double learningRate = 1;
		int batchSize = 1;
		double regularization = 0.01;
		double dropout = 0.0;
		boolean randGreedy = true;
		boolean noNegativeFeatures = false;
		
		if(options.has("t")) {
			dataFolder = (String)options.valueOf("t");
		}
		if(options.has("o")) {
			outputFolder = (String)options.valueOf("o");
		}
		if(options.has("c")) {
			cores = Integer.parseInt((String)options.valueOf("c"));
		}
		if(options.has("i")) {
			iterations = Integer.parseInt((String)options.valueOf("i"));
		}
		if(options.has("s")) {
			secondOrder = "t".equals(options.valueOf("s"));
		}
		if(options.has("a")) {
			costAugmenting = "t".equals(options.valueOf("a"));
		}
		if(options.has("p")) {
			percentOfData = Double.parseDouble((String)options.valueOf("p"));
		}
		if(options.has("m")) {
			startModel = (String)options.valueOf("m");
		}
		if(options.has("l")) {
			learningRate = Double.parseDouble((String)options.valueOf("l"));
		}
		if(options.has("b")) {
			batchSize = Integer.parseInt((String)options.valueOf("b"));
		}
		if(options.has("r")) {
			regularization = Double.parseDouble((String)options.valueOf("r"));
		}
		if(options.has("d")) {
			dropout = Double.parseDouble((String)options.valueOf("d"));
		}
		if(options.has("z")) {
			randGreedy = false;
			secondOrder = false;
			costAugmenting = false;
		}
		if(options.has("n")) {
			noNegativeFeatures = true;
		}
		
		System.out.println("Running training with " + cores + " cores for " + iterations + " iterations.");
		System.out.println("Data directory: " + dataFolder);
		System.out.println("Output directory: " + outputFolder);
		System.out.println("Cores: " + cores);
		System.out.println("Iteration: " + iterations);
		System.out.println("secondOrder: " + secondOrder);
		System.out.println("costAugmenting: " + costAugmenting);
		System.out.println("learningRate: " + learningRate);
		System.out.println("batchSize: " + batchSize);
		System.out.println("regularization: " + regularization);
		System.out.println("dropout: " + dropout);
		System.out.println("noNegativeFeatures: " + noNegativeFeatures);
		System.out.println("randGreedy: " + randGreedy);
		if(startModel != null)
			System.out.println("starting from " + startModel);
		if(percentOfData < 1)
			System.out.println("using " + percentOfData + " of data");
		
		train(dataFolder, outputFolder, cores, iterations, percentOfData, dropout, startModel, secondOrder, costAugmenting, learningRate, batchSize, regularization, randGreedy, noNegativeFeatures);
	}
	
	public static void train(String dataFolder, String outputFolder, int cores, int iterations, double percentOfData, double dropout, String startModel, boolean secondOrder, boolean costAugmenting, double learningRate, int batchSize, double regularization, boolean useRandGreedy, boolean noNegativeFeatures) throws IOException, ClassNotFoundException {
		WordEnumeration words = new WordEnumeration();
		LabelEnumeration labels = new LabelEnumeration();
		RuleEnumeration rules = new RuleEnumeration();
		FeatureParameters params = new FeatureParameters(learningRate, regularization);
		
		if(startModel != null) {
			SaveObject start = SaveObject.loadSaveObject(startModel);
			words = start.getWords();
			labels = start.getLabels();
			rules = start.getRules();
			params = start.getParameters();
		}
		
		System.out.println("load data... ");
		
		List<SpannedWords> examples = PennTreebankReader.loadFromFiles(dataFolder, 2,22, words, labels, rules, true); // use only between 2 and 21 for training
		if(percentOfData < 1) {
			examples = new ArrayList<>(examples.subList(0, (int)(examples.size() * percentOfData)));
		}
		
		System.out.println("build alphabet...");
		if(noNegativeFeatures) {
			int cnt = 0;
			for(SpannedWords ex : examples) {
				cnt++;
				if (cnt % 1000 == 0)
					System.out.print("  " + cnt);
				List<Long> features = Features.getAllFeatures(ex.getSpans(), ex.getWords(), secondOrder, words, labels, rules);
				params.ensureContainsFeatures(features);
			}
			params.stopAddingFeatures();
		}
		System.out.println(" Done.");
		
		Decoder decoder;
		if(useRandGreedy)
			decoder = new RandomizedGreedyDecoder(words, labels, rules, cores);
		else
			decoder = new DiscriminativeCKYDecoder(words, labels, rules);
		
		Train pa = new Train(words, labels, rules, decoder, params);
		
		for(int i = 0; i < iterations; i++) {
			System.out.println("Iteration " + i + ":");
			pa.train(examples, dropout, secondOrder, costAugmenting, batchSize, i);
			params = pa.getParameters();
			
			params.averageParameters((i + 1) * examples.size());
			Test.test(words, labels, rules, params, dataFolder, secondOrder, 100, .3, cores, useRandGreedy, 0);
			
			SaveObject so = new SaveObject(words, labels, rules, params);
			so.save(outputFolder + "/modelIteration"+i);
			params.unaverageParameters();
		}
	}
	
	static class TrainResult {
		FeatureParameters finalParameters;
		FeatureParameters parametersAveragedOverIterations;
	}
	
	/**
	 * Class used for running several trainings in parallel
	 * @author david
	 *
	 */
	static class TrainOneIteration implements Callable<TrainResult> {
		WordEnumeration words;
		LabelEnumeration labels;
		RuleEnumeration rules;
		List<SpannedWords> data;
		FeatureParameters initialParams;
		double dropout;
		boolean secondOrder;
		boolean costAugmenting;
		
		public TrainOneIteration(WordEnumeration words, LabelEnumeration labels, RuleEnumeration rules, List<SpannedWords> data, FeatureParameters initialParams, double dropout, boolean secondOrder, boolean costAugmenting) {
			this.words = words;
			this.labels = labels;
			this.rules = rules;
			this.data = data;
			this.initialParams = initialParams;
			this.dropout = dropout;
			this.secondOrder = secondOrder;
			this.costAugmenting = costAugmenting;
		}

		@Override
		public TrainResult call() throws Exception {
			System.out.println("Starting new training.");
			
			RandomizedGreedyDecoder decoder = new RandomizedGreedyDecoder(words, labels, rules, 1);
			Train pa = new Train(words, labels, rules, decoder, initialParams);
			try {
				pa.train(data, dropout, secondOrder, costAugmenting, 1, 0);
			}
			catch(Exception ex) {
				ex.printStackTrace(); //because otherwise exceptions get caught by the executor and don't give a stack trace
				throw ex;
			}
			words = null;
			labels = null;
			rules = null;
			data = null;
			initialParams = null;
			
			TrainResult result = new TrainResult();
			result.finalParameters = pa.getParameters();
			
			return result;
		}
	}
	
	/**
	 * Trains a model in parallel by running numberThreads different training in parallel on different subsets of the data then combining them at the end of each iteration
	 * @param dataFolder
	 * @param outputFolder
	 * @param numberThreads
	 * @param iterations
	 * @param percentOfData
	 * @param dropout
	 * @param startModel
	 * @param secondOrder
	 * @param costAugmenting
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws ExecutionException
	 * @throws ClassNotFoundException
	 */
	public static void trainParallel(String dataFolder, String outputFolder, int numberThreads, int iterations, double percentOfData, double dropout, String startModel, boolean secondOrder, boolean costAugmenting) throws IOException, InterruptedException, ExecutionException, ClassNotFoundException {
		WordEnumeration words = new WordEnumeration();
		LabelEnumeration labels = new LabelEnumeration();
		RuleEnumeration rules = new RuleEnumeration();
		FeatureParameters shared = new FeatureParameters(1, 0);
		
		if(startModel != null) {
			SaveObject start = SaveObject.loadSaveObject(startModel);
			words = start.getWords();
			labels = start.getLabels();
			rules = start.getRules();
			shared = start.getParameters();
		}
		
		List<SpannedWords> unsplitData = PennTreebankReader.loadFromFiles(dataFolder, 2,22, words, labels, rules, true); // use only between 2 and 21 for training
		if(percentOfData < 1) {
			unsplitData = new ArrayList<>(unsplitData.subList(0, (int)(unsplitData.size() * percentOfData)));
		}
		
		ExecutorService pool = Executors.newFixedThreadPool(numberThreads);
		
		Random random = new Random();
		
		for(int i = 0; i < iterations; i++) {
			System.out.println("Iteration " + i);
			
			List<List<SpannedWords>> data = new ArrayList<>();
			for(int t = 0; t < numberThreads; t++) {
				data.add(new ArrayList<SpannedWords>());
			}
			for(SpannedWords example : unsplitData) {
				data.get(random.nextInt(numberThreads)).add(example);
			}
			
			List<Future<TrainResult>> futures = new ArrayList<>();
			for(List<SpannedWords> d : data) {
				Future<TrainResult> future = pool.submit(new TrainOneIteration(new WordEnumeration(words), new LabelEnumeration(labels), new RuleEnumeration(rules), d, new FeatureParameters(shared), dropout, secondOrder, costAugmenting));
				futures.add(future);
			}
			
			List<FeatureParameters> finalParams = new ArrayList<>();
			for(Future<TrainResult> future : futures) {
				TrainResult result = future.get();
				finalParams.add(result.finalParameters);
			}
			
			shared = FeatureParameters.average(finalParams);
			
			Test.test(words, labels, rules, shared, dataFolder, secondOrder, 100, .1, 1, true, 0);
			
			SaveObject so = new SaveObject(words, labels, rules, shared);
			so.save(outputFolder + "/modelIteration"+i);
		}
		
		pool.shutdown();
	}
}
