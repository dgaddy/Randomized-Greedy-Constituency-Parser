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

import constituencyParser.features.FeatureParameters;


public class RunTraining {
	public static void main(String[] args) throws Exception {
		if(args.length < 6) {
			System.out.println("Arguments are [data folder] [output folder] [number cores] [number iterations] [t/f second order] [t/f cost augmentation] [optional: percent of data] [optional: model to start with]");
			return;
		}
		
		double dropout = .45;
		
		String dataFolder = args[0];
		String outputFolder = args[1];
		int cores = Integer.parseInt(args[2]);
		int iterations = Integer.parseInt(args[3]);
		boolean secondOrder = args[4].equals("t");
		boolean costAugmenting = args[5].equals("t");
		
		if((!secondOrder && !args[4].equals("f")) || (!costAugmenting && !args[5].equals("f"))) {
			System.out.println("second order and cost augmentation args must be t or f");
			return;
		}
		
		String startModel = null;
		double percentOfData = 1;
		
		if(args.length > 7) {
			startModel = args[7];
		}
		if(args.length > 6) {
			percentOfData = Double.parseDouble(args[6]);
			if(percentOfData > 1) {
				System.out.println("Percent of data must be less than 1");
				return;
			}
		}
		
		System.out.println("Running training with " + cores + " cores for " + iterations + " iterations.");
		System.out.println("Data directory: " + dataFolder);
		System.out.println("Output directory: " + outputFolder);
		if(startModel != null)
			System.out.println("starting from " + startModel);
		if(percentOfData < 1)
			System.out.println("using " + percentOfData + " of data");
		
		trainParallel(dataFolder, outputFolder, cores, iterations, percentOfData, dropout, startModel, secondOrder, costAugmenting);
	}
	
	public static void train(String folder) throws IOException {
		WordEnumeration words = new WordEnumeration();
		LabelEnumeration labels = new LabelEnumeration();
		RuleEnumeration rules = new RuleEnumeration();
		List<SpannedWords> examples = PennTreebankReader.loadFromFiles(folder, 2, 3, words, labels, rules);
		
		RandomizedGreedyDecoder decoder = new RandomizedGreedyDecoder(words, labels, rules);
		Train pa = new Train(words, labels, rules, decoder);
		pa.train(examples, .05, true, true);
		FeatureParameters params = pa.getParameters();
		
		SaveObject so = new SaveObject(words, labels, rules, params);
		so.save("model");
	}
	
	/**
	 * Class used for running several trainings in parallel
	 * @author david
	 *
	 */
	static class TrainOneIteration implements Callable<FeatureParameters> {
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
		public FeatureParameters call() throws Exception {
			System.out.println("Starting new training.");
			
			RandomizedGreedyDecoder decoder = new RandomizedGreedyDecoder(words, labels, rules);
			Train pa = new Train(words, labels, rules, decoder, initialParams);
			try {
				pa.train(data, dropout, secondOrder, costAugmenting);
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
			
			return pa.getParameters();
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
		FeatureParameters shared = new FeatureParameters();
		
		if(startModel != null) {
			SaveObject start = SaveObject.loadSaveObject(startModel);
			words = start.getWords();
			labels = start.getLabels();
			rules = start.getRules();
			shared = start.getParameters();
		}
		
		List<SpannedWords> unsplitData = PennTreebankReader.loadFromFiles(dataFolder, 2,22, words, labels, rules); // use only between 2 and 21 for training
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
			
			List<Future<FeatureParameters>> futures = new ArrayList<>();
			for(List<SpannedWords> d : data) {
				Future<FeatureParameters> future = pool.submit(new TrainOneIteration(new WordEnumeration(words), new LabelEnumeration(labels), new RuleEnumeration(rules), d, new FeatureParameters(shared), dropout, secondOrder, costAugmenting));
				futures.add(future);
			}
			
			List<FeatureParameters> results = new ArrayList<>();
			for(Future<FeatureParameters> future : futures) {
				results.add(future.get());
			}
			
			shared = FeatureParameters.average(results);
			
			Test.test(words, labels, rules, shared, dataFolder, secondOrder, 100, .1);
			
			SaveObject so = new SaveObject(words, labels, rules, shared);
			so.save(outputFolder + "/modelIteration"+i);
		}
		
		pool.shutdown();
	}
}
