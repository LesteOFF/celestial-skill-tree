package dev.nostalgiacraft.skills.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Errors block a snapshot from becoming active; warnings are authoring diagnostics only. */
public final class ValidationReport {
    public enum Severity { ERROR, WARNING }

    public record Issue(Severity severity, String source, String field, String message) {
        @Override
        public String toString() {
            return severity + " " + source + (field.isEmpty() ? "" : " [" + field + "]") + ": " + message;
        }
    }

    private final List<Issue> issues = new ArrayList<>();

    public void error(String source, String field, String message) {
        issues.add(new Issue(Severity.ERROR, source, field, message));
    }

    public void warn(String source, String field, String message) {
        issues.add(new Issue(Severity.WARNING, source, field, message));
    }

    public boolean hasErrors() {
        return issues.stream().anyMatch(i -> i.severity() == Severity.ERROR);
    }

    public List<Issue> issues() {
        return Collections.unmodifiableList(issues);
    }

    public List<Issue> errors() {
        return issues.stream().filter(i -> i.severity() == Severity.ERROR).toList();
    }

    public List<Issue> warnings() {
        return issues.stream().filter(i -> i.severity() == Severity.WARNING).toList();
    }

    public void addAll(ValidationReport other) {
        issues.addAll(other.issues);
    }
}
