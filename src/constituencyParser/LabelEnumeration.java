package constituencyParser;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class LabelEnumeration implements Serializable {
	private static final long serialVersionUID = 1L;
	
	List<String> idToLabel = new ArrayList<>();
	HashMap<String, Integer> labelToId = new HashMap<>();
	
	public LabelEnumeration() {
		
	}
	
	public LabelEnumeration(LabelEnumeration other) {
		this.idToLabel = new ArrayList<>(other.idToLabel);
		this.labelToId = new HashMap<>(other.labelToId);
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
