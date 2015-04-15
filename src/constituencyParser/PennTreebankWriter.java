package constituencyParser;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

import constituencyParser.Rule.Type;

public class PennTreebankWriter {
	WordEnumeration words;
	LabelEnumeration labels;
	
	Writer writer;
	
	boolean addTop;
	boolean first = true;
	
	public PennTreebankWriter(String file, WordEnumeration words, LabelEnumeration labels, boolean addTop) throws IOException {
		this.words = words;
		this.labels = labels;
		this.writer = new FileWriter(file);
		this.addTop = addTop;
	}
	
	public void writeTree(SpannedWords tree) throws IOException {
		if(first) {
			first = false;
		}
		else {
			writer.append('\n');
		}
		if(addTop) {
			writer.append("(TOP ");
		}
		
		for (Span span : tree.getSpans()) {
			if (span.getRule().getType() == Type.TERMINAL && !labels.isTerminalLabel(span.getRule().getLabel())) {
				SpanUtilities.print(tree.getSpans(), labels);
				throw new RuntimeException("aaa");
			}
		}
		writer.append(TreeNode.makeTreeFromSpans(tree.getSpans(), tree.getWords(), words, labels).unbinarize().toString());
		
		if(addTop)
			writer.append(')');
	}
	
	public void close() throws IOException {
		writer.close();
	}
}
