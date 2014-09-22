package constituencyParser;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import constituencyParser.features.FeatureParameters;


public class Main {
	public static void main(String[] args) throws Exception {
		trainParallel();
		//test();
	}
	
	/**
	 * Loads data from files numbered firstFile to lastFile (exclusive)
	 * @param firstFile
	 * @param lastFile exclusive
	 * @param labels all new labels will be added to this
	 * @param rules all new rules will be added to this
	 * @return
	 * @throws IOException 
	 */
	static List<SpannedWords> loadFromFiles(int firstFile, int lastFile, LabelEnumeration labels, Rules rules) throws IOException {

		List<SpannedWords> loaded = new ArrayList<>();
		List<TreeNode> trees = new ArrayList<>();
		
		for(int i = 0; i < 1; i++) {
			String fileName = String.format("../WSJ data/wsj.%1$02d.txt", i);
			PennTreebankReader reader = new PennTreebankReader(fileName);
			TreeNode tree;
			while((tree = reader.readPtbTree()) != null) {
				tree.removeNoneLabel();
				tree.makeLabelsSimple();
				tree = tree.makeBinary();
				trees.add(tree);
				labels.addAllLabels(tree.getAllLabels());
			}
			
			reader.close();
		}
		
		
		for(TreeNode tree : trees) {
			loaded.add(tree.getSpans(labels));
		}
		

		rules.addAllRules(loaded);
		
		return loaded;
	}
	
	public static void train() throws IOException {
		LabelEnumeration labels = new LabelEnumeration();
		Rules rules = new Rules();
		List<SpannedWords> examples = loadFromFiles(0, 24, labels, rules);
		
		CKYDecoder decoder = new CKYDecoder(labels, rules);
		PassiveAgressive pa = new PassiveAgressive(labels, rules, decoder);
		pa.train(examples);
		FeatureParameters params = pa.getParameters();
		
		SaveObject so = new SaveObject(labels, rules, params);
		so.save("model");
	}
	
	static class TrainOneIteration implements Callable<FeatureParameters> {
		LabelEnumeration labels;
		Rules rules;
		List<SpannedWords> data;
		
		public TrainOneIteration(LabelEnumeration labels, Rules rules, List<SpannedWords> data) {
			this.labels = labels;
			this.rules = rules;
			this.data = data;
		}

		@Override
		public FeatureParameters call() throws Exception {
			System.out.println("Starting new training.");
			CKYDecoder decoder = new CKYDecoder(labels, rules);
			PassiveAgressive pa = new PassiveAgressive(labels, rules, decoder);
			pa.train(data);
			return pa.getParameters();
		}
	}
	
	public static void trainParallel() throws IOException, InterruptedException, ExecutionException {
		int numberGroups = 24;
		int numberThreads = 8;
		
		LabelEnumeration labels = new LabelEnumeration();
		Rules rules = new Rules();
		
		ExecutorService pool = Executors.newFixedThreadPool(numberThreads);
		List<List<SpannedWords>> data = new ArrayList<>();
		for(int i = 0; i < numberGroups; i++) {
			data.add(loadFromFiles(i, i+1, labels, rules));
		}
		
		List<Future<FeatureParameters>> futures = new ArrayList<>();
		for(List<SpannedWords> d : data) {
			Future<FeatureParameters> future = pool.submit(new TrainOneIteration(new LabelEnumeration(labels), new Rules(rules), d));
			futures.add(future);
		}
		
		List<FeatureParameters> results = new ArrayList<>();
		for(Future<FeatureParameters> future : futures) {
			results.add(future.get());
		}
		
		FeatureParameters average = FeatureParameters.average(results, labels.getNumberOfLabels(), rules);
		SaveObject so = new SaveObject(labels, rules, average);
		so.save("parallelModel");
	}
	
	public static void test() throws IOException, ClassNotFoundException {
		SaveObject savedModel = SaveObject.loadSaveObject("model");
		LabelEnumeration labels = savedModel.getLabels();
		Rules rules = savedModel.getRules();
		FeatureParameters parameters = savedModel.getParameters();
		
		List<SpannedWords> gold = loadFromFiles(24, 25, labels, rules);
		
		parameters.resize(labels.getNumberOfLabels(), rules);
		
		CKYDecoder decoder = new CKYDecoder(labels, rules);
		
		int success = 0;
		for(SpannedWords example : gold) {
			HashSet<Span> goldSet = new HashSet<>(example.getSpans());
			HashSet<Span> result = new HashSet<>(decoder.decode(example.getWords(), parameters));
			System.out.println(goldSet);
			System.out.println(result);
			if(goldSet.equals(result)) {
				success++;
			}
		}
		System.out.println("" + success + " correct out of " + gold.size());
	}
}
