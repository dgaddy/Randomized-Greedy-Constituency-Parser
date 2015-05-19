package constituencyParser.unlabeled;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import constituencyParser.LabelEnumeration;
import constituencyParser.Pruning;
import constituencyParser.Rule;
import constituencyParser.Rule.Type;
import constituencyParser.Span;
import constituencyParser.SpanUtilities;
import constituencyParser.Word;

/**
 * Used by RandomizeedGreedyDecoder to find all possible greedy changes to a parse tree.
 */
public class GreedyChange {
	/**
	 * Holds a list of spans parse tree and the parents of each span.
	 *
	 */
	public static class ParentedSpans {
		public ParentedSpans() {}
		public ParentedSpans(List<Span> spans, int[] parents) {
			this.spans = spans;
			this.parents = parents;
		}

		public List<Span> spans;
		public int[] parents;
	}

	/**
	 * Another way of representing parse trees that makes doing greedy updates easier.
	 */
	private static class ConstituencyNode {
		ConstituencyNode parent;
		int label;
		int headWordIndex;

		boolean terminal;
		int index; // for terminals

		// for non-terminals
		ConstituencyNode left;
		ConstituencyNode right;

		int start;
		int end;

		public ConstituencyNode() {
		}

		public ConstituencyNode(ConstituencyNode other) {
			this.parent = null;
			this.label = other.label;
			this.terminal = other.terminal;
			this.index = other.index;
			if(other.left != null)
				this.left = new ConstituencyNode(other.left, this);
			if(other.right != null)
				this.right = new ConstituencyNode(other.right, this);
			this.start = other.start;
			this.end = other.end;
		}

		public ConstituencyNode(ConstituencyNode other, ConstituencyNode parent) {
			this.parent = parent;
			this.label = other.label;
			this.terminal = other.terminal;
			this.index = other.index;
			if(other.left != null)
				this.left = new ConstituencyNode(other.left, this);
			if(other.right != null)
				this.right = new ConstituencyNode(other.right, this);
			this.start = other.start;
			this.end = other.end;
		}

		private void updateSpanLocations() {
			if(terminal){
				start = index;
				end = index + 1;
				return;
			}
			else {
				left.updateSpanLocations();
				right.updateSpanLocations();
				start = left.start;
				end = right.end;
			}
		}
		
		private void propagateLabels(BinaryHeadPropagation p) {
			if(terminal) {
				headWordIndex = index;
				return;
			}
			else {
				left.propagateLabels(p);
				right.propagateLabels(p);
				boolean propLeft = p.getPropagateLeft(left.label, right.label);
				if(propLeft) {
					label = left.label;
					headWordIndex = left.headWordIndex;
				}
				else {
					label = right.label;
					headWordIndex = right.headWordIndex;
				}
			}
		}

		List<ConstituencyNode> getAdjacent(ConstituencyNode node) {
			List<ConstituencyNode> result = new ArrayList<>();
			getAdjacent(node, result);
			return result;
		}

		private void getAdjacent(ConstituencyNode node, List<ConstituencyNode> result) {
			if(start == node.end || end == node.start)
				result.add(this);

			if(!terminal) {
				left.getAdjacent(node, result);
				right.getAdjacent(node, result);
			}
		}

		private void remove(ConstituencyNode node) {
			if(!terminal) {
				if(left == node) {
					right.parent = parent;
					if(parent.left == this)
						parent.left = right;
					else
						parent.right = right;
				}
				else if(right == node) {
					left.parent = parent;
					if(parent.left == this)
						parent.left = left;
					else
						parent.right = left;
				}
				else {
					left.remove(node);
					right.remove(node);
				}
			}
		}

		/**
		 * Finds a node with same start and end as 
		 * @param node
		 * @return
		 */
		private ConstituencyNode insertNodeAsSibling(ConstituencyNode node) {
			ConstituencyNode newParent = new ConstituencyNode();
			newParent.parent = parent;
			if(parent != null) {
				if(parent.left == this) {
					parent.left = newParent;
				}
				else {
					parent.right = newParent;
				}
			}

			if(node.start == end) {
				newParent.left = this;
				newParent.right = node;
			}
			else if(node.end == start) {
				newParent.left = node;
				newParent.right = this;
			}
			else {
				throw new RuntimeException();
			}

			this.parent = newParent;
			node.parent = newParent;

			return newParent;
		}

		private ParentedSpans getSpans(List<Word> words) {
			List<Span> spans = new ArrayList<>();
			List<Integer> parents = new ArrayList<>();
			getSpans(spans, parents, -1, words);
			ParentedSpans result = new ParentedSpans();
			result.parents = new int[parents.size()];
			for(int i = 0; i < parents.size(); i++)
				result.parents[i] = parents.get(i);
			result.spans = spans;
			return result;
		}

