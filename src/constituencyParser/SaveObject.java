package constituencyParser;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import constituencyParser.features.FeatureParameters;

/**
 * Used to hold a model and all required enumerations for saving.
 */
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
	
	private WordEnumeration words;
	private LabelEnumeration labels;
	private RuleEnumeration rules;
	private FeatureParameters parameters;
	
	public SaveObject(WordEnumeration words, LabelEnumeration labels, RuleEnumeration rules, FeatureParameters parameters) {
		this.words = words;
		this.labels = labels;
		this.rules = rules;
		this.parameters = parameters;
	}
	
	public WordEnumeration getWords() {
		return words;
	}
	
	public LabelEnumeration getLabels() {
		return labels;
	}
	
	public RuleEnumeration getRules() {
		return rules;
	}
	
	public FeatureParameters getParameters() {
		return parameters;
	}
}
