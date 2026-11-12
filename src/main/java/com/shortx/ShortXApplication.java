package com.shortx;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application bootstrap class.
 *
 * Responsibilities of this class ONLY:
 *  - Start the Spring application context.
 *  - Declare cross-cutting enablement annotations that must live at the
 *    application root (caching, async execution, scheduling).
 *
 * It intentionally contains no business logic, no bean definitions beyond
 * what Spring Boot auto-configures, and no configuration values. Splitting
 * "start the app" from "configure the app" keeps this class stable — it
 * should almost never need to change as the codebase grows.
 */
@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableScheduling
public class ShortXApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShortXApplication.class, args);
    }
}
