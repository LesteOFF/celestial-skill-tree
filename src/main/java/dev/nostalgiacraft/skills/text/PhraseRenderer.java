package dev.nostalgiacraft.skills.text;

import dev.nostalgiacraft.skills.text.Part.Amount;
import dev.nostalgiacraft.skills.text.Part.Literal;
import dev.nostalgiacraft.skills.text.Part.Named;

import java.util.List;
import java.util.stream.Collectors;

public final class PhraseRenderer {
    public static final String KEY_PREFIX = "gui.nostalgiaskills.";
    private static final String DECIMAL_SEPARATOR_KEY = KEY_PREFIX + "decimal_separator";
    private static final String DEFAULT_DECIMAL_SEPARATOR = ".";

    public interface Lookup {
        String translate(String key, String fallback);

        String attributeName(String id);

        String mobEffectName(String id);
    }

    private final Lookup lookup;

    public PhraseRenderer(Lookup lookup) {
        this.lookup = lookup;
    }

    public String sentence(List<Phrase> phrases) {
        return phrases.stream().map(this::render).collect(Collectors.joining("; ")) + ".";
    }

    public String line(Phrase phrase) {
        return render(phrase);
    }

    private String render(Part part) {
        return switch (part) {
            case Phrase phrase -> String.format(lookup.translate(KEY_PREFIX + phrase.key(), phrase.key()),
                    phrase.parts().stream().map(this::render).toArray());
            case Amount amount -> Numbers.format(amount).replace(DEFAULT_DECIMAL_SEPARATOR,
                    lookup.translate(DECIMAL_SEPARATOR_KEY, DEFAULT_DECIMAL_SEPARATOR));
            case Named named -> name(named);
            case Literal literal -> literal.text();
        };
    }

    private String name(Named named) {
        return switch (named.kind()) {
            case ATTRIBUTE -> lookup.attributeName(named.id());
            case MOB_EFFECT -> lookup.mobEffectName(named.id());
            case ITEM_TAG -> lookup.translate(KEY_PREFIX + "item_tag." + dotted(named.id()), "#" + named.id());
            case ENTITY_TAG -> lookup.translate(KEY_PREFIX + "entity_tag." + dotted(named.id()), "#" + named.id());
            case TRIGGER -> lookup.translate(KEY_PREFIX + "trigger." + dotted(named.id()), named.id());
            case DAMAGE_SCOPE -> lookup.translate(KEY_PREFIX + "damage_scope." + named.id(), named.id());
        };
    }

    private static String dotted(String id) {
        return id.replace(':', '.').replace('/', '.');
    }
}
