package constituencyParser;

import java.util.List;

public class Pruning {
	boolean[][][] prune;
	
	public Pruning(int numWords, int numLabels) {
		prune = new boolean[numWords][numWords+1][numLabels];
	}
	
	public void prune(int start, int end, int label) {
		prune[start][end][label] = true;
	}
	
	public boolean isPruned(int start, int end, int label) {
		return prune[start][end][label];
	}
	
	public int countPruned() {
		int count = 0;
		for(boolean[][] a : prune) {
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
			if(isPruned(s.getStart(), s.getEnd(), s.getRule().getLabel()))
				return true;
		}
		return false;
	}
}
