package dev.nostalgiacraft.skills.progression;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Authoritative per-player progression state (persisted as a data attachment).
 * Ledger invariant: {@code availablePoints + activeSpentPoints() == purchasedPoints + netGrantedPoints}.
 */
public final class PlayerSkillState {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_AUDIT = 32;

    private String treeId = "nostalgiaskills:main";
    private String startingRoot;
    private final Map<String, Allocation> allocations = new LinkedHashMap<>();
    private long nextAcquisition;
    private int purchasedPoints;
    private int netGrantedPoints;
    private int availablePoints;
    private long lifetimeSpent;
    private long lifetimeRefunded;
    private long stateRevision;
    private String reconciledFingerprint = "";
    private final Set<String> migrationMarkers = new TreeSet<>();
    private final Deque<String> audit = new ArrayDeque<>();

    public PlayerSkillState() {
    }

    /** Full restore used by deserialisation; validates the ledger before accepting the data. */
    public static PlayerSkillState restore(String treeId, String startingRoot, Collection<Allocation> allocations,
                                           long nextAcquisition, int purchased, int netGranted, int available,
                                           long lifetimeSpent, long lifetimeRefunded, long revision,
                                           String fingerprint, Collection<String> markers, Collection<String> audit) {
        PlayerSkillState s = new PlayerSkillState();
        s.treeId = treeId;
        s.startingRoot = startingRoot;
        allocations.stream().sorted(Comparator.comparingLong(Allocation::acquisitionIndex))
                .forEach(a -> s.allocations.put(a.nodeId(), a));
        s.nextAcquisition = Math.max(nextAcquisition,
                allocations.stream().mapToLong(Allocation::acquisitionIndex).max().orElse(-1) + 1);
        s.purchasedPoints = purchased;
        s.netGrantedPoints = netGranted;
        s.availablePoints = available;
        s.lifetimeSpent = lifetimeSpent;
        s.lifetimeRefunded = lifetimeRefunded;
        s.stateRevision = revision;
        s.reconciledFingerprint = fingerprint == null ? "" : fingerprint;
        s.migrationMarkers.addAll(markers);
        audit.forEach(s.audit::addLast);
        if (!s.ledgerHolds()) {
            // Repair by treating the discrepancy as an administrative adjustment, never by touching purchases.
            s.netGrantedPoints = s.availablePoints + s.activeSpentPoints() - s.purchasedPoints;
            s.log("ledger repaired on load");
        }
        return s;
    }

    public PlayerSkillState copy() {
        return restore(treeId, startingRoot, allocations.values(), nextAcquisition, purchasedPoints,
                netGrantedPoints, availablePoints, lifetimeSpent, lifetimeRefunded, stateRevision,
                reconciledFingerprint, migrationMarkers, audit);
    }

    // ------------------------------------------------------------ queries

    public String treeId() {
        return treeId;
    }

    public Optional<String> startingRoot() {
        return Optional.ofNullable(startingRoot);
    }

    public Map<String, Allocation> allocations() {
        return java.util.Collections.unmodifiableMap(allocations);
    }

    public boolean isAllocated(String id) {
        return allocations.containsKey(id);
    }

    /** Allocations ordered by acquisition (oldest first), ties broken by id. */
    public List<Allocation> orderedAllocations() {
        List<Allocation> l = new ArrayList<>(allocations.values());
        l.sort(Comparator.comparingLong(Allocation::acquisitionIndex).thenComparing(Allocation::nodeId));
        return l;
    }

    public int purchasedPoints() {
        return purchasedPoints;
    }

    public int netGrantedPoints() {
        return netGrantedPoints;
    }

    public int availablePoints() {
        return availablePoints;
    }

    public int activeSpentPoints() {
        return allocations.values().stream().mapToInt(Allocation::paidCost).sum();
    }

    public int totalPoints() {
        return purchasedPoints + netGrantedPoints;
    }

    public long lifetimeSpent() {
        return lifetimeSpent;
    }

    public long lifetimeRefunded() {
        return lifetimeRefunded;
    }

    public long stateRevision() {
        return stateRevision;
    }

    public long nextAcquisition() {
        return nextAcquisition;
    }

    public String reconciledFingerprint() {
        return reconciledFingerprint;
    }

    public Set<String> migrationMarkers() {
        return java.util.Collections.unmodifiableSet(migrationMarkers);
    }

    public List<String> audit() {
        return List.copyOf(audit);
    }

    public boolean ledgerHolds() {
        return availablePoints >= 0 && availablePoints + activeSpentPoints() == purchasedPoints + netGrantedPoints;
    }

    // ------------------------------------------------------------ mutations (package-private: services only)

    void allocate(String id, int paidCost) {
        allocations.put(id, new Allocation(id, 1, paidCost, nextAcquisition++));
        availablePoints -= paidCost;
        lifetimeSpent += paidCost;
    }

    /** Removes an allocation and refunds exactly its recorded paid cost. */
    int refund(String id) {
        Allocation a = allocations.remove(id);
        if (a == null) {
            return 0;
        }
        availablePoints += a.paidCost();
        lifetimeRefunded += a.paidCost();
        if (id.equals(startingRoot)) {
            startingRoot = null;
        }
        return a.paidCost();
    }

    void rename(String from, String to) {
        Allocation a = allocations.remove(from);
        if (a != null) {
            allocations.put(to, new Allocation(to, a.rank(), a.paidCost(), a.acquisitionIndex()));
            if (from.equals(startingRoot)) {
                startingRoot = to;
            }
        }
    }

    void setStartingRoot(String id) {
        this.startingRoot = id;
    }

    void addPurchasedPoint() {
        purchasedPoints++;
        availablePoints++;
    }

    void adjustGranted(int delta) {
        netGrantedPoints += delta;
        availablePoints += delta;
    }

    void bumpRevision() {
        stateRevision++;
    }

    void setReconciledFingerprint(String fp) {
        reconciledFingerprint = fp;
    }

    void addMarker(String marker) {
        migrationMarkers.add(marker);
    }

    void log(String line) {
        audit.addLast(line);
        while (audit.size() > MAX_AUDIT) {
            audit.removeFirst();
        }
    }
}
