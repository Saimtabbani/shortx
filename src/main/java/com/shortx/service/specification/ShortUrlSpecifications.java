package com.shortx.service.specification;

import com.shortx.dto.request.ShortUrlFilterRequest;
import com.shortx.entity.ShortUrl;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds a {@link Specification} for {@link ShortUrl} from an optional set
 * of filters, always scoped to a single owner. Every filter clause is
 * added conditionally — an unset filter contributes no predicate at all,
 * rather than a wasteful "col = col OR :param IS NULL" style condition.
 *
 * <p>A plain static builder method rather than a Spring bean: it has no
 * dependencies and no state, so there's nothing dependency injection
 * would buy here — it's a pure function from (ownerId, filters) to a
 * Specification.
 */
public final class ShortUrlSpecifications {

    private ShortUrlSpecifications() {
        // Non-instantiable utility class.
    }

    public static Specification<ShortUrl> ownedByWithFilters(UUID ownerId, ShortUrlFilterRequest filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("user").get("id"), ownerId));

            if (filter != null) {
                if (filter.getActive() != null) {
                    predicates.add(cb.equal(root.get("active"), filter.getActive()));
                }

                if (filter.getCustomAliasOnly() != null && filter.getCustomAliasOnly()) {
                    predicates.add(cb.isTrue(root.get("customAlias")));
                }

                if (filter.getCreatedAfter() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.getCreatedAfter()));
                }

                if (filter.getCreatedBefore() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.getCreatedBefore()));
                }

                if (filter.getSearch() != null && !filter.getSearch().isBlank()) {
                    String pattern = "%" + filter.getSearch().toLowerCase() + "%";
                    predicates.add(cb.or(
                        cb.like(cb.lower(root.get("originalUrl")), pattern),
                        cb.like(cb.lower(root.get("shortCode")), pattern)
                    ));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
