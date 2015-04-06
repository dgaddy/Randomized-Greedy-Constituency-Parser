package constituencyParser.features;

import java.util.ArrayList;
import java.util.List;

import constituencyParser.LabelEnumeration;
import constituencyParser.Rule.Type;
import constituencyParser.Span;
import constituencyParser.features.Features.FeatureType;

public class GlobalFeatures {
	/*
	 * The Spans passed to these feature methods must have left and right set to child Spans
	 */
	
	LabelEnumeration labels;
	
	public GlobalFeatures(LabelEnumeration labels) {
		this.labels = labels;
	}
	
	public void getAll(List<Span> spans, List<Long> resultAccumulator) {
		for(Span s : spans) {
			if(s.getRule().getType() != Type.TERMINAL && s.getLeft() == null)
				throw new RuntimeException("Span left and right must be set when getting global features");
			
			coPar(s, resultAccumulator);
		}
	}
	
	// Whole tree features -  features that only need to be called on the root span
	
	// Node features - features that need to be called on all nodes
	
	/**
	 * 
	 * @param depth
	 * @param matches 1 if matches, 0 if not
	 * @return
	 */
	public static long coPar(int depth, int matches) {
		return Features.getCodeBase(FeatureType.CO_PAR) + depth << 10 + matches;
	}
	
	private void coPar(Span span, List<Long> resultAccumulator) {
		int label = span.getRule().getLabel();
		int extendLabel = labels.getExtendLabel(label);
		Span left = span.getLeft();
		if(left == null || left.getRule().getLabel() != extendLabel)
			return;
		Span leftRight = left.getRight();
		if(leftRight != null && labels.isConjunction(leftRight.getRule().getLabel())) { // coordination structure
			Span lastChild = null;
			for(Span child : getChildren(span)) {
				int cl = child.getRule().getLabel();
				if(labels.isConjunction(cl) || labels.isPunctuation(cl))
					continue;
				
				if(lastChild != null) {
					for(int i = 1; i <= 5; i++) {
						int result = matches(child, lastChild, i);
						if(result == -1) {
							break;
						}
						else {
							resultAccumulator.add(coPar(i, result));
						}
					}
				}
				lastChild = child;
			}
		}
	}
	
	/**
	 * -1 if match but not deep enough for depth, 0 if no match, 1 if match
	 * @param left
	 * @param right
	 * @param depth
	 * @return
	 */
	private int matches(Span left, Span right, int depth) {
		if(left.getRule().getLabel() != right.getRule().getLabel()) {
			return 0;
		}
		if(depth == 1) {
			return 1;
		}
		if(left.getRule().getType() != Type.TERMINAL && right.getRule().getType() != Type.TERMINAL) {
			int leftMatches = matches(left.getLeft(), right.getLeft(), depth - 1);
			if(left.getRight() == null || right.getRight() == null) {
				if(left.getRight() != null || right.getRight() != null)
					return 0;
				else {
					return leftMatches;
				}
			}
			int rightMatches = matches(left.getRight(), right.getRight(), depth - 1);
			if(leftMatches == 0 || rightMatches == 0)
				return 0;
			else if(leftMatches == 1 || rightMatches == 1)
				return 1;
			else
				return -1;
		}
		else {
			return -1;
		}
	}
	
	/**
	 * Gets all children as if the node was not binarized
	 * @return
	 */
	private List<Span> getChildren(Span span) {
		List<Span> result = new ArrayList<>();
		
		int label = span.getRule().getLabel();
		int extendedLabel = labels.getExtendLabel(label);
		if(span.getRight() != null)
			result.add(span.getRight());
		Span next = span.getLeft();
		while(next != null && next.getRule().getLabel() == extendedLabel) {
			if(next.getRight() != null)
				result.add(next.getRight());
			next = next.getLeft();
		}
		if(next != null)
			result.add(next);
		return result;
	}
}
