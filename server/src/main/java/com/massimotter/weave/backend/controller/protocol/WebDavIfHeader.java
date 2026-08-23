package com.massimotter.weave.backend.controller.protocol;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Bounded RFC 4918 If-header parser and Boolean evaluator. */
public final class WebDavIfHeader {

    private static final int MAXIMUM_CHARACTERS = 16_384;
    private static final int MAXIMUM_PRODUCTIONS = 64;
    private static final int MAXIMUM_LISTS = 128;
    private static final int MAXIMUM_CONDITIONS = 512;
    private static final int MAXIMUM_REFERENCE_CHARACTERS = 4_096;

    private WebDavIfHeader() {}

    public static Header parse(String value) {
        return new Parser(value).parse();
    }

    /**
     * Evaluates the parsed header according to RFC 4918 section 10.4.
     *
     * <p>Lists are conjunctions, lists following one resource tag are disjunctions, and the
     * complete header is a disjunction of no-tag or tagged productions. State-token submission is
     * returned independently from the Boolean result, as required for lock-token processing.
     */
    public static Evaluation evaluate(
            Header header,
            String requestResource,
            StateResolver resolver) {
        Objects.requireNonNull(header, "header");
        String requestTarget = requiredReference(requestResource, false);
        StateResolver states = Objects.requireNonNull(resolver, "resolver");
        boolean satisfied = false;
        LinkedHashSet<String> submitted = new LinkedHashSet<>();

        for (Production production : header.productions()) {
            String resource = production.resourceTag() == null
                    ? requestTarget
                    : production.resourceTag();
            boolean productionSatisfied = false;
            for (ConditionList list : production.lists()) {
                boolean listSatisfied = true;
                for (Condition condition : list.conditions()) {
                    boolean matched;
                    if (condition.operand() instanceof StateToken token) {
                        submitted.add(token.uri());
                        matched = states.matchesStateToken(resource, token.uri());
                    } else if (condition.operand() instanceof EntityTag entityTag) {
                        matched = states.matchesEntityTag(resource, entityTag.value());
                    } else {
                        throw new IllegalStateException("Unknown WebDAV If operand");
                    }
                    if (condition.negated()) {
                        matched = !matched;
                    }
                    listSatisfied &= matched;
                }
                productionSatisfied |= listSatisfied;
            }
            satisfied |= productionSatisfied;
        }
        return new Evaluation(satisfied, submitted);
    }

    public interface StateResolver {
        boolean matchesStateToken(String resourceReference, String stateToken);

        boolean matchesEntityTag(String resourceReference, String entityTag);
    }

    public record Header(boolean tagged, List<Production> productions) {
        public Header {
            productions = List.copyOf(productions == null ? List.of() : productions);
            if (productions.isEmpty() || productions.stream().anyMatch(Objects::isNull)) {
                throw invalid();
            }
            boolean anyTagged = productions.stream().anyMatch(production -> production.resourceTag() != null);
            boolean allTagged = productions.stream().allMatch(production -> production.resourceTag() != null);
            if ((tagged && !allTagged) || (!tagged && anyTagged)) {
                throw invalid();
            }
        }
    }

    /** One No-tag-list or one Resource-Tag followed by its one-or-more Lists. */
    public record Production(String resourceTag, List<ConditionList> lists) {
        public Production {
            resourceTag = resourceTag == null ? null : requiredReference(resourceTag, false);
            lists = List.copyOf(lists == null ? List.of() : lists);
            if (lists.isEmpty() || lists.stream().anyMatch(Objects::isNull)) {
                throw invalid();
            }
        }
    }

    public record ConditionList(List<Condition> conditions) {
        public ConditionList {
            conditions = List.copyOf(conditions == null ? List.of() : conditions);
            if (conditions.isEmpty() || conditions.stream().anyMatch(Objects::isNull)) {
                throw invalid();
            }
        }
    }

    public record Condition(boolean negated, Operand operand) {
        public Condition {
            operand = Objects.requireNonNull(operand, "operand");
        }
    }

    public sealed interface Operand permits StateToken, EntityTag {}

    public record StateToken(String uri) implements Operand {
        public StateToken {
            uri = requiredReference(uri, true);
        }
    }

    public record EntityTag(String value) implements Operand {
        public EntityTag {
            value = requiredEntityTag(value);
        }
    }

    public record Evaluation(boolean satisfied, Set<String> submittedStateTokens) {
        public Evaluation {
            submittedStateTokens = Set.copyOf(
                    submittedStateTokens == null ? Set.of() : submittedStateTokens);
        }

        public boolean submitted(String stateToken) {
            return submittedStateTokens.contains(stateToken);
        }
    }

    public static final class InvalidIfHeaderException extends RuntimeException {
        private InvalidIfHeaderException() {
            super("The WebDAV If header is invalid.");
        }
    }

    private static InvalidIfHeaderException invalid() {
        return new InvalidIfHeaderException();
    }

