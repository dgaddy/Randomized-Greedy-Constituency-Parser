package constituencyParser;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import constituencyParser.features.FeatureParameters;

public class SaveObject implements Serializable {
	private static final long serialVersionUID = 1L;

	public static SaveObject loadSaveObject(String filename) throws IOException, ClassNotFoundException {
		FileInputStream fileStream = new FileInputStream(filename);
		ObjectInputStream objectStream = new ObjectInputStream(fileStream);
		SaveObject loaded = (SaveObject)objectStream.readObject();
		objectStream.close();
		return loaded;
	}
	
	public void save(String filename) throws IOException {
		FileOutputStream fileStream = new FileOutputStream(filename);
		ObjectOutputStream objectStream = new ObjectOutputStream(fileStream);
		objectStream.writeObject(this);
		objectStream.close();
	}
	
	private LabelEnumeration labels;
	private Rules rules;
	private FeatureParameters parameters;
	
	public SaveObject(LabelEnumeration labels, Rules rules, FeatureParameters parameters) {
		this.labels = labels;
		this.rules = rules;
		this.parameters = parameters;
	}
	
	public LabelEnumeration getLabels() {
		return labels;
	}
	
	public Rules getRules() {
		return rules;
	}
	
	public FeatureParameters getParameters() {
		return parameters;
	}
}
