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
	
	static final String NUMBER_REGEX = "[0-9]+|[0-9]+[0-9,]*\\.[0-9]+|[0-9]+[0-9,]+";
	
	boolean useSuffixes;
	int rareWordCutoff;
	
	HashMap<String, Integer> trainingSuffixCounts;
	
	List<String> idToWord = new ArrayList<>();
	HashMap<String, Integer> wordToId = new HashMap<>(); // if word is most common suffix, will be "-ffix"
	
	List<String> idToPrefix = new ArrayList<>();
	HashMap<String, Integer> prefixToId = new HashMap<>();
	
	List<String> idToSuffix = new ArrayList<>();
	HashMap<String, Integer> suffixToId = new HashMap<>();
	
	List<List<Integer>> partsOfSpeech = new ArrayList<>();
	
	public WordEnumeration(boolean useSuffixes, int rareWordCutoff) {
		this.useSuffixes = useSuffixes;
		this.rareWordCutoff = rareWordCutoff;
		
		getOrAddWordId("-UNK-");
	}
	
	public WordEnumeration(WordEnumeration other) {
		this.useSuffixes = other.useSuffixes;
		this.rareWordCutoff = other.rareWordCutoff;
		trainingSuffixCounts = new HashMap<>(other.trainingSuffixCounts);
		
		this.idToWord = new ArrayList<>(other.idToWord);
		this.wordToId = new HashMap<>(other.wordToId);
		
		idToPrefix = new ArrayList<>(other.idToPrefix);
		prefixToId = new HashMap<>(other.prefixToId);
		
		idToSuffix = new ArrayList<>(other.idToSuffix);
		suffixToId = new HashMap<>(other.suffixToId);
		
		partsOfSpeech = new ArrayList<>(other.partsOfSpeech);
	}
	
	/**
	 * 
	 * @param words A map from all words in training set to number of times they occur
	 */
	public void addTrainingWords(Map<String, Integer> wordCounts, Map<WordPOS, Integer> labelCounts, LabelEnumeration labels) {
		if(trainingSuffixCounts != null) {
			System.out.println("Warning: not adding words to existing model to prevent repeats, should only call addTrainingWords once");
		}
		else {
			trainingSuffixCounts = new HashMap<String, Integer>();
			
			if(useSuffixes) {
				for(Entry<String, Integer> entry : wordCounts.entrySet()) {
					for(String suffix : getAllSuffixes(entry.getKey())) {
						Integer currentCount = trainingSuffixCounts.get(suffix);
						if(currentCount == null)
							trainingSuffixCounts.put(suffix, entry.getValue());
						else
							trainingSuffixCounts.put(suffix, currentCount+entry.getValue());
					}
				}
				
				
			}
			for(Entry<String, Integer> entry : wordCounts.entrySet()) {
				if(entry.getValue() >= rareWordCutoff) {
					getOrAddWordId(entry.getKey()); // this should be the only place we add full words; the only other things that can get added are suffixes and UNK
				}
			}
			for(Entry<WordPOS, Integer> entry : labelCounts.entrySet()) {
				String word = entry.getKey().word;
				int pos = labels.getId(entry.getKey().POS);
				int id = getWord(word).getId();
				List<Integer> poss = partsOfSpeech.get(id);
				if(!poss.contains(pos))
					poss.add(pos);
			}
			for(int i = 0; i < partsOfSpeech.size(); i++) { // TODO what happens when rare word cutoff is 0 or when using suffixes
				List<Integer> pos = partsOfSpeech.get(i);
				
				if(pos.size() == 0)
					throw new RuntimeException();
			}
		}
	}
	
	public List<Integer> getPartsOfSpeech(Word word) {
		return partsOfSpeech.get(word.getId());
	}
	
	public Word getWord(String word) {
		int id = getOrAddWordId("-UNK-");
		if(word.matches(NUMBER_REGEX))
			id = getOrAddWordId("-NUM-");
		else if(wordToId.containsKey(word))
			id = wordToId.get(word);
		else if(useSuffixes){
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
		if(word.matches(NUMBER_REGEX))
			return getOrAddWordId("-NUM-");
		if(wordToId.containsKey(word))
			return wordToId.get(word);
		else {
			int id = idToWord.size();
			wordToId.put(word, id);
			idToWord.add(word);
			partsOfSpeech.add(new ArrayList<>());
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
	
	/**
	 * Should only be called for training data, not test data
	 * @param wordEnum
	 */
	public static void loadWordsFromTrainingData(WordEnumeration wordEnum, LabelEnumeration labels, List<TreeNode> examples) {
		HashMap<String, Integer> wordCounts = new HashMap<>();
		HashMap<WordPOS, Integer> wordPOSCounts = new HashMap<>();
		for(TreeNode tree : examples) {
			List<String> ws = tree.getAllWords();
			for(String word : ws) {
				Integer count = wordCounts.get(word);
				if(count == null)
					wordCounts.put(word, 1);
				else
					wordCounts.put(word, count+1);
			}
			List<WordPOS> poss = tree.getWordsWithPOS();
			for(WordPOS word : poss) {
				Integer count = wordPOSCounts.get(word);
				if(count == null)
					wordPOSCounts.put(word, 1);
				else
					wordPOSCounts.put(word, count+1);
			}
			if(poss.size() != ws.size())
				throw new RuntimeException();
		}
		
		wordEnum.addTrainingWords(wordCounts, wordPOSCounts, labels);
	}
}
