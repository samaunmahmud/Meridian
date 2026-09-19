package com.meridian.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

// A Clock bean so time-dependent logic (daily budgets, poll pacing) can be
// tested with a fake clock instead of real waiting.
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
