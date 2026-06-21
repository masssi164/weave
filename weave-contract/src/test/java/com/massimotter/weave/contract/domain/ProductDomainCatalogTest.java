package com.massimotter.weave.contract.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class ProductDomainCatalogTest {
    private static final Set<String> APPROVED_V01_PRODUCT_DOMAINS = Set.of(
            "identity", "people", "spaces", "chat", "files", "documents", "calendar", "calls", "boards", "decisions", "notifications");

    @Test void productDomainKeysMatchApprovedV01Set() {
        var actual = ProductDomainCatalog.domains().stream().map(ProductDomainContract::key).collect(Collectors.toUnmodifiableSet());
        assertEquals(APPROVED_V01_PRODUCT_DOMAINS, actual);
    }

    @Test void weaverAndHealthAreNotProductDomains() {
        assertFalse(ProductDomainCatalog.isCanonicalProductDomain("weaver"));
        assertFalse(ProductDomainCatalog.isCanonicalProductDomain("health"));
        for (var domain : ProductDomainCatalog.domains()) {
            assertNotEquals("weaver", domain.key());
            assertNotEquals("health", domain.key());
        }
    }

    @Test void groupedMcpMemberNamesAreAliasesOnlyNotProductDomains() {
        for (var groupedName : Set.of("files-docs", "calendar-meetings", "boards-tasks")) {
            assertFalse(ProductDomainCatalog.isCanonicalProductDomain(groupedName), groupedName);
            assertTrue(ProductDomainCatalog.isAggregationAlias(groupedName), groupedName);
            assertEquals(ProductDomainAlias.AliasKind.AGGREGATION_ALIAS, ProductDomainCatalog.aliasesByKey().get(groupedName).kind());
        }
    }

    @Test void aggregationAliasesPointOnlyToExistingProductDomains() {
        for (var alias : ProductDomainCatalog.aliases()) {
            assertFalse(alias.productDomainKeys().isEmpty(), alias.key());
            for (var productDomainKey : alias.productDomainKeys()) {
                assertTrue(ProductDomainCatalog.isCanonicalProductDomain(productDomainKey), alias.key() + " -> " + productDomainKey);
            }
        }
        assertEquals(Set.of("files", "documents"), Set.copyOf(ProductDomainCatalog.aliasesByKey().get("files-docs").productDomainKeys()));
        assertEquals(Set.of("calendar", "calls"), Set.copyOf(ProductDomainCatalog.aliasesByKey().get("calendar-meetings").productDomainKeys()));
        assertEquals(Set.of("boards"), Set.copyOf(ProductDomainCatalog.aliasesByKey().get("boards-tasks").productDomainKeys()));
    }

    @Test void eachProductDomainCarriesContractSkeleton() {
        for (var domain : ProductDomainCatalog.domains()) {
            assertFalse(domain.label().isBlank(), domain.key());
            assertFalse(domain.canonicalObjectKinds().isEmpty(), domain.key());
            assertFalse(domain.readCapabilities().isEmpty(), domain.key());
            assertFalse(domain.writeCapabilities().isEmpty(), domain.key());
            assertFalse(domain.providerAdapterSlots().isEmpty(), domain.key());
            assertFalse(domain.providerCategoryHints().isEmpty(), domain.key());
            assertFalse(domain.portabilityRequirements().isEmpty(), domain.key());
        }
    }
}
