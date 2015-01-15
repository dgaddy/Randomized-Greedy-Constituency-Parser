package constituencyParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import constituencyParser.Rule.Type;

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
		
		List<Span> spans;
		int[] parents;
	}
	
	/**
	 * Another way of representing parse trees that makes doing greedy updates easier.
	 */
	private static class ConstituencyNode {
		ConstituencyNode parent;
		int unaryLabel; // -1 if there is not a unary on this
		int label;
		
		boolean terminal;
		int index; // for terminals
		
		// for non-terminals
		ConstituencyNode left;
		ConstituencyNode right;
		
		int start;
		int end;
		
		public ConstituencyNode() {
			unaryLabel = -1;
		}
		
		public ConstituencyNode(ConstituencyNode other) {
			this.parent = null;
			this.unaryLabel = other.unaryLabel;
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
			this.unaryLabel = other.unaryLabel;
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
		
		private ParentedSpans getSpans() {
			List<Span> spans = new ArrayList<>();
			List<Integer> parents = new ArrayList<>();
			getSpans(spans, parents, -1);
			ParentedSpans result = new ParentedSpans();
			result.parents = new int[parents.size()];
			for(int i = 0; i < parents.size(); i++)
				result.parents[i] = parents.get(i);
			result.spans = spans;
			return result;
		}
		
		private void getSpans(List<Span> result, List<Integer> parents, int parentIndex) {
			if(unaryLabel != -1) {
				parents.add(parentIndex);
				parentIndex = result.size(); // set the unary index to the parent index for the terminal and binary rules below
				result.add(new Span(start, end, unaryLabel, label));
			}
			
			if(terminal) {
				result.add(new Span(index, label));
				parents.add(parentIndex);
			}
			else {
				int index = result.size();
				result.add(new Span(start, end, left.end, label, left.unaryLabel != -1 ? left.unaryLabel : left.label, right.unaryLabel != -1 ? right.unaryLabel : right.label));
				parents.add(parentIndex);
				
				left.getSpans(result, parents, index);
				right.getSpans(result, parents, index);
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
	private RuleEnumeration rules;
	
	public GreedyChange(LabelEnumeration labels, RuleEnumeration rules) {
		this.labels = labels;
		this.rules = rules;
	}
	
	/**
	 * Iterate over possible labels for the span (or spans if there is a unary rule) that starts at indexStart and goes to indexEnd
	 * @param spans
	 * @param indexStart
	 * @param indexEnd
	 * @param topLevel
	 * @return
	 */
	public List<ParentedSpans> makeGreedyLabelChanges(List<Span> spans, int indexStart, int indexEnd, boolean topLevel) {
		List<ParentedSpans> result = new ArrayList<>();
		
		ConstituencyNode root = getTree(spans);
		
		ConstituencyNode toUpdate = root.getNode(indexStart, indexEnd);
		
		iterateLabels(root, toUpdate, result, topLevel);
		
		return result;
	}
	
	/**
	 * Returns a list of parse trees (in the form of ParentedSpans) that are all local changes from changing the span at (spanToUpdateStart, spanToUpdateEnd)
	 * This includes trying to connect this span to the tree differently and iterating over different label values at each location
	 * @param spans
	 * @param spanToUpdateStart
	 * @param spanToUpdateEnd
	 * @return
	 */
	public List<ParentedSpans> makeGreedyChanges(List<Span> spans, int spanToUpdateStart, int spanToUpdateEnd) {
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
			
			iterateLabels(newRoot, parent, result, false);
		}
		
		return result;
	}
	
	/**
	 * Iterate over possible values of labels for toIterate node.  Puts all possible resulting parses in resultAccumulator.
	 * @param root
	 * @param toIterate
	 * @param resultAccumulator
	 * @param topLevel
	 */
	void iterateLabels(ConstituencyNode root, ConstituencyNode toIterate, List<ParentedSpans> resultAccumulator, boolean topLevel) {
		Set<Integer> topLevelLabels = labels.getTopLevelLabelIds();
		
		// iterate with no unary labels
		toIterate.unaryLabel = -1; // -1 indicates no unary
		toIterate.label = -2; // using -2 as a marker to find this span
		ParentedSpans spans = root.getSpans();
		int spanToIterateIndex = -1;
		Span spanToIterate = null;
		for(int i = 0; i < spans.spans.size(); i++){
			if(spans.spans.get(i).getRule().getLabel() == -2) {
				spanToIterateIndex = i;
				spanToIterate = spans.spans.get(i);
			}
		}
		int parentIndex = spans.parents[spanToIterateIndex];
		Span parent = null;
		boolean leftChild = true;
		if(parentIndex != -1) {
			parent = spans.spans.get(parentIndex);
			leftChild = parent.getRule().getLeft() == -2;
		}
		for(int i = 0; i < labels.getNumberOfLabels(); i++) {
			if(topLevel && !topLevelLabels.contains(i))
				continue;
			
			List<Span> newSpans = new ArrayList<>(spans.spans);
			newSpans.set(spanToIterateIndex, spanToIterate.changeLabel(i));
			if(parentIndex != -1)
				newSpans.set(parentIndex, parent.changeChildLabel(leftChild, i));
			
			resultAccumulator.add(new ParentedSpans(newSpans, spans.parents));
		}
		
		// iterate unary combinations
		toIterate.unaryLabel = -2; // using -2 as a marker to find this span
		toIterate.label = -3; // using -3 as a marker to find this span
		spans = root.getSpans();
		spanToIterateIndex = -1;
		spanToIterate = null;
		for(int i = 0; i < spans.spans.size(); i++){
			if(spans.spans.get(i).getRule().getLabel() == -3) {
				spanToIterateIndex = i;
				spanToIterate = spans.spans.get(i);
			}
		}
		int unaryIndex = spans.parents[spanToIterateIndex];
		Span unary = spans.spans.get(unaryIndex);
		parentIndex = spans.parents[unaryIndex];
		if(parentIndex != -1) {
			parent = spans.spans.get(parentIndex);
			leftChild = parent.getRule().getLeft() == -2;
		}
		for(int i = 0; i < rules.getNumberOfUnaryRules(); i++) {
			Rule rule = rules.getUnaryRule(i);
			if(topLevel && !topLevelLabels.contains(rule.getLabel()))
				continue;

			List<Span> newSpans = new ArrayList<>(spans.spans);
			newSpans.set(spanToIterateIndex, spanToIterate.changeLabel(rule.getLeft()));
			newSpans.set(unaryIndex, unary.changeChildLabel(true, rule.getLeft()).changeLabel(rule.getLabel()));
			if(parentIndex != -1)
				newSpans.set(parentIndex, parent.changeChildLabel(leftChild, rule.getLabel()));
			
			resultAccumulator.add(new ParentedSpans(newSpans, spans.parents));
		}
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
				continue;
			else {
				ConstituencyNode node = new ConstituencyNode();
				if(rule.getType() == Type.TERMINAL) {
					node.terminal = true;
					node.index = spans.get(i).getStart();
				}
				node.label = rule.getLabel();
				nodes[i] = node;
				int pi = parents[i];
				if(pi != -1) {
					Span parent = spans.get(pi);
					if(parent.getRule().getType() == Type.UNARY) {
						nodes[pi] = node;
						node.unaryLabel = parent.getRule().getLabel();
					}
				}
			}
		}
		ConstituencyNode root = null;
		for(int i = 0; i < spans.size(); i++) {
			int pi = parents[i];
			if(pi != -1) {
				if(spans.get(pi).getRule().getType() == Type.UNARY) {
					continue; // we will handle this one when we get to the unary
				}
				
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
