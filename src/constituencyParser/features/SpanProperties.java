package constituencyParser.features;

import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;

import java.util.List;

import constituencyParser.WordEnumeration;


public class SpanProperties {
	
	/*
	 * these functions return 32 bit codes
	 * bits 31-28  from end are an id for the type of property 
	 * word property: 0001
	 * length property: 0010
	 */
	
	/**
	 * Returns 32 bit span properties
	 * @param words
	 * @param location
	 * @return
	 */
	public static TLongList getTerminalSpanProperties(List<Integer> words, int location) {
		TLongList properties = new TLongArrayList();
		
		properties.add(getWordPropertyCode(words.get(location), WordPropertyType.FIRST));
		if(location > 0)
			properties.add(getWordPropertyCode(words.get(location - 1), WordPropertyType.BEFORE));
		
		if(location + 1 < words.size())
			properties.add(getWordPropertyCode(words.get(location + 1), WordPropertyType.AFTER));
		
		return properties;
	}
	
	public static TLongList getUnarySpanProperties(List<Integer> words, int start, int end) {
		TLongList properties = new TLongArrayList();
		
		properties.add(getLengthPropertyCode(end - start));
		
		properties.add(getWordPropertyCode(words.get(start), WordPropertyType.FIRST));
		if(start > 0)
			properties.add(getWordPropertyCode(words.get(start - 1), WordPropertyType.BEFORE));
		
		if(end < words.size())
			properties.add(getWordPropertyCode(words.get(end), WordPropertyType.AFTER));
			
		properties.add(getWordPropertyCode(words.get(end - 1), WordPropertyType.LAST));
		
		return properties;
	}
	
	public static TLongList getBinarySpanProperties(List<Integer> words, int start, int end, int split) {
		TLongList properties = new TLongArrayList();
		
		properties.add(getLengthPropertyCode(end - start));
		
		properties.add(getWordPropertyCode(words.get(start), WordPropertyType.FIRST));
		if(start > 0)
			properties.add(getWordPropertyCode(words.get(start - 1), WordPropertyType.BEFORE));
		
		if(end < words.size())
			properties.add(getWordPropertyCode(words.get(end), WordPropertyType.AFTER));
			
		properties.add(getWordPropertyCode(words.get(end - 1), WordPropertyType.LAST));
		
		properties.add(getWordPropertyCode(words.get(split - 1), WordPropertyType.BEFORE_SPLIT));
		properties.add(getWordPropertyCode(words.get(split), WordPropertyType.AFTER_SPLIT));
		
		return properties;
	}
	
	enum WordPropertyType {
		FIRST,
		LAST,
		BEFORE,
		AFTER,
		BEFORE_SPLIT,
		AFTER_SPLIT
	}
	
	/**
	 * 32 bit id for word span properties
	 * @param word
	 * @param type
	 * @return
	 */
	public static long getWordPropertyCode(int word, WordPropertyType type) {
		return (1L << 28L) + (type.ordinal() << 24L) + word;
	}
	
	/**
	 * 32 bit id binned length code
	 * @param length
	 * @return
	 */
	public static long getLengthPropertyCode(int length) {
		if(length <= 5)
			; // keep actual length
		else if(length <= 10)
			length = 6;
		else if(length <= 20)
			length = 7;
		else
			length = 8;
		
		return (2L << 28L) + length;
	}
	
	public static String getSpanPropertyCodeString(long code, WordEnumeration words) {
		int type = (int) (code >> 28L);
		if(type == 1) {
			int wordId = (int) (code % (1L << 24L));
			int typeOrdinal = (int) ((code % (1L<<28L)) >> 24L);
			WordPropertyType propType = WordPropertyType.values()[typeOrdinal];
			String word = words.getWord(wordId);
			return "Word " + propType + " " + word;
		}
		else if(type == 2) {
			return "Length " + (code % (1L<<28L));
		}
		else
			return "invalid";
	}
}
