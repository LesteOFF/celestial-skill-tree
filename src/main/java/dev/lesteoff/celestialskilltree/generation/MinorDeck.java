package dev.lesteoff.celestialskilltree.generation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

final class MinorDeck {
    private static final int TRUNK_WEIGHT = 10;

    private final Map<String, Integer> copiesLeft = new LinkedHashMap<>();
    private final Map<String, Integer> reserved = new HashMap<>();
    private final Map<String, Integer> timesUsed = new HashMap<>();
    private final Map<String, Integer> weights = new LinkedHashMap<>();
    private final Random random;

    MinorDeck(ClassPool pool, int maxCopies, Random random) {
        this.random = random;
        add(TreeDraw.TRUNK_SKILL, TRUNK_WEIGHT, maxCopies);
        pool.themes().stream().sorted(Comparator.comparing(ThemeDefinition::id))
                .forEach(theme -> add(theme.id(), theme.weight(), maxCopies));
    }

    static int capacity(ClassPool pool, int maxCopies) {
        return (pool.themes().size() + 1) * maxCopies;
    }

    void reserve(String skill) {
        if (hasCopies(skill)) {
            copiesLeft.merge(skill, -1, Integer::sum);
            reserved.merge(skill, 1, Integer::sum);
        }
    }

    List<String> varied(int length, Map<Integer, String> wantedAt, List<String> reservedForThisRun, List<String> previous) {
        reservedForThisRun.forEach(this::release);
        List<String> sequence = new ArrayList<>();
        for (int position = 0; position < length; position++) {
            int current = position;
            String before = position < previous.size() ? previous.get(position) : null;
            Set<String> keptForLater = new HashSet<>();
            wantedAt.forEach((wantedPosition, skill) -> {
                if (wantedPosition > current) {
                    keptForLater.add(skill);
                }
            });
            String chosen = pick(before, wantedAt.get(position), sequence, keptForLater);
            copiesLeft.merge(chosen, -1, Integer::sum);
            timesUsed.merge(chosen, 1, Integer::sum);
            sequence.add(chosen);
        }
        return List.copyOf(sequence);
    }

    private void add(String skill, int weight, int maxCopies) {
        copiesLeft.put(skill, maxCopies);
        weights.put(skill, weight);
    }

    private void release(String skill) {
        if (reserved.getOrDefault(skill, 0) > 0) {
            reserved.merge(skill, -1, Integer::sum);
            copiesLeft.merge(skill, 1, Integer::sum);
        }
    }

    private String pick(String before, String wanted, List<String> sequence, Set<String> keptForLater) {
        if (isNewIn(wanted, sequence)) {
            return wanted;
        }
        return staysFrom(before, sequence, keptForLater) ? before : leastUsed(sequence, keptForLater);
    }

    private boolean staysFrom(String before, List<String> sequence, Set<String> keptForLater) {
        boolean repeatsNeighbour = !sequence.isEmpty() && sequence.get(sequence.size() - 1).equals(before);
        return before != null && hasCopies(before) && !repeatsNeighbour && !keptForLater.contains(before);
    }

    private boolean isNewIn(String skill, List<String> sequence) {
        return skill != null && !sequence.contains(skill) && hasCopies(skill);
    }

    private boolean hasCopies(String skill) {
        return copiesLeft.getOrDefault(skill, 0) > 0;
    }

    private String leastUsed(List<String> sequence, Set<String> keptForLater) {
        String last = sequence.isEmpty() ? null : sequence.get(sequence.size() - 1);
        List<String> allowed = firstNonEmpty(
                skills(skill -> isNewIn(skill, sequence) && !keptForLater.contains(skill)),
                skills(skill -> hasCopies(skill) && !skill.equals(last) && !keptForLater.contains(skill)),
                skills(skill -> hasCopies(skill) && !skill.equals(last)),
                skills(this::hasCopies));
        int fewestUses = allowed.stream().mapToInt(this::timesUsed).min()
                .orElseThrow(() -> new IllegalStateException("the class has more minor skills than its themes allow"));
        List<String> candidates = allowed.stream().filter(skill -> timesUsed(skill) == fewestUses).toList();
        int roll = random.nextInt(candidates.stream().mapToInt(weights::get).sum());
        for (String candidate : candidates) {
            roll -= weights.get(candidate);
            if (roll < 0) {
                return candidate;
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    private int timesUsed(String skill) {
        return timesUsed.getOrDefault(skill, 0);
    }

    private List<String> skills(Predicate<String> accepted) {
        return copiesLeft.keySet().stream().filter(accepted).toList();
    }

    @SafeVarargs
    private static List<String> firstNonEmpty(List<String>... choices) {
        for (List<String> choice : choices) {
            if (!choice.isEmpty()) {
                return choice;
            }
        }
        return List.of();
    }
}
