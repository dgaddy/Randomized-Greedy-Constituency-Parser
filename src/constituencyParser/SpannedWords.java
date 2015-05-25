package constituencyParser;
import java.util.ArrayList;
import java.util.List;


/**
 * Contains a list of words in a sentence and a parse tree in the form of a list of Spans
 */
public class SpannedWords {
	private List<Span> spans = new ArrayList<>();
	private List<Word> words = new ArrayList<>();
	
	public SpannedWords(List<Span> spans, List<Word> words) {
		this.spans = spans;
		this.words = words;
	}
	
	// These constructors are used to build a span representation from a tree representation
	
	public SpannedWords(Word word) {
		words.add(word);
	}
	
	/**
	 * Make a new terminal rule
	 * @param child
	 * @param label
	 */
	public SpannedWords(SpannedWords child, int label) {
		words.addAll(child.words);
		spans.addAll(child.spans);
		
		spans.add(new Span(0, label));
	}
	
	/**
	 * Put child under a unary rule
	 * @param child
	 * @param label
	 * @param childLabel
	 */
	public SpannedWords(SpannedWords child, int label, int childLabel) {
		words.addAll(child.words);
		spans.addAll(child.spans);
		
		spans.add(new Span(0, words.size(), label, childLabel));
	}
	
	/**
	 * Combines left and right under a new binary rule
	 * @param left
	 * @param right
	 * @param label
	 * @param leftLabel
	 * @param rightLabel
	 */
	public SpannedWords(SpannedWords left, SpannedWords right, int label, int leftLabel, int rightLabel, boolean leftHead) {
		words.addAll(left.words);
		words.addAll(right.words);
		
		spans.addAll(left.spans);
		int shift = left.words.size();
		for(Span s : right.spans) {
			s.shiftRight(shift);
		}
		spans.addAll(right.getSpans());
		
		spans.add(new Span(0, words.size(), shift, label, leftLabel, rightLabel, leftHead));
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
	public List<Word> getWords() {
		return words;
	}
	
	public String toString() {
		return "Part: " + words + " " + spans;
	}
}