    private static String requiredReference(String value, boolean absolute) {
        if (value == null
                || value.isBlank()
                || value.length() > MAXIMUM_REFERENCE_CHARACTERS
                || value.chars().anyMatch(WebDavIfHeader::forbiddenReferenceCharacter)) {
            throw invalid();
        }
        try {
            URI uri = URI.create(value);
            if (!value.equals(uri.toASCIIString())
                    || uri.getFragment() != null
                    || (absolute && !uri.isAbsolute())
                    || (!absolute && !uri.isAbsolute() && !value.startsWith("/"))) {
                throw invalid();
            }
            return value;
        } catch (InvalidIfHeaderException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private static boolean forbiddenReferenceCharacter(int character) {
        return character <= 0x20
                || character == 0x7f
                || character > 0x7f
                || character == '<'
                || character == '>';
    }

    private static String requiredEntityTag(String value) {
        if (value == null
                || value.length() < 2
                || value.length() > MAXIMUM_REFERENCE_CHARACTERS
                || value.chars().anyMatch(character -> character < 0x20 || character == 0x7f)) {
            throw invalid();
        }
        int quote = value.startsWith("W/") ? 2 : 0;
        if (quote >= value.length() || value.charAt(quote) != '"' || value.charAt(value.length() - 1) != '"') {
            throw invalid();
        }
        boolean escaped = false;
        for (int index = quote + 1; index < value.length() - 1; index++) {
            char character = value.charAt(index);
            if (escaped) {
                escaped = false;
            } else if (character == '\\') {
                escaped = true;
            } else if (character == '"') {
                throw invalid();
            }
        }
        if (escaped) {
            throw invalid();
        }
        return value;
    }

    private static final class Parser {
        private final String value;
        private int index;
        private int productionCount;
        private int listCount;
        private int conditionCount;

        private Parser(String value) {
            if (value == null
                    || value.isBlank()
                    || value.length() > MAXIMUM_CHARACTERS
                    || value.chars().anyMatch(character -> character == '\r' || character == '\n')) {
                throw invalid();
            }
            this.value = value;
        }

        Header parse() {
            whitespace();
            boolean tagged = peek('<');
            List<Production> productions = tagged ? taggedProductions() : noTagProductions();
            whitespace();
            if (!end()) {
                throw invalid();
            }
            return new Header(tagged, productions);
        }

        private List<Production> noTagProductions() {
            List<Production> productions = new ArrayList<>();
            while (true) {
                whitespace();
                if (!peek('(')) {
                    break;
                }
                productions.add(production(null, List.of(conditionList())));
            }
            if (productions.isEmpty()) {
                throw invalid();
            }
            return List.copyOf(productions);
        }

        private List<Production> taggedProductions() {
            List<Production> productions = new ArrayList<>();
            while (true) {
                whitespace();
                if (!peek('<')) {
                    break;
                }
                String resourceTag = codedReference(false);
                List<ConditionList> lists = new ArrayList<>();
                while (true) {
                    whitespace();
                    if (!peek('(')) {
                        break;
                    }
                    lists.add(conditionList());
                }
                if (lists.isEmpty()) {
                    throw invalid();
                }
                productions.add(production(resourceTag, lists));
            }
            if (productions.isEmpty()) {
                throw invalid();
            }
            return List.copyOf(productions);
        }

        private Production production(String resourceTag, List<ConditionList> lists) {
            if (++productionCount > MAXIMUM_PRODUCTIONS) {
                throw invalid();
            }
            return new Production(resourceTag, lists);
        }

        private ConditionList conditionList() {
            if (++listCount > MAXIMUM_LISTS) {
                throw invalid();
            }
            require('(');
            List<Condition> conditions = new ArrayList<>();
            while (true) {
                whitespace();
                if (peek(')')) {
                    break;
                }
                if (++conditionCount > MAXIMUM_CONDITIONS) {
                    throw invalid();
                }
                boolean negated = keyword("Not");
                if (negated) {
                    whitespace();
                }
                Operand operand;
                if (peek('<')) {
                    operand = new StateToken(codedReference(true));
                } else if (peek('[')) {
                    operand = new EntityTag(entityTag());
                } else {
                    throw invalid();
                }
                conditions.add(new Condition(negated, operand));
            }
            require(')');
            return new ConditionList(conditions);
        }

        private String codedReference(boolean absolute) {
            require('<');
            int start = index;
            while (!end() && value.charAt(index) != '>') {
                char character = value.charAt(index++);
                if (forbiddenReferenceCharacter(character)) {
                    throw invalid();
                }
            }
            if (end() || index == start) {
                throw invalid();
            }
            String reference = value.substring(start, index);
            require('>');
            return requiredReference(reference, absolute);
        }

        private String entityTag() {
            require('[');
            int start = index;
            if (startsWith("W/")) {
                index += 2;
            }
            require('"');
            boolean escaped = false;
            while (!end()) {
                char character = value.charAt(index++);
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (character == '\\') {
                    escaped = true;
                    continue;
                }
                if (character == '"') {
                    break;
                }
                if (character < 0x20 || character == 0x7f) {
                    throw invalid();
                }
            }
            if (end() || escaped || !peek(']')) {
                throw invalid();
            }
            String entityTag = value.substring(start, index);
            require(']');
            return requiredEntityTag(entityTag);
        }

        private boolean keyword(String keyword) {
            if (!value.regionMatches(true, index, keyword, 0, keyword.length())) {
                return false;
            }
            int end = index + keyword.length();
            if (end < value.length()) {
                char next = value.charAt(end);
                if (Character.isLetterOrDigit(next) || next == '-' || next == '_') {
                    return false;
                }
            }
            index = end;
            return true;
        }

        private boolean startsWith(String candidate) {
            return value.startsWith(candidate, index);
        }

        private void whitespace() {
            while (!end() && (value.charAt(index) == ' ' || value.charAt(index) == '\t')) {
                index++;
            }
        }

        private boolean peek(char expected) {
            return !end() && value.charAt(index) == expected;
        }

        private void require(char expected) {
            if (!peek(expected)) {
                throw invalid();
            }
            index++;
        }

        private boolean end() {
            return index >= value.length();
        }
    }
}
