package it.l_soft.wows.indicators;

import it.l_soft.wows.comms.Price;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.regex.*;

public final class IndicatorFactory {

    // ------------ Parsing ------------

    // NAME(p1[,p2[,p3]][,PRICE])
    // NAME(p1[,p2[,p3]][,PRICE])
    private static final Pattern PAT = Pattern.compile(
        "^\\s*([A-Za-z0-9_.]+)\\s*\\(\\s*" +
        "(?:([-+]?[0-9]*\\.?[0-9]+)" +              // p1: optional integer or float
        "(?:\\s*,\\s*([-+]?[0-9]*\\.?[0-9]+))?" +   // p2
        "(?:\\s*,\\s*([-+]?[0-9]*\\.?[0-9]+))?" +   // p3
        "(?:\\s*,\\s*([A-Za-z_]+))?" +              // PRICE
        ")?\\s*\\)\\s*$"
    );

    public static final class Spec {
        public final String name;
        public final double[] params;
        public final Price price;

        public Spec(String name, double[] params, Price price) {
            this.name = name;
            this.params = params;
            this.price = price;
        }
    }

    public static Spec parse(String token) {
        Matcher m = PAT.matcher(token.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException(
                "Invalid indicator spec: " + token +
                " (expected NAME(p1[,p2[,p3]][,PRICE]))"
            );
        }

        String name = m.group(1);
        Double p1 = m.group(2) != null ? Double.parseDouble(m.group(2)) : null;
        Double p2 = m.group(3) != null ? Double.parseDouble(m.group(3)) : null;
        Double p3 = m.group(4) != null ? Double.parseDouble(m.group(4)) : null;
        String priceStr = m.group(5);

        List<Double> ps = new ArrayList<>();
        if (p1 != null) ps.add(p1);
        if (p2 != null) ps.add(p2);
        if (p3 != null) ps.add(p3);

        Price price = (priceStr == null || priceStr.isEmpty())
            ? null
            : Price.parsePrice(priceStr.trim());

        return new Spec(name, ps.stream().mapToDouble(d -> d).toArray(), price);
    }

    // ------------ Family resolution ------------

    private static final String BASE = "it.l_soft.wows.indicators";

    // Family base packages
    private static final Map<String,String> FAMILIES = Map.of(
        "trend",      BASE + ".trend",
        "momentum",   BASE + ".momentum",
        "volatility", BASE + ".volatility",
        "bands",      BASE + ".bands",
        "composite",  BASE + ".composite",
        "volume",     BASE + ".volume"
    );

    // Known simple-name → FQCN registry (case-insensitive keys)
    // Add here as you implement more indicators.
    private static final Map<String,String> REGISTRY;
    static {
        Map<String,String> m = new HashMap<>();
        // trend
        m.put("ema".toLowerCase(),           FAMILIES.get("trend") + ".EMA");
        m.put("sma".toLowerCase(),           FAMILIES.get("trend") + ".SMA");
        m.put("kama".toLowerCase(),          FAMILIES.get("trend") + ".KAMA");
        m.put("wma".toLowerCase(),           FAMILIES.get("trend") + ".WMA");
        m.put("aroon".toLowerCase(),         FAMILIES.get("trend") + ".Aroon");

        // momentum
        m.put("rsi".toLowerCase(),           FAMILIES.get("momentum") + ".RSI");
        m.put("roc".toLowerCase(),           FAMILIES.get("momentum") + ".ROC");
        m.put("williamsr".toLowerCase(),     FAMILIES.get("momentum") + ".WilliamsR");
        m.put("cci".toLowerCase(),           FAMILIES.get("momentum") + ".CCI");
        m.put("cmo".toLowerCase(),           FAMILIES.get("momentum") + ".CMO");
        m.put("trix".toLowerCase(),          FAMILIES.get("momentum") + ".TRIX");
        m.put("coppockcurve".toLowerCase(),  FAMILIES.get("momentum") + ".CoppockCurve");
        m.put("dpo".toLowerCase(),           FAMILIES.get("momentum") + ".DPO");
        m.put("awesomeoscillator".toLowerCase(), FAMILIES.get("momentum") + ".AwesomeOscillator");
        m.put("stochastick".toLowerCase(),   FAMILIES.get("momentum") + ".StochasticK");

        // volatility
        m.put("atr".toLowerCase(),           FAMILIES.get("volatility") + ".ATR");
        m.put("ulcerindex".toLowerCase(),    FAMILIES.get("volatility") + ".UlcerIndex");
        m.put("massindex".toLowerCase(),     FAMILIES.get("volatility") + ".MassIndex");

        // composite
        m.put("stochasticd".toLowerCase(),   FAMILIES.get("composite") + ".StochasticD");
        m.put("macd".toLowerCase(),          FAMILIES.get("composite") + ".MACD");

        // bands
        m.put("bollinger".toLowerCase(),     FAMILIES.get("bands") + ".Bollinger");
        m.put("keltner".toLowerCase(),       FAMILIES.get("bands") + ".Keltner");
        m.put("donchian".toLowerCase(),      FAMILIES.get("bands") + ".Donchian");

        // volume
        m.put("obv".toLowerCase(),           FAMILIES.get("volume") + ".OBV");
        m.put("mfi".toLowerCase(),           FAMILIES.get("volume") + ".MFI");

        REGISTRY = Collections.unmodifiableMap(m);
    }

