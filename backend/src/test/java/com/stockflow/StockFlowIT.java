package com.stockflow;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.stockflow.auth.application.AuthService;
import com.stockflow.auth.domain.SessionIdentity;
import com.stockflow.billing.application.*;
import com.stockflow.billing.domain.*;
import com.stockflow.businesses.domain.*;
import com.stockflow.categories.domain.*;
import com.stockflow.inventory.application.InventoryExitService;
import com.stockflow.inventory.application.ManualMovementService;
import com.stockflow.inventory.application.SaleInventoryListener;
import com.stockflow.inventory.domain.InventoryMovement;
import com.stockflow.inventory.domain.MovementType;
import com.stockflow.payments.domain.PaymentStrategy.PaymentRequest;
import com.stockflow.products.domain.*;
import com.stockflow.reports.domain.ReportRepository;
import com.stockflow.sales.application.*;
import com.stockflow.sales.domain.*;
import com.stockflow.settings.domain.*;
import com.stockflow.users.application.UserService;
import com.stockflow.users.domain.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(
        properties = {
            "app.bootstrap.email=",
            "app.bootstrap.password=",
            "app.cookie-secure=false",
            "debug=false"
        })
@AutoConfigureMockMvc
@Testcontainers
class StockFlowIT {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired BusinessRepository businesses;
    @Autowired AccountRepository accounts;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired SettingsRepository settings;
    @Autowired SaleService sales;
    @Autowired SaleItemRepository saleItems;
    @Autowired InventoryExitService exits;
    @Autowired ManualMovementService manualMovements;
    @Autowired ReportRepository reports;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired SubscriptionPlanRepository subscriptionPlans;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired SubscriptionPlanService subscriptionPlanService;
    @Autowired SubscriptionAdminService subscriptionAdminService;
    @Autowired SubscriptionLifecycleService subscriptionLifecycleService;
    @Autowired SubscriptionPaymentService subscriptionPaymentService;
    @Autowired SubscriptionScheduler subscriptionScheduler;
    @Autowired UserService userService;
    @Autowired AuthService authService;
    @MockitoSpyBean SaleInventoryListener saleInventoryListener;
    private UUID businessA;
    private UUID businessB;
    private UUID userA;
    private UUID userB;
    private UUID productA;
    private UUID productB;
    private UsernamePasswordAuthenticationToken identityA;
    private UsernamePasswordAuthenticationToken identityB;

