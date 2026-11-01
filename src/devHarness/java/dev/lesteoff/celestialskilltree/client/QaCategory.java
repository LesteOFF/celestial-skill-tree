package dev.lesteoff.celestialskilltree.client;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

enum QaCategory {
    TREE, ECONOMY, PROGRESSION, SIGN, ECLIPSE, COMMUNITY, TABS, SKY, SHOWCASE, ASTRAL;

    private static final List<String> REQUESTED = Arrays.stream(System.getProperty("celestialskilltree.qa", "").split(","))
            .map(String::trim).filter(name -> !name.isEmpty()).map(name -> name.toUpperCase(Locale.ROOT)).toList();

    void include(Runnable addSteps) {
        if (REQUESTED.isEmpty() || REQUESTED.contains(name())) {
            addSteps.run();
        }
    }

    static void checkRequested(QaScript qa) {
        List<String> known = Arrays.stream(values()).map(Enum::name).toList();
        REQUESTED.forEach(name -> qa.expect("the QA category " + name.toLowerCase(Locale.ROOT) + " exists (known: "
                + String.join(", ", known).toLowerCase(Locale.ROOT) + ")", known.contains(name)));
    }
}
