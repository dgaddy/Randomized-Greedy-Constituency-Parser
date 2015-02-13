package constituencyParser;

import java.util.List;

public class Word {
	private String word;
	private int id;
	private List<Integer> suffixIds;
	private List<Integer> prefixIds;
	
	public Word(String word, int id, List<Integer> prefixIds, List<Integer> suffixIds) {
		this.word = word;
		this.id = id;
		this.suffixIds = suffixIds;
		this.prefixIds = prefixIds;
	}
	
	public String getWord() {
		return word;
	}
	
	public int getId() {
		return id;
	}
	
	public List<Integer> getSuffixIds() {
		return suffixIds;
	}
	
	public List<Integer> getPrefixIds() {
		return prefixIds;
	}
}
