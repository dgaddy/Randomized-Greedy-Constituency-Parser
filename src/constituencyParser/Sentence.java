package constituencyParser;

import java.util.List;

public class Sentence {
	private List<String> words;
	private List<Span> spans;
	
	Span[][] spanLocations;
	
	public Sentence(SpannedWords part) {
		this.words = part.getWords();
		this.spans = part.getSpans();
		
		int size = words.size() + 1;
		spanLocations = new Span[size][size];
		
		for(Span s : spans) {
			spanLocations[s.getStart()][s.getEnd()] = s;
		}
	}
	
	public Span getSpan(int start, int end) {
		return spanLocations[start][end];
	}
	
	public String getWord(int location) {
		return words.get(location);
	}
}
