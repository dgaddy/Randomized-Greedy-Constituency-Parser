package constituencyParser.features;

import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;

import java.util.List;

import constituencyParser.Word;
import constituencyParser.WordEnumeration;

/**
 * Functions in this class return 32 bit codes
 * bits 31-28  from end are an id for the type of property 
 * word property: 0001
 * length property: 0010
 * prefix/sufix property: 0011
 */
public class SpanProperties {
	
	/**
	 * Returns 32 bit span properties
	 * @param words
	 * @param location
	 * @return
	 */
	public static TLongList getTerminalSpanProperties(List<Word> words, int location, WordEnumeration wordEnum) {
		TLongList properties = new TLongArrayList();
		
		properties.add(getWordPropertyCode(words.get(location), WordPropertyType.FIRST));
		if(location > 0)
			properties.add(getWordPropertyCode(words.get(location - 1), WordPropertyType.BEFORE));
		
		if(location + 1 < words.size())
			properties.add(getWordPropertyCode(words.get(location + 1), WordPropertyType.AFTER));
		
		
		//System.out.println(words.get(location).getPrefixIds());
		for(int prefix : words.get(location).getPrefixIds()) {
			properties.add(getPrefixPropertyCode(prefix));
		}
		
		for(int suffix : words.get(location).getSuffixIds()) {
			properties.add(getSuffixPropertyCode(suffix));
		}
		
		return properties;
	}
	
	public static TLongList getUnarySpanProperties(List<Word> words, int start, int end) {
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
	
	public static TLongList getBinarySpanProperties(List<Word> words, int start, int end, int split) {
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
	
	public enum WordPropertyType {
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
	public static long getWordPropertyCode(Word word, WordPropertyType type) {
		return ((1L << 28L) | (((long)type.ordinal()) << 24L)) | word.getId();
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
		
		return (2L << 28L) | length;
	}
	
	public static long getPrefixPropertyCode(int prefix) {
		return ((3L << 28L) | (1L << 24L)) | prefix;
	}
	
	public static long getSuffixPropertyCode(int suffix) {
		return ((3L << 28L) | (2L << 24L)) | suffix;
	}
	
	public static String getSpanPropertyCodeString(long code, WordEnumeration words) {
		int type = (int) (code >> 28L);
		if(type == 1) {
			int wordId = (int) (code % (1L << 24L));
			int typeOrdinal = (int) ((code % (1L<<28L)) >> 24L);
			WordPropertyType propType = WordPropertyType.values()[typeOrdinal];
			String word = words.getWordForId(wordId);
			return "Word " + propType + " " + word;
		}
		else if(type == 2) {
			return "Length " + (code % (1L<<28L));
		}
		else if(type == 3) {
			int pfxorsfx = (int) ((code % (1L<<28L)) >> 24L);
			if(pfxorsfx == 1)
				return "Prefix " + words.getPrefixForId((int)(code % (1L << 24L)));
			else if(pfxorsfx == 2)
				return "Suffix " + words.getSuffixForId((int)(code % (1L << 24L)));
			else
				return "invalid prefix/suffix";
		}
		else
			return "invalid";
	}
}
