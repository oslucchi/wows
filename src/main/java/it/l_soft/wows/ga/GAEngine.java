package it.l_soft.wows.ga;

import it.l_soft.wows.ApplicationProperties;
import it.l_soft.wows.indicators.Indicator;
import it.l_soft.wows.indicators.volatility.ATR;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.log4j.Logger;

public final class GAEngine {
	private final Logger log = Logger.getLogger(this.getClass());
	ApplicationProperties props = ApplicationProperties.getInstance();
    private final List<Indicator> catalog; // all available indicator *prototypes* (NOT shared state!)
    private final List<Gene> population = new ArrayList<>();

    // Shared ATR for normalization scaling
    private final ATR atrScale;

    // For horizon scoring, keep a ring of past closes (or compute pct when horizon fulfilled)
    private final Deque<Double> closeRing = new ArrayDeque<>();

    public GAEngine(List<Indicator> indicatorCatalog) {
        this.catalog = indicatorCatalog;
        this.atrScale = new ATR(props.getAtrPeriodForScaling());
        initPopulation();
    }

    private void initPopulation() {
        population.clear();
        for (int i = 0; i < props.getPopulationSize(); i++) {
            population.add(randomGene());
        }
    }

    private Gene randomGene() {
    	String geneIndicators = "";
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int[] indIdx = new int[props.getGeneSize()];
        for (int i = 0; i < props.getGeneSize(); i++) {
        	// use the n-th indicator in the indicators list
        	// theoretically an indicator could appear more than once in a gene
        	indIdx[i] = r.nextInt(catalog.size());
        	geneIndicators += catalog.get(indIdx[i]).getName() + " ";
        }
        Gene g = new Gene(indIdx);
        log.debug("Indicators selected for the current gene are: " + geneIndicators);
        g.name = geneIndicators;
        return g;
    }
 
/*
    private boolean shouldEvolveNow() {
        // Example trigger: every 250 bars *and* every gene saw enough bars
        // You can wire a timer/iteration counter; here keep it simple:
        return population.stream().allMatch(g -> g.warmedUp());
    }
*/
    
    public List<Gene> ranked() {
        List<Gene> list = new ArrayList<Gene>(population);
        list.sort(Comparator.comparingInt((Gene g) -> g.score).reversed());
        return list;
    }

    /** Selection: top keep, middle crossover, bottom replaced. */
    public void evolve() {
        List<Gene> rank = ranked();
        int n = rank.size();
        int keep = (int) Math.round(n * props.getElitePct());
        int cross= (int) Math.round(n * props.getCrossoverPct());

        List<Gene> next = new ArrayList<Gene>(n);

        // 1) Elites
        for (int i = 0; i < keep; i++) {
            next.add(rank.get(i));
        }

        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        // 2) Crossover (produce 'cross' children)
        while (next.size() < keep + cross) {
            Gene p1 = tournament(rank, 4, rnd);
            Gene p2 = tournament(rank, 4, rnd);
            Gene child = crossover(p1, p2, rnd);
            mutate(child, rnd);
            child.score = 0; child.barsSeen = 0; // reset
            resetIndicators(child);
            next.add(child);
        }

        // 3) Replacement (random new)
        while (next.size() < n) {
            next.add(randomGene());
        }

        population.clear();
        population.addAll(next);
        // Reset horizon buffer so we don’t mix pre/post evolution windows
        closeRing.clear();
        atrScale.reset();
    }

    private Gene tournament(List<Gene> rank, int k, ThreadLocalRandom rnd) {
        Gene best = null;
        for (int i = 0; i < k; i++) {
            Gene g = rank.get(rnd.nextInt(rank.size()));
            if (best == null || g.score > best.score) best = g;
        }
        return best;
    }

    private Gene crossover(Gene a, Gene b, ThreadLocalRandom rnd) {
        int[] child = new int[props.getGeneSize()];
        int cut = rnd.nextInt(props.getGeneSize()); // single-point
        for (int i = 0; i < props.getGeneSize(); i++) {
            int proto = (i < cut ? a.indicatorsIndex[i] : b.indicatorsIndex[i]);
            child[i] = proto;
        }
        return new Gene(child);
    }

    private void mutate(Gene g, ThreadLocalRandom rnd) {
        if (rnd.nextDouble() > props.getMutationRate()) return;
/*
        // swap two loci sometimes
        if (rnd.nextDouble() < props.getMutationSwapRate() && g.indicators.length >= 2) {
            int i = rnd.nextInt(g.indicators.length);
            int j = rnd.nextInt(g.indicators.length);
            Indicator tmp = g.indicators[i]; g.indicators[i] = g.indicators[j]; g.indicators[j] = tmp;
        } else {
            // replace one locus with a random indicator from the catalog
            int pos = rnd.nextInt(g.indicators.length);
            Indicator proto = catalog.get(rnd.nextInt(catalog.size()));
            g.indicators[pos] = cloneIndicator(proto);
        }
*/
    }

    private void resetIndicators(Gene g) {
//        for (Indicator ind : g.indicators) ind.reset();
    }
    
    
    public List<Gene> getPopulation() {
    	return population;
    }
}
