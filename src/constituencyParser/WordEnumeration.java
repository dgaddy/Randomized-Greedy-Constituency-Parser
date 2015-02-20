package constituencyParser;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class WordEnumeration implements Serializable {
	private static final long serialVersionUID = 1L;
	
	HashMap<String, Integer> trainingSuffixCounts;
	
	List<String> idToWord = new ArrayList<>();
	HashMap<String, Integer> wordToId = new HashMap<>(); // if word is most common suffix, will be "-ffix"
	
	List<String> idToPrefix = new ArrayList<>();
	HashMap<String, Integer> prefixToId = new HashMap<>();
	
	List<String> idToSuffix = new ArrayList<>();
	HashMap<String, Integer> suffixToId = new HashMap<>();
	
	public WordEnumeration() {
		getOrAddWordId("-UNK-");
	}
	
	public WordEnumeration(WordEnumeration other) {
		trainingSuffixCounts = new HashMap<>(other.trainingSuffixCounts);
		
		this.idToWord = new ArrayList<>(other.idToWord);
		this.wordToId = new HashMap<>(other.wordToId);
		
		idToPrefix = new ArrayList<>(other.idToPrefix);
		prefixToId = new HashMap<>(other.prefixToId);
		
		idToSuffix = new ArrayList<>(other.idToSuffix);
		suffixToId = new HashMap<>(other.suffixToId);
	}
	
	/**
	 * 
	 * @param words A map from all words in training set to number of times they occur
	 */
	public void addTrainingWords(Map<String, Integer> wordCounts) {
		if(trainingSuffixCounts != null) {
			System.out.println("Warning: not adding words to existing model to prevent repeats, should only call addTrainingWords once");
		}
		else {
			trainingSuffixCounts = new HashMap<String, Integer>();
			
			for(Entry<String, Integer> entry : wordCounts.entrySet()) {
				for(String suffix : getAllSuffixes(entry.getKey())) {
					Integer currentCount = trainingSuffixCounts.get(suffix);
					if(currentCount == null)
						trainingSuffixCounts.put(suffix, entry.getValue());
					else
						trainingSuffixCounts.put(suffix, currentCount+entry.getValue());
				}
			}
			
			for(Entry<String, Integer> entry : wordCounts.entrySet()) {
				if(entry.getValue() >= 100) {
					getOrAddWordId(entry.getKey()); // this should be the only place we add full words; the only other things that can get added are suffixes and UNK
				}
			}
		}
	}
	
	public Word getWord(String word) {
		int id = getOrAddWordId("-UNK-");
		if(word.matches("[0-9]+|[0-9]+[0-9,]*\\.[0-9]+|[0-9]+[0-9,]+"))
			id = getOrAddWordId("-NUM-");
		else if(wordToId.containsKey(word))
			id = wordToId.get(word);
		else {
			for(String suffix : getAllSuffixes(word)) { // getAllSuffixes starts with largest (including full word)
				Integer count = trainingSuffixCounts.get(suffix);
				if(count != null && count >= 100) {
					id =  getOrAddWordId("-" + suffix);
					break;
				}
			}
		}
		return new Word(word, id, getPrefixIds(word), getSuffixIds(word));
	}
	
	/**
	 * In order from largest to smallest
	 * @param word
	 * @return
	 */
	private List<String> getAllSuffixes(String word) {
		List<String> result = new ArrayList<>();
		for(int i = 0; i < word.length(); i++) {
			result.add(word.substring(i));
		}
		return result;
	}
	
	/**
	 * Get the ids of 5 prefixes
	 * @param word
	 * @param number
	 * @return
	 */
	private List<Integer> getPrefixIds(String word) {
		List<Integer> result = new ArrayList<>();
		for(int i = 1; i <= word.length() && result.size() < 5; i++) {
			result.add(getOrAddPrefix(word.substring(0,i)));
		}
		return result;
	}
	
	/**
	 * Get the ids of 5 suffixes
	 * @param word
	 * @param number
	 * @return
	 */
	private List<Integer> getSuffixIds(String word) {
		List<Integer> result = new ArrayList<>();
		for(int i = word.length() - 1; i >= 0 && result.size() < 5; i--) {
			result.add(getOrAddSuffix(word.substring(i)));
		}
		return result;
	}
	
	private int getOrAddWordId(String word) {
		if(wordToId.containsKey(word))
			return wordToId.get(word);
		else {
			int id = idToWord.size();
			wordToId.put(word, id);
			idToWord.add(word);
			return id;
		}
	}
	
	public String getWordForId(int id) {
		return idToWord.get(id);
	}
	
	public List<Word> getWords(List<String> words) {
		List<Word> result = new ArrayList<>();
		for(String word : words) {
			result.add(getWord(word));
		}
		return result;
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
	
	public String getSuffixForId(int id) {
		return idToSuffix.get(id);
	}
	
	public String getPrefixForId(int id) {
		return idToPrefix.get(id);
	}
	
	public void shuffleWordIds() {
		List<String> words = idToWord;
		idToWord = new ArrayList<>();
		wordToId = new HashMap<>();
		
		Collections.shuffle(words);
		for(String word : words)
			getOrAddWordId(word);
		
		List<String> prefixes = idToPrefix;
		idToPrefix = new ArrayList<>();
		prefixToId = new HashMap<>();
		
		Collections.shuffle(prefixes);
		for(String prefix : prefixes)
			getOrAddPrefix(prefix);
		
		List<String> suffixes = idToSuffix;
		idToSuffix = new ArrayList<>();
		suffixToId = new HashMap<>();
		
		Collections.shuffle(suffixes);
		for(String suffix : suffixes)
			getOrAddSuffix(suffix);
	}
}
