package constituencyParser;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class WordEnumeration implements Serializable {
	private static final long serialVersionUID = 1L;
	
	List<String> idToWord = new ArrayList<>();
	HashMap<String, Integer> wordToId = new HashMap<>();
	
	List<String> idToPrefix = new ArrayList<>();
	HashMap<String, Integer> prefixToId = new HashMap<>();
	
	List<String> idToSuffix = new ArrayList<>();
	HashMap<String, Integer> suffixToId = new HashMap<>();
	
	List<List<Integer>> suffixes = new ArrayList<>();
	List<List<Integer>> prefixes = new ArrayList<>();
	
	public WordEnumeration() {
		
	}
	
	public WordEnumeration(WordEnumeration other) {
		this.idToWord = new ArrayList<>(other.idToWord);
		this.wordToId = new HashMap<>(other.wordToId);
		
		idToPrefix = new ArrayList<>(other.idToPrefix);
		prefixToId = new HashMap<>(other.prefixToId);
		
		idToSuffix = new ArrayList<>(other.idToSuffix);
		suffixToId = new HashMap<>(other.suffixToId);
		
		suffixes = new ArrayList<>(other.suffixes);
		prefixes = new ArrayList<>(other.prefixes);
	}
	
	/**
	 * Adds word if not already added
	 * @param word
	 */
	public void addWord(String word) {
		if(wordToId.containsKey(word))
			return;
		
		List<Integer> pfxs = new ArrayList<>();
		for(int i = 1; i <= 5 && i <= word.length(); i++) {
			String pfx = word.substring(0, i);
			int id = getOrAddPrefix(pfx);
			pfxs.add(id);
		}
		
		List<Integer> sfxs = new ArrayList<>();
		for(int i = Math.max(word.length() - 5, 0); i < word.length(); i++) {
			String sfx = word.substring(i, word.length());
			int id = getOrAddSuffix(sfx);
			sfxs.add(id);
		}
		
		wordToId.put(word, idToWord.size());
		idToWord.add(word);
		prefixes.add(pfxs);
		suffixes.add(sfxs);
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
	
	private int getOrAddPrefix(String prefix) {
		if(prefixToId.containsKey(prefix))
			return prefixToId.get(prefix);
		else {
			int id = idToPrefix.size();
			idToPrefix.add(prefix);
			prefixToId.put(prefix, id);
			return id;
		}
	}
	
	private int getOrAddSuffix(String suffix) {
		if(suffixToId.containsKey(suffix))
			return suffixToId.get(suffix);
		else {
			int id = idToSuffix.size();
			idToSuffix.add(suffix);
			suffixToId.put(suffix, id);
			return id;
		}
	}
	
	public List<Integer> getPrefixes(int word) {
		return prefixes.get(word);
	}
	
	public List<Integer> getSuffixes(int word) {
		return suffixes.get(word);
	}
	
	public String getSuffixForId(int id) {
		return idToSuffix.get(id);
	}
	
	public String getPrefixForId(int id) {
		return idToPrefix.get(id);
	}
}
