package com.stockflow.billing.infrastructure;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class BillingConfiguration {
    @Bean
    Clock applicationClock() {
        return Clock.systemUTC();
    }
}
