package constituencyParser;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LabelEnumeration implements Serializable {
	private static final long serialVersionUID = 1L;
	
	List<String> idToLabel = new ArrayList<>();
	HashMap<String, Integer> labelToId = new HashMap<>();
	
	HashSet<Integer> topLevelLabels = new HashSet<>();
	
	public LabelEnumeration() {
		
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
		
		labelToId.put(label, idToLabel.size());
		idToLabel.add(label);
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
}
