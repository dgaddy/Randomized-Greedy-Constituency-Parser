package constituencyParser;

public class WordPOS {
	String word;
	String POS;
	
	public WordPOS(String w, String p) {
		word = w;
		POS = p;
	}
	
	public int hashCode() {
		return word.hashCode() + POS.hashCode() * 3;
	}
	
	public boolean equals(Object other) {
		if(!(other instanceof WordPOS))
			return false;
		WordPOS otherW = (WordPOS)other;
		return otherW.POS.equals(POS) && otherW.word.equals(word);
	}
}
