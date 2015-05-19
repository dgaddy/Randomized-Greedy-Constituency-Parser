package constituencyParser.unlabeled;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import constituencyParser.Decoder;
import constituencyParser.LabelEnumeration;
import constituencyParser.PennTreebankReader;
import constituencyParser.RuleEnumeration;
import constituencyParser.SaveObject;
import constituencyParser.SpannedWords;
import constituencyParser.TestOptions;
import constituencyParser.TrainOptions;
import constituencyParser.TreeNode;
import constituencyParser.WordEnumeration;
import constituencyParser.features.FeatureParameters;
import constituencyParser.features.Features;


public class RunTraining {
	public static void main(String[] args) throws Exception {
		File loc = new File(RunTraining.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
		System.out.println("Running training with jar verion: " + loc.getName());
		TrainOptions options = new TrainOptions(args);
		
		train(options);
	}
	
	public static void train(TrainOptions options) throws IOException, ClassNotFoundException {
		WordEnumeration words = new WordEnumeration(options.useSuffixes, options.rareWordCutoff);
		LabelEnumeration labels = new LabelEnumeration();
		RuleEnumeration rules = new RuleEnumeration();
		FeatureParameters params = new FeatureParameters(options.learningRate, options.regularization);
		
		if(options.startModel != null) {
			SaveObject start = SaveObject.loadSaveObject(options.startModel);
			words = start.getWords();
			labels = start.getLabels();
			rules = start.getRules();
			params = start.getParameters();
		}
		
		System.out.println("load data... ");
		
		List<TreeNode> trees = PennTreebankReader.loadTreeNodesFromFiles(PennTreebankReader.getWSJFiles(options.dataFolder, 2, 22));
		BinaryHeadPropagation prop = BinaryHeadPropagation.getBinaryHeadPropagation(trees);
		List<SpannedWords> examples = prop.getExamples(true, words, rules);
		labels = prop.getLabels();
		
		if(options.percentOfData < 1) {
			examples = new ArrayList<>(examples.subList(0, (int)(examples.size() * options.percentOfData)));
		}
		
		System.out.println("build alphabet...");
		if(options.noNegativeFeatures) {
			int cnt = 0;
			for(SpannedWords ex : examples) {
				cnt++;
				if (cnt % 1000 == 0)
					System.out.print("  " + cnt);
				List<Long> features = Features.getAllFeatures(ex.getSpans(), ex.getWords(), options.secondOrder, words, labels, rules);
				params.ensureContainsFeatures(features);
			}
			params.stopAddingFeatures();
		}
		System.out.println(" Done.");
		
		Decoder decoder;
		if(options.randGreedy)
			decoder = new RandomizedGreedyDecoder(words, labels, rules, options.cores, prop);
		else
			decoder = new UnlabeledCKY(words, labels, rules);
		
		Train pa = new Train(words, labels, rules, decoder, params);
		
		for(int i = 0; i < options.iterations; i++) {
			System.out.println("Iteration " + i + ":");
			pa.train(examples, options);
			params = pa.getParameters();
			
			if(options.mira)
				params.averageParameters();
			Test.test(words, labels, rules, params, new TestOptions(options, .3, 0));
			
			SaveObject so = new SaveObject(words, labels, rules, params);
			so.save(options.outputFolder + "/modelIteration"+i);
			if(options.mira)
				params.unaverageParameters();
		}
	}
}
