package dev.nostalgiacraft.skills.generation;

import dev.nostalgiacraft.skills.generation.TreeDraw.ClassDraw;
import dev.nostalgiacraft.skills.generation.TreeDraw.NotableChoice;
import dev.nostalgiacraft.skills.generation.TreeDraw.RibDraw;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;

final class TreeDrawer {
    private static final long SEED_SPREAD = 31;
    private static final int MIN_SLOTS_TO_OPEN = 3;
    private static final int MIN_SLOTS_FOR_A_MIDDLE = 7;

    private record Candidate(NotableChoice choice, int weight) {
    }

    private TreeDrawer() {
    }

    static String ribKey(Rib rib) {
        return rib.ring() + rib.side().label();
    }

    static int minorSlots(ClassPool pool, ClassShape shape) {
        int ribSlots = shape.ribs().stream().mapToInt(rib -> rib.slots().size()).sum();
        return shape.trunk().size() + ribSlots - Math.min(notableCapacity(shape), pairs(pool).size());
    }

    static int notableCapacity(ClassShape shape) {
        return shape.ribs().size() + ribsWithRoom(shape, MIN_SLOTS_TO_OPEN).size() + ribsWithRoom(shape, MIN_SLOTS_FOR_A_MIDDLE).size();
    }

    static int notableCount(ClassPool pool) {
        return pairs(pool).size();
    }

    private static List<Rib> ribsWithRoom(ClassShape shape, int minSlots) {
        List<Rib> roomy = new ArrayList<>(shape.ribs().stream().filter(rib -> rib.slots().size() >= minSlots).toList());
        Collections.reverse(roomy);
        return roomy;
    }

    static ClassDraw draw(ClassPool pool, ClassShape shape, int maxCopies, long seed, Optional<ClassDraw> previous) {
        Random random = new Random(seed * SEED_SPREAD + pool.definition().id().hashCode());
        Map<String, RibDraw> ribsBefore = previous.map(ClassDraw::ribs).orElse(Map.of());
        List<Candidate> unused = pairs(pool);
        Map<String, NotableChoice> notables = drawNotables(unused, shape.ribs(), random, ribsBefore, RibDraw::notable);
        Map<String, NotableChoice> openings = drawNotables(unused, ribsWithRoom(shape, MIN_SLOTS_TO_OPEN), random, ribsBefore,
                RibDraw::opening);
        Map<String, NotableChoice> middles = drawNotables(unused, ribsWithRoom(shape, MIN_SLOTS_FOR_A_MIDDLE), random, ribsBefore,
                RibDraw::middle);
        List<String> keystones = drawKeystones(pool, shape.keystones().size(), random,
                previous.map(ClassDraw::keystones).orElse(List.of()));
        MinorDeck deck = new MinorDeck(pool, maxCopies, random);
        notables.values().forEach(choice -> deck.reserve(choice.theme()));
        openings.values().forEach(choice -> deck.reserve(choice.theme()));
        middles.values().forEach(choice -> deck.reserve(choice.theme()));
        List<String> trunk = deck.varied(shape.trunk().size(), Map.of(0, TreeDraw.TRUNK_SKILL), List.of(),
                previous.map(ClassDraw::trunk).orElse(List.of()));
        Map<String, RibDraw> ribs = new LinkedHashMap<>();
        for (Rib rib : shape.ribs()) {
            Optional<NotableChoice> notable = Optional.ofNullable(notables.get(ribKey(rib)));
            Optional<NotableChoice> opening = Optional.ofNullable(openings.get(ribKey(rib)));
            Optional<NotableChoice> middle = Optional.ofNullable(middles.get(ribKey(rib)));
            RibDraw placed = new RibDraw(opening, middle, notable, List.of());
            int minors = rib.slots().size() - placed.notableCount();
            List<String> before = Optional.ofNullable(ribsBefore.get(ribKey(rib))).map(RibDraw::minors).orElse(List.of());
            List<String> drawn = deck.varied(minors, skillsBesideNotables(rib, placed, minors), themesOf(placed), before);
            ribs.put(ribKey(rib), new RibDraw(opening, middle, notable, drawn));
        }
        return new ClassDraw(pool.definition().id(), trunk, ribs, keystones);
    }

