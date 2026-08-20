package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.files.domain.FilesSearch;
import java.util.List;
import java.util.Objects;
import javax.xml.namespace.QName;

/** Closed, bounded representation of a supported {@code DAV:basicsearch} request. */
public record WebDavSearchRequest(
        String arbiterPath,
        String scopePath,
        FilesSearch.ScopeDepth scopeDepth,
        Selection selection,
        Predicate predicate,
        List<OrderClause> orderBy,
        int limit) {

    public WebDavSearchRequest {
        arbiterPath = requireProductPath(arbiterPath, "arbiterPath");
        scopePath = requireProductPath(scopePath, "scopePath");
        if (!("/".equals(arbiterPath)
                || scopePath.equals(arbiterPath)
                || scopePath.startsWith(arbiterPath + "/"))) {
            throw new IllegalArgumentException("scopePath must be at or below arbiterPath");
        }
        scopeDepth = Objects.requireNonNull(scopeDepth, "scopeDepth");
        selection = Objects.requireNonNull(selection, "selection");
        predicate = Objects.requireNonNull(predicate, "predicate");
        orderBy = List.copyOf(orderBy == null ? List.of() : orderBy);
        if (orderBy.size() > 2) {
            throw new IllegalArgumentException("orderBy must contain at most two clauses");
        }
        if (limit < 0 || limit > 100) {
            throw new IllegalArgumentException("limit must be between zero and 100");
        }
    }

    public sealed interface Selection permits AllProperties, SelectedProperties {}

    public record AllProperties() implements Selection {}

    public record SelectedProperties(List<QName> properties) implements Selection {
        public SelectedProperties {
            properties = copyProperties(properties, "properties");
            if (properties.size() > 16) {
                throw new IllegalArgumentException("properties must contain at most 16 names");
            }
        }
    }

    public sealed interface Predicate permits TruePredicate, LogicalPredicate, ComparisonPredicate,
            IsCollectionPredicate, IsDefinedPredicate {}

    public record TruePredicate() implements Predicate {}

    public record LogicalPredicate(LogicalOperator operator, List<Predicate> operands) implements Predicate {
        public LogicalPredicate {
            operator = Objects.requireNonNull(operator, "operator");
            operands = List.copyOf(operands == null ? List.of() : operands);
            if (operands.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("operands must not contain null");
            }
            if (operator == LogicalOperator.NOT && operands.size() != 1) {
                throw new IllegalArgumentException("NOT requires exactly one operand");
            }
            if (operator != LogicalOperator.NOT && operands.isEmpty()) {
                throw new IllegalArgumentException("AND and OR require at least one operand");
            }
        }
    }

    public record ComparisonPredicate(ComparisonOperator operator, QName property, String literal)
            implements Predicate {
        public ComparisonPredicate {
            operator = Objects.requireNonNull(operator, "operator");
            property = requireProperty(property, "property");
            literal = Objects.requireNonNull(literal, "literal");
        }
    }

    public record IsCollectionPredicate() implements Predicate {}

    public record IsDefinedPredicate(QName property) implements Predicate {
        public IsDefinedPredicate {
            property = requireProperty(property, "property");
        }
    }

    public record OrderClause(QName property, OrderDirection direction) {
        public OrderClause {
            property = requireProperty(property, "property");
            direction = Objects.requireNonNull(direction, "direction");
        }
    }

    public enum LogicalOperator {
        AND,
        OR,
        NOT
    }

    public enum ComparisonOperator {
        EQ,
        LT,
        LTE,
        GT,
        GTE,
        LIKE
    }

    public enum OrderDirection {
        ASCENDING,
        DESCENDING
    }

    private static List<QName> copyProperties(List<QName> properties, String fieldName) {
        List<QName> copy = List.copyOf(properties == null ? List.of() : properties);
        for (QName property : copy) {
            requireProperty(property, fieldName);
        }
        return copy;
    }

    private static QName requireProperty(QName property, String fieldName) {
        Objects.requireNonNull(property, fieldName);
        if (property.getLocalPart().isBlank()) {
            throw new IllegalArgumentException(fieldName + " must have a local name");
        }
        return property;
    }

    private static String requireProductPath(String path, String fieldName) {
        Objects.requireNonNull(path, fieldName);
        if (path.isBlank() || path.charAt(0) != '/') {
            throw new IllegalArgumentException(fieldName + " must be an absolute product path");
        }
        return FilePathCodec.normalizeProductPath(path);
    }
}
