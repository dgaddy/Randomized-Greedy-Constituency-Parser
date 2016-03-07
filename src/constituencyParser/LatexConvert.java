package constituencyParser;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LatexConvert {
	static String[] header = {
			"\\documentclass{article}",
			"\\usepackage[landscape, margin=1in]{geometry}",
			"\\usepackage{tikz-qtree}",
			"\\usepackage{tikz-qtree-compat}",
			"\\begin{document}",
			"\\tiny",
			"\\tikzset{level distance=17pt}",
			""
		};
	static String[] footer = {
			"\\end{document}"
		};
	
	static String replaceLatexSpecial(String string) {
		string = string.replace("\\", "\\textbackslash{}");
		string = string.replace("^", "\\textasciicircum{}");
		string = string.replace("~", "\\textascitilde{}");
		String[] replace = {"#", "$", "%", "&", "_", "{", "}"};
		for (String c : replace)
			string = string.replace(c, "\\" + c);
		return string;
	}
	
	public static void main(String[] args) throws IOException {
		if (args.length != 3) {
			System.err.println("Takes 3 arguments: [gold file] [predicted file] [out file]");
			return;
		}
		
		String goldFile = args[0];
		String predFile = args[1];
		String outFile = args[2];
		
		WordEnumeration words = new WordEnumeration(false, 0);
		LabelEnumeration labels = new LabelEnumeration();
		List<SpannedWords> goldTrees = PennTreebankReader.loadFromFiles(Arrays.asList(goldFile), words, labels, new RuleEnumeration(), true);
		List<SpannedWords> predTrees = PennTreebankReader.loadFromFiles(Arrays.asList(predFile), words, labels, new RuleEnumeration(), true);
		
		if (goldTrees.size() != predTrees.size()) {
			System.err.println("Input sizes do not match.");
			return;
		}
		
		PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(outFile)));
		for (String line : header)
			writer.println(line);
		
		for (int i = 0; i < goldTrees.size(); i++) {
			SpannedWords goldSW = goldTrees.get(i);
			TreeNode goldTree = TreeNode.makeTreeFromSpans(goldSW.getSpans(), goldSW.getWords(), words, labels)
					.unbinarize(); 
			SpannedWords predSW = predTrees.get(i);
			TreeNode predTree = TreeNode.makeTreeFromSpans(predSW.getSpans(), predSW.getWords(), words, labels)
					.unbinarize();
			
			Set<String> goldStrings = new HashSet<>();
			Set<String> predStrings = new HashSet<>();
			String goldString = goldTree.getAllNodeLatexStrings(goldStrings);
			String predString = predTree.getAllNodeLatexStrings(predStrings);
			StringBuilder sentenceBuilder = new StringBuilder();
			for (Word w : goldSW.getWords()) {
				sentenceBuilder.append(w.getWord());
				sentenceBuilder.append(' ');
			}
			String sentence = replaceLatexSpecial(sentenceBuilder.toString());
			if (goldString.equals(predString)) {
				writer.format("Gold %d and Predicted %d match\\\\\n\n", i+1, i+1);
			} else {
				writer.format("Gold %d\\\\\n", i+1);
				String tree = goldTree.toLatexString();//toCollapsedLatexString(predStrings);
				writer.println("\\Tree " + tree + "\\\\\\\\");
				writer.println(sentence);
				writer.println();
				writer.println("\\pagebreak");
				writer.format("Predicted %d\\\\\n", i+1);
				tree = predTree.toLatexString();//toCollapsedLatexString(goldStrings);
				writer.println("\\Tree " + tree + "\\\\\\\\");
				writer.println(sentence);
				writer.println();
				writer.println("\\pagebreak");
			}
		}
		
		for (String line : footer)
			writer.println(line);
		writer.close();
	}
}
