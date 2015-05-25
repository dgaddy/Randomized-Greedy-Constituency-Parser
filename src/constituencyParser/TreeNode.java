package constituencyParser;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import constituencyParser.Rule.Type;
import constituencyParser.unlabeled.BinaryHeadPropagation;
import danbikel.lisp.SexpList;
import danbikel.lisp.Symbol;
import danbikel.parser.english.HeadFinder;

/**
 * A tree representation of a parse tree.  Used for reading in parse trees, then converted to span form using getSpans. 
 */
public class TreeNode {
	static HashSet<String> specialLabels;
	static {
		specialLabels = new HashSet<>(Arrays.asList("-LRB-", "-RRB-", "-LCB-", "-RCB-", "-LSB-", "-RSB-"));
	}

	private TreeNode parent;

	private boolean isWord;

	// word
	private String word;


	// non-word
	private String label;
	private List<TreeNode> children = new ArrayList<>();

	private static HeadFinder headFinder;
	static {
		try {
			headFinder = new HeadFinder();
		} catch(IOException ex) {
			System.err.println("Head finder exception");
		}
	}
	private int head = -1; // child index of head node

	/**
	 * Make non-word node
	 */
	public TreeNode() {
		isWord = false;
	}

	public TreeNode(String label, TreeNode... children) {
		isWord = false;
		this.label = label;
		for(TreeNode child : children) {
			this.children.add(child);
		}
	}

	/**
	 * Make word node with word
	 * @param word
	 */
	public TreeNode(String word) {
		isWord = true;
		this.word = word;
	}

	public void setLabel(String label) {
		if(isWord)
			throw new UnsupportedOperationException("Cannot add label to a word node.");

		this.label = label;
	}

	public void addChild(TreeNode node) {
		if(isWord)
			throw new UnsupportedOperationException("Cannot add child to a word node.");

		node.setParent(this);
		children.add(node);
	}

	private void setParent(TreeNode parent) {
		if(this.parent != null)
			throw new IllegalStateException("Cannot have more than one parent.");
		this.parent = parent;
	}

	public boolean isWord() {
		return isWord;
	}

	public String getWord() {
		return word;
	}

	public String getLabel() {
		return label;
	}

	public TreeNode getParent() {
		return parent;
	}

	public TreeNode getFirstChild() {
		if(children.size() == 0)
			return null;
		return children.get(0);
	}

	public List<String> getAllLabels() {
		List<String> result = new ArrayList<>();
		getAllLabels(result);
		return result;
	}

	private void getAllLabels(List<String> result) {
		if(label != null)
			result.add(label);
		for(TreeNode child : children) {
			child.getAllLabels(result);
		}
	}

	public List<String> getAllWords() {
		List<String> result = new ArrayList<>();
		getAllWords(result);
		return result;
	}

	private void getAllWords(List<String> result) {
		if(isWord)
			result.add(word);
		else {
			for(TreeNode child : children) {
				child.getAllWords(result);
			}
		}
	}
	
	public List<String> getAllPOSLabels() {
		List<String> result = new ArrayList<>();
		getAllPOSLabels(result);
		return result;
	}
	
	private void getAllPOSLabels(List<String> result) {
		if(!isWord) {
			if(children.size() == 1 && children.get(0).isWord()) {
				result.add(getLabel());
			}
			else {
				for(TreeNode child : children) {
					child.getAllPOSLabels(result);
				}
			}
		}
	}
	
	public List<WordPOS> getWordsWithPOS() {
		List<WordPOS> result = new ArrayList<>();
		getWordsWithPOS(result);
		return result;
	}
	
	private void getWordsWithPOS(List<WordPOS> result) {
		if(isWord) {
			result.add(new WordPOS(word, parent.getLabel()));
		}
		else {
			for(TreeNode child : children) {
				child.getWordsWithPOS(result);
			}
		}
	}

	public static class Bracket {
		int start;
		int end;
		String label;
		boolean unary;

		public Bracket(int s, int e, String l, boolean u) {
			start = s;
			end = e;
			label = l;
			unary = u;
		}

		@Override
		public boolean equals(Object other) {
			if(other instanceof Bracket) {
				Bracket ob = (Bracket)other;
				return ob.start == start && ob.end == end && ob.label.equals(label) && ob.unary == unary;
			}
			return false;
		}

		@Override
		public int hashCode() {
			return start + end + label.hashCode();
		}

		@Override
		public String toString() {
			return start + " " + end + " " + label;
		}
	}

	public List<Bracket> getAllBrackets() {
		List<Bracket> result = new ArrayList<>();
		getAllBrackets(result, 0);
		return result;
	}

	private int getAllBrackets(List<Bracket> result, int start) {
		if(isWord)
			return start + 1;
		else {
			boolean punctuation = Arrays.asList("''", ":", "#", ",", ".", "``", "-LRB-", "-", "-RRB-").contains(label);
			if(punctuation)
				return start;

			int i = start;
			for(TreeNode child : children) {
				i = child.getAllBrackets(result, i);
			}

			if(!(children.size() == 1 && children.get(0).isWord))
				result.add(new Bracket(start, i, label, children.size() == 1));
			return i;
		}
	}

