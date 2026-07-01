package com.massimotter.weave.backend.provider;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ProviderCategoryCatalog {

    private static final Map<String, Category> CATEGORIES = Map.of(
            "identity-idm", new Category("identity-idm", "identity/IDM", Set.of(ProviderModule.IDENTITY_REALM, ProviderModule.MATRIX_AUTH), true),
            "chat", new Category("chat", "chat", Set.of(ProviderModule.MATRIX), true),
            "files", new Category("files", "files", Set.of(ProviderModule.FILES), true),
            "calendar", new Category("calendar", "calendar", Set.of(ProviderModule.CALENDAR), true),
            "boards-tasks", new Category("boards-tasks", "boards/tasks", Set.of(ProviderModule.BOARDS), true),
            "meetings-calls", new Category("meetings-calls", "meetings/calls", Set.of(ProviderModule.MEETINGS), false),
            "documents-collaboration", new Category("documents-collaboration", "documents/collaboration", Set.of(ProviderModule.OFFICE, ProviderModule.FORMS, ProviderModule.CONTACTS), false),
            "weaver", new Category("weaver", "Weaver", Set.of(), true),
            "model", new Category("model", "model provider", Set.of(), true));

    private ProviderCategoryCatalog() {
    }

    public static List<String> categoryKeys() {
        return List.of("identity-idm", "chat", "files", "calendar", "boards-tasks", "meetings-calls", "documents-collaboration", "model", "weaver");
    }

    public static Optional<Category> category(String key) {
        return Optional.ofNullable(CATEGORIES.get(key));
    }

    public static Optional<String> categoryForModule(ProviderModule module) {
        return CATEGORIES.values().stream()
                .filter(category -> category.modules().contains(module))
                .map(Category::key)
                .findFirst();
    }

    public static boolean providerMatchesCategory(ProviderStatusResponse provider, String categoryKey) {
        return category(categoryKey)
                .map(category -> category.modules().contains(provider.module()))
                .orElse(false);
    }

    public record Category(String key, String label, Set<ProviderModule> modules, boolean capabilityBacked) {
        public Category {
            modules = modules == null ? Set.of() : Set.copyOf(modules);
        }
    }
}
