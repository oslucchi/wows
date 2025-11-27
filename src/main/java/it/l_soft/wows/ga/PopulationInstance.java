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
}