    @BeforeEach
    void seedIsolatedBusinesses() {
        businessA = UUID.randomUUID();
        businessB = UUID.randomUUID();
        userA = UUID.randomUUID();
        userB = UUID.randomUUID();
        productA = UUID.randomUUID();
        productB = UUID.randomUUID();
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> {
                            seed(businessA, userA, productA);
                            seed(businessB, userB, productB);
                        });
        identityA = identity(userA, businessA, "BUSINESS_USER");
        identityB = identity(userB, businessB, "BUSINESS_USER");
        SecurityContextHolder.getContext().setAuthentication(identityA);
    }

    private void seed(UUID businessId, UUID userId, UUID productId) {
        var now = Instant.now();
        var categoryId = UUID.randomUUID();
        businesses.save(new Business(businessId, "Test business", null, true, now, now));
        var plan = subscriptionPlans.findDefaultActive().orElseThrow();
        var periodEnd = now.plusSeconds(86_400L * 3650);
        subscriptions.save(
                new Subscription(
                        UUID.randomUUID(),
                        businessId,
                        plan.id(),
                        SubscriptionStatus.ACTIVE,
                        now,
                        null,
                        now,
                        periodEnd,
                        null,
                        periodEnd,
                        null,
                        now,
                        now));
        accounts.save(
                new Account(
                        userId,
                        businessId,
                        userId + "@test.local",
                        "Test user",
                        passwords.encode("LocalTestPassword-42"),
                        "BUSINESS_USER",
                        true,
                        false,
                        0,
                        null,
                        now,
                        now));
        settings.save(
                new BusinessSettings(
                        businessId,
                        "POS",
                        true,
                        true,
                        true,
                        new BigDecimal("0.19"),
                        true,
                        "America/Santiago"));
        categories.save(new Category(categoryId, businessId, "Test category", true, now, now));
        products.save(
                new Product(
                        productId,
                        businessId,
                        categoryId,
                        "Test product",
                        null,
                        "SHARED-BARCODE",
                        new BigDecimal("100"),
                        null,
                        5,
                        3,
                        null,
                        true,
                        now,
                        now));
    }

    private UsernamePasswordAuthenticationToken identity(
            UUID userId, UUID businessId, String role) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new SessionIdentity(userId, businessId, role, 0),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
    }

    private ConfirmSaleCommand command(UUID productId, int quantity) {
        return new ConfirmSaleCommand(
                UUID.randomUUID(),
                List.of(new ConfirmSaleCommand.Line(productId, quantity)),
                new PaymentRequest("CASH", new BigDecimal("100000"), null, false),
                new BigDecimal("119").multiply(BigDecimal.valueOf(quantity)));
    }

    @Test
    void manualMovementRetriesReturnOriginalAndRejectChangedPayload() throws Exception {
        var requestId = UUID.randomUUID();
        var result = manualMovements.confirm(requestId, productA, MovementType.MANUAL_EXIT, 2);
        assertThat(manualMovements.confirm(requestId, productA, MovementType.MANUAL_EXIT, 2).id())
                .isEqualTo(result.id());
        assertThatThrownBy(
                        () ->
                                manualMovements.confirm(
                                        requestId, productA, MovementType.MANUAL_EXIT, 1))
                .hasMessage("IDEMPOTENCY_CONFLICT");
        assertThat(products.find(businessA, productA).orElseThrow().stock()).isEqualTo(3);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from inventory_movements where business_id = ?",
                                Long.class,
                                businessA))
                .isEqualTo(1);
        SecurityContextHolder.getContext().setAuthentication(identityB);
        var other = manualMovements.confirm(requestId, productB, MovementType.MANUAL_EXIT, 1);
        assertThat(other.id()).isNotEqualTo(result.id());
        mvc.perform(
                        post("/api/v1/inventory")
                                .with(authentication(identityA))
                                .with(csrf())
                                .contentType("application/json")
                                .content(
                                        "{\"productId\":\""
                                                + productA
                                                + "\",\"type\":\"MANUAL_EXIT\",\"quantity\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post("/api/v1/inventory")
                                .with(authentication(identityA))
                                .with(csrf())
                                .contentType("application/json")
                                .content(
                                        "{\"requestId\":\""
                                                + requestId
                                                + "\",\"productId\":\""
                                                + productA
                                                + "\",\"type\":\"MANUAL_EXIT\",\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(result.id().toString()));
    }

    @Test
    void simultaneousManualRetriesChangeStockOnceAndFailureDoesNotClaimRequest() throws Exception {
        var requestId = UUID.randomUUID();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<InventoryMovement> task =
                    () -> {
                        SecurityContextHolder.getContext().setAuthentication(identityA);
                        try {
                            start.await();
                            return manualMovements.confirm(
                                    requestId, productA, MovementType.MANUAL_EXIT, 2);
                        } finally {
                            SecurityContextHolder.clearContext();
                        }
                    };
            var first = executor.submit(task);
            var second = executor.submit(task);
            start.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS).id())
                    .isEqualTo(second.get(15, TimeUnit.SECONDS).id());
        }
        assertThat(products.find(businessA, productA).orElseThrow().stock()).isEqualTo(3);
        var failedRequest = UUID.randomUUID();
        assertThatThrownBy(
                        () ->
                                manualMovements.confirm(
                                        failedRequest, productA, MovementType.MANUAL_EXIT, 4))
                .hasMessage("INSUFFICIENT_STOCK");
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from manual_inventory_requests where business_id = ? and request_id = ?",
                                Long.class,
                                businessA,
                                failedRequest))
                .isZero();
        assertThat(
                        manualMovements
                                .confirm(failedRequest, productA, MovementType.MANUAL_ENTRY, 1)
                                .newStock())
                .isEqualTo(4);
    }

    @Test
    void priceOrTaxChangeRejectsUnconfirmedSaleBeforeAnySideEffects() throws Exception {
        var requested = command(productA, 1);
        jdbc.update(
                "update products set sale_price = 120 where business_id = ? and id = ?",
                businessA,
                productA);
        assertThatThrownBy(() -> sales.confirm(requested)).hasMessage("PRICE_CHANGED");
        jdbc.update(
                "update products set sale_price = 100 where business_id = ? and id = ?",
                businessA,
                productA);
        jdbc.update(
                "update business_settings set tax_enabled = false where business_id = ?",
                businessA);
        assertThatThrownBy(() -> sales.confirm(requested)).hasMessage("PRICE_CHANGED");
        assertThat(products.find(businessA, productA).orElseThrow().stock()).isEqualTo(5);
        for (String table :
                List.of(
                        "sales",
                        "sale_items",
                        "inventory_movements",
                        "audit_events",
                        "notifications"))
            assertThat(
                            jdbc.queryForObject(
                                    "select count(*) from " + table + " where business_id = ?",
                                    Long.class,
                                    businessA))
                    .isZero();
        var revised =
                new ConfirmSaleCommand(
                        requested.requestId(),
                        requested.items(),
                        requested.payment(),
                        new BigDecimal("100"));
        assertThat(sales.confirm(revised).sale().total()).isEqualByComparingTo("100");
        mvc.perform(
                        post("/api/v1/sales")
                                .with(authentication(identityA))
                                .with(csrf())
                                .contentType("application/json")
                                .content(
                                        "{\"requestId\":\""
                                                + UUID.randomUUID()
                                                + "\",\"items\":[{\"productId\":\""
                                                + productA
                                                + "\",\"quantity\":1}],\"paymentMethod\":\"CASH\",\"cashReceived\":1000}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirmedSaleRetryReturnsOriginalDespiteCatalogAndTaxChanges() {
        var requested = command(productA, 1);
        var result = sales.confirm(requested);
        jdbc.update(
                "update products set sale_price = 500, active = false where business_id = ? and id = ?",
                businessA,
                productA);
        jdbc.update(
                "update business_settings set tax_enabled = false where business_id = ?",
                businessA);
        assertThat(sales.confirm(requested).sale().id()).isEqualTo(result.sale().id());
        assertThat(sales.confirm(requested).sale().total()).isEqualByComparingTo("119");
        assertThatThrownBy(
                        () ->
                                sales.confirm(
                                        new ConfirmSaleCommand(
                                                requested.requestId(),
                                                requested.items(),
                                                requested.payment(),
                                                new BigDecimal("500"))))
                .hasMessage("IDEMPOTENCY_CONFLICT");
        assertThat(products.find(businessA, productA).orElseThrow().stock()).isEqualTo(4);
    }

    @Test
    void csrfRejectedLoginRequestsDoNotConsumeRateLimit() throws Exception {
        for (int i = 0; i < 21; i++) {
            mvc.perform(
                            post("/api/v1/auth/login")
                                    .with(
                                            request -> {
                                                request.setRemoteAddr("198.51.100.20");
                                                return request;
                                            })
                                    .contentType("application/json")
                                    .content("{}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(
                        post("/api/v1/auth/login")
                                .with(csrf())
                                .with(
                                        request -> {
                                            request.setRemoteAddr("198.51.100.20");
                                            return request;
                                        })
                                .contentType("application/json")
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rankingUsesIdentifiersAcrossRenamesAndReusedNames() {
        var originalCategory = products.find(businessA, productA).orElseThrow().categoryId();
        sales.confirm(command(productA, 2));
        jdbc.update(
                "update products set name = 'Leading product' where business_id = ? and id = ?",
                businessA,
                productA);
        jdbc.update(
                "update categories set name = 'Leading category' where business_id = ? and id = ?",
                businessA,
                originalCategory);
        sales.confirm(command(productA, 2));
        var secondProduct = UUID.randomUUID();
        var secondCategory = UUID.randomUUID();
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> {
                            var now = Instant.now();
                            categories.save(
                                    new Category(
                                            secondCategory,
                                            businessA,
                                            "Test category",
                                            true,
                                            now,
                                            now));
                            products.save(
                                    new Product(
                                            secondProduct,
                                            businessA,
                                            secondCategory,
                                            "Test product",
                                            null,
                                            null,
                                            new BigDecimal("100"),
                                            null,
                                            5,
                                            0,
                                            null,
                                            true,
                                            now,
                                            now));
                        });
        sales.confirm(command(secondProduct, 3));
        var today = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        var summary = reports.summarize(businessA, today, today, java.time.ZoneOffset.UTC, false);
        assertThat(summary.topProduct()).isEqualTo("Leading product");
        assertThat(summary.topCategory()).isEqualTo("Leading category");
        assertThat(summary.unitsSold()).isEqualTo(7);
        // Reclassification after the sales must not rewrite the category attribution.
        jdbc.update(
                "update products set category_id = ? where business_id = ? and id = ?",
                secondCategory,
                businessA,
                productA);
        assertThat(
                        reports.summarize(businessA, today, today, java.time.ZoneOffset.UTC, false)
                                .topCategory())
                .isEqualTo("Leading category");
    }

    @Test
    void loginPersistsCompactPrincipalAndRestoresIdentityFromJdbcSession() throws Exception {
        SecurityContextHolder.clearContext();
        var csrfResponse =
                mvc.perform(get("/api/v1/auth/csrf"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse();
        var anonymousCookie = csrfResponse.getCookie("STOCKFLOW_SESSION");
        assertThat(anonymousCookie).isNotNull();
        String csrfToken =
                com.jayway.jsonpath.JsonPath.read(csrfResponse.getContentAsString(), "$.token");
        var loginResponse =
                mvc.perform(
                                post("/api/v1/auth/login")
                                        .cookie(anonymousCookie)
                                        .header("X-CSRF-TOKEN", csrfToken)
                                        .contentType("application/json")
                                        .content(
                                                "{\"email\":\""
                                                        + userA
                                                        + "@test.local\",\"password\":\"LocalTestPassword-42\"}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.id").value(userA.toString()))
                        .andReturn()
                        .getResponse();
        var authenticatedCookie = loginResponse.getCookie("STOCKFLOW_SESSION");
        assertThat(authenticatedCookie).isNotNull();
        assertThat(authenticatedCookie.getValue()).isNotEqualTo(anonymousCookie.getValue());
        assertThat(
                        jdbc.queryForObject(
                                "select principal_name from spring_session where principal_name = ?",
                                String.class,
                                userA.toString()))
                .isEqualTo(userA.toString());

        SecurityContextHolder.clearContext();
        mvc.perform(get("/api/v1/auth/me").cookie(authenticatedCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userA.toString()))
                .andExpect(jsonPath("$.businessId").value(businessA.toString()));
        mvc.perform(get("/api/v1/auth/me").cookie(anonymousCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void synchronousListenerFailureRollsBackSaleStockMovementsAndNotifications() {
        var command = command(productA, 2);
        SaleInventoryListener listener =
                AopTestUtils.getUltimateTargetObject(saleInventoryListener);
        doAnswer(
                        invocation -> {
                            invocation.callRealMethod();
                            assertThat(products.find(businessA, productA).orElseThrow().stock())
                                    .isEqualTo(3);
                            assertThat(
                                            jdbc.queryForObject(
                                                    "select count(*) from inventory_movements where business_id = ?",
                                                    Long.class,
                                                    businessA))
                                    .isEqualTo(1);
                            throw new IllegalStateException(
                                    "Simulated synchronous listener failure");
                        })
                .when(listener)
                .confirmed(any(SaleConfirmedEvent.class));

        assertThatThrownBy(() -> sales.confirm(command))
                .hasMessage("Simulated synchronous listener failure");
        assertThat(products.find(businessA, productA).orElseThrow().stock()).isEqualTo(5);
        for (String table :
                List.of(
                        "sales",
                        "sale_items",
                        "inventory_movements",
                        "notifications",
                        "audit_events")) {
            assertThat(
                            jdbc.queryForObject(
                                    "select count(*) from " + table + " where business_id = ?",
                                    Long.class,
                                    businessA))
                    .as("Rollback of %s", table)
                    .isZero();
        }

        doCallRealMethod().when(listener).confirmed(any(SaleConfirmedEvent.class));
        assertThat(sales.confirm(command).sale().status()).isEqualTo("CONFIRMED");
        assertThat(products.find(businessA, productA).orElseThrow().stock()).isEqualTo(3);
    }

    @Test
    void isolatesIdentifiersSearchBarcodesAndReports() throws Exception {
        mvc.perform(get("/api/v1/products/" + productB).with(authentication(identityA)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/products/barcode/SHARED-BARCODE").with(authentication(identityA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productA.toString()));
        mvc.perform(get("/api/v1/products").with(authentication(identityA)))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].businessId").value(businessA.toString()));
        SecurityContextHolder.getContext().setAuthentication(identityB);
        sales.confirm(command(productB, 1));
        mvc.perform(get("/api/v1/reports/summary").with(authentication(identityA)))
                .andExpect(jsonPath("$.revenue").value(0))
                .andExpect(jsonPath("$.saleCount").value(0));
    }

    @Test
    void notificationReadsAndInventoryHistoryRemainTenantScoped() throws Exception {
        sales.confirm(command(productA, 2));
        var notificationId =
                jdbc.queryForObject(
                        "select id from notifications where business_id = ?",
                        UUID.class,
                        businessA);
        mvc.perform(get("/api/v1/notifications").with(authentication(identityA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].read").value(false));
        mvc.perform(get("/api/v1/notifications").with(authentication(identityB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(
                        post("/api/v1/notifications/" + notificationId + "/read")
                                .with(authentication(identityB))
                                .with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(
                        post("/api/v1/notifications/" + notificationId + "/read")
                                .with(authentication(identityA))
                                .with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/notifications").with(authentication(identityA)))
                .andExpect(jsonPath("$.items[0].read").value(true));
        mvc.perform(get("/api/v1/inventory").with(authentication(identityA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].productId").value(productA.toString()));
        mvc.perform(
                        get("/api/v1/inventory")
                                .param("productId", productA.toString())
                                .with(authentication(identityB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void confirmsAtomicallyEmitsLowStockAndRetriesWithoutDuplication() {
        var command = command(productA, 2);
        var result = sales.confirm(command);
        assertThat(result.sale().total()).isEqualByComparingTo("238.00");
        assertThat(products.find(businessA, productA).orElseThrow().stock()).isEqualTo(3);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from inventory_movements where business_id = ?",
                                Long.class,
                                businessA))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from notifications where business_id = ?",
                                Long.class,
                                businessA))
                .isEqualTo(1);
        assertThat(sales.confirm(command).sale().id()).isEqualTo(result.sale().id());
        assertThat(products.find(businessA, productA).orElseThrow().stock()).isEqualTo(3);
        assertThatThrownBy(
                        () ->
                                sales.confirm(
                                        new ConfirmSaleCommand(
                                                command.requestId(),
                                                List.of(new ConfirmSaleCommand.Line(productA, 1)),
                                                command.payment(),
                                                command.expectedTotal())))
                .hasMessage("IDEMPOTENCY_CONFLICT");
        sales.confirm(command(productA, 1));
        assertThat(products.find(businessA, productA).orElseThrow().stock()).isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from notifications where business_id = ?",
                                Long.class,
                                businessA))
                .isEqualTo(2);
    }

    @Test
    void lowStockEntryAlsoEmitsNotification() {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> {
                            var product = products.find(businessA, productA).orElseThrow();
                            products.save(
                                    new Product(
                                            product.id(),
                                            product.businessId(),
                                            product.categoryId(),
                                            product.name(),
                                            product.description(),
                                            product.barcode(),
                                            product.salePrice(),
                                            product.costPrice(),
                                            product.stock(),
                                            10,
                                            product.imageKey(),
                                            product.active(),
                                            product.createdAt(),
                                            Instant.now()));
                        });

        manualMovements.confirm(UUID.randomUUID(), productA, MovementType.PURCHASE_ENTRY, 1);

        assertThat(products.find(businessA, productA).orElseThrow().stock()).isEqualTo(6);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from notifications where business_id = ?",
                                Long.class,
                                businessA))
                .isEqualTo(1);
    }

    @Test
    void insufficientStockRollsBackEverySideEffect() {
        assertThatThrownBy(() -> sales.confirm(command(productA, 6)))
                .hasMessage("INSUFFICIENT_STOCK");
        assertThat(products.find(businessA, productA).orElseThrow().stock()).isEqualTo(5);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from sales where business_id = ?",
                                Long.class,
                                businessA))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from inventory_movements where business_id = ?",
                                Long.class,
                                businessA))
                .isZero();
    }

    @Test
    void concurrentSalesCannotOversell() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> task =
                    () -> {
                        SecurityContextHolder.getContext().setAuthentication(identityA);
                        start.await();
                        try {
                            sales.confirm(command(productA, 4));
                            return true;
                        } catch (com.stockflow.shared.domain.BusinessException exception) {
                            assertThat(exception.getMessage()).isEqualTo("INSUFFICIENT_STOCK");
                            return false;
                        } finally {
                            SecurityContextHolder.clearContext();
                        }
                    };
            var first = executor.submit(task);
            var second = executor.submit(task);
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(products.find(businessA, productA).orElseThrow().stock()).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from sales where business_id = ?",
                                Long.class,
                                businessA))
                .isEqualTo(1);
    }

    @Test
    void cancellationRestoresStockExactlyOnceAndPreservesSnapshots() {
        var result = sales.confirm(command(productA, 2));
        sales.cancel(result.sale().id());
        sales.cancel(result.sale().id());
        assertThat(products.find(businessA, productA).orElseThrow().stock()).isEqualTo(5);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from inventory_movements where business_id = ? and type = 'RETURN'",
                                Long.class,
                                businessA))
                .isEqualTo(1);
        assertThat(
                        saleItems
                                .forSale(businessA, result.sale().id())
                                .getFirst()
                                .productNameSnapshot())
                .isEqualTo("Test product");
        assertThat(saleItems.forSale(businessB, result.sale().id())).isEmpty();
    }

    @Test
    void inventoryOnlyExitsAreConfirmedAndDoNotRecordRevenue() throws Exception {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status ->
                                settings.save(
                                        new BusinessSettings(
                                                businessA,
                                                "INVENTORY_ONLY",
                                                true,
                                                true,
                                                true,
                                                new BigDecimal("0.19"),
                                                true,
                                                "America/Santiago")));
        assertThatThrownBy(() -> sales.confirm(command(productA, 1))).hasMessage("POS_DISABLED");
        var requestId = UUID.randomUUID();
        var result =
                exits.confirm(requestId, List.of(new InventoryExitService.ExitLine(productA, 2)));
        assertThat(
                        exits.confirm(
                                        requestId,
                                        List.of(new InventoryExitService.ExitLine(productA, 2)))
                                .id())
                .isEqualTo(result.id());
        assertThat(products.find(businessA, productA).orElseThrow().stock()).isEqualTo(3);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from sales where business_id = ?",
                                Long.class,
                                businessA))
                .isZero();
        mvc.perform(
                        get("/api/v1/inventory")
                                .param("type", "MANUAL_EXIT")
                                .with(authentication(identityA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].type").value("MANUAL_EXIT"))
                .andExpect(jsonPath("$.items[0].productId").value(productA.toString()));
        mvc.perform(get("/api/v1/inventory").param("type", "SALE").with(authentication(identityA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void enforcesCsrfRolesAndImmediateRevocation() throws Exception {
        mvc.perform(
                        post("/api/v1/categories")
                                .with(authentication(identityA))
                                .contentType("application/json")
                                .content("{\"name\":\"Denied\",\"active\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/users").with(authentication(identityA)))
                .andExpect(status().isForbidden());
        var cashierId = UUID.randomUUID();
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status ->
                                accounts.save(
                                        new Account(
                                                cashierId,
                                                businessA,
                                                cashierId + "@test.local",
                                                "Cashier",
                                                passwords.encode("LocalTestPassword-42"),
                                                "BUSINESS_USER",
                                                true,
                                                false,
                                                0,
                                                null,
                                                Instant.now(),
                                                Instant.now())));
        mvc.perform(
                        post("/api/v1/categories")
                                .with(
                                        authentication(
                                                identity(cashierId, businessA, "BUSINESS_USER")))
                                .with(csrf())
                                .contentType("application/json")
                                .content("{\"name\":\"Denied\",\"active\":true}"))
                .andExpect(status().isCreated());
        jdbc.update("update users set active = false where id = ?", userA);
        mvc.perform(get("/api/v1/products").with(authentication(identityA)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsClientSuppliedTenantAndCrossTenantCategory() throws Exception {
        mvc.perform(
                        post("/api/v1/categories")
                                .with(authentication(identityA))
                                .with(csrf())
                                .contentType("application/json")
                                .content(
                                        "{\"name\":\"Forged\",\"active\":true,\"businessId\":\""
                                                + businessB
                                                + "\"}"))
                .andExpect(status().isBadRequest());
        var foreignCategory = products.find(businessB, productB).orElseThrow().categoryId();
        mvc.perform(
                        post("/api/v1/products")
                                .with(authentication(identityA))
                                .with(csrf())
                                .contentType("application/json")
                                .content(
                                        "{\"categoryId\":\""
                                                + foreignCategory
                                                + "\",\"name\":\"Forged\",\"salePrice\":10,\"minimumStock\":0,\"active\":true}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void temporaryPasswordMustBeChangedAndSuspensionInvalidatesAccess() throws Exception {
        var adminId = UUID.randomUUID();
        var now = Instant.now();
        saveAccount(
                new Account(
                        adminId,
                        null,
                        adminId + "@admin.test",
                        "Admin",
                        passwords.encode("AdminPassword-42"),
                        "SUPER_ADMIN",
                        true,
                        false,
                        0,
                        null,
                        now,
                        now));
        SecurityContextHolder.getContext()
                .setAuthentication(identity(adminId, null, "SUPER_ADMIN"));
        var created =
                userService.create(
                        businessA,
                        UUID.randomUUID() + "@test.local",
                        "Temporary user",
                        "TemporaryPassword-42");
        assertThat(created.mustChangePassword()).isTrue();
        mvc.perform(
                        get("/api/v1/admin/users")
                                .param("businessId", businessA.toString())
                                .with(authentication(identity(adminId, null, "SUPER_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.items[?(@.id == '%s')].businessName", created.id())
                                .value(org.hamcrest.Matchers.hasItem("Test business")));
        var authenticated = authService.authenticate(created.email(), "TemporaryPassword-42");
        assertThat(authenticated.lastLoginAt()).isNotNull();

        var temporaryIdentity = identity(created.id(), businessA, "BUSINESS_USER");
        mvc.perform(get("/api/v1/products").with(authentication(temporaryIdentity)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mvc.perform(get("/api/v1/auth/me").with(authentication(temporaryIdentity)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.lastLoginAt").isNotEmpty());

        SecurityContextHolder.getContext().setAuthentication(temporaryIdentity);
        var changed = userService.changeOwnPassword("PermanentPassword-42");
        assertThat(changed.mustChangePassword()).isFalse();
        assertThat(changed.accessVersion()).isEqualTo(1);
        assertThat(authService.authenticate(created.email(), "PermanentPassword-42").id())
                .isEqualTo(created.id());

        SecurityContextHolder.getContext()
                .setAuthentication(identity(adminId, null, "SUPER_ADMIN"));
        var reset = userService.resetPassword(created.id(), "ResetPassword-42");
        assertThat(reset.mustChangePassword()).isTrue();
        assertThat(reset.accessVersion()).isEqualTo(2);
        var suspended = userService.suspend(created.id());
        assertThat(suspended.active()).isFalse();
        assertThatThrownBy(() -> authService.authenticate(created.email(), "ResetPassword-42"))
                .hasMessage("INVALID_CREDENTIALS");
    }

    @Test
    void billingLifecyclePaymentsAndTenantEndpointsAreEnforced() throws Exception {
        var adminId = UUID.randomUUID();
        var now = Instant.now();
        saveAccount(
                new Account(
                        adminId,
                        null,
                        adminId + "@admin.test",
                        "Admin",
                        passwords.encode("AdminPassword-42"),
                        "SUPER_ADMIN",
                        true,
                        false,
                        0,
                        null,
                        now,
                        now));
        var adminIdentity = identity(adminId, null, "SUPER_ADMIN");
        SecurityContextHolder.getContext().setAuthentication(adminIdentity);
        var plan =
                subscriptionPlanService.save(
                        null,
                        "Plan " + UUID.randomUUID(),
                        null,
                        new BigDecimal("120000"),
                        30,
                        7,
                        true);
        var createdBusinessResponse =
                mvc.perform(
                                post("/api/v1/admin/businesses")
                                        .with(authentication(adminIdentity))
                                        .with(csrf())
                                        .contentType("application/json")
                                        .content(
                                                "{\"name\":\"Billing onboarding\",\"taxId\":null,\"active\":true,\"planId\":\""
                                                        + plan.id()
                                                        + "\"}"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.subscriptionStatus").value("ACTIVE"))
                        .andExpect(jsonPath("$.planName").value(plan.name()))
                        .andReturn()
                        .getResponse();
        UUID newBusiness =
                UUID.fromString(
                        com.jayway.jsonpath.JsonPath.read(
                                createdBusinessResponse.getContentAsString(), "$.id"));
        var noTrial = subscriptions.findByBusiness(newBusiness).orElseThrow();
        assertThat(noTrial.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(noTrial.trialEndsAt()).isNull();

        SecurityContextHolder.getContext().setAuthentication(adminIdentity);
        var replacementPlan =
                subscriptionPlanService.save(
                        null,
                        "Replacement " + UUID.randomUUID(),
                        null,
                        new BigDecimal("180000"),
                        30,
                        7,
                        true);
        mvc.perform(
                        put("/api/v1/admin/businesses/{id}", newBusiness)
                                .with(authentication(adminIdentity))
                                .with(csrf())
                                .contentType("application/json")
                                .content(
                                        "{\"name\":\"Billing onboarding\",\"taxId\":null,\"active\":true,\"planId\":\""
                                                + replacementPlan.id()
                                                + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").value(replacementPlan.id().toString()))
                .andExpect(jsonPath("$.planName").value(replacementPlan.name()));
        assertThat(subscriptions.findByBusiness(newBusiness).orElseThrow().planId())
                .isEqualTo(replacementPlan.id());

        var unassignedBusiness = UUID.randomUUID();
        saveBusiness(new Business(unassignedBusiness, "Unassigned", null, true, now, now));
        mvc.perform(
                        put("/api/v1/admin/businesses/{id}", unassignedBusiness)
                                .with(authentication(adminIdentity))
                                .with(csrf())
                                .contentType("application/json")
                                .content(
                                        "{\"name\":\"Unassigned\",\"taxId\":null,\"active\":true,\"planId\":\""
                                                + plan.id()
                                                + "\",\"trialDays\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").value(plan.id().toString()))
                .andExpect(jsonPath("$.subscriptionStatus").value("TRIAL"));

        SecurityContextHolder.getContext().setAuthentication(adminIdentity);
        var trialBusiness = UUID.randomUUID();
        saveBusiness(new Business(trialBusiness, "Trial test", null, true, now, now));
        var trialUser = UUID.randomUUID();
        saveAccount(
                new Account(
                        trialUser,
                        trialBusiness,
                        trialUser + "@test.local",
                        "Trial user",
                        passwords.encode("LocalTestPassword-42"),
                        "BUSINESS_USER",
                        true,
                        false,
                        0,
                        null,
                        now,
                        now));
        var trial = subscriptionAdminService.assign(trialBusiness, plan.id(), 3);
        assertThat(trial.status()).isEqualTo(SubscriptionStatus.TRIAL);
        var extended = subscriptionAdminService.extendTrial(trial.id(), 2);
        assertThat(extended.trialEndsAt()).isAfter(trial.trialEndsAt());

        jdbc.update(
                "update subscriptions set status='ACTIVE', trial_ends_at=null, current_period_start=?, current_period_end=?, next_billing_date=?, grace_ends_at=null, suspended_at=null where business_id=?",
                Timestamp.from(now.minusSeconds(86_400)),
                Timestamp.from(now.minusSeconds(1)),
                Timestamp.from(now.minusSeconds(1)),
                trialBusiness);
        var grace = subscriptionLifecycleService.refresh(trialBusiness);
        assertThat(grace.status()).isEqualTo(SubscriptionStatus.GRACE_PERIOD);
        assertThat(subscriptionLifecycleService.hasCommercialAccess(trialBusiness)).isTrue();

        jdbc.update(
                "update subscriptions set grace_ends_at=? where business_id=?",
                Timestamp.from(now.minusSeconds(1)),
                trialBusiness);
        var suspended = subscriptionLifecycleService.refresh(trialBusiness);
        assertThat(suspended.status()).isEqualTo(SubscriptionStatus.SUSPENDED);
        subscriptionScheduler.advanceDueSubscriptions();
        subscriptionScheduler.advanceDueSubscriptions();
        assertThat(subscriptions.findByBusiness(trialBusiness).orElseThrow().status())
                .isEqualTo(SubscriptionStatus.SUSPENDED);

        var trialIdentity = identity(trialUser, trialBusiness, "BUSINESS_USER");
        mvc.perform(get("/api/v1/products").with(authentication(trialIdentity)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION_SUSPENDED"));
        mvc.perform(
                        get("/api/v1/subscription")
                                .param("businessId", businessA.toString())
                                .with(authentication(trialIdentity)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").value(trialBusiness.toString()))
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
        mvc.perform(get("/api/v1/subscription/payments").with(authentication(trialIdentity)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));

        SecurityContextHolder.getContext().setAuthentication(trialIdentity);
        var requestId = UUID.randomUUID();
        var pendingResponse =
                mvc.perform(
                                post("/api/v1/subscription/payments")
                                        .with(authentication(trialIdentity))
                                        .with(csrf())
                                        .contentType("application/json")
                                        .content(
                                                "{\"requestId\":\""
                                                        + requestId
                                                        + "\",\"paymentMethod\":\"MANUAL_TRANSFER\",\"externalReference\":\"BANK-42\"}"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.status").value("PENDING"))
                        .andExpect(jsonPath("$.businessId").value(trialBusiness.toString()))
                        .andReturn()
                        .getResponse();
        UUID pendingId =
                UUID.fromString(
                        com.jayway.jsonpath.JsonPath.read(
                                pendingResponse.getContentAsString(), "$.id"));
        SecurityContextHolder.getContext().setAuthentication(trialIdentity);
        var pending =
                subscriptionPaymentService
                        .listForCurrentBusiness(PaymentStatus.PENDING, 0, 20)
                        .items()
                        .stream()
                        .filter(payment -> payment.id().equals(pendingId))
                        .findFirst()
                        .orElseThrow();
        assertThat(pending.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(
                        subscriptionPaymentService
                                .requestForCurrentBusiness(requestId, "MANUAL_TRANSFER", "BANK-42")
                                .id())
                .isEqualTo(pending.id());
        assertThatThrownBy(
                        () ->
                                subscriptionPaymentService.requestForCurrentBusiness(
                                        requestId, "MANUAL_CASH", "BANK-42"))
                .hasMessage("IDEMPOTENCY_CONFLICT");

        SecurityContextHolder.getContext().setAuthentication(adminIdentity);
        mvc.perform(
                        post("/api/v1/admin/subscription-payments/" + pending.id() + "/confirm")
                                .with(authentication(adminIdentity))
                                .with(csrf())
                                .contentType("application/json")
                                .content("{\"externalReference\":\"BANK-42-CONFIRMED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
        SecurityContextHolder.getContext().setAuthentication(adminIdentity);
        assertThat(subscriptionPaymentService.confirm(pending.id(), null).id())
                .isEqualTo(pending.id());
        assertThat(subscriptions.findByBusiness(trialBusiness).orElseThrow().status())
                .isEqualTo(SubscriptionStatus.ACTIVE);
    }

    private void saveAccount(Account account) {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> accounts.save(account));
    }

    private void saveBusiness(Business business) {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> businesses.save(business));
    }
}
