package constituencyParser;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.junit.Test;

import constituencyParser.features.Features;


public class UnitTests {
	
	@Test
	public void testTreeNode() {
		// check stacked unary removal
		TreeNode tree = new TreeNode("S", new TreeNode("NP", new TreeNode("NP2", new TreeNode("NN", new TreeNode("John")))), new TreeNode("VP", new TreeNode("VP2", new TreeNode("left"))));
		tree.removeStackedUnaries();
		assertTrue(tree.getAllLabels().size() == 5);
		
		tree = new TreeNode("S", new TreeNode("NP", new TreeNode("NP2", new TreeNode("NP3", new TreeNode("NN", new TreeNode("John"))))), new TreeNode("."));
		tree.removeStackedUnaries();
		assertTrue(tree.getAllLabels().size() == 3);
		
		// check binarization
		TreeNode orig = new TreeNode("S", new TreeNode("1"), new TreeNode("2"), new TreeNode("3"));
		TreeNode bin = orig.makeBinary();
		assertEquals(new TreeNode("S", new TreeNode("S-BAR", new TreeNode("1"), new TreeNode("2")), new TreeNode("3")), bin);
		
		assertEquals(orig, bin.unbinarize());
		
		// check none removal
		tree = new TreeNode("S", new TreeNode("NP", new TreeNode("-NONE-", new TreeNode("word"))), new TreeNode("other"));
		tree.removeNoneLabel();
		assertEquals(new TreeNode("S", new TreeNode("other")), tree);
	}
	
	@Test
	public void testTreeNodeToSpanConversion() {
		// check span creation
		WordEnumeration words = new WordEnumeration();
		LabelEnumeration labels = new LabelEnumeration();
		TreeNode tree = new TreeNode("S", new TreeNode("N", new TreeNode("word")), new TreeNode("VP", new TreeNode("V", new TreeNode("word2")), new TreeNode("N", new TreeNode("word3"))));
		labels.addAllLabels(tree.getAllLabels()); 
		words.addTrainingWords(new HashMap<String, Integer>()); // Note this will make all words enumerate to unknown
		SpannedWords spans = tree.getSpans(words, labels);
		assertTrue(spans.getWords().size() == 3);
		assertTrue(spans.getSpans().size() == 5);
		
		// check reverse
		assertEquals(tree, TreeNode.makeTreeFromSpans(spans.getSpans(), spans.getWords(), words, labels));
	}
	
	@Test
	public void testLoading() throws IOException { // mostly tests enumerations
		
		// load section 2 from file
		// assumes data is in "../WSJ data"
		WordEnumeration words = new WordEnumeration();
		LabelEnumeration labels = new LabelEnumeration();
		RuleEnumeration rules = new RuleEnumeration();
		PennTreebankReader.loadFromFiles("../WSJ data/", 2, 3, words, labels, rules, true);
		
		// test words
		Word w = words.getWord("the");
		assertEquals("the", words.getWordForId(w.getId()));
		w = words.getWord("fjdklsfjz"); // unknown, no common suffix
		assertEquals("-UNK-", words.getWordForId(w.getId()));
		w = words.getWord("lathe"); // unknown, common suffix
		assertEquals("-the", words.getWordForId(w.getId()));
		w = words.getWord("M'Bow"); // occurs in data, but not enough times
		assertEquals("-ow", words.getWordForId(w.getId()));
		
		// test rules
		Rule r = Rule.getRule("NP", "DT", "NN", labels);
		int id = rules.getBinaryId(r);
		assertTrue(id != -1);
		assertEquals(r, rules.getBinaryRule(id));
		assertTrue(rules.getBinaryId(Rule.getRule("NN", "DT", "NN", labels)) == -1);
	}
	
	@Test
	public void testFeatures() throws IOException {
		// load section 2 from file
		// assumes data is in "../WSJ data"
		WordEnumeration words = new WordEnumeration();
		LabelEnumeration labels = new LabelEnumeration();
		RuleEnumeration rules = new RuleEnumeration();
		List<SpannedWords> examples = PennTreebankReader.loadFromFiles("../WSJ data/", 2, 3, words, labels, rules, true);
		
		SpannedWords example = examples.get(1); // ( (S (NP-SBJ (NNP Ms.) (NNP Haag)) (VP (VBZ plays) (NP (NNP Elianti) ))	(. .) ))

		List<Long> features =  Features.getAllFeatures(example.getSpans(), example.getWords(), false, words, labels, rules);
		List<Long> targetFeatures = Arrays.asList(
				Features.getRuleFeature(Rule.getRule("S", "S-BAR", ".", labels), rules),
				Features.getRuleFeature(Rule.getRule("S-BAR", "NP", "VP", labels), rules),
				Features.getRuleFeature(Rule.getRule("NP", "NNP", "NNP", labels), rules),
				Features.getRuleFeature(Rule.getRule("VP", "VBZ", "NP", labels), rules),
				Features.getRuleFeature(Rule.getRule("NP", "NNP", labels), rules));
		for(long feature : targetFeatures)
			assertTrue(features.contains(feature));
		
		// second order
		features =  Features.getAllFeatures(example.getSpans(), example.getWords(), true, words, labels, rules);
		targetFeatures = Arrays.asList(
				Features.getSecondOrderRuleFeature(labels.getId("NP"), labels.getId("S-BAR"), labels.getId("S")));
		for(long feature : targetFeatures)
			assertTrue(features.contains(feature));
	}
}
