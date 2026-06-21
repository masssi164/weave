package com.massimotter.weave.contract.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ProductDomainCatalog {
    private ProductDomainCatalog() {}

    private static final List<ProductDomainContract> DOMAINS = Arrays.stream(ProductDomainDefinition.values())
            .map(ProductDomainDefinition::contract)
            .toList();

    private static final Map<String, ProductDomainContract> DOMAINS_BY_KEY = DOMAINS.stream()
            .collect(Collectors.toUnmodifiableMap(ProductDomainContract::key, Function.identity()));

    private static final List<ProductDomainAlias> ALIASES = List.of(
            new ProductDomainAlias("files-docs", "Files and documents MCP/member grouping", List.of("files", "documents"), ProductDomainAlias.AliasKind.AGGREGATION_ALIAS, "Compatibility aggregation only; not canonical product-domain truth."),
            new ProductDomainAlias("calendar-meetings", "Calendar and meetings MCP/member grouping", List.of("calendar", "calls"), ProductDomainAlias.AliasKind.AGGREGATION_ALIAS, "Compatibility aggregation only; not canonical product-domain truth."),
            new ProductDomainAlias("boards-tasks", "Boards and tasks MCP/member grouping", List.of("boards"), ProductDomainAlias.AliasKind.AGGREGATION_ALIAS, "Compatibility aggregation only; not canonical product-domain truth."));

    private static final Map<String, ProductDomainAlias> ALIASES_BY_KEY = ALIASES.stream()
            .collect(Collectors.toUnmodifiableMap(ProductDomainAlias::key, Function.identity()));

    public static List<ProductDomainContract> domains() { return DOMAINS; }
    public static Map<String, ProductDomainContract> domainsByKey() { return DOMAINS_BY_KEY; }
    public static List<ProductDomainAlias> aliases() { return ALIASES; }
    public static Map<String, ProductDomainAlias> aliasesByKey() { return ALIASES_BY_KEY; }
    public static boolean isCanonicalProductDomain(String key) { return DOMAINS_BY_KEY.containsKey(key); }
    public static boolean isAggregationAlias(String key) { return ALIASES_BY_KEY.containsKey(key); }
}