    private static String resolveFqcn(String nameOrPath) {
        String n = nameOrPath.trim();

        // Already a full FQCN?
        if (n.startsWith(BASE + ".")) return n;

        // If user supplied family prefix like "momentum.StochasticK"
        if (n.contains(".")) {
            // treat as <family>.<Class>
            String fam = n.substring(0, n.lastIndexOf('.'));
            String cls = n.substring(n.lastIndexOf('.') + 1);
            String famPkg = FAMILIES.get(fam.toLowerCase());
            if (famPkg != null) return famPkg + "." + cls;
            // else: allow truly fully qualified external names
            if (n.contains(".")) return n; // assume FQCN
        }

        // Plain short name -> registry
        String fqcn = REGISTRY.get(n.toLowerCase());
        if (fqcn != null) return fqcn;

        // Last resort: try each family base with provided class name
        for (String famPkg : FAMILIES.values()) {
            String candidate = famPkg + "." + n;
            try {
                Class.forName(candidate);
                return candidate;
            } catch (ClassNotFoundException ignore) {}
        }

        throw new IllegalArgumentException("Unknown indicator name: " + nameOrPath);
    }

    // ------------ Construction ------------

    public static Indicator create(Spec spec) {
        String fqcn = resolveFqcn(spec.name);
        try {
            Class<?> raw = Class.forName(fqcn);
            if (!Indicator.class.isAssignableFrom(raw)) {
                throw new IllegalArgumentException(fqcn + " does not implement Indicator");
            }
            @SuppressWarnings("unchecked")
            Class<? extends Indicator> clazz = (Class<? extends Indicator>) raw;

            double[] p = spec.params;
            Price pr = spec.price;

            // ======== Constructors WITH Price ========

            // (double,double,double,Price)
            if (p.length == 3 && pr != null) {
                try { return ctor(clazz,
                        new Class[]{double.class,double.class,double.class,Price.class},
                        p[0], p[1], p[2], pr);
                } catch (NoSuchMethodException ignored) {}
            }
            // (int,int,int,Price)
            if (p.length == 3 && pr != null) {
                try { return ctor(clazz,
                        new Class[]{int.class,int.class,int.class,Price.class},
                        (int)p[0], (int)p[1], (int)p[2], pr);
                } catch (NoSuchMethodException ignored) {}
            }

            // (double,double,Price)
            if (p.length == 2 && pr != null) {
                try { return ctor(clazz,
                        new Class[]{double.class,double.class,Price.class},
                        p[0], p[1], pr);
                } catch (NoSuchMethodException ignored) {}
            }
            // (int,int,Price)
            if (p.length == 2 && pr != null) {
                try { return ctor(clazz,
                        new Class[]{int.class,int.class,Price.class},
                        (int)p[0], (int)p[1], pr);
                } catch (NoSuchMethodException ignored) {}
            }

            // (double,Price)
            if (p.length == 1 && pr != null) {
                try { return ctor(clazz,
                        new Class[]{double.class,Price.class},
                        p[0], pr);
                } catch (NoSuchMethodException ignored) {}
            }
            // (int,Price)
            if (p.length == 1 && pr != null) {
                try { return ctor(clazz,
                        new Class[]{int.class,Price.class},
                        (int)p[0], pr);
                } catch (NoSuchMethodException ignored) {}
            }

            // ======== Constructors WITHOUT Price ========

            // 3 params: (double,double,double)
            if (p.length == 3) {
                try { return ctor(clazz,
                        new Class[]{double.class,double.class,double.class},
                        p[0], p[1], p[2]);
                } catch (NoSuchMethodException ignored) {}
            }
            // 3 params: (int,int,int)
            if (p.length == 3) {
                try { return ctor(clazz,
                        new Class[]{int.class,int.class,int.class},
                        (int)p[0], (int)p[1], (int)p[2]);
                } catch (NoSuchMethodException ignored) {}
            }

            // 2 params: (double,double)
            if (p.length == 2) {
                try { return ctor(clazz,
                        new Class[]{double.class,double.class},
                        p[0], p[1]);
                } catch (NoSuchMethodException ignored) {}
            }
            // 2 params: (int,int)   <-- this is what StochasticD(int,int) needs
            if (p.length == 2) {
                try { return ctor(clazz,
                        new Class[]{int.class,int.class},
                        (int)p[0], (int)p[1]);
                } catch (NoSuchMethodException ignored) {}
            }

            // 1 param: (double)
            if (p.length == 1) {
                try { return ctor(clazz,
                        new Class[]{double.class},
                        p[0]);
                } catch (NoSuchMethodException ignored) {}
            }
            // 1 param: (int)
            if (p.length == 1) {
                try { return ctor(clazz,
                        new Class[]{int.class},
                        (int)p[0]);
                } catch (NoSuchMethodException ignored) {}
            }

            // () no-arg
            try { return ctor(clazz, new Class[]{}); } catch (NoSuchMethodException ignored) {}

            // If we reach here, list constructors to help debug
            StringBuilder sb = new StringBuilder("No matching constructor for ")
                    .append(fqcn).append("\nAvailable:\n");
            for (Constructor<?> c : clazz.getConstructors()) {
                sb.append("  ").append(c).append('\n');
            }
            throw new RuntimeException(sb.toString());

        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create indicator " + fqcn, e);
        }
    }
    private static Indicator ctor(Class<? extends Indicator> clazz, Class<?>[] sig, Object... args) throws Exception {
        Constructor<? extends Indicator> c = clazz.getConstructor(sig);
        return c.newInstance(args);
    }

    // Convenience: create directly from config token like "EMA(14,CLOSE)"
    public static Indicator createFromToken(String token) {
        return create(parse(token));
    }
}
