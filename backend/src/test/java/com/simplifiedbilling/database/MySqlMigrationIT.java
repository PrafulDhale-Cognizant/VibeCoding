package com.simplifiedbilling.database;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Testcontainers
class MySqlMigrationIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.10")
            .withDatabaseName("billing")
            .withUsername("billing_app")
            .withPassword("billing_test_password");

    @DynamicPropertySource
    static void configureMySql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.create-schemas", () -> false);
        registry.add("spring.flyway.default-schema", MYSQL::getDatabaseName);
        registry.add("spring.flyway.schemas", MYSQL::getDatabaseName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesFoundationMigrationToMySql() {
        Integer tableCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN (
                      'app_settings',
                      'audit_events',
                      'shop_profiles',
                      'users',
                      'user_roles',
                      'refresh_tokens',
                      'product_units',
                      'categories',
                      'products',
                      'product_barcodes',
                      'inventory_balances',
                      'stock_transactions',
                      'internal_barcode_sequences',
                      'invoice_sequences',
                      'invoices',
                      'invoice_items',
                      'payments',
                      'customers',
                      'customer_credit_balances',
                      'khata_ledger_entries',
                      'purchase_sequences',
                      'suppliers',
                      'supplier_payable_balances',
                      'purchases',
                      'purchase_items',
                      'supplier_ledger_entries'
                  )
                """,
                Integer.class);

        assertThat(tableCount).isEqualTo(26);
    }
}
