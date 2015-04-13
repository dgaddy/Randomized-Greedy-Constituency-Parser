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
	public static class ConstituencyNode {
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
				return "{" + index + ":" + unaryLabel + "," + label + "}";
			}
			else {
				return "{" + left.toString() + "(" + unaryLabel + "," + label + ")" +right.toString() + "}";
			}
		}
		
		private int getConnectLabel() {
			return unaryLabel < 0 ? label : unaryLabel;
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
	public List<ParentedSpans> makeGreedyLabelChanges(List<Span> spans, int indexStart, int indexEnd, boolean topLevel, Pruning pruning) {
		List<ParentedSpans> result = new ArrayList<>();
		
		ConstituencyNode root = getTree(spans);
		
		ConstituencyNode toUpdate = root.getNode(indexStart, indexEnd);
		
		//iterateLabels(root, toUpdate, result, topLevel, pruning);
		iterateLabelsYuan(root, toUpdate, result, topLevel, pruning);
		
		return result;
	}
	
	// use new iterate label function
	public List<ParentedSpans> makeGreedyLabelChangesYuan(List<Span> spans, int indexStart, int indexEnd, boolean topLevel, Pruning pruning) {
		List<ParentedSpans> result = new ArrayList<>();
		
		ConstituencyNode root = getTree(spans);
		
		ConstituencyNode toUpdate = root.getNode(indexStart, indexEnd);
		
		//iterateLabels(root, toUpdate, result, topLevel, pruning);
		iterateLabelsYuan2(root, toUpdate, result, topLevel, pruning);
		
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
	public List<ParentedSpans> makeGreedyChanges(List<Span> spans, int spanToUpdateStart, int spanToUpdateEnd, int size, Pruning pruning) {
		//System.out.println("begin update " + spanToUpdateStart + " " + spanToUpdateEnd);
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
		
		//boolean findOld = false;
		
		for(ConstituencyNode a : adjacent) {
			ConstituencyNode newRoot = new ConstituencyNode(root);
			ConstituencyNode newToUpdate = new ConstituencyNode(toUpdate);
			ConstituencyNode sibling = newRoot.getNode(a.start, a.end);
			boolean addingToRoot = sibling.parent == null;
			ConstituencyNode parent = sibling.insertNodeAsSibling(newToUpdate);
			if(addingToRoot) {
				newRoot = parent;
			}
			
			newRoot.updateSpanLocations();
			
			parent.unaryLabel = parent.label = -1;
//			ParentedSpans tmpSpans = newRoot.getSpans();
//			if (sameSpans(tmpSpans.spans, spans)) {
//				System.out.println("old positions!");
//				//System.out.println(tmpSpans.spans);
//				//System.out.println(spans);
//				SpanUtilities.print(tmpSpans.spans, labels);
//				SpanUtilities.print(spans, labels);
//				findOld = true;
//			}
			
			//int id = checkSpan(newRoot, 0);
			//SpanUtilities.Assert(id == size);
			
			fixBarErrors(newRoot);
			
			//iterateLabels(newRoot, parent, result, parent == newRoot ? true : false, pruning);
			//iterateLabels(newRoot, parent, result, false, pruning);
			iterateLabelsYuan(newRoot, parent, result, false, pruning);
			//iterateLabelsYuan(newRoot, parent, result, parent == newRoot ? true : false, pruning);
		}
		
		return result;
	}
	
	public boolean sameSpans(List<Span> span1, List<Span> span2) {
		//if (span1.size() != span2.size())
		//	return false;
		for (int i = 0; i < span1.size(); ++i) {
			Span s1 = span1.get(i);
			boolean find = false;
			for (int j = 0; j < span2.size(); ++j) {
				Span s2 = span2.get(j);
				if (s1.getStart() == s2.getStart()
						&& s1.getEnd() == s2.getEnd()) {
					find = true;
					break;
				}
			}
			if (!find)
				return false;
		}
		return true;
	}

	public int checkSpan(ConstituencyNode node, int id) {
		if (node.terminal) {
			SpanUtilities.Assert(node.index == id);
			SpanUtilities.Assert(node.start == id && node.end == id + 1);
			return id + 1;
		}
		else {
			id = checkSpan(node.left, id);
			id = checkSpan(node.right, id);
			return id;
		}
	}
	
	public void fixBarErrors(ConstituencyNode node) {
		// 1: -bar on the right
		// 2: -bar with unary
		// 3: -bar with different head
		
		if (node.terminal)
			return;
		
		SpanUtilities.Assert(node.left.unaryLabel == -1 || 
				(!labels.isBarLabel(node.left.unaryLabel) && !labels.isBarLabel(node.left.label)));
		SpanUtilities.Assert(node.right.unaryLabel == -1 || 
				(!labels.isBarLabel(node.right.unaryLabel) && !labels.isBarLabel(node.right.label)));
			
		if (node.right.label >= 0 && labels.isBarLabel(node.right.label)) {
			//System.out.print("fix " + labels.getLabel(node.right.label) + " to ");
			node.right.label = labels.getOrigLabel(node.right.label);
			//System.out.println(labels.getLabel(node.right.label) + " due to 1");
			//System.out.println(node.start + " " + node.end + " " + node.left.end + " " + node.label + " " + node.left.label + " " + node.right.label);
		}
		
		if (node.left.label >= 0 && labels.isBarLabel(node.left.label)) {
			int origLabel = labels.getOrigLabel(node.left.label);
			if (node.left.unaryLabel != -1) {
				//System.out.println("fix " + labels.getLabel(node.left.label) + " to "
				//		+ labels.getLabel(origLabel) + " due to 2");
				
				node.left.label = origLabel;
			}
			else if (node.label >= 0 && 
					(node.label != node.left.label && node.label != origLabel)) {
				//System.out.println("fix " + labels.getLabel(node.left.label) + " to "
				//		+ labels.getLabel(origLabel) + " due to 3");
				node.left.label = origLabel;
			}
		}
		
		fixBarErrors(node.left);
		fixBarErrors(node.right);
	}
	
	/**
	 * Iterate over possible values of labels for toIterate node.  Puts all possible resulting parses in resultAccumulator.
	 * @param root
	 * @param toIterate
	 * @param resultAccumulator
	 * @param topLevel
	 */
	void iterateLabels(ConstituencyNode root, ConstituencyNode toIterate, List<ParentedSpans> resultAccumulator, boolean topLevel, Pruning pruning) {
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
			
			if(pruning.isPrunedBeforeUnary(spanToIterate.getStart(), spanToIterate.getEnd(), i))
				continue;
			
			Span newToIterate = spanToIterate.changeLabel(i);
			if(!rules.isExistingRule(newToIterate.getRule()))
				continue;
			List<Span> newSpans = new ArrayList<>(spans.spans);
			newSpans.set(spanToIterateIndex, newToIterate);
			if(parentIndex != -1) {
				Span newParent = parent.changeChildLabel(leftChild, i);
				if(!rules.isExistingRule(newParent.getRule()))
					continue;
				newSpans.set(parentIndex, newParent);
			}
			
			SpanUtilities.connectChildren(newSpans, spans.parents);
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
			
			if(pruning.isPrunedAfterUnary(spanToIterate.getStart(), spanToIterate.getEnd(), rule.getLabel()) || pruning.isPrunedBeforeUnary(spanToIterate.getStart(), spanToIterate.getEnd(), rule.getLeft()))
				continue;

			Span newToIterate = spanToIterate.changeLabel(rule.getLeft());
			if(!rules.isExistingRule(newToIterate.getRule()))
				continue;
			Span newUnary = unary.changeChildLabel(true, rule.getLeft()).changeLabel(rule.getLabel());
			List<Span> newSpans = new ArrayList<>(spans.spans);
			newSpans.set(spanToIterateIndex, newToIterate);
			newSpans.set(unaryIndex, newUnary);
			if(parentIndex != -1) {
				Span newParent = parent.changeChildLabel(leftChild, rule.getLabel());
				if(!rules.isExistingRule(newParent.getRule()))
					continue;
				newSpans.set(parentIndex, newParent);
			}
			
			SpanUtilities.connectChildren(newSpans, spans.parents);
			resultAccumulator.add(new ParentedSpans(newSpans, spans.parents));
		}
	}
	
	void iterateLabelsYuan(ConstituencyNode root, ConstituencyNode toIterate, List<ParentedSpans> resultAccumulator, boolean topLevel, Pruning pruning) {
		// difference from the old iterate labels: children's unary rules may be removed
		Set<Integer> topLevelLabels = labels.getTopLevelLabelIds();
//		if (root.unaryLabel >= 0) {
//			if (!topLevelLabels.contains(root.unaryLabel)) {
//				System.out.println(root + " " + toIterate + " " + topLevel);
//			}
//			SpanUtilities.Assert(topLevelLabels.contains(root.unaryLabel));
//		}
//		else if (root.label >= 0) {
//			SpanUtilities.Assert(topLevelLabels.contains(root.label));
//		}
		
		ConstituencyNode parent = toIterate.parent;
		boolean isLeft = parent != null && parent.left == toIterate ? true : false;
		boolean isRight = parent != null && parent.right == toIterate ? true : false;

		int L = labels.getNumberOfLabels();
		
		int leftChildLabelOptions = toIterate.terminal || toIterate.left.unaryLabel < 0 ? 1 : 2;
		int rightChildLabelOptions = toIterate.terminal || toIterate.right.unaryLabel < 0 ? 1 : 2;
		int leftChildUnaryLabel = toIterate.terminal ? -1 : toIterate.left.unaryLabel;
		int rightChildUnaryLabel = toIterate.terminal ? -1 : toIterate.right.unaryLabel;
		
		// iterate binary labels
		for(int i = 0; i < L; ++i) {
			if(pruning.isPrunedBeforeUnary(toIterate.start, toIterate.end, i))
				continue;

			toIterate.label = i;
			
			// iterate child label combinartions
			for (int l = 0; l < leftChildLabelOptions; ++l) {
				if (!toIterate.terminal)
					toIterate.left.unaryLabel = l == 0 ? leftChildUnaryLabel : -1;
				
				for (int r = 0; r < rightChildLabelOptions; ++r) {
					if (!toIterate.terminal)
						toIterate.right.unaryLabel = r == 0 ? rightChildUnaryLabel : -1;
					
					if (!toIterate.terminal && !rules.isExistingBinaryRule(i, toIterate.left.getConnectLabel(), toIterate.right.getConnectLabel()))
						continue;
					
					// iterate unary combinations
					for (int j = 0; j < L; ++j) {
						if (!rules.isExistingUnaryRule(j, i))
							continue;
						
						if (pruning.isPrunedAfterUnary(toIterate.start, toIterate.end, j))
							continue;
						
						if(topLevel && !topLevelLabels.contains(j))
							continue;
						
						if (parent != null && !rules.isExistingBinaryRule(parent.label, isLeft ? j : parent.left.getConnectLabel(), isRight ? j : parent.right.getConnectLabel()))
							continue;

						toIterate.unaryLabel = j;
						
						//if (root.unaryLabel >= 0) {
						//	if (!topLevelLabels.contains(root.unaryLabel)) {
						//		System.out.println(root + " " + toIterate + " " + topLevel);
						//	}
						//	SpanUtilities.Assert(topLevelLabels.contains(root.unaryLabel));
						//}
						//else if (root.label >= 0) {
						//	SpanUtilities.Assert(topLevelLabels.contains(root.label));
						//}
						
						ParentedSpans spans = root.getSpans();
						SpanUtilities.connectChildren(spans.spans, spans.parents);
						resultAccumulator.add(spans);
					}
					
					toIterate.unaryLabel = -1;
					
					// the case without unary
					if(topLevel && !topLevelLabels.contains(i))
						continue;

					if (parent != null && !rules.isExistingBinaryRule(parent.label, isLeft ? i : parent.left.getConnectLabel(), isRight ? i : parent.right.getConnectLabel()))
						continue;
					
					ParentedSpans spans = root.getSpans();
					SpanUtilities.connectChildren(spans.spans, spans.parents);
					resultAccumulator.add(spans);
				}
			}

			// recover
			if (!toIterate.terminal) {
				toIterate.left.unaryLabel = leftChildUnaryLabel;
				toIterate.right.unaryLabel = rightChildUnaryLabel;
			}
		}
	}
	
	boolean updateNodeLabel(ConstituencyNode node, int label, int oldLabel, int mode) {
		if (mode == 0) {
			// add to binary
			node.unaryLabel = -1;
			node.label = label;
			
			if (!node.terminal && !rules.isExistingBinaryRule(label, node.left.getConnectLabel(), node.right.getConnectLabel()))
				return false;
			else
				return true;
		}
		else {
			// add to unary
			node.unaryLabel = label;
			node.label = oldLabel;
			
			if (!rules.isExistingUnaryRule(label, oldLabel))
				return false;
			
			SpanUtilities.Assert(node.terminal || rules.isExistingBinaryRule(oldLabel, node.left.getConnectLabel(), node.right.getConnectLabel()));
			return true;
		}
	}
	
	// change both parent and children node labels
	void iterateLabelsYuan2(ConstituencyNode root, ConstituencyNode toIterate, List<ParentedSpans> resultAccumulator, boolean topLevel, Pruning pruning) {
		Set<Integer> topLevelLabels = labels.getTopLevelLabelIds();
		
		ConstituencyNode parent = toIterate.parent;
		boolean isLeft = parent != null && parent.left == toIterate ? true : false;
		boolean isRight = parent != null && parent.right == toIterate ? true : false;

		int L = labels.getNumberOfLabels();
		int binaryRuleNum = rules.getNumberOfBinaryRules();
		
		int leftChildUnaryLabel = toIterate.terminal ? -1 : toIterate.left.unaryLabel;
		int rightChildUnaryLabel = toIterate.terminal ? -1 : toIterate.right.unaryLabel;
		int leftChildLabel = toIterate.terminal ? -1 : toIterate.left.label;
		int rightChildLabel = toIterate.terminal ? -1 : toIterate.right.label;
		
		// iterate binary rules
		for(int i = 0; i < binaryRuleNum; ++i) {
			Rule binaryRule = rules.getBinaryRule(i);
			
			if(pruning.isPrunedBeforeUnary(toIterate.start, toIterate.end, binaryRule.getLabel()))
				continue;

			toIterate.label = binaryRule.getLabel();
			
			// iterate child label combinations
			for (int l = 0; l < (toIterate.terminal ? 1 : 2); ++l) {
				ConstituencyNode leftChildNode = toIterate.left;
				if (!toIterate.terminal) {
					if (!updateNodeLabel(leftChildNode, binaryRule.getLeft(), leftChildLabel, l))
						continue;
				}
				
				for (int r = 0; r < (toIterate.terminal ? 1 : 2); ++r) {
					ConstituencyNode rightChildNode = toIterate.right;
					if (!toIterate.terminal) {
						if (!updateNodeLabel(rightChildNode, binaryRule.getRight(), rightChildLabel, r))
							continue;
					}
					
					if (!toIterate.terminal && !rules.isExistingBinaryRule(toIterate.label, leftChildNode.getConnectLabel(), rightChildNode.getConnectLabel()))
						continue;
					
					// iterate unary combinations
					for (int j = 0; j < L; ++j) {
						if (!rules.isExistingUnaryRule(j, toIterate.label))
							continue;
						
						if (pruning.isPrunedAfterUnary(toIterate.start, toIterate.end, j))
							continue;
						
						if(topLevel && !topLevelLabels.contains(j))
							continue;
						
						if (parent != null && !rules.isExistingBinaryRule(parent.label, isLeft ? j : parent.left.getConnectLabel(), isRight ? j : parent.right.getConnectLabel()))
							continue;

						toIterate.unaryLabel = j;
						
						//if (root.unaryLabel >= 0) {
						//	if (!topLevelLabels.contains(root.unaryLabel)) {
						//		System.out.println(root + " " + toIterate + " " + topLevel);
						//	}
						//	SpanUtilities.Assert(topLevelLabels.contains(root.unaryLabel));
						//}
						//else if (root.label >= 0) {
						//	SpanUtilities.Assert(topLevelLabels.contains(root.label));
						//}
						
						ParentedSpans spans = root.getSpans();
						SpanUtilities.connectChildren(spans.spans, spans.parents);
						resultAccumulator.add(spans);
					}
					
					toIterate.unaryLabel = -1;
					
					// the case without unary
					if(topLevel && !topLevelLabels.contains(toIterate.label))
						continue;

					if (parent != null && !rules.isExistingBinaryRule(parent.label, isLeft ? toIterate.label : parent.left.getConnectLabel(), isRight ? toIterate.label : parent.right.getConnectLabel()))
						continue;
					
					ParentedSpans spans = root.getSpans();
					SpanUtilities.connectChildren(spans.spans, spans.parents);
					resultAccumulator.add(spans);
				}
			}

			// recover
			if (!toIterate.terminal) {
				toIterate.left.unaryLabel = leftChildUnaryLabel;
				toIterate.right.unaryLabel = rightChildUnaryLabel;
				toIterate.left.label = leftChildLabel;
				toIterate.right.label = rightChildLabel;
			}
		}
	}
	
	/**
	 * Converts list of Span representation to ConstituencyNode representation.  Used before doing greedy updates.
	 * @param spans
	 * @return
	 */
	static ConstituencyNode getTree(List<Span> spans) {
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
				SpanUtilities.Assert(root == null);
				root = nodes[i];
			}
		}
		
		root.updateSpanLocations();
		return root;
	}
	
	
}
