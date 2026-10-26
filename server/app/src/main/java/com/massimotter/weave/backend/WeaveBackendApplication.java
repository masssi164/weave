package com.massimotter.weave.backend;

import com.massimotter.weave.backend.operator.OfflineOperatorCommands;
import com.massimotter.weave.backend.identity.migration.KeycloakRealmMigrationCli;
import com.massimotter.weave.backend.identity.migration.KeycloakRealmMigrationReceiptVerifierCli;
import com.massimotter.weave.backend.schema.SchemaAuthorityInitializer;
import com.massimotter.weave.backend.schema.SchemaReceiptVerifier;
import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Product composition. Reviewed migrations initialize the schema; serving Hibernate validates it. */
@SpringBootApplication
@org.springframework.context.annotation.ComponentScan(
        basePackages = {
                "com.massimotter.weave.backend.config",
                "com.massimotter.weave.backend.controller",
                "com.massimotter.weave.backend.service",
                "com.massimotter.weave.backend.dogfood",
                "com.massimotter.weave.backend.manuals"
        },
        excludeFilters = {
                @org.springframework.context.annotation.ComponentScan.Filter(
                        type = org.springframework.context.annotation.FilterType.CUSTOM,
                        classes = org.springframework.boot.context.TypeExcludeFilter.class),
                @org.springframework.context.annotation.ComponentScan.Filter(
                        type = org.springframework.context.annotation.FilterType.CUSTOM,
                        classes = org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter.class)
        })
@EnableScheduling
public class WeaveBackendApplication {

    public static void main(String[] args) {
        if (args != null && args.length > 0) {
            String[] operatorArguments = Arrays.copyOfRange(args, 1, args.length);
            if ("dogfood-prepare-members".equals(args[0])) {
                com.massimotter.weave.backend.dogfood.DogfoodPreparationCli.main(operatorArguments);
                return;
            }
            if ("schema-init".equals(args[0])) {
                SchemaAuthorityInitializer.main(operatorArguments);
                return;
            }
            if ("schema-receipt-check".equals(args[0])) {
                SchemaReceiptVerifier.main(operatorArguments);
                return;
            }
            if ("keycloak-realm-migration".equals(args[0])) {
                KeycloakRealmMigrationCli.main(operatorArguments);
                return;
            }
            if ("keycloak-realm-migration-receipt-check".equals(args[0])) {
                KeycloakRealmMigrationReceiptVerifierCli.main(operatorArguments);
                return;
            }
            if (!args[0].startsWith("--")) {
                int status = OfflineOperatorCommands.executeRequired(
                        args[0], operatorArguments, System.out, System.err,
                        WeaveBackendApplication.class.getClassLoader());
                if (status != 0) {
                    System.exit(status);
                }
                return;
            }
        }
        SpringApplication.run(WeaveBackendApplication.class, args);
    }
}
