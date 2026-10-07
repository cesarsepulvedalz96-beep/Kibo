package com.stockflow.shared.infrastructure;

import com.stockflow.billing.domain.*;
import com.stockflow.businesses.domain.*;
import com.stockflow.categories.domain.*;
import com.stockflow.inventory.domain.*;
import com.stockflow.notifications.domain.*;
import com.stockflow.products.domain.*;
import com.stockflow.settings.domain.*;
import com.stockflow.users.application.UserService;
import com.stockflow.users.domain.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
public class DevelopmentSeed implements CommandLineRunner {
    private final BusinessRepository businesses;
    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final InventoryMovementRepository movements;
    private final NotificationRepository notifications;
    private final SettingsRepository settings;
    private final PasswordEncoder passwords;
    private final String email;
    private final String password;
    private final Clock clock;
    private final SubscriptionPlanRepository subscriptionPlans;
    private final SubscriptionRepository subscriptions;

    public DevelopmentSeed(
            BusinessRepository businesses,
            AccountRepository accounts,
            CategoryRepository categories,
            ProductRepository products,
            InventoryMovementRepository movements,
            NotificationRepository notifications,
            SettingsRepository settings,
            PasswordEncoder passwords,
            Clock clock,
            SubscriptionPlanRepository subscriptionPlans,
            SubscriptionRepository subscriptions,
            @Value("${app.demo.email}") String email,
            @Value("${app.demo.password}") String password) {
        this.businesses = businesses;
        this.accounts = accounts;
        this.categories = categories;
        this.products = products;
        this.movements = movements;
        this.notifications = notifications;
        this.settings = settings;
        this.passwords = passwords;
        this.clock = clock;
        this.subscriptionPlans = subscriptionPlans;
        this.subscriptions = subscriptions;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (password.isBlank() || accounts.findByEmail(email).isPresent()) return;
        UserService.validatePassword(password);
        var now = clock.instant();
        var businessId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        businesses.save(new Business(businessId, "Minimarket San José", null, true, now, now));
        var plan = subscriptionPlans.findDefaultActive().orElseThrow();
        var periodEnd = now.plusSeconds((long) plan.billingPeriodDays() * 86_400);
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
        accounts.save(
                new Account(
                        userId,
                        businessId,
                        email,
                        "Equipo San José",
                        passwords.encode(password),
                        "BUSINESS_USER",
                        true,
                        false,
                        0,
                        null,
                        now,
                        now));
        String[] names = {"Bebidas", "Abarrotes", "Lácteos", "Panadería"};
        UUID[] categoryIds = new UUID[names.length];
        for (int index = 0; index < names.length; index++) {
            categoryIds[index] = UUID.randomUUID();
            categories.save(
                    new Category(categoryIds[index], businessId, names[index], true, now, now));
        }
        String[][] catalog = {
            {"Agua mineral 1,5 L", "0", "850", "48", "10"},
            {"Bebida cola 1,5 L", "0", "1690", "24", "8"},
            {"Jugo de naranja 1 L", "0", "1490", "7", "10"},
            {"Arroz grado 1 · 1 kg", "1", "1290", "32", "10"},
            {"Aceite vegetal 1 L", "1", "2190", "16", "6"},
            {"Azúcar blanca 1 kg", "1", "1190", "4", "8"},
            {"Fideos espagueti 400 g", "1", "890", "36", "12"},
            {"Café instantáneo 170 g", "1", "4290", "12", "4"},
            {"Leche entera 1 L", "2", "1090", "18", "12"},
            {"Yogur natural 125 g", "2", "390", "6", "10"},
            {"Queso laminado 250 g", "2", "2890", "14", "5"},
            {"Pan de molde integral", "3", "1990", "9", "5"}
        };
        for (int index = 0; index < catalog.length; index++) {
            var row = catalog[index];
            var productId = UUID.randomUUID();
            int stock = Integer.parseInt(row[3]);
            var product =
                    products.save(
                            new Product(
                                    productId,
                                    businessId,
                                    categoryIds[Integer.parseInt(row[1])],
                                    row[0],
                                    "Producto de demostración",
                                    "DEMO" + String.format("%08d", index + 1),
                                    new BigDecimal(row[2]),
                                    null,
                                    stock,
                                    Integer.parseInt(row[4]),
                                    null,
                                    true,
                                    now,
                                    now));
            movements.save(
                    new InventoryMovement(
                            UUID.randomUUID(),
                            businessId,
                            productId,
                            "PURCHASE_ENTRY",
                            stock,
                            0,
                            stock,
                            null,
                            userId,
                            now));
            if (product.isLowStock()) {
                notifications.save(
                        new Notification(
                                UUID.randomUUID(),
                                businessId,
                                productId,
                                product.name(),
                                product.stock(),
                                false,
                                now));
            }
        }
    }
}
