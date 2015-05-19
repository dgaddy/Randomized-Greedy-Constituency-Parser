package constituencyParser;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

public class TrainOptions {
	public String dataFolder = "";
	public String outputFolder = "";
	public int cores = 1;
	public int iterations = 1;
	public boolean secondOrder = true;
	public boolean costAugmenting = true;
	public String startModel = null;
	public double percentOfData = 1;
	public double learningRate = 1;
	public int batchSize = 1;
	public double regularization = 0.01;
	public double dropout = 0.0;
	public boolean randGreedy = true;
	public boolean noNegativeFeatures = false;
	public boolean mira = false;
	public boolean useSuffixes = true;
	public int rareWordCutoff = 0;
	public String dataFile = null;
	public String testFile = null;
	
	public TrainOptions(String[] args) {
		OptionParser parser = new OptionParser("t:o:c:i:s:a:p:m:l:b:r:d:znqu:w:f:g:");
		OptionSet options = parser.parse(args);
		
		if(options.has("t")) {
			dataFolder = (String)options.valueOf("t");
		}
		if(options.has("o")) {
			outputFolder = (String)options.valueOf("o");
		}
		if(options.has("c")) {
			cores = Integer.parseInt((String)options.valueOf("c"));
		}
		if(options.has("i")) {
			iterations = Integer.parseInt((String)options.valueOf("i"));
		}
		if(options.has("s")) {
			secondOrder = "t".equals(options.valueOf("s"));
		}
		if(options.has("a")) {
			costAugmenting = "t".equals(options.valueOf("a"));
		}
		if(options.has("p")) {
			percentOfData = Double.parseDouble((String)options.valueOf("p"));
		}
		if(options.has("m")) {
			startModel = (String)options.valueOf("m");
		}
		if(options.has("l")) {
			learningRate = Double.parseDouble((String)options.valueOf("l"));
		}
		if(options.has("b")) {
			batchSize = Integer.parseInt((String)options.valueOf("b"));
		}
		if(options.has("r")) {
			regularization = Double.parseDouble((String)options.valueOf("r"));
		}
		if(options.has("d")) {
			dropout = Double.parseDouble((String)options.valueOf("d"));
		}
		if(options.has("z")) {
			randGreedy = false;
			secondOrder = false;
		}
		if(options.has("n")) {
			noNegativeFeatures = true;
		}
		if(options.has("q")) {
			mira = true;
		}
		if(options.has("u")) {
			useSuffixes = "t".equals(options.valueOf("u"));
		}
		if(options.has("w")) {
			rareWordCutoff = Integer.parseInt((String)options.valueOf("w"));
		}
		if(options.has("f")) {
			dataFile = (String)options.valueOf("f");
		}
		if(options.has("g")) {
			testFile = (String)options.valueOf("g");
		}
		
		System.out.println("Training options");
		if(dataFile == null)
			System.out.println("Data directory: " + dataFolder);
		else
			System.out.println("Data file: " + dataFile);
		System.out.println("Output directory: " + outputFolder);
		System.out.println("Cores: " + cores);
		System.out.println("Iteration: " + iterations);
		System.out.println("secondOrder: " + secondOrder);
		System.out.println("costAugmenting: " + costAugmenting);
		System.out.println("learningRate: " + learningRate);
		System.out.println("batchSize: " + batchSize);
		System.out.println("regularization: " + regularization);
		System.out.println("dropout: " + dropout);
		System.out.println("noNegativeFeatures: " + noNegativeFeatures);
		System.out.println("randGreedy: " + randGreedy);
		System.out.println("mira: " + mira);
		System.out.println("use suffixes: " + useSuffixes);
		System.out.println("rare word cutoff: " + rareWordCutoff);
		if(startModel != null)
			System.out.println("starting from: " + startModel);
		if(percentOfData < 1)
			System.out.println("using " + percentOfData + " of data");
	}
}
