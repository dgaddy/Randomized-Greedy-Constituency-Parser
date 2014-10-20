package constituencyParser;

import gnu.trove.list.TLongList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import constituencyParser.Rule.Type;
import constituencyParser.features.FeatureParameters;
import constituencyParser.features.Features;

public class RandomizedGreedyDecoder {
	Sampler sampler;
	
	WordEnumeration wordEnum;
	LabelEnumeration labels;
	Rules rules;
	
	public RandomizedGreedyDecoder(WordEnumeration words, LabelEnumeration labels, Rules rules) {
		sampler = new Sampler(words, labels, rules);
		this.wordEnum = words;
		this.labels = labels;
		this.rules = rules;
	}
	
	public List<Span> decode(List<Integer> words, FeatureParameters params, boolean dropout) {
		sampler.calculateProbabilities(words, params);
		List<Span> spans = sampler.sample();
		
		Collections.sort(spans, new Comparator<Span>() {
			@Override
			public int compare(Span arg0, Span arg1) {
				return (arg0.getEnd() - arg0.getStart()) - (arg1.getEnd() - arg1.getStart());
			}
		});
		
		for(int i = 0; i < spans.size(); i++) {
			spans = greedyUpdate(words, spans, params, i, dropout);
		}
		return spans;
	}
	
	/**
	 * Tries to move toMove to different locations with different parent labels
	 * @param spans
	 * @param toMove
	 * @return best update (possibly resulting in the same thing)
	 */
	private List<Span> greedyUpdate(List<Integer> words, List<Span> spans, FeatureParameters params, int indexToMove, boolean dropout) {
		spans = new ArrayList<>(spans);
		
		SpanPrint.printSpans(spans, words.size());
		
		Span toMove = spans.get(indexToMove);
		int[] parents = getParents(spans);
		int start = toMove.getStart();
		int end = toMove.getEnd();
		
		int parentIndex = parents[indexToMove];
		if(parentIndex == -1)
			return spans;
		
		int oldGrandparentIndex = parents[parentIndex];
		
		if(oldGrandparentIndex == -1) {
			// there's not anywhere to move, so we just need to iterate over labels for the parent
			Span parent = spans.get(parentIndex);
			double max = Double.NEGATIVE_INFINITY;
			List<Span> best = null;
			for(int label = 0; label < labels.getNumberOfLabels(); label++) {
				Rule newRule = parent.getRule().changeLabel(label);
				spans.set(parentIndex, new Span(parent.getStart(), parent.getEnd(), parent.getSplit(), newRule));
				double score = score(words, spans, params, dropout);
				if(score > max) {
					max = score;
					best = new ArrayList<>(spans);
				}
			}
			return best;
		}
		
		// shrink ancestors
		int p = parentIndex;
		while(p != -1) {
			Span s = spans.get(p);
			spans.set(p, s.removeRange(start, end));
			p = parents[p];
		}
		
		// put sibling onto grandparent
		for(int i = 0; i < spans.size(); i++) {
			if(i != indexToMove && parents[i] == parentIndex) {
				parents[i] = oldGrandparentIndex;
				Span gp = spans.get(oldGrandparentIndex);
				Span sibling = spans.get(i);
				spans.set(oldGrandparentIndex, gp.changeChildLabel(sibling.getStart() < gp.getSplit(), sibling.getRule().getParent()));
			}
		}
		
		SpanPrint.printSpans(spans, words.size());
		
		List<Span> spansBackup = spans;
		double bestScore = Double.NEGATIVE_INFINITY;
		List<Span> best = null;
		
		// try attaching to a new parent
		for(int siblingIndex = 0; siblingIndex < spans.size(); siblingIndex++) {
			if(siblingIndex == indexToMove)
				continue;
			
			spans = new ArrayList<>(spansBackup);
			
			Span sibling = spans.get(siblingIndex);
			int newStart;
			int newEnd;
			boolean addingToRight;
			if(sibling.getEnd() == start) {// if adjacent to the left of the one we are moving
				addingToRight = true;
				newStart = sibling.getStart();
				newEnd = end;
			}
			else {
				addingToRight = false;
				newStart = start;
				newEnd = sibling.getEnd();
			}
			
			// expand size of ancestors
			int ns = newStart;
			int ne = newEnd;
			p = parents[siblingIndex];
			while(p != -1) {
				Span s = spans.get(p);
				Span newSpan = s.childExpanded(ns, ne);
				spans.set(p, newSpan);
				ns = newSpan.getStart();
				ne = newSpan.getEnd();
				p = parents[p];
			}
			
			int grandparentIndex = parents[siblingIndex];
			
			for(int label = 0; label < labels.getNumberOfLabels(); label++) {
				Span newParent;
				if(addingToRight)
					newParent = new Span(sibling.getStart(), end, start, label, sibling.getRule().getParent(), toMove.getRule().getParent());
				else
					newParent = new Span(start, sibling.getEnd(), end, label, toMove.getRule().getParent(), sibling.getRule().getParent());
				
				spans.set(parentIndex, newParent);
				
				if(grandparentIndex != -1) {
					Span gp = spans.get(grandparentIndex);
					Span newGp = gp.changeChildLabel(sibling.getStart() < gp.getSplit(), label);
					spans.set(grandparentIndex, newGp);
				}
				SpanPrint.printSpans(spans, words.size());
				
				double score = score(words, spans, params, dropout);
				if(score > bestScore) {
					bestScore = score;
					best = new ArrayList<>(spans);
				}
			}
		}
		
		return best;
	}
	
	private int[] getParents(List<Span> spans) {
		int[] result = new int[spans.size()];
		for(int i = 0; i < result.length; i++) {
			Span s = spans.get(i);
			result[i] = -1;
			for(int j = 0; j < result.length; j++) {
				Span p = spans.get(j);
				if(p.getRule().getType() == Type.BINARY && ((p.getStart() == s.getStart() && p.getSplit() == s.getEnd()) || (p.getSplit() == s.getStart() && p.getEnd() == s.getEnd()))) {
					result[i] = j;
				}
			}
		}
		return result;
	}
	
	private double score(List<Integer> words, List<Span> spans, FeatureParameters params, boolean dropout) {
		double score = 0;
		for(Span s : spans) {
			if(!rules.isExistingRule(s.getRule()))
				return Double.NEGATIVE_INFINITY;
		}
		for(Span s : spans) {
			TLongList featureCodes = Features.getSpanPropertyByRuleFeatures(words, s, rules, wordEnum);
			for(int i = 0; i < featureCodes.size(); i++) {
				score += params.getScore(featureCodes.get(i), dropout);
			}
			score += params.getScore(Features.getRuleFeature(s.getRule(), rules), dropout);
			TLongList propertyByLabelCodes = Features.getSpanPropertyByLabelFeatures(words, s);
			for(int i = 0; i < propertyByLabelCodes.size(); i++) {
				score += params.getScore(propertyByLabelCodes.get(i), dropout);
			}
		}
		return score;
	}
}
