package it.l_soft.wows.ga;

import java.util.List;

public class PopulationInstance {
	List<Gene> genes, roundRank;
	long[] accumulators;
	Gene arbitrator;
	int horizonBars;
	int size;
	int populationId;
	
	public PopulationInstance() {}
			
	public int getHorizonBars() {
		return horizonBars;
	}

	public List<Gene> getGenes() {
		return genes;
	}

	public long[] getAccumulators() {
		return accumulators;
	}

	public Gene getArbitrator() {
		return arbitrator;
	}

	public void setArbitrator(Gene arbitrator) {
		this.arbitrator = arbitrator;
	}

	public int getSize() {
		return size;
	}

	public int getPopulationId() {
		return populationId;
	}
	
    public static PopulationInstance initPopulation(int populationId, List<Gene> genes, int horizonBars, int numOfAccumulators) {
    	PopulationInstance instance = new PopulationInstance();
    	instance.genes = genes;
    	instance.size = genes.size();
        instance.horizonBars = horizonBars;
        instance.accumulators = new long[numOfAccumulators];
        instance.populationId = populationId;
        return instance;

    }


    /*            
    try {
    	TextFileHandler temp = new TextFileHandler(props.getGeneEvalDumpPath(), 
    											   props.getGeneEvalDumpName() + "_genes_" + y, 
    											   "csv", false, false);
		temp.write("Gene,Indicators,BarsSurvived,MktTS,MktBar#,MktDir,Direction (M-P)," +
				   	   "PrevClose,CurrClose,PredMktPrice,PredMove,PredMovSign,Denom,NextPredScore", true);
		temp.close();
    	temp = new TextFileHandler(props.getGeneEvalDumpPath(), 
								   props.getGeneEvalDumpName() + "_arbi_" + y, 
								   "csv", false, false);
		temp.write("Gene,Indicators,BarsSurvived,MktTS,MktBar#,MktDir,Direction (M-P)," +
					"PrevClose,CurrClose,PredMktPrice,PredMove,Denom,NextPredScore", true);
		temp.close();
		temp = new TextFileHandler(props.getGeneEvalDumpPath(),"SuperArbitrator", "csv", false, false);
		temp.write("Bar#,Timestamp,", false);
		for(int i = 0; i < props.getNumberOfPopulations(); i++)
		{
			temp.write("Arbitrator_" + i + ",", false);    				
		}
		temp.write("ConsolidatedPrediction", true); 
		temp.close();
	} 
    catch (Exception e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	System.out.println("QUI");
}
*/
}