    private static List<String> themesOf(RibDraw placed) {
        return Stream.of(placed.opening(), placed.middle(), placed.notable()).flatMap(Optional::stream).map(NotableChoice::theme).toList();
    }

    private static Map<Integer, String> skillsBesideNotables(Rib rib, RibDraw placed, int minors) {
        Map<Integer, String> wanted = new HashMap<>();
        int openingSlots = placed.opening().isPresent() ? 1 : 0;
        placed.opening().ifPresent(choice -> wanted.put(0, choice.theme()));
        placed.middle().ifPresent(choice -> wanted.put(rib.middleIndex() - 1 - openingSlots, choice.theme()));
        placed.notable().ifPresent(choice -> wanted.put(minors - 1, choice.theme()));
        wanted.keySet().removeIf(position -> position < 0 || position >= minors);
        return wanted;
    }

    private static Map<String, NotableChoice> drawNotables(List<Candidate> unused, List<Rib> ribs, Random random,
                                                          Map<String, RibDraw> previous,
                                                          Function<RibDraw, Optional<NotableChoice>> placeInRib) {
        Map<String, NotableChoice> chosen = new HashMap<>();
        for (Rib rib : ribs) {
            Optional<NotableChoice> before = Optional.ofNullable(previous.get(ribKey(rib))).flatMap(placeInRib);
            if (before.isPresent() && unused.removeIf(candidate -> candidate.choice().equals(before.get()))) {
                chosen.put(ribKey(rib), before.get());
            }
        }
        for (Rib rib : ribs) {
            if (!chosen.containsKey(ribKey(rib)) && !keptEmpty(previous, rib, placeInRib) && !unused.isEmpty()) {
                chosen.put(ribKey(rib), take(unused, random, Candidate::weight).choice());
            }
        }
        return chosen;
    }

    private static boolean keptEmpty(Map<String, RibDraw> previous, Rib rib, Function<RibDraw, Optional<NotableChoice>> placeInRib) {
        return Optional.ofNullable(previous.get(ribKey(rib))).filter(before -> placeInRib.apply(before).isEmpty()).isPresent();
    }

    private static List<Candidate> pairs(ClassPool pool) {
        List<Candidate> pairs = new ArrayList<>();
        pool.themes().stream().sorted(Comparator.comparing(ThemeDefinition::id)).forEach(theme -> theme.notables()
                .forEach(notable -> pairs.add(new Candidate(new NotableChoice(theme.id(), notable.id()), theme.weight()))));
        return pairs;
    }

    private static List<String> drawKeystones(ClassPool pool, int count, Random random, List<String> previous) {
        List<KeystoneDefinition> unused = new ArrayList<>(pool.keystones());
        unused.sort(Comparator.comparing(KeystoneDefinition::id));
        String[] chosen = new String[count];
        for (int position = 0; position < Math.min(count, previous.size()); position++) {
            String before = previous.get(position);
            if (unused.removeIf(keystone -> keystone.id().equals(before))) {
                chosen[position] = before;
            }
        }
        for (int position = 0; position < count; position++) {
            if (chosen[position] == null) {
                chosen[position] = take(unused, random, KeystoneDefinition::weight).id();
            }
        }
        return List.of(chosen);
    }

    private static <T> T take(List<T> candidates, Random random, ToIntFunction<T> weight) {
        int roll = random.nextInt(candidates.stream().mapToInt(weight).sum());
        for (int index = 0; index < candidates.size(); index++) {
            roll -= weight.applyAsInt(candidates.get(index));
            if (roll < 0) {
                return candidates.remove(index);
            }
        }
        throw new IllegalStateException("weights changed while drawing");
    }
}
