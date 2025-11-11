package it.l_soft.wows.ga;

import it.l_soft.wows.comms.Bar;
import it.l_soft.wows.indicators.Indicator;
import it.l_soft.wows.indicators.volatility.ATR;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class GAEngine {
    private final GAConfig cfg;
    private final List<Indicator> catalog; // all available indicator *prototypes* (NOT shared state!)
    private final List<Gene> population = new ArrayList<>();

    // Shared ATR for normalization scaling
    private final ATR atrScale;

    // For horizon scoring, keep a ring of past closes (or compute pct when horizon fulfilled)
    private final Deque<Double> closeRing = new ArrayDeque<>();

    public GAEngine(GAConfig cfg, List<Indicator> indicatorCatalog) {
        this.cfg = cfg;
        this.catalog = indicatorCatalog;
        this.atrScale = new ATR(cfg.atrPeriodForScaling);
        initPopulation();
    }

    private void initPopulation() {
        population.clear();
        for (int i = 0; i < cfg.populationSize; i++) {
            population.add(randomGene());
        }
    }

    private Gene randomGene() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Indicator[] inds = new Indicator[cfg.geneSize];
        for (int i = 0; i < cfg.geneSize; i++) {
            // pick a random catalog member and clone it by reflection (fresh state)
            Indicator proto = catalog.get(r.nextInt(catalog.size()));
            inds[i] = cloneIndicator(proto);
        }
        return new Gene(inds);
    }

    // Shallow clone via no-arg/new matching constructor. If your indicators don’t have no-arg,
    // consider keeping a Supplier<Indicator> factory alongside the catalog instead.
    private Indicator cloneIndicator(Indicator proto) {
        try {
            return proto.getClass().getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("Indicator must have no-arg ctor for GA cloning: " + proto.getClass(), e);
        }
    }

    /** Feed one bar into GA (and all genes). Returns whether an evolution step was performed. */
    public boolean onBar(Bar bar) {
        // 1) update shared ATR for normalization
        atrScale.add(bar);

        // 2) push close for horizon scoring
        closeRing.addLast(bar.getClose());
        if (closeRing.size() > cfg.horizonBars + 1) closeRing.removeFirst();

        // 3) evaluate each gene on this bar
        for (Gene g : population) {
            g.barsSeen++;

            double sum = 0.0;
            for (Indicator ind : g.indicators) {
                double s = ind.add(bar);
                // s is raw; now normalize using shared ATR and current bar
                double norm = SignalNormalizer.normalize(ind, bar, atrScale, cfg);
                sum += norm;
            }
            // Decision made now, but we score it only when horizon closes.
            // Store decision per gene temporarily in a field if you want.
            // For simplicity we score when horizon data available:
            if (closeRing.size() == cfg.horizonBars + 1) {
                double c0 = closeRing.peekFirst();
                double cH = closeRing.peekLast();
                double pct = (cH - c0) / c0 * 100.0;

                int delta = Scoring.score(Scoring.decide(sum), pct, cfg.holdThresholdPct);
                g.score += delta;
            }
        }

        // 4) Optional: early cull if a gene sinks below the current elite floor
        if (cfg.earlyCullAtEliteFloor && shouldEvolveNow()) {
            evolve();
            return true;
        }
        return false;
    }

    private boolean shouldEvolveNow() {
        // Example trigger: every 250 bars *and* every gene saw enough bars
        // You can wire a timer/iteration counter; here keep it simple:
        return population.stream().allMatch(g -> g.barsSeen >= cfg.minBarsBeforeScoring);
    }

    public List<Gene> ranked() {
        List<Gene> list = new ArrayList<>(population);
        list.sort(Comparator.comparingInt((Gene g) -> g.score).reversed());
        return list;
    }

    /** Selection: top keep, middle crossover, bottom replaced. */
    public void evolve() {
        List<Gene> rank = ranked();
        int n = rank.size();
        int keep = (int) Math.round(n * cfg.elitePct);
        int cross= (int) Math.round(n * cfg.crossoverPct);
        int repl = n - keep - cross;

        List<Gene> next = new ArrayList<>(n);

        // 1) Elites
        for (int i = 0; i < keep; i++) {
            next.add(rank.get(i).copyShallow());
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
        Indicator[] child = new Indicator[cfg.geneSize];
        int cut = rnd.nextInt(cfg.geneSize); // single-point
        for (int i = 0; i < cfg.geneSize; i++) {
            Indicator proto = (i < cut ? a.indicators[i] : b.indicators[i]);
            child[i] = cloneIndicator(proto);
        }
        return new Gene(child);
    }

    private void mutate(Gene g, ThreadLocalRandom rnd) {
        if (rnd.nextDouble() > cfg.mutationRate) return;

        // swap two loci sometimes
        if (rnd.nextDouble() < cfg.mutationSwapRate && g.indicators.length >= 2) {
            int i = rnd.nextInt(g.indicators.length);
            int j = rnd.nextInt(g.indicators.length);
            Indicator tmp = g.indicators[i]; g.indicators[i] = g.indicators[j]; g.indicators[j] = tmp;
        } else {
            // replace one locus with a random indicator from the catalog
            int pos = rnd.nextInt(g.indicators.length);
            Indicator proto = catalog.get(rnd.nextInt(catalog.size()));
            g.indicators[pos] = cloneIndicator(proto);
        }
    }

    private void resetIndicators(Gene g) {
        for (Indicator ind : g.indicators) ind.reset();
    }
}
