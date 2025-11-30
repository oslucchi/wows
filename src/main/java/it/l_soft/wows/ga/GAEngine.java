package it.l_soft.wows.ga;

import it.l_soft.wows.ApplicationProperties;
import it.l_soft.wows.indicators.Indicator;
import it.l_soft.wows.indicators.volatility.ATR;


import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;

public class GAEngine {
	private final Logger log = Logger.getLogger(this.getClass());
	private ApplicationProperties props = ApplicationProperties.getInstance();
	
    private final List<Indicator> catalog; // all available indicator *prototypes* (NOT shared state!)
    private final World[] worlds = new World[props.getNumberOfWorlds()];
    
    // Shared ATR for normalization scaling
    private final ATR atrScale;
    
	// For horizon scoring, keep a ring of past closes (or compute pct when horizon fulfilled)
	//  private final Deque<Double> closeRing = new ArrayDeque<>();

    public GAEngine(List<Indicator> indicatorCatalog) 
    		throws Exception 
    {
        this.catalog = indicatorCatalog;
        this.atrScale = new ATR(props.getAtrPeriodForScaling());
        for(int i = 0; i < props.getNumberOfWorlds(); i++)
        {
            List<Gene> genes = new ArrayList<Gene>();
            for (int y = 0; y < props.getPopulationSize(i); y++) {
                genes.add(randomGene(props.getPopulationSize(i)));
            }
            Gene arbitrator = new Gene("arbitrator", catalog, 
						   				 new int[props.getPopulationSize(i)], 
						   				 new double[props.getPopulationSize(i)]);
            genes.add(arbitrator);
            
        	worlds[i] = World.initPopulation(i, genes, props.getHorizonBars(i));
        }
        log.trace("World initialized");
    }

    private Gene randomGene(int populationSize) {
    	
    	String geneIndicators = "";
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int[] indIdx = new int[populationSize];
        double[] weigths = new double[populationSize];
        
        for (int i = 0; i < populationSize; i++) {
        	// use the n-th indicator in the indicators list
        	// theoretically an indicator could appear more than once in a gene
        	indIdx[i] = r.nextInt(catalog.size());
        	weigths[i] = 1;
        	geneIndicators += catalog.get(indIdx[i]).getName() + " ";
        }
        Gene g = new Gene(geneIndicators, catalog, indIdx, weigths);
        return g;
    }
    
    
    public List<Gene> ranked(List<Gene> population) {
        final double TOTAL_WEIGHT = props.getWaitOfScoreInRanking();
        final double WINRATE_WEIGHT = props.getWaitOfWinRateInRanking();
        return population.stream()
                .sorted(Comparator.comparingDouble((Gene g) -> {
                    double totalScore = g.getTotalScore() / g.getTotalBarsSurviving();
                    double winRate = g.getWinRate() / g.getTotalBarsSurviving();
                    return totalScore * TOTAL_WEIGHT + winRate * WINRATE_WEIGHT;
                }).reversed())
                .toList();
    }

    /** Selection: top keep, middle crossover, bottom replaced. */
    public World evolve(World world) {
    	
        int n = world.getGenes().size() - 1; // take the arbitrator off
        int keep = (int) Math.round(n * props.getElitePct());
        int generateNew = (int) Math.round(n * props.getGenerateNewPct());

        List<Gene> next = new ArrayList<Gene>(n);

        // 1) Elites
        for (int i = 0; i < keep; i++) {
            next.add(world.getGenes().get(i));
        }
        
        // Younger genes to pass unmuted
        for (int i = keep; i < n; i++) {
            Gene g = world.getGenes().get(i);
            if (g.getTotalBarsSurviving() < props.getMinBarsBeforeReplacing())
            {
            	next.add(g);
            }
        }
        
        // Generate the new genes if there is quota for that
        for (int i = next.size(); i < n - generateNew; i++) {
            Gene g = world.getGenes().get(i);
            if (g.getTotalBarsSurviving() < props.getMinBarsBeforeReplacing())
            {
                next.add(randomGene(world.getGenes().size()));
            }
        }

        int cross = (int) Math.round((n - next.size()) * props.getCrossoverPct());
        List<Gene> crossedAndMutated = new ArrayList<Gene>();
        
        ThreadLocalRandom r = ThreadLocalRandom.current();

        // Cross & mutate the best for the remaining slots free
        for (int i = next.size(); i < n && cross > 0; i++) 
        {
        	// Cross genes in the 'keep' set 
        	Gene[] toCross = new Gene[2];
        	toCross[0] = world.getGenes().get(r.nextInt(keep));
        	toCross[1] = world.getGenes().get(r.nextInt(keep));
        	int k = toCross[0].getIndicators()[i];
        	toCross[0].getIndicators()[i] = toCross[1].getIndicators()[i];
        	toCross[1].getIndicators()[i] = k;

        	// Ranodmly mutate part of them
            for(int y = 0; y < world.getSize() * props.getMutationRate(); y++)
            {
            	if (r.nextInt(2) > 0)
            	{
            		toCross[0].getIndicators()[r.nextInt(toCross[0].getIndicators().length)] = r.nextInt(catalog.size());
            		toCross[1].getIndicators()[r.nextInt(toCross[0].getIndicators().length)] = r.nextInt(catalog.size());
            	}
            }
            
            // Reset their counters and add to temporary list
        	toCross[0].resetCounters();
        	toCross[1].resetCounters();
            crossedAndMutated.add(toCross[0]);
            crossedAndMutated.add(toCross[1]);
            cross --;
        }
        next.addAll(crossedAndMutated);
        next.addAll(world.getGenes().stream()
	        						.filter(g -> g.getName().equals("arbitrator"))
	        						.collect(Collectors.toList()));

        world.getGenes().clear();
        world.getGenes().addAll(next);
        // Reset horizon buffer so we don’t mix pre/post evolution windows
        atrScale.reset();
        
        return world;
    }

	public World getPopulation(int i)
	{
		return worlds[i];
	}
	
	public World[] getWorlds()
	{
		return this.worlds;
	}
}
