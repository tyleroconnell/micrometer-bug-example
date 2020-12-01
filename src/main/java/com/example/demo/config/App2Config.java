package com.example.demo.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * Created this class to see if the default springboot wiring was creating a statds registry.  I simply injected it and
 * printed out the class name.  It does create one and the application works regardless of debug level.
 */
@Configuration
public class App2Config {
    Logger logger = LoggerFactory.getLogger(App2Config.class);

    @Autowired
    List<MeterRegistry> registry;

    @PostConstruct
    private void init() {
        registry.forEach(r -> {
            logger.info("BUGLOG registry {} {}", this.registry.size(), r);
        });
    }

}
