package constituencyParser;
import static org.junit.Assert.*;
import gnu.trove.map.hash.TLongDoubleHashMap;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import org.junit.Test;

import constituencyParser.GreedyChange.ParentedSpans;
import constituencyParser.features.FeatureParameters;
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
		
		// check simplification
		tree = new TreeNode("S-1", new TreeNode("-LRB-", new TreeNode("word")), new TreeNode("PRP$", new TreeNode("word2")));
		tree.makeLabelsSimple();
		List<String> labels = tree.getAllLabels();
		assertTrue(labels.size() == 3);
		assertTrue(labels.contains("S"));
		assertTrue(labels.contains("-LRB-"));
		assertTrue(labels.contains("PRP$"));
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
		w = words.getWord("3.5");
		assertEquals("-NUM-", words.getWordForId(w.getId()));
		
		// test rules
		Rule r = Rule.getRule("NP", "DT", "NN", labels);
		int id = rules.getBinaryId(r);
		assertTrue(id != -1);
		assertEquals(r, rules.getBinaryRule(id));
		assertTrue(rules.getBinaryId(Rule.getRule("NN", "DT", "NN", labels)) == -1);
		
		// test labels
		assertTrue(labels.getId("NP") >= 0);
		assertEquals(labels.getId("S-BAR"), labels.getExtendLabel(labels.getId("S")));
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
				Features.getRuleFeature(rules.getRuleCode(Rule.getRule("S", "S-BAR", ".", labels))),
				Features.getRuleFeature(rules.getRuleCode(Rule.getRule("S-BAR", "NP", "VP", labels))),
				Features.getRuleFeature(rules.getRuleCode(Rule.getRule("NP", "NNP", "NNP", labels))),
				Features.getRuleFeature(rules.getRuleCode(Rule.getRule("VP", "VBZ", "NP", labels))),
				Features.getRuleFeature(rules.getRuleCode(Rule.getRule("NP", "NNP", labels))));
		for(long feature : targetFeatures)
			assertTrue(features.contains(feature));
		
		// second order
		features =  Features.getAllFeatures(example.getSpans(), example.getWords(), true, words, labels, rules);
		targetFeatures = Arrays.asList(
				Features.getSecondOrderRuleFeature(rules.getRuleCode(Rule.getRule("S-BAR", "NP", "VP", labels)), labels.getId("S")),
				Features.getSecondOrderRuleFeature(rules.getRuleCode(Rule.getRule("NP", "NNP", labels)), labels.getId("VP")));
		for(long feature : targetFeatures)
			assertTrue(features.contains(feature));
	}
	
	@Test
	public void testAdagrad() {
		FeatureParameters params = new FeatureParameters(1, 0); // learningRate = 1, no regularization
		testFeatureParameters(params);
		params = new FeatureParameters(.1, 5);
		testFeatureParameters(params);
	}
	
	private void testFeatureParameters(FeatureParameters params) {
		Random random = new Random();
		TLongDoubleHashMap featureUpdates = new TLongDoubleHashMap();
		for(long code = 100; code < 1000; code++) {
			featureUpdates.put(code, -random.nextInt(1000)-1); // good features
		}
		for(long code = 1100; code < 2000; code++) {
			featureUpdates.put(code, random.nextInt(1000)+1); // bad features
		}
		
		params.update(featureUpdates);
		
		assertTrue(params.getScore(0) == 0);
		double sumPositive = 0;
		for(long code = 100; code < 1000; code++) {
			assertTrue(params.getScore(code) > 0);
			sumPositive += params.getScore(code);
		}
		double sumNegative = 0;
		for(long code = 1100; code < 2000; code++) {
			assertTrue(params.getScore(code) < 0);
			sumNegative += params.getScore(code);
		}
		
		// test dropout
		params.resetDropout(.5);
		double dropoutSumPositive = 0;
		for(long code = 100; code < 1000; code++) {
			dropoutSumPositive += params.getScore(code);
		}
		double dropoutSumNegative = 0;
		for(long code = 1100; code < 2000; code++) {
			dropoutSumNegative += params.getScore(code);
		}
		assertTrue(dropoutSumPositive < sumPositive);
		assertTrue(dropoutSumNegative > sumNegative);
	}
	
	@Test
	public void testGreedyChange() throws IOException {
		// load section 2 from file
		// assumes data is in "../WSJ data"
		WordEnumeration words = new WordEnumeration();
		LabelEnumeration labels = new LabelEnumeration();
		RuleEnumeration rules = new RuleEnumeration();
		List<SpannedWords> examples = PennTreebankReader.loadFromFiles("../WSJ data/", 2, 22, words, labels, rules, true);
		
		SpannedWords example = examples.get(1); // ( (S (NP-SBJ (NNP Ms.) (NNP Haag)) (VP (VBZ plays) (NP (NNP Elianti) ))	(. .) ))
		
		GreedyChange gc = new GreedyChange(labels, rules);
		Pruning noPrune = new Pruning(example.getWords().size(), labels.getNumberOfLabels());
		List<ParentedSpans> changes = gc.makeGreedyLabelChanges(example.getSpans(), 0, 1, false, noPrune);
		int expected = 0;
		int np = labels.getId("NP");
		int nnp = labels.getId("NNP");
		for(int label = 0; label < labels.getNumberOfLabels(); label++) {
			if(rules.isExistingRule(new Rule(np, label, nnp)))
				expected++;
		}
		for(int un = 0; un < rules.getNumberOfUnaryRules(); un++) {
			Rule unary = rules.getUnaryRule(un);
			if(rules.isExistingRule(new Rule(np, unary.getLabel(), nnp)))
				expected++;
		}
		assertEquals(expected, changes.size());
		
		changes = gc.makeGreedyChanges(example.getSpans(), 2, 3, noPrune);
		assertTrue(existsStructure(changes, new int[] {2,4, 0,2, 0,4, 0,5})); // original structure
		assertTrue(existsStructure(changes, new int[] {1,3, 0,3, 0,4, 0,5})); // connect to left NNP
		assertTrue(existsStructure(changes, new int[] {0,2, 0,3, 0,4, 0,5})); // connect to left NP
		
		// make sure all changes give back at least itself
		for(SpannedWords ex : examples) {
			List<Word> w = ex.getWords();
			List<Span> spans = ex.getSpans();
			noPrune = new Pruning(ex.getWords().size(), labels.getNumberOfLabels());
			
			// terminal labels
			for(int i = 0; i < w.size(); i++) {
				List<ParentedSpans> options = gc.makeGreedyLabelChanges(spans, i, i+1, false, noPrune);
				
				assertTrue(hasSpans(options, spans));
			}
			
			// other spans
			for(int length = 1; length < w.size() + 1; length++) {
				for(int start = 0; start < w.size() - length + 1; start++) {
					boolean exists = false; // if there is actually a span with this start and length
					for(int i = 0; i < spans.size(); i++) {
						Span s = spans.get(i);
						if(s.getStart() == start && s.getEnd() == start + length) {
							exists = true;
						}
					}
					if(exists) {
						List<ParentedSpans> update = gc.makeGreedyChanges(spans, start, start + length, noPrune);
						
						assertTrue(hasSpans(update, spans));
					}
				}
			}
		}
	}
	
	/**
	 * Checks if one of the options has structure described by spans
	 * @param options
	 * @param spans pairs of start, end indices where there should be a span
	 */
	private boolean existsStructure(List<ParentedSpans> options, int[] spans) {
		for(ParentedSpans option : options) {
			boolean matches = true;
			for(int i = 0; i < spans.length; i+=2) {
				boolean hasSpan = false;
				for(Span s : option.spans) {
					if(s.getStart() == spans[i] && s.getEnd() == spans[i+1]) {
						hasSpan = true;
						break;
					}
				}
				if(!hasSpan) {
					matches = false;
					break;
				}
			}
			if(matches)
				return true;
		}
		return false;
	}
	
	private boolean hasSpans(List<ParentedSpans> options, List<Span> spans) {
		for(ParentedSpans option : options) {
			if((new HashSet<Span>(option.spans)).equals(new HashSet<Span>(spans)))
				return true;
		}
		return false;
	}
}
