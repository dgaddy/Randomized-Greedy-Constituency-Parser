package constituencyParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import constituencyParser.Rule.Type;

public class GreedyChange {
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
		
		private List<Span> getSpans() {
			List<Span> result = new ArrayList<>();
			getSpans(result);
			return result;
		}
		
		private void getSpans(List<Span> result) {
			if(unaryLabel != -1)
				result.add(new Span(start, end, unaryLabel, label));
			
			if(terminal)
				result.add(new Span(index, label));
			else {
				result.add(new Span(start, end, left.end, label, left.unaryLabel != -1 ? left.unaryLabel : left.label, right.unaryLabel != -1 ? right.unaryLabel : right.label));
			
				left.getSpans(result);
				right.getSpans(result);
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
	private Rules rules;
	
	public GreedyChange(LabelEnumeration labels, Rules rules) {
		this.labels = labels;
		this.rules = rules;
	}
	
	public List<List<Span>> makeGreedyTerminalLabelChanges(List<Span> spans, int index) {
		List<List<Span>> result = new ArrayList<>();
		
		ConstituencyNode root = getTree(spans);
		
		ConstituencyNode toUpdate = root.getNode(index, index + 1);
		
		iterateLabels(root, toUpdate, result, false);
		
		return result;
	}
	
	public List<List<Span>> makeGreedyChanges(List<Span> spans, int spanToUpdateStart, int spanToUpdateEnd) {
		List<List<Span>> result = new ArrayList<>();
		
		ConstituencyNode root = getTree(spans);
		
		ConstituencyNode toUpdate = root.getNode(spanToUpdateStart, spanToUpdateEnd);
		
		//List<Span> reconstructed = root.getSpans();
		
		if(toUpdate.parent == null) {
			return Arrays.asList(spans); // it doesn't really make sense to remove and add back the entire tree
		}
		else if(toUpdate.parent.parent == null) {
			// one level down from root
			// we just want to iterate over labels for root
			iterateLabels(root, root, result, true);
			return result;
		}
		
		root.remove(toUpdate);
		root.updateSpanLocations();
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
	
	void iterateLabels(ConstituencyNode root, ConstituencyNode toIterate, List<List<Span>> resultAccumulator, boolean topLevel) {
		Set<Integer> topLevelLabels = labels.getTopLevelLabelIds();
		
		// iterate with no unary labels
		toIterate.unaryLabel = -1;
		for(int i = 0; i < labels.getNumberOfLabels(); i++) {
			if(topLevel && !topLevelLabels.contains(i))
				continue;
			
			toIterate.label = i;
			resultAccumulator.add(root.getSpans());
		}
		
		// iterate unary combinations
		for(int i = 0; i < rules.getNumberOfUnaryRules(); i++) {
			Rule rule = rules.getUnaryRule(i);
			if(topLevel && !topLevelLabels.contains(rule.getParent()))
				continue;
			
			toIterate.unaryLabel = rule.getParent();
			toIterate.label = rule.getLeft();
			resultAccumulator.add(root.getSpans());
		}
	}
	
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
				node.label = rule.getParent();
				nodes[i] = node;
				int pi = parents[i];
				if(pi != -1) {
					Span parent = spans.get(pi);
					if(parent.getRule().getType() == Type.UNARY) {
						nodes[pi] = node;
						node.unaryLabel = parent.getRule().getParent();
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
