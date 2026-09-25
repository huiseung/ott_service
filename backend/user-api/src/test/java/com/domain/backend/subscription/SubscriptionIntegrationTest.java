package com.domain.backend.subscription;

import com.domain.backend.user.application.ContentEligibility;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
class SubscriptionIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");
    static AnnotationConfigApplicationContext context;
    JdbcTemplate jdbc;
    SubscriptionService service;
    SubscriptionOutboxStore store;

    @Configuration @EnableTransactionManagement
    @Import({SubscriptionService.class, SubscriptionOutbox.class, SubscriptionOutboxStore.class})
    static class Config {
        @Bean DataSource dataSource() { return new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()); }
        @Bean JdbcTemplate jdbc(DataSource source) { return new JdbcTemplate(source); }
        @Bean PlatformTransactionManager transactionManager(DataSource source) { return new JdbcTransactionManager(source); }
        @Bean ContentEligibility eligibility(JdbcTemplate jdbc) { return new ContentEligibility(jdbc, "KR"); }
    }
    @BeforeAll static void start() {
        context = new AnnotationConfigApplicationContext(Config.class);
        Flyway.configure().dataSource(context.getBean(DataSource.class)).load().migrate();
    }
    @AfterAll static void stop() { if (context != null) context.close(); }
    @BeforeEach void seed() {
        jdbc = context.getBean(JdbcTemplate.class); service = context.getBean(SubscriptionService.class); store = context.getBean(SubscriptionOutboxStore.class);
        jdbc.update("delete from subscription_outbox");
        jdbc.update("delete from subscription_checkouts");
        jdbc.update("delete from user_subscriptions");
        jdbc.update("delete from content_availabilities");
        jdbc.update("delete from contents");
        jdbc.update("delete from users");
        jdbc.update("insert into users(id,login_id,display_name,password_hash,role,enabled,created_at,updated_at) values (1,'one','one','unused','USER',true,now(),now()),(2,'two','two','unused','USER',true,now(),now())");
        jdbc.update("insert into contents(id,type,status,original_country,original_language,created_at,updated_at) values (10,'MOVIE','PUBLISHED','KR','ko',now(),now())");
        jdbc.update("insert into content_availabilities(content_id,country_code,available_from,status) values(10,'KR','2020-01-01','AVAILABLE')");
    }
    private int count(String table) { return jdbc.queryForObject("select count(*) from " + table, Integer.class); }

    @Test void retryActivationAndCancellationAreIdempotentAndReactivationIsNotAcquisition() {
        UUID request = UUID.randomUUID(), cta = UUID.randomUUID();
        var checkout = service.checkout(1, request, 10L, cta);
        assertThat(service.checkout(1, request, 10L, cta).checkoutId()).isEqualTo(checkout.checkoutId());
        assertThatThrownBy(() -> service.checkout(1, request, null, null)).hasMessageContaining("409");
        service.activateLocal(1, UUID.fromString(checkout.checkoutId()));
        service.activateLocal(1, UUID.fromString(checkout.checkoutId()));
        assertThat(count("subscription_outbox")).isEqualTo(3);
        service.cancel(1); service.cancel(1);
        var second = service.checkout(1, UUID.randomUUID(), 10L, UUID.randomUUID());
        service.activateLocal(1, UUID.fromString(second.checkoutId()));
        assertThat(count("subscription_outbox")).isEqualTo(6);
        assertThat(jdbc.queryForObject("select count(*) from subscription_outbox where event_type='SUBSCRIPTION_ACTIVATED' and json_extract(payload,'$.payload.firstActivation')=true", Integer.class)).isEqualTo(1);
    }
    @Test void rollbackRevertsBothBusinessRowsAndOutbox() {
        var tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        assertThatThrownBy(() -> tx.execute(status -> {
            var checkout = service.checkout(1, UUID.randomUUID(), 10L, null);
            service.activateLocal(1, UUID.fromString(checkout.checkoutId()));
            throw new IllegalStateException("abort");
        })).hasMessage("abort");
        assertThat(count("subscription_outbox")).isZero();
        assertThat(count("subscription_checkouts")).isZero();
        assertThat(count("user_subscriptions")).isZero();
    }
    @Test void concurrentSameRequestCreatesOneCheckout() throws Exception {
        UUID request = UUID.randomUUID();
        try (var threads = Executors.newFixedThreadPool(2)) {
            var one = threads.submit(() -> service.checkout(1, request, 10L, null));
            var two = threads.submit(() -> service.checkout(1, request, 10L, null));
            assertThat(one.get(10, TimeUnit.SECONDS).checkoutId()).isEqualTo(two.get(10, TimeUnit.SECONDS).checkoutId());
        }
        assertThat(count("subscription_checkouts")).isEqualTo(1);
        assertThat(count("subscription_outbox")).isEqualTo(2);
    }
    @Test void enforcesOwnershipExpiryAndAvailableContent() {
        var checkout = service.checkout(1, UUID.randomUUID(), 10L, null);
        assertThatThrownBy(() -> service.activateLocal(2, UUID.fromString(checkout.checkoutId()))).hasMessageContaining("404");
        jdbc.update("update subscription_checkouts set expires_at='2020-01-01'");
        assertThatThrownBy(() -> service.activateLocal(1, UUID.fromString(checkout.checkoutId()))).hasMessageContaining("409");
        jdbc.update("update contents set status='DRAFT'");
        assertThatThrownBy(() -> service.checkout(2, UUID.randomUUID(), 10L, null)).hasMessageContaining("404");
        assertThat(service.membership(1).status()).isEqualTo("PENDING");
        assertThat(count("subscription_outbox")).isEqualTo(2);
    }
    @Test void expiredLeaseFencesStaleAcknowledgementAndMaintainsAggregateOrder() {
        service.checkout(1, UUID.randomUUID(), 10L, null);
        var first = store.claim().getFirst();
        assertThat(store.claim()).isEmpty();
        jdbc.update("update subscription_outbox set available_at='2020-01-01' where id=?", first.id());
        var recovered = store.claim().getFirst();
        assertThat(recovered.payload()).isEqualTo(first.payload());
        assertThat(recovered.claimToken()).isNotEqualTo(first.claimToken());
        store.delivered(first);
        assertThat(store.claim()).isEmpty();
        store.delivered(recovered);
        assertThat(store.claim().getFirst().id()).isGreaterThan(first.id());
    }
    @Test void exhaustedEventBlocksLaterEventsWithoutDroppingThem() {
        service.checkout(1, UUID.randomUUID(), 10L, null);
        jdbc.update("update subscription_outbox set attempts=7");
        var delivery = store.claim().getFirst();
        store.failed(delivery, "TEST_FAILURE");
        assertThat(store.claim()).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from subscription_outbox where status='FAILED'", Integer.class)).isEqualTo(1);
        assertThat(count("subscription_outbox")).isEqualTo(2);
    }
    @Test @SuppressWarnings("unchecked")
    void kafkaFailureLeavesCommittedSubscriptionAndRetryableOutboxWithoutDbTransaction() {
        var checkout = service.checkout(1, UUID.randomUUID(), 10L, null);
        service.activateLocal(1, UUID.fromString(checkout.checkoutId()));
        KafkaTemplate<String,String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(anyString(), anyString(), anyString())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return CompletableFuture.failedFuture(new IllegalStateException("broker down"));
        });
        new SubscriptionOutboxRelay(store, kafka, "subscription-events").publish();
        assertThat(service.membership(1).status()).isEqualTo("ACTIVE");
        assertThat(count("subscription_outbox")).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from subscription_outbox where status='PENDING'", Integer.class)).isEqualTo(3);
    }
}
