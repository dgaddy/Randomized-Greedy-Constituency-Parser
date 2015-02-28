package constituencyParser;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import constituencyParser.Rule.Type;

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
			return new SpannedWords(left, right, labels.getId(label), labels.getId(children.get(0).getLabel()), labels.getId(children.get(1).getLabel()));
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
