package constituencyParser;
import java.util.ArrayList;
import java.util.List;


public class SpannedWords { // TODO: better name
	private List<Span> spans = new ArrayList<>();
	private List<Integer> words = new ArrayList<>();
	
	public SpannedWords(int word) {
		words.add(word);
	}
	
	public SpannedWords(SpannedWords child, int label) {
		words.addAll(child.words);
		spans.addAll(child.spans);
		
		spans.add(new Span(0, label));
	}
	
	public SpannedWords(SpannedWords child, int label, int childLabel) {
		words.addAll(child.words);
		spans.addAll(child.spans);
		
		spans.add(new Span(0, words.size(), label, childLabel));
	}
	
	public SpannedWords(SpannedWords left, SpannedWords right, int label, int leftLabel, int rightLabel) {
		words.addAll(left.words);
		words.addAll(right.words);
		
		spans.addAll(left.spans);
		int shift = left.words.size();
		for(Span s : right.spans) {
			s.shiftRight(shift);
		}
		spans.addAll(right.getSpans());
		
		spans.add(new Span(0, words.size(), shift, label, leftLabel, rightLabel));
	}
	
	/**
	 * Note: editing result will mutate this part
	 * @return
	 */
	public List<Span> getSpans() {
		return spans;
	}
	
	/**
	 * Note: editing result will mutate this part
	 * @return
	 */
	public List<Integer> getWords() {
		return words;
	}
	
	public String toString() {
		return "Part: " + words + " " + spans;
	}
}
