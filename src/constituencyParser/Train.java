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


public class Train {
	public static void main(String[] args) throws Exception {
		if(args.length < 4) {
			System.out.println("Arguments are [data folder] [output folder] [number cores] [number iterations] [optional: percent of data] [optional: model to start with]");
			return;
		}
		
		double dropout = .45;
		
		String dataFolder = args[0];
		String outputFolder = args[1];
		int cores = Integer.parseInt(args[2]);
		int iterations = Integer.parseInt(args[3]);
		String startModel = null;
		double percentOfData = 1;
		if(args.length > 5) {
			startModel = args[5];
		}
		if(args.length > 4) {
			percentOfData = Double.parseDouble(args[4]);
			if(percentOfData > 1) {
				System.out.println("Percent of data (4th argument) must be less than 1");
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
		
		trainParallel(dataFolder, outputFolder, cores, iterations, percentOfData, dropout, startModel);
	}
	
	public static void train(String folder) throws IOException {
		WordEnumeration words = new WordEnumeration();
		LabelEnumeration labels = new LabelEnumeration();
		Rules rules = new Rules();
		List<SpannedWords> examples = PennTreebankReader.loadFromFiles(folder, 2, 3, words, labels, rules);
		
		RandomizedGreedyDecoder decoder = new RandomizedGreedyDecoder(words, labels, rules);
		PassiveAgressive pa = new PassiveAgressive(words, labels, rules, decoder);
		pa.train(examples, .05, true);
		FeatureParameters params = pa.getParameters();
		
		SaveObject so = new SaveObject(words, labels, rules, params);
		so.save("model");
	}
	
	static class TrainOneIteration implements Callable<FeatureParameters> {
		WordEnumeration words;
		LabelEnumeration labels;
		Rules rules;
		List<SpannedWords> data;
		FeatureParameters initialParams;
		double dropout;
		
		public TrainOneIteration(WordEnumeration words, LabelEnumeration labels, Rules rules, List<SpannedWords> data, FeatureParameters initialParams, double dropout) {
			this.words = words;
			this.labels = labels;
			this.rules = rules;
			this.data = data;
			this.initialParams = initialParams;
			this.dropout = dropout;
		}

		@Override
		public FeatureParameters call() throws Exception {
			System.out.println("Starting new training.");
			RandomizedGreedyDecoder decoder = new RandomizedGreedyDecoder(words, labels, rules);
			PassiveAgressive pa = new PassiveAgressive(words, labels, rules, decoder, initialParams);
			pa.train(data, dropout, true);
			
			words = null;
			labels = null;
			rules = null;
			data = null;
			initialParams = null;
			
			return pa.getParameters();
		}
	}
	
	public static void trainParallel(String dataFolder, String outputFolder, int numberThreads, int iterations, double percentOfData, double dropout, String startModel) throws IOException, InterruptedException, ExecutionException, ClassNotFoundException {
		WordEnumeration words = new WordEnumeration();
		LabelEnumeration labels = new LabelEnumeration();
		Rules rules = new Rules();
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
				Future<FeatureParameters> future = pool.submit(new TrainOneIteration(new WordEnumeration(words), new LabelEnumeration(labels), new Rules(rules), d, new FeatureParameters(shared), dropout));
				futures.add(future);
			}
			
			List<FeatureParameters> results = new ArrayList<>();
			for(Future<FeatureParameters> future : futures) {
				results.add(future.get());
			}
			
			shared = FeatureParameters.average(results);
			
			Test.test(words, labels, rules, shared, dataFolder);
			
			SaveObject so = new SaveObject(words, labels, rules, shared);
			so.save(outputFolder + "/modelIteration"+i);
		}
		
		pool.shutdown();
	}
}
