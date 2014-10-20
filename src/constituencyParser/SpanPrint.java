package constituencyParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class SpanPrint {
	public static void printSpans(final List<Span> spans, int numberOfWords) {
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
			for(List<Integer> inner : list) {
				if(index < inner.size()) {
					System.out.print(inner.get(index));
					finished = false;
				}
				else
					System.out.print(' ');
			}
			System.out.println();
			index++;
		}
	}
}
