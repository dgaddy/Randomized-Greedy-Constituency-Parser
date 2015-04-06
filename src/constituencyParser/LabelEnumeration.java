package constituencyParser;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LabelEnumeration implements Serializable {
	private static final long serialVersionUID = 1L;
	
	List<String> idToLabel = new ArrayList<>();
	List<Integer> idToExtendLabel = new ArrayList<>(); // extend label meaning the -BAR version when we binarize
	HashMap<String, Integer> labelToId = new HashMap<>();
	
	HashSet<Integer> topLevelLabels = new HashSet<>();
	
	String[] punctuation = new String[] {"''", ":", "#", ",", ".", "``", "-LRB-", "-", "-RRB-"};
	String[] conjunctions = new String[] {"CC", "CONJP"};
	
	public LabelEnumeration() {
		Arrays.sort(punctuation);
	}
	
	public LabelEnumeration(LabelEnumeration other) {
		this.idToLabel = new ArrayList<>(other.idToLabel);
		this.labelToId = new HashMap<>(other.labelToId);
		this.topLevelLabels = new HashSet<>(other.topLevelLabels);
	}
	
	/**
	 * Adds label if not already added
	 * @param label
	 */
	public void addLabel(String label) {
		if(labelToId.containsKey(label))
			return;
		
		int num = idToLabel.size();
		labelToId.put(label, num);
		idToLabel.add(label);
		idToExtendLabel.add(-1);
		if(label.endsWith("-BAR")) {
			String base = label.substring(0, label.length()-4);
			if(labelToId.containsKey(base))
				idToExtendLabel.set(labelToId.get(base), num);
		}
		else {
			String extended = label + "-BAR";
			if(labelToId.containsKey(extended))
				idToExtendLabel.set(num, labelToId.get(extended));
		}
	}
	
	public void addAllLabels(List<String> labels) {
		for(String label : labels) {
			addLabel(label);
		}
	}
	
	public void shuffleLabels() {
		List<String> labels = idToLabel;
		labelToId = new HashMap<>();
		idToLabel = new ArrayList<>();
		
		Collections.shuffle(labels);
		addAllLabels(labels);
	}
	
	public void addTopLevelLabel(String label) {
		addLabel(label); // just to make sure it is added
		topLevelLabels.add(getId(label));
	}
	
	public Set<Integer> getTopLevelLabelIds() {
		return topLevelLabels;
	}
	
	public String getLabel(int id) {
		return idToLabel.get(id);
	}
	
	public int getId(String label) {
		return labelToId.get(label);
	}
	
	public int getNumberOfLabels() {
		return idToLabel.size();
	}
	
	/**
	 * Gets the label that is this label + "-BAR" from binarization
	 * @return
	 */
	public int getExtendLabel(int i) {
		return idToExtendLabel.get(i);
	}
	
	public boolean isPunctuation(int i) {
		String label = idToLabel.get(i);
		return (Arrays.binarySearch(punctuation, label) >= 0);
	}
	
	public boolean isConjunction(int i) {
		String label = idToLabel.get(i);
		for(String c : conjunctions)
			if(c.equals(label))
				return true;
		return false;
	}
}