	public SpannedWords getSpans(WordEnumeration words, LabelEnumeration labels) {
		if(isWord)
			return new SpannedWords(words.getWord(word));

		if(label == null)
			throw new IllegalStateException("Must have label for non-terminals.");

		if(children.size() == 1) {
			if(children.get(0).isWord)
				return new SpannedWords(children.get(0).getSpans(words, labels), labels.getId(label));
			else
				return new SpannedWords(children.get(0).getSpans(words, labels), labels.getId(label), labels.getId(children.get(0).getLabel()));
		}
		else if(children.size() == 2) {
			SpannedWords left = children.get(0).getSpans(words, labels);
			SpannedWords right = children.get(1).getSpans(words, labels);
			if(!(head == 0 || head == 1))
				throw new RuntimeException("Expected head to be set.");
			return new SpannedWords(left, right, labels.getId(label), labels.getId(children.get(0).getLabel()), labels.getId(children.get(1).getLabel()), head == 0);
		}
		else {
			throw new UnsupportedOperationException("Can only get spans for binary trees.  Use makeBinary().");
		}
	}

	/**
	 * Makes left-branching binarization
	 * Does not mutate current tree
	 * @return
	 */
	public TreeNode makeBinary() {
		if(word != null)
			return new TreeNode(word);

		if(children.size() < 1)
			throw new IllegalStateException("Must have label for non-terminals.");

		if(children.size() == 1) {
			TreeNode result = new TreeNode();
			result.setLabel(label);
			result.addChild(children.get(0).makeBinary());
			return result;
		}

		if(children.size() == 2) {
			TreeNode result = new TreeNode();
			result.setLabel(label);
			result.addChild(children.get(0).makeBinary());
			result.addChild(children.get(1).makeBinary());
			return result;
		}

		if(children.size() > 2) {
			String middleLabel = label + "-BAR";
			TreeNode left = children.get(0).makeBinary();
			for(int i = 1; i < children.size(); i++) {
				if(i == children.size() - 1) {
					TreeNode top = new TreeNode();
					top.setLabel(label);
					top.addChild(left);
					top.addChild(children.get(i).makeBinary());
					return top;
				}
				else {
					TreeNode middle = new TreeNode();
					middle.setLabel(middleLabel);
					middle.addChild(left);
					middle.addChild(children.get(i).makeBinary());
					left = middle;
				}
			}
		}

		throw new RuntimeException("Should never get here.");
	}

	public TreeNode unbinarize() {
		if(isWord)
			return new TreeNode(word);

		if(children.size() == 0)
			throw new RuntimeException("Non-word nodes must have children");
		if(children.size() > 2)
			throw new RuntimeException("Tree must be binary before calling unbinarize.");

		TreeNode result = new TreeNode();
		result.setLabel(label);
		TreeNode left = children.get(0).unbinarize();
		if(left.label != null && left.label.endsWith("-BAR" )) {
			if(!left.label.startsWith(label))
				throw new RuntimeException("-BAR label below a non-matching parent");

			for(TreeNode child : left.children) {
				child.parent = null;
				result.addChild(child);
			}
		}
		else {
			result.addChild(left);
		}

		if(children.size() > 1) {
			TreeNode right = children.get(1).unbinarize();
			result.addChild(right);
		}

		return result;
	}

	public void makeLabelsSimple() {
		if(isWord)
			return;

		if(!specialLabels.contains(label)) {
			int dash = label.indexOf('-');
			if(dash != -1)
				label = label.substring(0, dash);
			int equals = label.indexOf('=');
			if(equals != -1)
				label = label.substring(0, equals);
		}

		if(label.isEmpty())
			throw new RuntimeException("Empty label: may have been a label starting with - or =");

		for(TreeNode child : children) {
			child.makeLabelsSimple();
		}
	}

	/**
	 * 
	 * @return true if this tree is only none and should be removed
	 */
	public boolean removeNoneLabel() {
		if(isWord)
			return false;
		else if(label.equals("-NONE-"))
			return true;
		else {
			List<TreeNode> childrenToRemove = new ArrayList<>();
			for(TreeNode child : children) {
				if(child.removeNoneLabel()) {
					childrenToRemove.add(child);
				}
			}
			children.removeAll(childrenToRemove);
			if(children.size() == 0)
				return true;
		}
		return false;
	}

	/**
	 * In training data, after removing none labels, sometimes it leaves two unary rules directly above eachother.
	 * This method removes one of the layers, since the decoder can't handle two unaries like this
	 * @return
	 */
	public void removeStackedUnaries() {
		for(TreeNode child : children) {
			child.removeStackedUnaries();
		}
		if(children.size() == 1) {
			TreeNode child = children.get(0);
			if(child.children.size() == 1 && !child.children.get(0).isWord) {
				// remove child, connecting grandChild directly to this
				TreeNode grandChild = child.children.get(0);
				grandChild.parent = null;
				children = new ArrayList<>();
				addChild(grandChild);
			}
		}
	}