		private void getSpans(List<Span> result, List<Integer> parents, int parentIndex, List<Word> words) {
			if(terminal) {
				Span s = new Span(index, label);
				//s.setHeadWord(words.get(headWordIndex).getId()); TODO: put back
				result.add(s);
				parents.add(parentIndex);
			}
			else {
				int index = result.size();
				Span s = new Span(start, end, left.end, label, left.label, right.label);
				//s.setHeadWord(words.get(headWordIndex).getId()); TODO: put back
				result.add(s);
				parents.add(parentIndex);

				left.getSpans(result, parents, index, words);
				right.getSpans(result, parents, index, words);
			}
		}

		private ConstituencyNode getNode(int s, int e) {
			if(start == s && end == e)
				return this;
			else if(s < right.start)
				return left.getNode(s, e);
			else
				return right.getNode(s, e);
		}

		public String toString() {
			if(terminal) {
				return "{" + index + ":" + label + "}";
			}
			else {
				return "{" + left.toString() + right.toString() + "}";
			}
		}
	}

	private LabelEnumeration labels;
	private BinaryHeadPropagation headPropagation;

	public GreedyChange(LabelEnumeration labels, BinaryHeadPropagation headPropagation) {
		this.labels = labels;
		this.headPropagation = headPropagation;
	}
	
	public List<ParentedSpans> makeGreedyTerminalChanges(List<Span> spans, int indexToChange, Pruning pruning, List<Word> words) {
		List<ParentedSpans> result = new ArrayList<>();
		
		ConstituencyNode root = getTree(spans);
		
		ConstituencyNode toChange = root.getNode(indexToChange, indexToChange+1);
		for(int label : labels.getPOSLabels()) {
			toChange.label = label;
			root.propagateLabels(headPropagation);
			result.add(root.getSpans(words));
		}
		
		return result;
	}

	/**
	 * Returns a list of parse trees (in the form of ParentedSpans) that are all local changes from changing the span at (spanToUpdateStart, spanToUpdateEnd)
	 * This includes trying to connect this span to the tree differently
	 * @param spans
	 * @param spanToUpdateStart
	 * @param spanToUpdateEnd
	 * @return
	 */
	public List<ParentedSpans> makeGreedyChanges(List<Span> spans, int spanToUpdateStart, int spanToUpdateEnd, Pruning pruning, List<Word> words) {
		List<ParentedSpans> result = new ArrayList<>();

		ConstituencyNode root = getTree(spans);

		ConstituencyNode toUpdate = root.getNode(spanToUpdateStart, spanToUpdateEnd);

		if(toUpdate.parent == null) {
			return Arrays.asList(new ParentedSpans(spans, SpanUtilities.getParents(spans))); // it doesn't really make sense to remove and add back the entire tree
		}

		if(toUpdate.parent.parent == null) {
			// one level down from root
			// remove root and make sibling root
			if(root.left == toUpdate) {
				root = root.right;
				root.parent = null;
			}
			else {
				root = root.left;
				root.parent = null;
			}
		}
		else {
			root.remove(toUpdate);
			root.updateSpanLocations();
		}
		List<ConstituencyNode> adjacent = root.getAdjacent(toUpdate);

		for(ConstituencyNode a : adjacent) {
			ConstituencyNode newRoot = new ConstituencyNode(root);
			ConstituencyNode sibling = newRoot.getNode(a.start, a.end);
			boolean addingToRoot = sibling.parent == null;
			ConstituencyNode parent = sibling.insertNodeAsSibling(toUpdate);
			if(addingToRoot) {
				newRoot = parent;
			}

			newRoot.updateSpanLocations();
			newRoot.propagateLabels(headPropagation);

			result.add(newRoot.getSpans(words));
		}

		return result;
	}

	/**
	 * Converts list of Span representation to ConstituencyNode representation.  Used before doing greedy updates.
	 * @param spans
	 * @return
	 */
	private static ConstituencyNode getTree(List<Span> spans) {
		int[] parents = SpanUtilities.getParents(spans);

		ConstituencyNode[] nodes = new ConstituencyNode[spans.size()];
		for(int i = 0; i < spans.size(); i++) {
			Rule rule = spans.get(i).getRule();
			if(rule.getType() == Type.UNARY) // we are going to merge the unaries with the node below them
				throw new RuntimeException("This is not for trees with unaries");

			ConstituencyNode node = new ConstituencyNode();
			if(rule.getType() == Type.TERMINAL) {
				node.terminal = true;
				node.index = spans.get(i).getStart();
			}
			node.label = rule.getLabel();
			nodes[i] = node;
		}
		ConstituencyNode root = null;
		for(int i = 0; i < spans.size(); i++) {
			int pi = parents[i];
			if(pi != -1) {
				nodes[i].parent = nodes[pi];

				if(spans.get(i).getStart() < spans.get(pi).getSplit())
					nodes[pi].left = nodes[i];
				else
					nodes[pi].right = nodes[i];
			}
			else {
				root = nodes[i];
			}
		}

		root.updateSpanLocations();
		return root;
	}
}
