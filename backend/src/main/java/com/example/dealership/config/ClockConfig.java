package com.example.dealership.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.validation.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// "Today" comes from one Clock bean, also for Bean Validation (@PastOrPresent), so it can be fixed (INTENT Z12).
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public ValidationConfigurationCustomizer clockProviderCustomizer(Clock clock) {
        return configuration -> configuration.clockProvider(() -> clock);
    }
}
