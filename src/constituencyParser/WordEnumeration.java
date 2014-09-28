package constituencyParser;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class WordEnumeration implements Serializable {
	private static final long serialVersionUID = 1L;
	
	List<String> idToWord = new ArrayList<>();
	HashMap<String, Integer> wordToId = new HashMap<>();
	
	public WordEnumeration() {
		
	}
	
	public WordEnumeration(WordEnumeration other) {
		this.idToWord = new ArrayList<>(other.idToWord);
		this.wordToId = new HashMap<>(other.wordToId);
	}
	
	/**
	 * Adds word if not already added
	 * @param word
	 */
	public void addWord(String word) {
		if(wordToId.containsKey(word))
			return;
		
		wordToId.put(word, idToWord.size());
		idToWord.add(word);
	}
	
	public void addAllWords(List<String> words) {
		for(String word : words) {
			addWord(word);
		}
	}
	
	public String getWord(int id) {
		return idToWord.get(id);
	}
	
	public int getId(String word) {
		return wordToId.get(word);
	}
	
	public int getNumberOfWords() {
		return idToWord.size();
	}
}
