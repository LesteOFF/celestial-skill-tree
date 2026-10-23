package dev.lesteoff.celestialskilltree.progression;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public final class PlayerSkillState {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_AUDIT = 32;

    private String treeId = "celestialskilltree:main";
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
    private String sign;
    private final Set<String> discoveries = new TreeSet<>();
    private final Set<String> disabled = new TreeSet<>();
    private final Set<String> migrationMarkers = new TreeSet<>();
    private final Deque<String> audit = new ArrayDeque<>();
    private boolean repairedOnLoad;

    public PlayerSkillState() {
    }

    public static PlayerSkillState restore(String treeId, String startingRoot, Collection<Allocation> allocations,
                                           long nextAcquisition, int purchased, int netGranted, int available,
                                           long lifetimeSpent, long lifetimeRefunded, long revision,
                                           String fingerprint, Collection<String> markers, Collection<String> audit) {
        PlayerSkillState state = new PlayerSkillState();
        state.treeId = treeId;
        state.startingRoot = startingRoot;
        allocations.stream().sorted(Comparator.comparingLong(Allocation::acquisitionIndex))
                .forEach(a -> state.allocations.put(a.nodeId(), a));
        state.nextAcquisition = Math.max(nextAcquisition,
                allocations.stream().mapToLong(Allocation::acquisitionIndex).max().orElse(-1) + 1);
        state.purchasedPoints = purchased;
        state.netGrantedPoints = netGranted;
        state.availablePoints = available;
        state.lifetimeSpent = lifetimeSpent;
        state.lifetimeRefunded = lifetimeRefunded;
        state.stateRevision = revision;
        state.reconciledFingerprint = fingerprint == null ? "" : fingerprint;
        state.migrationMarkers.addAll(markers);
        audit.forEach(state.audit::addLast);
        if (!state.ledgerHolds()) {
            state.repairLedger();
        }
        return state;
    }

    private void repairLedger() {
        availablePoints = Math.max(0, availablePoints);
        netGrantedPoints = availablePoints + activeSpentPoints() - purchasedPoints;
        repairedOnLoad = true;
        log("ledger repaired on load");
    }

    public boolean repairedOnLoad() {
        return repairedOnLoad;
    }

    public PlayerSkillState forClient() {
        PlayerSkillState view = copy();
        view.audit.clear();
        view.migrationMarkers.clear();
        return view;
    }

    public PlayerSkillState copy() {
        PlayerSkillState copy = restore(treeId, startingRoot, allocations.values(), nextAcquisition, purchasedPoints,
                netGrantedPoints, availablePoints, lifetimeSpent, lifetimeRefunded, stateRevision,
                reconciledFingerprint, migrationMarkers, audit);
        copy.sign = sign;
        copy.discoveries.addAll(discoveries);
        copy.disabled.addAll(disabled);
        return copy;
    }

    public PlayerSkillState withRestoredSign(Optional<String> restoredSign) {
        sign = restoredSign.orElse(null);
        return this;
    }

    public Optional<String> sign() {
        return Optional.ofNullable(sign);
    }

    public PlayerSkillState withRestoredDiscoveries(Collection<String> restoredDiscoveries) {
        discoveries.addAll(restoredDiscoveries);
        return this;
    }

    public Set<String> discoveries() {
        return Collections.unmodifiableSet(discoveries);
    }

    public PlayerSkillState withRestoredDisabled(Collection<String> restoredDisabled) {
        restoredDisabled.stream().filter(allocations::containsKey).forEach(disabled::add);
        return this;
    }

    public Set<String> disabled() {
        return Collections.unmodifiableSet(disabled);
    }

    public boolean isDisabled(String id) {
        return disabled.contains(id);
    }

    public String treeId() {
        return treeId;
    }

    public Optional<String> startingRoot() {
        return Optional.ofNullable(startingRoot);
    }

    public Map<String, Allocation> allocations() {
        return Collections.unmodifiableMap(allocations);
    }

    public boolean isAllocated(String id) {
        return allocations.containsKey(id);
    }

    public int rank(String id) {
        Allocation allocation = allocations.get(id);
        return allocation == null ? 0 : allocation.rank();
    }

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
        return Collections.unmodifiableSet(migrationMarkers);
    }

    public List<String> audit() {
        return List.copyOf(audit);
    }

    public boolean ledgerHolds() {
        return availablePoints >= 0 && availablePoints + activeSpentPoints() == purchasedPoints + netGrantedPoints;
    }

    void allocate(String id, int paidCost) {
        allocations.put(id, new Allocation(id, 1, paidCost, nextAcquisition++));
        availablePoints -= paidCost;
        lifetimeSpent += paidCost;
    }

    void rankUp(String id, int paidCost) {
        Allocation current = allocations.get(id);
        allocations.put(id, new Allocation(id, current.rank() + 1, current.paidCost() + paidCost, current.acquisitionIndex()));
        availablePoints -= paidCost;
        lifetimeSpent += paidCost;
    }

    int refund(String id) {
        Allocation allocation = allocations.remove(id);
        if (allocation == null) {
            return 0;
        }
        disabled.remove(id);
        availablePoints += allocation.paidCost();
        lifetimeRefunded += allocation.paidCost();
        if (id.equals(startingRoot)) {
            startingRoot = null;
        }
        return allocation.paidCost();
    }

    void forfeit(String id) {
        Allocation forfeited = allocations.remove(id);
        if (forfeited != null) {
            disabled.remove(id);
            netGrantedPoints -= forfeited.paidCost();
        }
    }

    void reclaim(String id, int rank, int paidCost) {
        allocations.put(id, new Allocation(id, rank, paidCost, nextAcquisition++));
        netGrantedPoints += paidCost;
    }

    void rename(String from, String to) {
        Allocation allocation = allocations.remove(from);
        if (allocation != null) {
            allocations.put(to, new Allocation(to, allocation.rank(), allocation.paidCost(), allocation.acquisitionIndex()));
            if (from.equals(startingRoot)) {
                startingRoot = to;
            }
            if (disabled.remove(from)) {
                disabled.add(to);
            }
        }
    }

    void setStartingRoot(String id) {
        this.startingRoot = id;
    }

    void addPurchasedPoints(int points) {
        purchasedPoints += points;
        availablePoints += points;
    }

    void adjustGranted(int delta) {
        netGrantedPoints += delta;
        availablePoints += delta;
    }

    boolean discover(String key) {
        return discoveries.add(key);
    }

    void forgetDiscoveries() {
        discoveries.clear();
    }

    void setDisabled(String id, boolean off) {
        if (off) {
            disabled.add(id);
        } else {
            disabled.remove(id);
        }
    }

    void setSign(String signId) {
        sign = signId;
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
