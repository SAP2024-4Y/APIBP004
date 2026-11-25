package com.jobportal.service.paging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobportal.exception.ValidationException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

/** SQL keyset pagination with explicit NULLS LAST and a unique ID tie-breaker in either direction. */
@Component
@RequiredArgsConstructor
public class KeysetPager {
    private final EntityManager em;
    private final ObjectMapper json;
    public record Page<T>(List<T> items, List<String> cursors, boolean hasMore, boolean hasPrevious) {
        public String startCursor() { return cursors.isEmpty() ? null : cursors.get(0); }
        public String endCursor() { return cursors.isEmpty() ? null : cursors.get(cursors.size()-1); }
    }
    private record Cursor(int version, String resource, String sort, String order, String filters, int id, String value) {}

    public static String sort(String input) {
        if (input == null) return "createdAt";
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "id" -> "id";
            case "title" -> "title";
            case "created_at", "createdat" -> "createdAt";
            case "min_price", "min_salary", "minsalary" -> "minSalary";
            case "max_price", "max_salary", "maxsalary" -> "maxSalary";
            default -> throw new ValidationException("Unsupported sort field");
        };
    }
    public static String order(String input) {
        if (input == null) return "desc";
        String order = input.toLowerCase(Locale.ROOT);
        if (!Set.of("asc", "desc").contains(order)) throw new ValidationException("order must be asc or desc");
        return order;
    }

    @Transactional(readOnly = true)
    public <T> Page<T> fetch(Class<T> entity, String resource, String sort, String order, Object filters,
                             String after, int limit, BiFunction<CriteriaBuilder, Root<T>, Predicate> filter,
                             Function<T, Integer> id, Function<T, Object> value) {
        if (limit < 1 || limit > 100) throw new ValidationException("Page size must be between 1 and 100");
        String fingerprint = fingerprint(filters);
        Cursor cursor = after == null ? null : decode(after, resource, sort, order, fingerprint);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(entity);
        Root<T> root = query.from(entity);
        Predicate matches = filter.apply(cb, root);
        query.select(root).where(cursor == null ? matches : cb.and(matches, seek(cb, root, cursor, true)));
        Expression<Integer> nullRank = cb.<Integer>selectCase().when(cb.isNull(root.get(sort)), 1).otherwise(0);
        boolean asc = order.equals("asc");
        query.orderBy(cb.asc(nullRank), asc ? cb.asc(root.get(sort)) : cb.desc(root.get(sort)),
                asc ? cb.asc(root.get("id")) : cb.desc(root.get("id")));
        List<T> found = em.createQuery(query).setMaxResults(limit + 1).getResultList();
        boolean more = found.size() > limit;
        List<T> items = List.copyOf(more ? found.subList(0, limit) : found);
        List<String> cursors = items.stream().map(item -> encode(new Cursor(1, resource, sort, order, fingerprint,
                id.apply(item), value.apply(item) == null ? null : value.apply(item).toString()))).toList();
        boolean previous = false;
        if (cursor != null) {
            CriteriaQuery<Integer> before = cb.createQuery(Integer.class);
            Root<T> previousRoot = before.from(entity);
            before.select(previousRoot.get("id")).where(cb.and(filter.apply(cb, previousRoot), seek(cb, previousRoot, cursor, false)));
            previous = !em.createQuery(before).setMaxResults(1).getResultList().isEmpty();
        }
        return new Page<>(items, cursors, more, previous);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Predicate seek(CriteriaBuilder cb, Root<?> root, Cursor cursor, boolean after) {
        Path field = root.get(cursor.sort());
        Path<Integer> id = root.get("id");
        boolean greater = cursor.order().equals("asc") == after;
        Predicate idCondition = after
                ? (greater ? cb.greaterThan(id, cursor.id()) : cb.lessThan(id, cursor.id()))
                : (greater ? cb.greaterThanOrEqualTo(id, cursor.id()) : cb.lessThanOrEqualTo(id, cursor.id()));
        if (cursor.value() == null) {
            Predicate nulls = cb.and(cb.isNull(field), idCondition);
            return after ? nulls : cb.or(cb.isNotNull(field), nulls);
        }
        Comparable value = typedValue(cursor.sort(), cursor.value());
        Predicate ordered = cb.or(greater ? cb.greaterThan(field, value) : cb.lessThan(field, value),
                cb.and(cb.equal(field, value), idCondition));
        return after ? cb.or(cb.isNull(field), ordered) : cb.and(cb.isNotNull(field), ordered);
    }

    private Comparable<?> typedValue(String sort, String value) {
        return switch (sort) {
            case "id", "minSalary", "maxSalary" -> Integer.valueOf(value);
            case "createdAt" -> LocalDateTime.parse(value);
            case "title" -> value;
            default -> throw new IllegalArgumentException("Unsupported cursor sort");
        };
    }
    private Cursor decode(String input, String resource, String sort, String order, String filters) {
        try {
            if (input.isBlank() || input.length() > 4096) throw new IllegalArgumentException();
            JsonNode tree = json.readTree(Base64.getUrlDecoder().decode(input));
            if (!tree.isObject() || !tree.path("version").isIntegralNumber() || tree.path("version").asInt() != 1 || !tree.has("value") || !(tree.path("value").isNull() || tree.path("value").isTextual())
                    || !tree.path("id").isIntegralNumber() || !tree.path("id").canConvertToInt()) throw new IllegalArgumentException();
            Cursor cursor = json.treeToValue(tree, Cursor.class);
            if (cursor.version() != 1 || cursor.id() <= 0 || !resource.equals(cursor.resource()) || !sort.equals(cursor.sort())
                    || !order.equals(cursor.order()) || !filters.equals(cursor.filters())) throw new IllegalArgumentException();
            if (cursor.value() == null && Set.of("id", "title").contains(sort)) throw new IllegalArgumentException();
            if (cursor.value() != null) {
                Comparable<?> value = typedValue(sort, cursor.value());
                if (sort.equals("id") && !value.equals(cursor.id())) throw new IllegalArgumentException();
                if (sort.equals("title") && cursor.value().length() > 255) throw new IllegalArgumentException();
                if (value instanceof Integer number && number < 0) throw new IllegalArgumentException();
            }
            return cursor;
        } catch (Exception ex) { throw new ValidationException("Invalid cursor, or cursor does not match this resource, filters, sort, and order"); }
    }
    private String encode(Cursor cursor) {
        try { return Base64.getUrlEncoder().withoutPadding().encodeToString(json.writeValueAsBytes(cursor)); }
        catch (Exception ex) { throw new IllegalStateException("Cannot encode cursor", ex); }
    }
    private String fingerprint(Object filters) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(filters).getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException("Cannot encode filters", ex); }
    }
}
