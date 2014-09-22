package constituencyParser;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import constituencyParser.features.FeatureParameters;


public class Main {
	public static void main(String[] args) throws IOException, ClassNotFoundException {
		train();
		//test();
	}
	
	public static void train() throws IOException {
		LabelEnumeration labels = new LabelEnumeration();

		List<SpannedWords> examples = new ArrayList<>();
		
		{ // so that trees will go away
			List<TreeNode> trees = new ArrayList<>();
			
			for(int i = 0; i < 24; i++) {
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
				examples.add(tree.getSpans(labels));
			}
		}
		
		Rules rules = new Rules();
		rules.addAllRules(examples);
		CKYDecoder decoder = new CKYDecoder(labels, rules);
		PassiveAgressive pa = new PassiveAgressive(labels, rules, decoder);
		pa.train(examples);
		FeatureParameters params = pa.getParameters();
		
		SaveObject so = new SaveObject(labels, rules, params);
		so.save("model");
	}
	
	public static void test() throws IOException, ClassNotFoundException {
		SaveObject savedModel = SaveObject.loadSaveObject("model");
		LabelEnumeration labels = savedModel.getLabels();
		Rules rules = savedModel.getRules();
		FeatureParameters parameters = savedModel.getParameters();
		
		List<TreeNode> trees = new ArrayList<>();
		String fileName = "../WSJ data/wsj.24.txt";
		PennTreebankReader reader = new PennTreebankReader(fileName);
		TreeNode tree;
		int count = 0;
		while(count < 5 && (tree = reader.readPtbTree()) != null) {
			tree.removeNoneLabel();
			tree.makeLabelsSimple();
			tree = tree.makeBinary();
			trees.add(tree);
			labels.addAllLabels(tree.getAllLabels());
			count++;
		}
		
		reader.close();
		
		List<SpannedWords> gold = new ArrayList<>();
		
		for(TreeNode t : trees) {
			gold.add(t.getSpans(labels));
		}
		
		rules.addAllRules(gold);
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
