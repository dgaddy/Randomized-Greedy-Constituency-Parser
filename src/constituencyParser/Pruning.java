package constituencyParser;

import java.util.List;

import constituencyParser.Rule.Type;

public class Pruning {
	// different pruning for after unary and before unary
	boolean[][][] pruneBeforeUnary;
	boolean[][][] pruneAfterUnary;
	
	public Pruning(int numWords, int numLabels) {
		pruneBeforeUnary = new boolean[numWords][numWords+1][numLabels];
		pruneAfterUnary = new boolean[numWords][numWords+1][numLabels];
	}
	
	public void pruneBeforeUnary(int start, int end, int label) {
		pruneBeforeUnary[start][end][label] = true;
	}
	
	public void pruneAfterUnary(int start, int end, int label) {
		pruneAfterUnary[start][end][label] = true;
	}
	
	public boolean isPrunedBeforeUnary(int start, int end, int label) {
		//return false;
		return pruneBeforeUnary[start][end][label];
	}
	
	public boolean isPrunedAfterUnary(int start, int end, int label) {
		return pruneAfterUnary[start][end][label];
	}
	
	public int countPruned() {
		int count = 0;
		for(boolean[][] a : pruneBeforeUnary) {
			for(boolean[] b : a) {
				for(boolean p : b) {
					if(p)
						count++;
				}
			}
		}
		return count;
	}
	
	public boolean containsPruned(List<Span> spans) {
		for(Span s : spans) {
			if (s.getRule().getType() == Type.UNARY) {
				if (isPrunedAfterUnary(s.getStart(), s.getEnd(), s.getRule().getLabel()))
					return true;
			}
			else  {
				if(isPrunedBeforeUnary(s.getStart(), s.getEnd(), s.getRule().getLabel()))
					return true;
			}
		}
		return false;
	}
}
