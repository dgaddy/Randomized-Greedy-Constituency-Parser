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
		if(args.length < 3) {
			System.out.println("Arguments are [folder] [number cores] [number iterations]");
			return;
		}
		
		
		String folder = args[0];
		int cores = Integer.parseInt(args[1]);
		int iterations = Integer.parseInt(args[2]);
		double percentOfData = 1;
		if(args.length > 3) {
			percentOfData = Double.parseDouble(args[3]);
			if(percentOfData > 1) {
				System.out.println("Percent of data (4th argument) must be less than 1");
				return;
			}
		}
		
		System.out.println("Running training with " + cores + " cores for " + iterations + " iterations.");
		System.out.println("Data directory: " + folder);
		if(percentOfData < 1)
			System.out.println("using " + percentOfData + " of data");
		trainParallel(folder, cores, iterations, percentOfData);
	}
	
	public static void train(String folder) throws IOException {
		WordEnumeration words = new WordEnumeration();
		LabelEnumeration labels = new LabelEnumeration();
		Rules rules = new Rules();
		List<SpannedWords> examples = PennTreebankReader.loadFromFiles(folder, 2, 3, words, labels, rules);
		
		CKYDecoder decoder = new CKYDecoder(labels, rules);
		PassiveAgressive pa = new PassiveAgressive(words, labels, rules, decoder);
		pa.train(examples);
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
		
		public TrainOneIteration(WordEnumeration words, LabelEnumeration labels, Rules rules, List<SpannedWords> data, FeatureParameters initialParams) {
			this.words = words;
			this.labels = labels;
			this.rules = rules;
			this.data = data;
			this.initialParams = initialParams;
		}

		@Override
		public FeatureParameters call() throws Exception {
			System.out.println("Starting new training.");
			CKYDecoder decoder = new CKYDecoder(labels, rules);
			PassiveAgressive pa = new PassiveAgressive(words, labels, rules, decoder, initialParams);
			pa.train(data);
			return pa.getParameters();
		}
	}
	
	public static void trainParallel(String folder, int numberThreads, int iterations, double percentOfData) throws IOException, InterruptedException, ExecutionException {
		WordEnumeration words = new WordEnumeration();
		LabelEnumeration labels = new LabelEnumeration();
		Rules rules = new Rules();
		
		List<SpannedWords> unsplitData = PennTreebankReader.loadFromFiles(folder, 2,22, words, labels, rules); // use only between 2 and 21 for training
		if(percentOfData < 1) {
			unsplitData = new ArrayList<>(unsplitData.subList(0, (int)(unsplitData.size() * percentOfData)));
		}
		
		ExecutorService pool = Executors.newFixedThreadPool(numberThreads);
		
		Random random = new Random();
		
		FeatureParameters shared = new FeatureParameters();
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
				Future<FeatureParameters> future = pool.submit(new TrainOneIteration(new WordEnumeration(words), new LabelEnumeration(labels), new Rules(rules), d, new FeatureParameters(shared)));
				futures.add(future);
			}
			
			List<FeatureParameters> results = new ArrayList<>();
			for(Future<FeatureParameters> future : futures) {
				results.add(future.get());
			}
			
			shared = FeatureParameters.average(results);
			SaveObject so = new SaveObject(words, labels, rules, shared);
			so.save("parallelModel"+i);
		}
		
		pool.shutdown();
	}
}
