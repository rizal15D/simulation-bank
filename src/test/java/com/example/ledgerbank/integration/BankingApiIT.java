package com.example.ledgerbank.integration;

import com.example.ledgerbank.common.config.RabbitMqConfig;
import com.example.ledgerbank.event.BankingEvent;
import com.example.ledgerbank.event.BankingEventType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.DeserializationFeature;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises HTTP, Flyway, JPA and real PostgreSQL transaction boundaries together.
 * Run explicitly with the integration Maven profile against a dedicated *_test database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BankingApiIT extends AbstractIntegrationTest {
    private static final String PASSWORD = "integration-pass-2026";

    @Autowired private Environment environment;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private StringRedisTemplate redis;
    @Autowired private RabbitTemplate rabbit;

    private final Map<String, String> customerTokens = new ConcurrentHashMap<>();
    private final Map<String, String> accountTokens = new ConcurrentHashMap<>();
    private volatile String currentToken;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    @BeforeAll
    void requirePostgresql18AndDedicatedTestDatabase() {
        assertTrue(jdbc.queryForObject("SELECT current_database()", String.class).endsWith("_test"),
                "Integration tests require a dedicated database whose name ends in _test");
        int version = Integer.parseInt(jdbc.queryForObject("SHOW server_version_num", String.class));
        assertTrue(version >= 180000 && version < 190000,
                "Milestone 1 integration tests must run against PostgreSQL 18");
    }

    @BeforeEach
    void cleanDedicatedDatabase() {
        // Repeat the guard immediately before the destructive operation.
        assertTrue(jdbc.queryForObject("SELECT current_database()", String.class).endsWith("_test"));
        jdbc.execute("TRUNCATE account_transactions, transfers, accounts, customers CASCADE");
        try (RedisConnection connection = redis.getConnectionFactory().getConnection()) {
            connection.serverCommands().flushDb();
        }
        customerTokens.clear();
        accountTokens.clear();
        currentToken = null;
    }

    @Test
    void healthAndCustomerAccountLifecycle() throws Exception {
        ApiResponse health = get("/api/v1/health");
        assertEquals(200, health.status());
        assertEquals("UP", health.body().get("status"));

        String customerId = customer("Ada Lovelace", "ada@example.com");
        ApiResponse found = get("/api/v1/customers/" + customerId);
        assertEquals(200, found.status());
        assertEquals("Ada Lovelace", found.body().get("fullName"));
        assertEquals("ada@example.com", found.body().get("email"));

        Map<String, Object> first = openAccount(customerId);
        Map<String, Object> second = openAccount(customerId);
        assertNotEquals(first.get("id"), second.get("id"));
        assertNotEquals(first.get("accountNumber"), second.get("accountNumber"));
        assertFalse(first.get("accountNumber").toString().isBlank());
        assertEquals("IDR", first.get("currency"));
        assertEquals("ACTIVE", first.get("status"));
        assertMoney("0", first.get("balance"));

        ApiResponse account = get("/api/v1/accounts/" + first.get("id"));
        assertEquals(200, account.status());
        assertEquals(first.get("accountNumber"), account.body().get("accountNumber"));

        HttpResponse<String> accounts = send("GET", "/api/v1/customers/" + customerId + "/accounts", null);
        assertEquals(200, accounts.statusCode());
        List<?> accountList = json.readValue(accounts.body(), List.class);
        assertEquals(2, accountList.size());
    }

    @Test
    void authenticationLifecycleRejectsMissingInvalidAndLoggedOutTokens() throws Exception {
        String customerId = customer("Session Owner", "session-owner@example.com");
        String token = customerTokens.get(customerId);
        String path = "/api/v1/customers/" + customerId;

        assertError(response(send("GET", path, null, null, Map.of())), 401, "UNAUTHORIZED");
        assertError(response(send("GET", path, null, "not-a-valid-session-token", Map.of())),
                401, "UNAUTHORIZED");
        assertEquals(200, response(send("GET", path, null, token, Map.of())).status());

        ApiResponse logout = response(send("POST", "/api/v1/auth/logout", null, token, Map.of()));
        assertEquals(204, logout.status());
        assertTrue(logout.body().isEmpty());
        assertError(response(send("GET", path, null, token, Map.of())), 401, "UNAUTHORIZED");
    }

    @Test
    void customerCannotReadOrMutateAnotherCustomersResources() throws Exception {
        String ownerId = customer("Account Owner", "account-owner@example.com");
        String ownerAccount = openAccount(ownerId).get("id").toString();
        assertEquals(201, amount(ownerAccount, "deposit", "100").status());

        String intruderId = customer("Other Customer", "other-customer@example.com");
        String intruderToken = customerTokens.get(intruderId);
        String intruderAccount = openAccount(intruderId).get("id").toString();

        assertError(response(send("GET", "/api/v1/customers/" + ownerId, null,
                intruderToken, Map.of())), 403, "FORBIDDEN");
        assertError(response(send("GET", "/api/v1/customers/" + ownerId + "/accounts", null,
                intruderToken, Map.of())), 403, "FORBIDDEN");
        assertError(response(send("GET", "/api/v1/accounts/" + ownerAccount, null,
                intruderToken, Map.of())), 403, "FORBIDDEN");
        assertError(response(send("GET", "/api/v1/accounts/" + ownerAccount + "/transactions", null,
                intruderToken, Map.of())), 403, "FORBIDDEN");
        assertError(response(send("POST", "/api/v1/accounts/" + ownerAccount + "/deposit",
                Map.of("amount", 10), intruderToken, Map.of())), 403, "FORBIDDEN");
        assertError(response(send("POST", "/api/v1/accounts/" + ownerAccount + "/withdraw",
                Map.of("amount", 10), intruderToken, Map.of())), 403, "FORBIDDEN");
        assertError(response(send("POST", "/api/v1/transfers", Map.of(
                        "sourceAccountId", ownerAccount,
                        "destinationAccountId", intruderAccount,
                        "amount", 10,
                        "description", "Forbidden transfer"), intruderToken,
                Map.of("Idempotency-Key", UUID.randomUUID().toString()))), 403, "FORBIDDEN");

        assertBalance(ownerAccount, "100");
        assertBalance(intruderAccount, "0");
        assertEquals(1L, count("account_transactions"));
        assertEquals(0L, count("transfers"));
    }

    @Test
    void customerValidationUniquenessAndMissingResourcesUseErrors() throws Exception {
        assertError(postPublic("/api/v1/auth/register", Map.of(
                "fullName", "", "email", "broken", "password", PASSWORD)), 400);
        customer("First", "duplicate@example.com");
        assertError(postPublic("/api/v1/auth/register", Map.of(
                "fullName", "Second", "email", "DUPLICATE@example.com", "password", PASSWORD)), 409);

        String missing = UUID.randomUUID().toString();
        assertError(get("/api/v1/customers/" + missing), 403, "FORBIDDEN");
        assertError(post("/api/v1/accounts", Map.of("customerId", missing)), 403, "FORBIDDEN");
        assertError(get("/api/v1/accounts/" + missing), 404);
        assertError(get("/api/v1/customers/" + missing + "/accounts"), 403, "FORBIDDEN");
        assertError(get("/api/v1/accounts/" + missing + "/transactions"), 404);
        assertError(get("/api/v1/accounts/not-a-uuid"), 400);
        assertEquals(1L, count("customers"));
        assertEquals(0L, count("accounts"));
    }

    @Test
    void malformedHttpRequestsKeepTheirClientErrorStatuses() throws Exception {
        String customerId = customer("Malformed Request User", "malformed@example.com");
        assertError(get("/api/v1/does-not-exist"), 404);
        assertError(response(send("DELETE", "/api/v1/customers/" + customerId, null)), 405);
        assertError(response(send("POST", "/api/v1/accounts", null)), 400);
        assertError(response(sendRaw("POST", "/api/v1/accounts", "{", "application/json")), 400);
        assertError(response(sendRaw("POST", "/api/v1/accounts", "null", "application/json")), 400);
        assertError(response(sendRaw("POST", "/api/v1/accounts", "plain text", "text/plain")), 415);
        assertEquals(1L, count("customers"));
        assertEquals(0L, count("accounts"));
    }

    @Test
    void depositAndWithdrawalPersistExactBalancesAndHistory() throws Exception {
        String accountId = newAccount();
        ApiResponse deposit = amount(accountId, "deposit", "100.25");
        assertEquals(201, deposit.status());
        assertEquals("DEPOSIT", deposit.body().get("transactionType"));
        assertEquals(accountId, deposit.body().get("accountId"));
        assertMoney("100.25", deposit.body().get("amount"));
        assertMoney("0", deposit.body().get("balanceBefore"));
        assertMoney("100.25", deposit.body().get("balanceAfter"));

        ApiResponse withdrawal = amount(accountId, "withdraw", "35.10");
        assertEquals(201, withdrawal.status());
        assertEquals("WITHDRAWAL", withdrawal.body().get("transactionType"));
        assertMoney("100.25", withdrawal.body().get("balanceBefore"));
        assertMoney("65.15", withdrawal.body().get("balanceAfter"));
        assertBalance(accountId, "65.15");
        assertEquals(2L, count("account_transactions"));
        assertEquals(0L, count("transfers"));

        ApiResponse history = get("/api/v1/accounts/" + accountId + "/transactions");
        assertEquals(200, history.status());
        assertEquals(2, ((Number) history.body().get("totalElements")).intValue());
        assertEquals(2, content(history).size());
        assertTrue(content(history).stream().allMatch(row -> row.get("id") != null && row.get("createdAt") != null));
    }

    @Test
    void invalidAmountsAndOverdraftLeaveBalancesAndHistoryUnchanged() throws Exception {
        String source = newAccount();
        String destination = newAccount();
        assertEquals(201, amount(source, "deposit", "10").status());

        for (String invalid : List.of("0", "-1", "0.001", "1000000000000000000000000000000")) {
            for (String operation : List.of("deposit", "withdraw")) {
                assertError(amount(source, operation, invalid), 400, "INVALID_AMOUNT");
            }
            assertError(transfer(source, destination, invalid), 400, "INVALID_AMOUNT");
        }
        assertError(post("/api/v1/accounts/" + source + "/deposit", Map.of()), 400, "INVALID_AMOUNT");
        assertError(amount(source, "withdraw", "10.01"), 409, "INSUFFICIENT_BALANCE");
        assertError(transfer(source, destination, "10.01"), 409, "INSUFFICIENT_BALANCE");
        assertBalance(source, "10");
        assertBalance(destination, "0");
        assertEquals(1L, count("account_transactions"));
        assertEquals(0L, count("transfers"));
    }

    @Test
    void balanceOverflowRejectsDepositAndTransferWithoutPartialChanges() throws Exception {
        String source = newAccount();
        String destination = newAccount();
        assertEquals(201, amount(source, "deposit", "100").status());
        // numeric(19, 2) admits at most 17 digits before the decimal point.
        jdbc.update("UPDATE accounts SET balance = ? WHERE id = ?",
                new BigDecimal("99999999999999999.99"), UUID.fromString(destination));

        assertError(amount(destination, "deposit", "0.01"), 409, "BALANCE_LIMIT_EXCEEDED");
        assertError(transfer(source, destination, "0.01"), 409, "BALANCE_LIMIT_EXCEEDED");
        assertBalance(source, "100");
        assertBalance(destination, "99999999999999999.99");
        assertEquals(1L, count("account_transactions"));
        assertEquals(0L, count("transfers"));
    }

    @Test
    void inactiveAccountsAndMissingTransferParticipantsAreRejected() throws Exception {
        String source = newAccount();
        String destination = newAccount();
        assertEquals(201, amount(source, "deposit", "100").status());
        String missing = UUID.randomUUID().toString();

        assertError(amount(missing, "deposit", "1"), 404, "ACCOUNT_NOT_FOUND");
        assertError(amount(missing, "withdraw", "1"), 404, "ACCOUNT_NOT_FOUND");
        assertError(transfer(missing, destination, "1"), 404, "ACCOUNT_NOT_FOUND");
        assertError(transfer(source, missing, "1"), 404, "ACCOUNT_NOT_FOUND");
        assertError(transfer(source, source, "1"), 400, "SAME_ACCOUNT");

        for (String status : List.of("BLOCKED", "CLOSED")) {
            setStatus(source, status);
            assertError(amount(source, "deposit", "1"), 409, "ACCOUNT_NOT_ACTIVE");
            assertError(amount(source, "withdraw", "1"), 409, "ACCOUNT_NOT_ACTIVE");
            assertError(transfer(source, destination, "1"), 409, "ACCOUNT_NOT_ACTIVE");
            setStatus(source, "ACTIVE");
            setStatus(destination, status);
            assertError(transfer(source, destination, "1"), 409, "ACCOUNT_NOT_ACTIVE");
            setStatus(destination, "ACTIVE");
        }
        assertBalance(source, "100");
        assertBalance(destination, "0");
        assertEquals(1L, count("account_transactions"));
        assertEquals(0L, count("transfers"));
    }

    @Test
    void transferCreatesOneHeaderAndTwoLinkedEntriesWithoutCreatingMoney() throws Exception {
        String source = newAccount();
        String destination = newAccount();
        assertEquals(201, amount(source, "deposit", "500.25").status());

        ApiResponse response = transfer(source, destination, "125.15");
        assertEquals(201, response.status());
        String transferId = response.body().get("id").toString();
        assertEquals(source, response.body().get("sourceAccountId"));
        assertEquals(destination, response.body().get("destinationAccountId"));
        assertEquals("SUCCESS", response.body().get("status"));
        assertMoney("125.15", response.body().get("amount"));
        assertNotNull(response.body().get("referenceNumber"));
        assertNotNull(response.body().get("completedAt"));
        assertBalance(source, "375.10");
        assertBalance(destination, "125.15");
        assertEquals(1L, count("transfers"));
        assertEquals(3L, count("account_transactions"));

        List<Map<String, Object>> entries = jdbc.queryForList(
                "SELECT * FROM account_transactions WHERE transfer_id = ? ORDER BY transaction_type",
                UUID.fromString(transferId));
        assertEquals(2, entries.size());
        Map<String, Object> credit = entries.get(0);
        Map<String, Object> debit = entries.get(1);
        assertEquals("TRANSFER_CREDIT", credit.get("transaction_type"));
        assertEquals(destination, credit.get("account_id").toString());
        assertMoney("0", credit.get("balance_before"));
        assertMoney("125.15", credit.get("balance_after"));
        assertEquals("TRANSFER_DEBIT", debit.get("transaction_type"));
        assertEquals(source, debit.get("account_id").toString());
        assertMoney("500.25", debit.get("balance_before"));
        assertMoney("375.10", debit.get("balance_after"));
        assertMoney("125.15", debit.get("amount"));
        assertMoney("125.15", credit.get("amount"));
    }

    @Test
    void durableTransferIdempotencySurvivesRedisCacheMiss() throws Exception {
        String source = newAccount();
        String destination = newAccount();
        assertEquals(201, amount(source, "deposit", "100").status());
        String key = "durable-retry-" + UUID.randomUUID();

        ApiResponse first = transfer(source, destination, "40", key);
        ApiResponse cachedRetry = transfer(source, destination, "40", key);
        assertEquals(201, first.status());
        assertEquals(201, cachedRetry.status());
        assertEquals(first.body().get("id"), cachedRetry.body().get("id"));
        assertEquals(1L, count("transfers"));
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM transfer_idempotency", Long.class));

        var cacheKeys = redis.keys("idempotency:transfer:*");
        assertFalse(cacheKeys.isEmpty(), "The successful transfer should be cached in Redis");
        redis.delete(cacheKeys);

        ApiResponse databaseRetry = transfer(source, destination, "40", key);
        assertEquals(201, databaseRetry.status());
        assertEquals(first.body().get("id"), databaseRetry.body().get("id"));
        assertError(transfer(source, destination, "41", key), 409, "IDEMPOTENCY_KEY_REUSED");

        assertBalance(source, "60");
        assertBalance(destination, "40");
        assertEquals(3L, count("account_transactions"));
        assertEquals(1L, count("transfers"));
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM transfer_idempotency", Long.class));
    }

    @Test
    void committedTransferEventuallyCreatesIdempotentNotificationAndAuditRecords() throws Exception {
        String source = newAccount();
        String destination = newAccount();
        assertEquals(201, amount(source, "deposit", "100").status());

        ApiResponse completed = transfer(source, destination, "25");
        assertEquals(201, completed.status());
        UUID transferId = UUID.fromString(completed.body().get("id").toString());
        UUID sourceCustomerId = jdbc.queryForObject("SELECT customer_id FROM accounts WHERE id = ?",
                UUID.class, UUID.fromString(source));
        UUID destinationCustomerId = jdbc.queryForObject("SELECT customer_id FROM accounts WHERE id = ?",
                UUID.class, UUID.fromString(destination));

        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> {
            assertEquals(1L, jdbc.queryForObject("""
                    SELECT count(*) FROM audit_logs
                    WHERE action = 'TRANSFER_COMPLETED' AND resource_id = ?
                    """, Long.class, transferId));
            assertEquals(2L, jdbc.queryForObject("""
                    SELECT count(*) FROM notifications n
                    JOIN audit_logs a ON a.event_id = n.event_id
                    WHERE a.action = 'TRANSFER_COMPLETED' AND a.resource_id = ?
                    """, Long.class, transferId));
        });

        UUID eventId = jdbc.queryForObject("""
                SELECT event_id FROM audit_logs
                WHERE action = 'TRANSFER_COMPLETED' AND resource_id = ?
                """, UUID.class, transferId);
        UUID actorId = jdbc.queryForObject("SELECT id FROM app_users WHERE customer_id = ?",
                UUID.class, sourceCustomerId);
        BankingEvent redelivery = new BankingEvent(eventId, BankingEventType.TRANSFER_COMPLETED,
                actorId, sourceCustomerId, destinationCustomerId, "TRANSFER", transferId,
                Map.of("sourceAccountId", source, "destinationAccountId", destination, "amount", "25"),
                Instant.now());
        rabbit.convertAndSend(RabbitMqConfig.BANKING_EXCHANGE,
                BankingEventType.TRANSFER_COMPLETED.routingKey(), redelivery);

        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE event_id = ?",
                    Long.class, eventId));
            assertEquals(2L, jdbc.queryForObject("SELECT count(*) FROM notifications WHERE event_id = ?",
                    Long.class, eventId));
        });
    }

    @Test
    void transferRollsBackAfterDestinationHistoryWriteFails() throws Exception {
        String source = newAccount();
        String destination = newAccount();
        assertEquals(201, amount(source, "deposit", "500").status());
        long entriesBefore = count("account_transactions");
        long transfersBefore = count("transfers");

        // A sequence records that the trigger saw flushed debit/header rows even after rollback.
        jdbc.execute("CREATE SEQUENCE integration_rollback_probe");
        jdbc.execute("""
                CREATE FUNCTION integration_reject_destination_history() RETURNS trigger AS $$
                BEGIN
                    IF NEW.account_id = '%s'::uuid AND NEW.transaction_type = 'TRANSFER_CREDIT' THEN
                        IF EXISTS (SELECT 1 FROM transfers WHERE id = NEW.transfer_id)
                           AND EXISTS (SELECT 1 FROM account_transactions
                                       WHERE transfer_id = NEW.transfer_id AND transaction_type = 'TRANSFER_DEBIT') THEN
                            PERFORM nextval('integration_rollback_probe');
                        END IF;
                        RAISE EXCEPTION 'integration forced destination history failure';
                    END IF;
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """.formatted(destination));
        try {
            jdbc.execute("""
                    CREATE TRIGGER integration_reject_destination_history
                    BEFORE INSERT ON account_transactions FOR EACH ROW
                    EXECUTE FUNCTION integration_reject_destination_history()
                    """);
            ApiResponse failed = transfer(source, destination, "125");
            assertTrue(failed.status() >= 400, "The database failure must not be reported as success");
            assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT is_called FROM integration_rollback_probe", Boolean.class),
                    "The failure must occur after the debit history and transfer header were flushed");
            assertBalance(source, "500");
            assertBalance(destination, "0");
            assertEquals(entriesBefore, count("account_transactions"));
            assertEquals(transfersBefore, count("transfers"));
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS integration_reject_destination_history ON account_transactions");
            jdbc.execute("DROP FUNCTION IF EXISTS integration_reject_destination_history()");
            jdbc.execute("DROP SEQUENCE IF EXISTS integration_rollback_probe");
        }

        assertEquals(201, transfer(source, destination, "125").status(),
                "The application must recover after the transaction rolls back");
        assertBalance(source, "375");
        assertBalance(destination, "125");
    }

    @Test
    void depositAndWithdrawalRollBackWhenHistoryCannotBeWritten() throws Exception {
        String accountId = newAccount();
        assertEquals(201, amount(accountId, "deposit", "100").status());
        jdbc.execute("""
                CREATE FUNCTION integration_reject_history() RETURNS trigger AS $$
                BEGIN
                    RAISE EXCEPTION 'integration forced history failure';
                END;
                $$ LANGUAGE plpgsql
                """);
        try {
            jdbc.execute("""
                    CREATE TRIGGER integration_reject_history BEFORE INSERT ON account_transactions
                    FOR EACH ROW EXECUTE FUNCTION integration_reject_history()
                    """);
            for (String operation : List.of("deposit", "withdraw")) {
                assertTrue(amount(accountId, operation, "10").status() >= 400);
                assertBalance(accountId, "100");
                assertEquals(1L, count("account_transactions"));
            }
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS integration_reject_history ON account_transactions");
            jdbc.execute("DROP FUNCTION IF EXISTS integration_reject_history()");
        }
    }

    @Test
    void transactionHistoryUsesStablePaginationAndStaysWithinAccount() throws Exception {
        String accountId = newAccount();
        String unrelated = newAccount();
        for (String value : List.of("10", "20", "30", "40", "50")) {
            assertEquals(201, amount(accountId, "deposit", value).status());
        }
        assertEquals(201, amount(unrelated, "deposit", "999").status());
        // Ties deliberately exercise the secondary sort key used across page boundaries.
        jdbc.update("UPDATE account_transactions SET created_at = TIMESTAMPTZ '2026-01-01 00:00:00+00' WHERE account_id = ?",
                UUID.fromString(accountId));
        List<String> expectedIds = jdbc.query(
                "SELECT id FROM account_transactions WHERE account_id = ? ORDER BY created_at DESC, id DESC",
                (row, index) -> row.getObject("id").toString(), UUID.fromString(accountId));

        ApiResponse first = get("/api/v1/accounts/" + accountId + "/transactions?page=0&size=2");
        ApiResponse second = get("/api/v1/accounts/" + accountId + "/transactions?page=1&size=2");
        ApiResponse third = get("/api/v1/accounts/" + accountId + "/transactions?page=2&size=2");
        assertEquals(200, first.status());
        assertEquals(5, ((Number) first.body().get("totalElements")).intValue());
        assertEquals(3, ((Number) first.body().get("totalPages")).intValue());
        assertEquals(0, ((Number) first.body().get("page")).intValue());
        assertEquals(2, ((Number) first.body().get("size")).intValue());
        assertEquals(expectedIds.subList(0, 2), ids(first));
        assertEquals(expectedIds.subList(2, 4), ids(second));
        assertEquals(expectedIds.subList(4, 5), ids(third));
        assertTrue(content(get("/api/v1/accounts/" + accountId + "/transactions?page=3&size=2")).isEmpty());
        assertTrue(content(get("/api/v1/accounts/" + newAccount() + "/transactions")).isEmpty());
        assertError(get("/api/v1/accounts/" + accountId + "/transactions?page=-1&size=2"), 400);
        assertError(get("/api/v1/accounts/" + accountId + "/transactions?page=0&size=0"), 400);
    }

    @Test
    void concurrentWithdrawalsCannotOverdrawOrLoseHistory() throws Exception {
        String accountId = newAccount();
        assertEquals(201, amount(accountId, "deposit", "100").status());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> concurrentWithdrawal(accountId, ready, start));
            var second = executor.submit(() -> concurrentWithdrawal(accountId, ready, start));
            try {
                assertTrue(ready.await(10, TimeUnit.SECONDS));
            } finally {
                start.countDown();
            }
            List<Integer> statuses = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            assertEquals(1, statuses.stream().filter(status -> status == 201).count());
            assertEquals(1, statuses.stream().filter(status -> status == 409).count());
        }
        assertBalance(accountId, "20");
        assertEquals(2L, count("account_transactions"));
        assertEquals(1L, jdbc.queryForObject(
                "SELECT count(*) FROM account_transactions WHERE transaction_type = 'WITHDRAWAL'", Long.class));
    }

    @Test
    void concurrentTransfersCannotOverdrawSourceAccount() throws Exception {
        String source = newAccount();
        String firstDestination = newAccount();
        String secondDestination = newAccount();
        assertEquals(201, amount(source, "deposit", "100").status());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> concurrentTransfer(source, firstDestination, "80",
                    UUID.randomUUID().toString(), ready, start));
            var second = executor.submit(() -> concurrentTransfer(source, secondDestination, "80",
                    UUID.randomUUID().toString(), ready, start));
            try {
                assertTrue(ready.await(10, TimeUnit.SECONDS));
            } finally {
                start.countDown();
            }
            List<ApiResponse> responses = List.of(
                    first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            assertEquals(1, responses.stream().filter(response -> response.status() == 201).count());
            assertEquals(1, responses.stream().filter(response -> response.status() == 409
                    && "INSUFFICIENT_BALANCE".equals(response.body().get("code"))).count());
        }

        assertBalance(source, "20");
        BigDecimal destinationTotal = jdbc.queryForObject("""
                SELECT sum(balance) FROM accounts WHERE id IN (?, ?)
                """, BigDecimal.class, UUID.fromString(firstDestination), UUID.fromString(secondDestination));
        assertMoney("80", destinationTotal);
        assertEquals(1L, count("transfers"));
        assertEquals(3L, count("account_transactions"));
        assertEquals(1L, jdbc.queryForObject("""
                SELECT count(*) FROM account_transactions
                WHERE account_id = ? AND transaction_type = 'TRANSFER_DEBIT'
                """, Long.class, UUID.fromString(source)));
    }

    private int concurrentWithdrawal(String accountId, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS));
        return amount(accountId, "withdraw", "80").status();
    }

    private ApiResponse concurrentTransfer(String source, String destination, String amount, String idempotencyKey,
                                           CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS));
        return transfer(source, destination, amount, idempotencyKey);
    }

    private String customer(String name, String email) throws Exception {
        ApiResponse registered = postPublic("/api/v1/auth/register", Map.of(
                "fullName", name, "email", email, "password", PASSWORD));
        assertEquals(201, registered.status(), registered.body().toString());
        String customerId = registered.body().get("customerId").toString();

        ApiResponse login = postPublic("/api/v1/auth/login", Map.of("email", email, "password", PASSWORD));
        assertEquals(200, login.status(), login.body().toString());
        String token = login.body().get("accessToken").toString();
        customerTokens.put(customerId, token);
        currentToken = token;
        return customerId;
    }

    private Map<String, Object> openAccount(String customerId) throws Exception {
        ApiResponse response = response(send("POST", "/api/v1/accounts", Map.of("customerId", customerId),
                customerTokens.get(customerId), Map.of()));
        assertEquals(201, response.status(), response.body().toString());
        accountTokens.put(response.body().get("id").toString(), customerTokens.get(customerId));
        return response.body();
    }

    private String newAccount() throws Exception {
        return openAccount(customer("Test Customer", UUID.randomUUID() + "@example.com")).get("id").toString();
    }

    private ApiResponse amount(String accountId, String operation, String amount) throws Exception {
        return response(send("POST", "/api/v1/accounts/" + accountId + "/" + operation,
                Map.of("amount", new BigDecimal(amount)), accountTokens.getOrDefault(accountId, currentToken), Map.of()));
    }

    private ApiResponse transfer(String source, String destination, String amount) throws Exception {
        return transfer(source, destination, amount, UUID.randomUUID().toString());
    }

    private ApiResponse transfer(String source, String destination, String amount, String idempotencyKey)
            throws Exception {
        return response(send("POST", "/api/v1/transfers", Map.of("sourceAccountId", source,
                        "destinationAccountId", destination, "amount", new BigDecimal(amount),
                        "description", "Integration transfer"), accountTokens.getOrDefault(source, currentToken),
                Map.of("Idempotency-Key", idempotencyKey)));
    }

    private void assertBalance(String accountId, String expected) throws Exception {
        assertMoney(expected, jdbc.queryForObject("SELECT balance FROM accounts WHERE id = ?",
                BigDecimal.class, UUID.fromString(accountId)));
        ApiResponse account = get("/api/v1/accounts/" + accountId);
        assertEquals(200, account.status());
        assertMoney(expected, account.body().get("balance"));
    }

    private void setStatus(String accountId, String status) {
        assertEquals(1, jdbc.update("UPDATE accounts SET status = ? WHERE id = ?", status, UUID.fromString(accountId)));
    }

    private long count(String table) {
        if (!List.of("customers", "accounts", "transfers", "account_transactions").contains(table)) {
            throw new IllegalArgumentException("Unexpected table");
        }
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private static void assertMoney(String expected, Object actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(actual.toString())),
                () -> "Expected " + expected + " but got " + actual);
    }

    private static void assertError(ApiResponse response, int expectedStatus) {
        assertEquals(expectedStatus, response.status(), response.body().toString());
        assertNotNull(response.body().get("code"));
        assertNotNull(response.body().get("message"));
        assertNotNull(response.body().get("timestamp"));
    }

    private static void assertError(ApiResponse response, int expectedStatus, String expectedCode) {
        assertError(response, expectedStatus);
        assertEquals(expectedCode, response.body().get("code"));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> content(ApiResponse response) {
        assertEquals(200, response.status(), response.body().toString());
        return (List<Map<String, Object>>) response.body().get("content");
    }

    private static List<String> ids(ApiResponse response) {
        return content(response).stream().map(row -> row.get("id").toString()).toList();
    }

    private ApiResponse get(String path) throws Exception {
        return response(send("GET", path, null, tokenFor(path), Map.of()));
    }

    private ApiResponse post(String path, Map<String, Object> body) throws Exception {
        return response(send("POST", path, body, currentToken, Map.of()));
    }

    private ApiResponse postPublic(String path, Map<String, Object> body) throws Exception {
        return response(send("POST", path, body, null, Map.of()));
    }

    private HttpResponse<String> send(String method, String path, Map<String, Object> body) throws Exception {
        return send(method, path, body, tokenFor(path), Map.of());
    }

    private HttpResponse<String> send(String method, String path, Map<String, Object> body,
                                      String token, Map<String, String> headers) throws Exception {
        return sendRaw(method, path, body == null ? null : json.writeValueAsString(body),
                "application/json", token, headers);
    }

    private HttpResponse<String> sendRaw(String method, String path, String body, String contentType) throws Exception {
        return sendRaw(method, path, body, contentType, tokenFor(path), Map.of());
    }

    private HttpResponse<String> sendRaw(String method, String path, String body, String contentType,
                                         String token, Map<String, String> headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:"
                        + environment.getRequiredProperty("local.server.port") + path))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        headers.forEach(request::header);
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", contentType)
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String tokenFor(String path) {
        for (Map.Entry<String, String> entry : accountTokens.entrySet()) {
            if (path.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        for (Map.Entry<String, String> entry : customerTokens.entrySet()) {
            if (path.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return currentToken;
    }

    @SuppressWarnings("unchecked")
    private ApiResponse response(HttpResponse<String> response) {
        Map<String, Object> body = response.body().isBlank()
                ? new HashMap<>() : json.readerFor(Map.class).with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readValue(response.body());
        return new ApiResponse(response.statusCode(), body);
    }

    private record ApiResponse(int status, Map<String, Object> body) {}
}
