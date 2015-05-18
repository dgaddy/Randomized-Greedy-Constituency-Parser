package constituencyParser.unlabeled;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import constituencyParser.LabelEnumeration;
import constituencyParser.Rule;
import constituencyParser.RuleEnumeration;
import constituencyParser.SpanUtilities;
import constituencyParser.SpannedWords;
import constituencyParser.TreeNode;
import constituencyParser.WordEnumeration;

public class BinaryHeadPropagation {
	static class BinaryHeadPropagationRule {
		String leftPOS;
		String rightPOS;
		int leftHeadCounts = 0;
		int rightHeadCounts = 0;

		public BinaryHeadPropagationRule(String l, String r) {
			leftPOS = l;
			rightPOS = r;
		}

		public void addLeftCount() {
			leftHeadCounts++;
		}

		public void addRightCount() {
			rightHeadCounts++;
		}
	}

	List<BinaryHeadPropagationRule> rules = new ArrayList<>();
	List<TreeNode> examples = new ArrayList<>(); // already head binarized
	
	private BinaryHeadPropagation() {
		
	}

	public void addCount(String left, String right, boolean leftHead) {
		boolean found = false;
		for(BinaryHeadPropagationRule r : rules) {
			if(r.leftPOS.equals(left) && r.rightPOS.equals(right)) {
				found = true;
				if(leftHead)
					r.addLeftCount();
				else
					r.addRightCount();
			}
		}
		if(!found) {
			BinaryHeadPropagationRule r = new BinaryHeadPropagationRule(left, right);
			if(leftHead)
				r.addLeftCount();
			else
				r.addRightCount();
			rules.add(r);
		}
	}

	public void printResults() {
		int total = 0;
		int incorrect = 0;
		int incorrectLabel = 0;
		for(BinaryHeadPropagationRule r : rules) {
			incorrect += Math.min(r.leftHeadCounts, r.rightHeadCounts);
			if(!r.leftPOS.equals(r.rightPOS)) {
				incorrectLabel += Math.min(r.leftHeadCounts, r.rightHeadCounts);
			}
			total += r.leftHeadCounts + r.rightHeadCounts;
		}
		System.out.println((incorrect/(double)total) + " wrong");
		System.out.println((incorrectLabel/(double)total) + " wrong label");

		rules.sort((BinaryHeadPropagationRule r1, BinaryHeadPropagationRule r2) -> 
		Math.min(r2.leftHeadCounts,r2.rightHeadCounts) - Math.min(r1.leftHeadCounts,r1.rightHeadCounts));
		int i = 0;
		for(; i < rules.size(); i++) {
			BinaryHeadPropagationRule r = rules.get(i);
			if(Math.min(r.leftHeadCounts, r.rightHeadCounts) < 100)
				break;

			System.out.println(r.leftPOS + " " + r.rightPOS + " " +  r.leftHeadCounts + " " + r.rightHeadCounts);
		}
		System.out.println("+ " + (rules.size() - i) + " more");
	}

	public List<Rule> getRules(LabelEnumeration labels) {
		List<Rule> result = new ArrayList<>();

		for(BinaryHeadPropagationRule r : rules) {
			result.add(Rule.getRule(r.leftHeadCounts > r.rightHeadCounts ? r.leftPOS : r.rightPOS, r.leftPOS, r.rightPOS, labels));
		}

		return result;
	}

	public List<SpannedWords> getExamples(boolean training, WordEnumeration words, LabelEnumeration labels, RuleEnumeration ruleEnum) {
		HashMap<String, Integer> wordCounts = new HashMap<>();
		for(TreeNode tree : examples) {
			labels.addAllLabels(tree.getAllLabels());
			labels.addTopLevelLabel(tree.getLabel());
			labels.addAllPOSLabels(tree.getAllPOSLabels());

			for(String word : tree.getAllWords()) {
				Integer count = wordCounts.get(word);
				if(count == null)
					wordCounts.put(word, 1);
				else
					wordCounts.put(word, count+1);
			}
		}
		
		if(training)
			words.addTrainingWords(wordCounts);
		
		List<SpannedWords> loaded = new ArrayList<>();
		for(TreeNode tree : examples) {
			SpannedWords sw = tree.getSpans(words, labels);
			SpanUtilities.connectChildren(sw.getSpans());
			loaded.add(sw);
		}
		
		if(training) {
			for(BinaryHeadPropagationRule r : rules) {
				ruleEnum.addBinaryRule(Rule.getRule(r.leftHeadCounts > r.rightHeadCounts ? r.leftPOS : r.rightPOS, r.leftPOS, r.rightPOS, labels));
			}
		}
		
		return loaded;
	}

	public static BinaryHeadPropagation getBinaryHeadPropagation(List<TreeNode> trees) {
		BinaryHeadPropagation prop = new BinaryHeadPropagation();

		for(TreeNode tree : trees) {
			tree.findHeads();
			tree = tree.headBinarize(true);
			tree.getHeadPropagationStats(prop);
			prop.examples.add(tree);
		}

		return prop;
	}
}
