package ca.sfu.jbn;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import main.Config;
import main.RunBB;
import edu.cmu.tetrad.util.ProbUtils;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;


/**
 * @author Sajjad. I implemented all the needed scores using tetrad code and Diljot global implementation.
 */
public class Scores implements LocalDiscreteScore {
	private DataSet dataSet;
	private double samplePrior = 10;
	private double structurePrior = 1.0;
	private String scoreName = "AIC";
	private String scoreType = "c";
	private String databaseName;
	
	public Scores(DataSet dataSet, double samplePrior, double structurePrior, String scoreName, String scoreType) {
		if (dataSet == null) {
			throw new NullPointerException();
		}
		this.dataSet = dataSet;
		this.samplePrior = samplePrior;
		this.structurePrior = structurePrior;
		this.scoreName = scoreName;
		this.scoreType = scoreType;
		Config conf = new Config();
		databaseName = conf.getProperty("dbname");
	}

	public Double[] localScore(int i, int parents[], Node y,
			Set<Node> parentNodes,
			Map<Node, Map<Set<Node>, Double[]>> globalScoreHash) {

		Double[] oldscore = null;
		if (globalScoreHash.containsKey(y)) {
			if (globalScoreHash.get(y).containsKey(parentNodes)) {
				oldscore = globalScoreHash.get(y).get(parentNodes);
			}
		}

		if (oldscore != null && !Double.isNaN(oldscore[0])) {

			// =================Writing to the file.=======================
			// We just use this file to compare the scores later after execution
			// is complete.
			// The following code will write the contents of the hit to file
			try {
				File file = new File("Hash-Hits");

				if (!file.exists()) {
					file.createNewFile();
				}

				FileWriter fileWriter = new FileWriter(file.getName(), true);
				BufferedWriter bufferWriter = new BufferedWriter(fileWriter);
				bufferWriter.write("Node:" + y + "\nParents: "
						+ parentNodes + "\nScore:" + oldscore);
				bufferWriter
						.write("\n_______________________________________________________________________________\n");
				bufferWriter.close();
			} catch (Exception e) {
				System.out.println("Error Writing");
			}
			// ===========================Writing to file
			// complete===============

			return oldscore;
		}

		/*
		 * double oldScore = localScoreCache.get(i, parents);
		 * 
		 * if (!Double.isNaN(oldScore)) { return oldScore; }
		 */

		// Number of categories for i.
		int r = numCategories(i);

		// Numbers of categories of parents.
		int dims[] = new int[parents.length];

		for (int p = 0; p < parents.length; p++) {
			dims[p] = numCategories(parents[p]);
		}

		// Number of parent states.
		int q = 1;
		for (int p = 0; p < parents.length; p++) {
			q *= dims[p];
		}

		// Conditional cell counts of data for i given parents(i).
		long n_ijk[][] = new long[q][r];
		long n_ij[] = new long[q];
		long n_ik[] = new long[r];
		long n_ijk1[][] = new long[q][r];
		int values[] = new int[parents.length];
		System.out.println("SampleSize for computing BDeu Score: "
				+ sampleSize());
		for (int n = 0; n < sampleSize(); n++) {
			for (int p = 0; p < parents.length; p++) {
				int parentValue = dataSet().getInt(n, parents[p]);

				if (parentValue == -99) {
					throw new IllegalStateException("Please remove or impute "
							+ "missing values.");
				}

				values[p] = parentValue;
			}

			int childValue = dataSet().getInt(n, i);

			if (childValue == -99) {
				throw new IllegalStateException(
						"Please remove or impute missing " + "values (record "
								+ n + " column " + i + ")");

			}

//			 n_ijk[getRowIndex(dims, values)][childValue]++;
//			for (int m = 0; m < dataSet().getMultiplier(n); m++) { // case
//																	// expander
//																	// May 1st,
//																	// @zqian
//				n_ijk[getRowIndex(dims, values)][childValue]++;
//				// LoopingCounter++; // LoopingCounter
//			}
//			
			
			n_ijk[getRowIndex(dims, values)][childValue] += dataSet().getMultiplier(n)*RunBB.resampelingFactor; // instead of the above loop @Saj
			
			
//			 System.out.println(" dataSet().getMultiplier(n) "
//			 +dataSet().getMultiplier(n));
//			 System.out.println(" after looping, n_ijk  :" +getRowIndex(dims,
//			 values)+", " +childValue+", "+(n_ijk[getRowIndex(dims,
//			 values)][childValue])); //@zqian

			// case expander Jun 13rd, @zqian
//			n_ijk1[getRowIndex(dims, values)][childValue] = n_ijk[getRowIndex(
//					dims, values)][childValue] + dataSet().getMultiplier(n);
		}

		
		//================* @Sajjad (06Jun15) *==================
		File f;
		FileWriter fw;
		BufferedWriter bw;
		File f2;
		FileWriter fw2;
		BufferedWriter bw2;
		Double spg[] = new Double[3];
		Double score = null;
		Double penalty = null;
		Double grounding = null;
		try {
			f = new File(databaseName + "_History.txt");
			if(!f.exists())
				f.createNewFile();
			fw = new FileWriter(f.getName(), true);
			bw = new BufferedWriter(fw);
			
			f2 = new File(databaseName + "/StructuresInfo.csv");
			if(!f2.exists())
				f2.createNewFile();
			fw2 = new FileWriter(f2, true);
			bw2 = new BufferedWriter(fw2);
		
			double N_i = 0.0; //Used for penalty term.
			for (int j = 0; j < q; j++) {
				for (int k = 0; k < r; k++) {
					n_ij[j] += n_ijk[j][k];
				}
				N_i += n_ij[j]; ///// N_i equals to number of groundings.
			}
			for (int k = 0; k < r; k++){
				for(int j=0; j<q; j++){
					n_ik[k] += n_ijk[j][k];
				}
			}
			
			// Based on "Scoring functions for learning Bayesian networks" slides
	        score = 0.0;
	        grounding = 1.0;
	        double wpll = 0.0;
	        double w = 0.0;
	        double prior = 0.0;
	        double conditional = 0.0;
	        double alpha = 0.01;
	        double paramNum = (double)(r-1)*(double)q;
	     // Based on "Scoring functions for learning Bayesian networks" slides
	        double loglikelihood = 0.0;
//	        bw.write("Structure:\n\tThe Node: " + y + "\n\tThe Parents: "+ parentNodes + "\nLogLikelihood:\n");
	        for (int j = 0; j < q; j++) {
	        	if (n_ij[j] != 0){ //if parent count is not 0, find corresponding likeilhood term
		            for (int k = 0; k < r; k++) {
		            	if (n_ijk[j][k] != 0){ //if parent-child count is not 0, find corresponding likeilhood term
		            		double theta_ijk = (double)n_ijk[j][k]/(double)n_ij[j];
		            		loglikelihood += n_ijk[j][k] * log2(theta_ijk); // LL(B|T) = SS(N_ijk * log(N_ijk/N_ij)) || This is equal to Lc in the paper
//		            		bw.write("\t" + loglikelihood + "\n");
		            	}
		            }
	        	}
	        }
	        
	        
	        grounding = (double)N_i;
	        boolean sw = false;
			if(grounding > 2200.0){
//				grounding = 2280.0;
				sw = true;
			}
			
	        switch(scoreName.toUpperCase()){
	        case "AIC":
	        	if(scoreType == "_count"){ //AIC_count
	        		score = loglikelihood - paramNum;
	    	        penalty = paramNum;
	        	}else if (scoreType.startsWith("_normalized")){ //AIC_normalized
	        		score = loglikelihood/N_i - paramNum/N_i;
	        		penalty = paramNum;
	        	}else if (scoreType == "_weighted"){ //AIC_weighted
	        		score = loglikelihood/N_i - paramNum;
	        		penalty = paramNum;
	        	}
	        	break;
	        case "BIC":
	        	if(scoreType == "_count"){ //BIC_count
	        		score = loglikelihood - 0.5*log2(N_i)*paramNum;
	    	        penalty = 0.5*log2(N_i)*paramNum;
	        	}else if (scoreType.startsWith("_normalized")){ //BIC_normalized
	        		score = loglikelihood/N_i - 0.5*log2(N_i)*paramNum/N_i;
	        		penalty = 0.5*log2(N_i)*paramNum;
	        	}else if (scoreType == "_weighted"){ //BIC_weighted
	        		score = loglikelihood/N_i - 0.5*log2(N_i)*paramNum;
	        		penalty = 0.5*log2(N_i)*paramNum;
	        	}
	        	break;
	        case "LL":
	        	if(scoreType == "_gain"){
	        		score = loglikelihood/N_i;
	        		penalty = 0.0;
	        	}
	        	break;
	        }
	         
	        int alaki = 0;
	        if(node2string(y).equals("teachingability(prof0)") && parents2string(parentNodes).equals("popularity(prof0)"))
	        	alaki = 10;
	        
	        bw.write("Structure:\n\tNode: " + y + "\n\tParents: "+ parentNodes + "\n");  
	        bw.write("# groundings: " + grounding + ", Penalty: " + penalty + ", r (node states): " + r + ", q (parents states): " + q + ", paramNum: " + paramNum + "\n");
	        bw.write(">>>>>>> " + scoreName + "_" + scoreType + ": " + score + "\n=============================\n");
	        bw2.write(scoreName+scoreType +","+ node2string(y) +","+ parents2string(parentNodes) +","+ score +","+ loglikelihood +","+ grounding +","+ penalty +","+ r +","+ q +","+ paramNum +"\n");
	        bw.close();
	        bw2.close();
	        
		} catch (Exception e) {
			System.out.println("Error Writing");
		}
		//================**********************=================
        
		// localScoreCache.add(i, parents, score);
		if (globalScoreHash.containsKey(y)) {
			globalScoreHash.get(y).put(parentNodes, new Double[]{score,penalty,grounding});
		} else {
			globalScoreHash.put(y, new HashMap<Set<Node>, Double[]>());
			globalScoreHash.get(y).put(parentNodes, new Double[]{score,penalty,grounding});
		}

		// ===============File =================
		// /Another file keeps track of the times when we don't get a hit in the
		// cache.
		try {
			File file = new File("NoHit");

			if (!file.exists())
				file.createNewFile();

			FileWriter fileWriter = new FileWriter(file.getName(), true);
			BufferedWriter bufferWriter = new BufferedWriter(fileWriter);
			bufferWriter.write("Node:" + y + "\nParents: "
					+ parentNodes + "\nScore:" + score);
			bufferWriter
					.write("\n_______________________________________________________________________________\n");
			bufferWriter.close();
		} catch (Exception e) {
			System.out.println("Error Writing");
		}
		// ======================================End writing===============
		spg[0] = score;
		spg[1] = penalty;
		spg[2] = grounding;
		return spg;
	}

