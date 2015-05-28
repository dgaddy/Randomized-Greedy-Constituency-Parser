package constituencyParser.unlabeled;

import java.util.ArrayList;
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

	private List<BinaryHeadPropagationRule> rules = new ArrayList<>();
	private List<TreeNode> examples = new ArrayList<>(); // already head binarized
	private boolean[][] propagationRules;
	private LabelEnumeration labels;
	
	private BinaryHeadPropagation() {
		
	}

	/**
	 * Called in TreeNode only
	 * @param left
	 * @param right
	 * @param leftHead
	 */
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
			boolean leftHead = r.leftHeadCounts > r.rightHeadCounts;
			result.add(Rule.getRule(leftHead ? r.leftPOS : r.rightPOS, r.leftPOS, r.rightPOS, labels, leftHead));
		}

		return result;
	}
	
	/**
	 * Returns true if should propagate left
	 * @param leftLabel
	 * @param rightLabel
	 * @return
	 */
	public boolean getPropagateLeft(int leftLabel, int rightLabel) {
		return propagationRules[leftLabel][rightLabel];
	}

	public List<SpannedWords> getExamples(boolean training, WordEnumeration words) {
		
		
		List<SpannedWords> loaded = new ArrayList<>();
		for(TreeNode tree : examples) {
			SpannedWords sw = tree.getSpans(words, labels);
			SpanUtilities.connectChildren(sw.getSpans());
			loaded.add(sw);
		}
		
		return loaded;
	}

	public static BinaryHeadPropagation getBinaryHeadPropagation(List<TreeNode> trees, LabelEnumeration labels) {
		BinaryHeadPropagation prop = new BinaryHeadPropagation();

		for(TreeNode tree : trees) {
			tree.findHeads();
			tree = tree.headBinarize(true);
			tree.getHeadPropagationStats(prop);
			tree.makeHeadPOSLabels();
			tree.removeUnaries();
			prop.examples.add(tree);
		}
		
		prop.labels = labels;
		
		int n = RuleEnumeration.NUMBER_LABELS;
		prop.propagationRules = new boolean[n][n]; // defaults to false, propagate right if not in training data
		
		for(BinaryHeadPropagationRule r : prop.rules) {
			int left = prop.labels.getId(r.leftPOS), right = prop.labels.getId(r.rightPOS);
			prop.propagationRules[left][right] = r.leftHeadCounts > r.rightHeadCounts;
		}

		return prop;
	}
	
	public static void addLabels(LabelEnumeration labels, List<TreeNode> trees) {
		for(TreeNode tree : trees) {
			List<String> l = tree.getAllPOSLabels();
			labels.addAllLabels(l);
			labels.addAllPOSLabels(l);
		}
	}
}
