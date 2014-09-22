package constituencyParser.features;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import constituencyParser.LabelEnumeration;
import constituencyParser.Span;
import constituencyParser.features.BasicFeatures.WordProperty.Type;
import constituencyParser.features.Feature.SpanProperty;
import constituencyParser.Rule;


public class BasicFeatures {
	public static List<SpanProperty> getTerminalSpanProperties(List<String> words, int location) {
		List<SpanProperty> properties = new ArrayList<>(7);
		
		properties.add(new LengthProperty(1));
		
		properties.add(new WordProperty(words.get(location), Type.FIRST));
		if(location > 0)
			properties.add(new WordProperty(words.get(location - 1), Type.BEFORE));
		
		if(location + 1 < words.size())
			properties.add(new WordProperty(words.get(location + 1), Type.AFTER));
		
		return properties;
	}
	
	public static List<SpanProperty> getUnarySpanProperties(List<String> words, int start, int end) {
		List<SpanProperty> properties = new ArrayList<>(7);
		
		properties.add(new LengthProperty(end - start));
		
		properties.add(new WordProperty(words.get(start), Type.FIRST));
		if(start > 0)
			properties.add(new WordProperty(words.get(start - 1), Type.BEFORE));
		
		if(end < words.size())
			properties.add(new WordProperty(words.get(end), Type.AFTER));
			
		properties.add(new WordProperty(words.get(end - 1), Type.LAST));
		
		return properties;
	}
	
	public static List<SpanProperty> getBinarySpanProperties(List<String> words, int start, int end, int split) {
		List<SpanProperty> properties = new ArrayList<>(7);
		
		properties.add(new LengthProperty(end - start));
		
		properties.add(new WordProperty(words.get(start), Type.FIRST));
		if(start > 0)
			properties.add(new WordProperty(words.get(start - 1), Type.BEFORE));
		
		if(end < words.size())
			properties.add(new WordProperty(words.get(end), Type.AFTER));
			
		properties.add(new WordProperty(words.get(end - 1), Type.LAST));
		
		properties.add(new WordProperty(words.get(split - 1), Type.BEFORE_SPLIT));
		properties.add(new WordProperty(words.get(split), Type.AFTER_SPLIT));
		
		return properties;
	}
	
	public static List<Feature> getSpanFeatures(List<String> words, Span span, LabelEnumeration labels) {
		List<Feature> result = new ArrayList<Feature>();
		if(span.getRule().getType() == Rule.Type.TERMINAL) {
			List<SpanProperty> properties = getTerminalSpanProperties(words, span.getStart());
			for(SpanProperty sp : properties) {
				result.add(new Feature(span.getRule(), sp));
			}
		}
		else if(span.getRule().getType() == Rule.Type.UNARY) {
			List<SpanProperty> properties = getUnarySpanProperties(words, span.getStart(), span.getEnd());
			for(SpanProperty sp : properties) {
				result.add(new Feature(span.getRule(), sp));
			}
		}
		else {
			List<SpanProperty> properties = getBinarySpanProperties(words, span.getStart(), span.getEnd(), span.getSplit());
			for(SpanProperty sp : properties) {
				result.add(new Feature(span.getRule(), sp));
			}
		}
		return result;
	}
	
	public static class WordProperty extends SpanProperty implements Serializable {
		private static final long serialVersionUID = 1L;
		private String word;
		private Type type;
		enum Type {
			FIRST,
			LAST,
			BEFORE,
			AFTER,
			BEFORE_SPLIT,
			AFTER_SPLIT
		}
		
		public WordProperty(String word, Type type) {
			this.word = word;
			this.type = type;
		}
		
		public boolean equals(Object other) {
			if(other instanceof WordProperty) {
				WordProperty f = (WordProperty)other;
				return f.type.equals(type) && f.word.equals(word);
			}
			return false;
		}
		
		public int hashCode() {
			return 503*word.hashCode() + 509*type.hashCode();
		}
	}
	
	public static class LengthProperty extends SpanProperty implements Serializable {
		private static final long serialVersionUID = 1L;
		Bin binnedLength;
		enum Bin { //bins are: 1, 2, 3, 4, 5, 10, 20, >20
			ONE,
			TWO,
			THREE,
			FOUR,
			FIVE,
			TEN,
			TWENTY,
			LONG
		}
		
		public LengthProperty(int length) {
			if(length <= 5) {
				switch(length) {
				case 1:
					binnedLength = Bin.ONE;
					break;
				case 2:
					binnedLength = Bin.TWO;
					break;
				case 3:
					binnedLength = Bin.THREE;
					break;
				case 4:
					binnedLength = Bin.FOUR;
					break;
				case 5:
					binnedLength = Bin.FIVE;
					break;
				}
			}
			else if(length <= 10)
				binnedLength = Bin.TEN;
			else if(length <= 20)
				binnedLength = Bin.TWENTY;
			else
				binnedLength = Bin.LONG;
		}
		
		public boolean equals(Object other) {
			if(other instanceof LengthProperty) {
				LengthProperty l = (LengthProperty) other;
				return l.binnedLength == binnedLength;
			}
			return false;
		}
		
		public int hashCode() {
			return 521 * binnedLength.hashCode();
		}
	}
}