	public DataSet getDataSet() {
		return dataSet;
	}

	private double log2(double x) {
        return Math.log(x) / Math.log(2);
    }
	
	private int getRowIndex(int[] dim, int[] values) {
		int rowIndex = 0;
		for (int i = 0; i < dim.length; i++) {
			rowIndex *= dim[i];
			rowIndex += values[i];
		}
		return rowIndex;
	}

	private int sampleSize() {
		return dataSet().getNumRows();
	}

	private int numCategories(int i) {
		return ((DiscreteVariable) dataSet().getVariable(i)).getNumCategories();
	}

	private DataSet dataSet() {
		return dataSet;
	}

	public double getStructurePrior() {
		return structurePrior;
	}

	public double getSamplePrior() {
		return samplePrior;
	}

	public void setStructurePrior(double structurePrior) {
		this.structurePrior = structurePrior;
	}

	public void setSamplePrior(double samplePrior) {
		this.samplePrior = samplePrior;
	}

	@Override
	public Double[] localScore(int i, int[] parents) {
		// TODO Auto-generated method stub
		return new Double[3];
	}

	public String parents2string(Set<Node> parentNodes){
		String out = "";
		for(Node n: parentNodes){
			out += node2string(n) + " ";
		}
		return out.trim();
	}
	
	public String node2string(Node node){
		String out = node.getName();
		out = out.replace(',', '-');
		return out;
	}
}