	public void findHeads() {
		if(isWord)
			return;
		else {
			for(TreeNode child : children) {
				child.findHeads();
			}

			SexpList childList = new SexpList();

			for(TreeNode child : children) {
				if(child.isWord())
					return;
				childList.add(Symbol.add(child.getLabel()));
			}

			head = headFinder.findHead(null, Symbol.add(label), childList) - 1; // -1 because this function is one indexed
		}
	}
	
	public void makeHeadPOSLabels() {
		if(!isWord && !(children.size() == 1 && children.get(0).isWord())) { // not word or POS
			for(TreeNode child : children)
				child.makeHeadPOSLabels();
			this.label = children.get(head).getLabel();
		}
	}
	
	public void removeUnaries() {
		for(TreeNode child : children) {
			child.removeUnaries();
		}
		if(!isWord && !(children.size() == 1 && children.get(0).isWord())) { // not word or POS
			if(children.size() == 1) {
				this.head = children.get(0).head;
				List<TreeNode> newChildren = children.get(0).children;
				children = new ArrayList<>();
				for(TreeNode child : newChildren) {
					child.parent = null;
					addChild(child);
				}
			}
		}
	}

	public TreeNode headBinarize(boolean leftBias) {
		if(isWord)
			return new TreeNode(word); 

		String middleLabel = label + "-BAR";
		TreeNode top = new TreeNode();
		top.setLabel(label);
		TreeNode current = top;
		List<TreeNode> remainingChildren = new ArrayList<>(children);
		int headPos = this.head;
		while(remainingChildren.size() > 2) {
			TreeNode newNode = new TreeNode();
			newNode.setLabel(middleLabel);
			if((leftBias && headPos != remainingChildren.size() - 1) || (!leftBias && headPos == 0)) {
				// left branch
				TreeNode c = remainingChildren.remove(remainingChildren.size() - 1);
				current.addChild(newNode);
				current.addChild(c.headBinarize(leftBias));
				current.head = 0;
			}
			else {
				// right branch
				TreeNode c = remainingChildren.remove(0);
				current.addChild(c.headBinarize(leftBias));
				current.addChild(newNode);
				current.head = 1;
				headPos--;
			}
			current = newNode;
		}
		current.head = headPos;
		for(TreeNode c : remainingChildren)
			current.addChild(c.headBinarize(leftBias));
		
		return top;
	}
	
	/**
	 * 
	 * @param counts
	 * @return the head pos label
	 */
	public String getHeadPropagationStats(BinaryHeadPropagation prop) {
		if(children.size() == 1) {
			TreeNode c = children.get(0);
			if(c.isWord())
				return label;
			else
				return c.getHeadPropagationStats(prop);
		}
		else if(children.size() == 2) {
			boolean leftHead = false;
			if(head == 0)
				leftHead = true;
			else if(head != 1)
				throw new RuntimeException("Head invalid");
			
			String leftPOS = children.get(0).getHeadPropagationStats(prop);
			String rightPOS = children.get(1).getHeadPropagationStats(prop);
			
			prop.addCount(leftPOS, rightPOS, leftHead);
			
			return leftHead ? leftPOS : rightPOS;
		} else {
			throw new RuntimeException("Must be binarized tree - call headBinarize()");
		}
	}

	public String toString() {
		if(word != null)
			return word;
		else {
			StringBuilder builder = new StringBuilder();
			builder.append('(');
			builder.append(label);
			for(TreeNode child : children) {
				builder.append(' ');
				builder.append(child.toString());
			}
			builder.append(')');
			return builder.toString();
		}
	}

	public int hashCode() {
		if(isWord)
			return 37 + word.hashCode();
		else
			return 53 + label.hashCode() + children.hashCode();
	}

	public boolean equals(Object other) {
		if(other instanceof TreeNode) {
			TreeNode otherTree = (TreeNode)other;
			if(isWord != otherTree.isWord)
				return false;
			if(isWord)
				return word.equals(otherTree.word);
			else
				return label.equals(otherTree.label) && children.equals(otherTree.children);
		}
		return false;
	}

	public static TreeNode makeTreeFromSpans(List<Span> spans, List<Word> words, WordEnumeration wordEnum, LabelEnumeration labels) {
		spans = new ArrayList<Span>(spans);
		Collections.sort(spans, new Comparator<Span>() {

			@Override
			public int compare(Span o1, Span o2) {
				return o1.getStart() - o2.getStart();
			}

		});

		int[] parents = SpanUtilities.getParents(spans);
		TreeNode[] nodes = new TreeNode[spans.size()];
		for(int i = 0; i < spans.size(); i++) {
			nodes[i] = new TreeNode();
		}
		TreeNode top = null;
		for(int i = 0; i < spans.size(); i++) {
			Span s = spans.get(i);
			nodes[i].setLabel(labels.getLabel(s.getRule().getLabel()));
			if(s.getRule().getType() == Type.TERMINAL) {
				nodes[i].addChild(new TreeNode(words.get(s.getStart()).getWord()));
			}
			if(parents[i] != -1) {
				nodes[parents[i]].addChild(nodes[i]);
			}
			else {
				top = nodes[i];
			}
		}
		return top;
	}
}
