package constituencyParser;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

public class TestOptions {
	public String modelFile = "";
	public String dataDir = "";
	public boolean secondOrder = true;
	public int numberOfThreads = 1;
	public int greedyIterations = 100;
	public int section = 0;
	public boolean useRandGreedy = true;
	public double percentOfData = 1;
	public String dataFile = null;
	
	public TestOptions(String[] args) {
		OptionParser parser = new OptionParser("m:d:s:t:i:w:zp:f:");
		OptionSet options = parser.parse(args);
		
		if(options.has("m")) {
			modelFile = (String)options.valueOf("m");
		}
		if(options.has("d")) {
			dataDir = (String)options.valueOf("d");
		}
		if(options.has("s")) {
			secondOrder = "t".equals(options.valueOf("s"));
		}
		if(options.has("t")) {
			numberOfThreads = Integer.parseInt((String)options.valueOf("t"));
		}
		if(options.has("i")) {
			greedyIterations = Integer.parseInt((String)options.valueOf("i"));
		}
		if(options.has("w")) {
			section = Integer.parseInt((String)options.valueOf("w"));
		}
		if(options.has("z")) {
			useRandGreedy = false;
			secondOrder = false;
		}
		if(options.has("p")) {
			percentOfData = Double.parseDouble((String)options.valueOf("p"));
			if(percentOfData > 1 || percentOfData < 0)
				throw new RuntimeException("Percent of data should be between 0 and 1");
		}
		if(options.has("f")) {
			dataFile = (String)options.valueOf("f");
		}
	}
	
	public TestOptions(TrainOptions options, double percentOfData, int section) {
		this.dataDir = options.dataFolder;
		this.dataFile = options.dataFile;
		this.numberOfThreads = options.cores;
		this.percentOfData = percentOfData;
		this.secondOrder = options.secondOrder;
		this.useRandGreedy = options.randGreedy;
		this.section = section;
	}
}
