package constituencyParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import constituencyParser.Rule.Type;

public class SpanUtilities {
	/**
	 * Prints spans for a sentence in a way that is relatively easier to read
	 * @param spans
	 * @param numberOfWords
	 * @param labels
	 */
	public static void printSpans(final List<Span> spans, int numberOfWords, LabelEnumeration labels) {
		List<List<Integer>> list = new ArrayList<>();
		for(int i = 0; i < numberOfWords; i++) {
			list.add(new ArrayList<Integer>());
		}
		for(int s = 0; s < spans.size(); s++) {
			Span span = spans.get(s);
			for(int i = span.getStart(); i < span.getEnd(); i++) {
				list.get(i).add(s);
			}
		}
		for(List<Integer> inner : list) {
			Collections.sort(inner, new Comparator<Integer>() {
				@Override
				public int compare(Integer arg0, Integer arg1) {
					Span s0 = spans.get(arg0);
					Span s1 = spans.get(arg1);
					return (s0.getEnd() - s0.getStart()) - (s1.getEnd() - s1.getStart());
				}
			});
			
			Collections.reverse(inner);
		}
		
		boolean finished = false;
		int index = 0;
		while(!finished) {
			finished = true;
			int last = -1;
			for(List<Integer> inner : list) {
				if(index < inner.size()) {
					int spanIndex = inner.get(index);
					if(spanIndex == last)
						System.out.print("-------->");
					else {
						last = spanIndex;
						Span s = spans.get(spanIndex);
						System.out.print(String.format("<%-7s>", labels.getLabel(s.getRule().getLabel())));
						finished = false;
					}
				}
				else
					System.out.print("         ");
			}
			System.out.println();
			index++;
		}
	}
	
	/**
	 * Throws exception if spans are not well formed
	 */
	public static void checkCorrectness(List<Span> spans) {
		int[] parents = getParents(spans);
		
		int topLevelCount = 0;
		for(int i = 0; i < spans.size(); i++) {
			Span s = spans.get(i);
			int pi = parents[i];
			if(pi == -1) {
				topLevelCount++;
				continue;
			}
			Span p = spans.get(pi);
			switch(p.getRule().getType()) {
			case UNARY:
				if(p.getRule().getLeft() != s.getRule().getLabel())
					throw new RuntimeException("Parent labels don't match.");
				if(p.getStart() != s.getStart() || p.getEnd() != s.getEnd())
					throw new RuntimeException("Parent dimensions do not match.");
				break;
			case BINARY:
				boolean left = p.getSplit() > s.getStart();
				if(left) {
					if(p.getRule().getLeft() != s.getRule().getLabel())
						throw new RuntimeException("Parent labels don't match.");
					if(p.getStart() != s.getStart() || p.getSplit() != s.getEnd())
						throw new RuntimeException("Parent dimensions do not match.");
				}
				else {
					if(p.getRule().getRight() != s.getRule().getLabel())
						throw new RuntimeException("Parent labels don't match.");
					if(p.getSplit() != s.getStart() || p.getEnd() != s.getEnd())
						throw new RuntimeException("Parent dimensions do not match.");
				}
				break;
			case TERMINAL:
				throw new RuntimeException("Terminal cannot be a parent.");
			}
		}
		if(topLevelCount != 1)
			throw new RuntimeException("Top level count is not 1, it is " + topLevelCount);
	}
	
	/**
	 * Returns an int[] where each element is the index of the parent span in spans or -1 if no parent
	 * @param spans
	 * @return
	 */
	public static int[] getParents(List<Span> spans) {
		int[] result = new int[spans.size()];
		for(int i = 0; i < result.length; i++) {
			Span s = spans.get(i);
			result[i] = -1;
			// connect to a unary if possible
			for(int j = 0; j < result.length; j++) {
				if(i==j)
					continue;
				
				Span p = spans.get(j);
				if(p.getRule().getType() == Type.UNARY) {
					if(p.getStart() == s.getStart() && p.getEnd() == s.getEnd() && p.getRule().getLeft() == s.getRule().getLabel())
						result[i] = j;
				}
			}
			
			if(result[i] != -1)
				continue;
			for(int j = 0; j < result.length; j++) {
				if(i == j)
					continue;
				
				Span p = spans.get(j);
				if(p.getRule().getType() == Type.BINARY) {
					if((p.getStart() == s.getStart() && p.getSplit() == s.getEnd() && p.getRule().getLeft() == s.getRule().getLabel())
							|| (p.getSplit() == s.getStart() && p.getEnd() == s.getEnd() && p.getRule().getRight() == s.getRule().getLabel())) {
						result[i] = j;
					}
				}
			}
		}
		return result;
	}
	
	public static boolean usesOnlyExistingRules(List<Span> spans, RuleEnumeration rules) {
		for(Span s : spans) {
			if(!rules.isExistingRule(s.getRule()))
				return false;
		}
		return true;
	}
}
