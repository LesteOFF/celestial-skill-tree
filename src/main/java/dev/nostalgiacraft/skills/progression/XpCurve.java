package dev.nostalgiacraft.skills.progression;

/**
 * nextPointCost(p) = ceil((base + linear*p + quadratic*p^2) * (1 - discount)), never below 1.
 * p is the lifetime number of points bought with XP; respecs and grants never lower it.
 */
public record XpCurve(int base, int linear, int quadratic, double discount) {
    public static final XpCurve DEFAULT = new XpCurve(100, 20, 2, 0.0);
    public static final int MAX_COST = 1_000_000_000;

    public XpCurve {
        if (base < 1 || linear < 0 || quadratic < 0) {
            throw new IllegalArgumentException("curve coefficients must be base>=1, linear>=0, quadratic>=0");
        }
        if (!(discount >= 0.0 && discount <= 0.9)) {
            throw new IllegalArgumentException("discount must be within 0..0.9");
        }
    }

    public int cost(int purchasedPoints) {
        long p = Math.max(0, purchasedPoints);
        double raw = base + (double) linear * p + (double) quadratic * p * p;
        double discounted = Math.ceil(raw * (1.0 - discount) - 1e-9);
        return (int) Math.max(1, Math.min(MAX_COST, discounted));
    }
}
