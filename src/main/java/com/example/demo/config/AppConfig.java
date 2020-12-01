package com.example.demo.config;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.statsd.StatsdConfig;
import io.micrometer.statsd.StatsdMeterRegistry;
import org.apache.commons.configuration.SystemConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * Our application is creating our own instance of the StatsDMeterRegistry and if this code is active and if
 * we are in debug then we re-create the problem.
 */
@Configuration
@ConditionalOnProperty("demo.custom.registry")
public class AppConfig {

    Logger logger = LoggerFactory.getLogger(AppConfig.class);

    private MeterRegistry registry;

    @PostConstruct
    private void init() {
        logger.info("BUGLOG AppConfig complete");
    }

    @Bean
    public MeterRegistry meterRegistry() {
        if (registry == null || registry.isClosed()) {
            StatsdConfig conf = new StatsdConfig() {
                @Override
                public String get(String key) {
                    return new SystemConfiguration().getString(key);
                }

                @Override
                public String prefix() {
                    return "com.demo.metrics";
                }
            };
            registry = new StatsdMeterRegistry(conf, Clock.SYSTEM);
        }
        return registry;
    }
}
