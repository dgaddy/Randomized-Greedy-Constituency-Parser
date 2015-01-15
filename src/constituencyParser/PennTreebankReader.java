package constituencyParser;
import java.io.Closeable;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapted from github: yfpeng's PennTreebankReader
 */
public class PennTreebankReader implements Closeable {

  Reader reader;
  int    currentChar;

  public PennTreebankReader(Reader reader)
      throws IOException {
    this.reader = reader;
    currentChar = nextChar();
  }

  public PennTreebankReader(File file)
      throws IOException {
    this(new FileReader(file));
  }

  public PennTreebankReader(String filename)
      throws IOException {
    this(new FileReader(filename));
  }

  private int nextChar()
      throws IOException {
    return reader.read();
  }

  private String nextToken()
      throws IOException {
    if (currentChar == -1) {
      return null;
    }
    if (currentChar == '(' || currentChar == ')') {
      String s = Character.toString((char) currentChar);
      currentChar = nextChar();
      return s;
    }

    // white space
    while (Character.isWhitespace(currentChar)) {
      currentChar = nextChar();
    }

    if (currentChar == -1) {
      return null;
    }
    if (currentChar == '(' || currentChar == ')') {
      String s = Character.toString((char) currentChar);
      currentChar = nextChar();
      return s;
    }
    StringBuilder sb = new StringBuilder();
    sb.append((char) currentChar);
    currentChar = nextChar();
    while (currentChar != '('
        && currentChar != ')'
        && currentChar != -1
        && !Character.isWhitespace(currentChar)) {
      sb.append((char) currentChar);
      currentChar = nextChar();
    }
    return sb.toString();

  }

  /**
   * Read a single ptb tree.
   * 
   * @return a ptb tree, or null if the end of the stream has been reached.
   * @throws IOException
   */
  public TreeNode readPtbTree()
      throws IOException {

    TreeNode root = new TreeNode();
    TreeNode current = root;

    int state = 0;
    while (true) {
      String s = nextToken();
      switch (state) {
      case 0:
        if (s == null) {
          return null;
        } else if (s.equals("(")) {
          TreeNode child = new TreeNode();
          current.addChild(child);
          current = child;
          state = 4;
        } else {
        	throw new IllegalArgumentException("the ptb should start with [(]");
        }
        break;
      case 1:
        if (s == null || s.equals("(") || s.equals(")")) {
          throw new IllegalArgumentException("expecting [tag]");
        } else {
          current.setLabel(s);
          state = 2;
        }
        break;
      case 2:
        if (s == null || s.equals(")")) {
          throw new IllegalArgumentException("expecting [(] or [word]");
        } else if (s.equals("(")) {
          TreeNode child = new TreeNode();
          current.addChild(child);
          current = child;
          state = 1;
        } else {
          TreeNode child = new TreeNode(s);
          current.addChild(child);
          state = 3;
        }
        break;
      case 3:
        if (s == null) {
          throw new IllegalArgumentException("expecting [(] or [)]");
        }
        if (s.equals(")")) {
          if (current == null) {
            throw new IllegalArgumentException("too much [)]");
          }
          current = current.getParent();
          if (current.getParent() == null) {
        	  state = 5;
            //return current.getFirstChild();
          }
        } else if (s.equals("(")) {
          TreeNode child = new TreeNode();
          current.addChild(child);
          current = child;
          state = 1;
        }
        break;
      case 4:
    	  if(!s.equals("("))
    		  throw new IllegalArgumentException("expecting [(]");
    	  state = 1;
    	  break;
      case 5:
    	  return current.getFirstChild();
      }
    }
  }

  @Override
  public void close()
      throws IOException {
    reader.close();
  }
  
	/**
	 * Loads data from files numbered firstFile to lastFile (exclusive)
	 * @param folder
	 * @param firstFile
	 * @param lastFile exclusive
	 * @param labels all new labels will be added to this
	 * @param rules all new rules will be added to this
	 * @return
	 * @throws IOException 
	 */
	static List<SpannedWords> loadFromFiles(String folder, int firstFile, int lastFile, WordEnumeration words, LabelEnumeration labels, RuleEnumeration rules) throws IOException {

		List<SpannedWords> loaded = new ArrayList<>();
		List<TreeNode> trees = new ArrayList<>();
		
		String formatString = folder+"wsj.%1$02d.txt";
		
		for(int i = firstFile; i < lastFile; i++) {
			String fileName = String.format(formatString, i);
			PennTreebankReader reader = new PennTreebankReader(fileName);
			TreeNode tree;
			while((tree = reader.readPtbTree()) != null) {
				tree.removeNoneLabel();
				tree.makeLabelsSimple();
				tree = tree.makeBinary();
				trees.add(tree);
				words.addAllWords(tree.getAllWords());
				labels.addAllLabels(tree.getAllLabels());
				labels.addTopLevelLabel(tree.getLabel());
			}
			
			reader.close();
		}
		
		
		for(TreeNode tree : trees) {
			loaded.add(tree.getSpans(words, labels));
		}
		

		rules.addAllRules(loaded);
		
		return loaded;
	}
}