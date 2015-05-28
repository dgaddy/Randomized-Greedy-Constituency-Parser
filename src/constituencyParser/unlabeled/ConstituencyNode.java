package constituencyParser.unlabeled;

import java.util.ArrayList;
import java.util.List;

import constituencyParser.Pruning;

public class ConstituencyNode {
	ConstituencyNode parent;
	int label;
	int headWordIndex;
	
	boolean propagateLeft;

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
		this.headWordIndex = other.headWordIndex;
		this.propagateLeft = other.propagateLeft;
		this.terminal = other.terminal;
		this.index = other.index;
		if(other.left != null)
			this.left = new ConstituencyNode(other.left, this);
		if(other.right != null)
			this.right = new ConstituencyNode(other.right, this);
		this.start = other.start;
		this.end = other.end;
	}

	private ConstituencyNode(ConstituencyNode other, ConstituencyNode parent) {
		this.parent = parent;
		this.label = other.label;
		this.headWordIndex = other.headWordIndex;
		this.propagateLeft = other.propagateLeft;
		this.terminal = other.terminal;
		this.index = other.index;
		if(other.left != null)
			this.left = new ConstituencyNode(other.left, this);
		if(other.right != null)
			this.right = new ConstituencyNode(other.right, this);
		this.start = other.start;
		this.end = other.end;
	}

	public void updateSpanLocations() {
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
	
	public void propagateLabels() {
		if(terminal) {
			headWordIndex = index;
			return;
		}
		else {
			left.propagateLabels();
			right.propagateLabels();
			if(propagateLeft) {
				label = left.label;
				headWordIndex = left.headWordIndex;
			}
			else {
				label = right.label;
				headWordIndex = right.headWordIndex;
			}
		}
	}

	public List<ConstituencyNode> getAdjacent(ConstituencyNode node) {
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

	public void remove(ConstituencyNode node) {
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
	public ConstituencyNode insertNodeAsSibling(ConstituencyNode node) {
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

	/*private ParentedSpans getSpans(List<Word> words) {
		List<Span> spans = new ArrayList<>(words.size()*2);
		List<Integer> parents = new ArrayList<>(words.size()*2);
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
			s.setHeadWord(words.get(headWordIndex).getId());
			result.add(s);
			parents.add(parentIndex);
		}
		else {
			int index = result.size();
			Span s = new Span(start, end, left.end, label, left.label, right.label, propagateLeft);
			s.setHeadWord(words.get(headWordIndex).getId());
			result.add(s);
			parents.add(parentIndex);

			left.getSpans(result, parents, index, words);
			right.getSpans(result, parents, index, words);
		}
	}*/

	public ConstituencyNode getNode(int s, int e) {
		if(start == s && end == e)
			return this;
		else if(s < right.start)
			return left.getNode(s, e);
		else
			return right.getNode(s, e);
	}
	
	public boolean isPruned(Pruning pruning) {
		if(this.terminal)
			return pruning.isPruned(start, end, label);
		else
			return pruning.isPruned(start, end, label) || left.isPruned(pruning) || right.isPruned(pruning);
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
