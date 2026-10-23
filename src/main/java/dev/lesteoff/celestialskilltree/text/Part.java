package dev.lesteoff.celestialskilltree.text;

public sealed interface Part permits Phrase, Part.Amount, Part.Named, Part.Literal {
    record Amount(double value, Style style) implements Part {
        public enum Style {
            PLAIN, SIGNED, PERCENT, SIGNED_PERCENT
        }
    }

    record Named(Kind kind, String id) implements Part {
        public enum Kind {
            ATTRIBUTE, MOB_EFFECT, ITEM_TAG, ENTITY_TAG, TRIGGER, DAMAGE_SCOPE
        }
    }

    record Literal(String text) implements Part {
    }
}
