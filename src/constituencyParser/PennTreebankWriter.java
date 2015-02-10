package constituencyParser;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

public class PennTreebankWriter {
	WordEnumeration words;
	LabelEnumeration labels;
	
	Writer writer;
	
	public PennTreebankWriter(String file, WordEnumeration words, LabelEnumeration labels) throws IOException {
		this.words = words;
		this.labels = labels;
		this.writer = new FileWriter(file);
	}
	
	public void writeTree(SpannedWords tree) throws IOException {
		writer.append("\n");
		writer.append(TreeNode.makeTreeFromSpans(tree.getSpans(), tree.getWords(), words, labels).toString());
	}
}
