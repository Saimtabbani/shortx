package com.shortx.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Spring Boot's default {@code @Async} executor (SimpleAsyncTaskExecutor)
 * spins up a brand-new thread per task with no pooling and no upper
 * bound — fine for a demo, a real liability under load (click recording
 * fires on every redirect). This replaces it with a properly bounded
 * pool.
 *
 * <p>Also supplies a custom {@link AsyncUncaughtExceptionHandler}: {@code
 * @Async void} methods (like ClickEventRecorder.record) have no return
 * value for a caller to inspect, so an exception thrown inside one is
 * silently swallowed by default — Spring's own default handler just logs
 * at a level easy to miss. Without this, a bug in click recording could
 * fail silently forever.
 */
@Configuration
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    @Override
    @Bean(name = "taskExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("async-analytics-");
        // CallerRunsPolicy: if the queue is ever full, the calling thread
        // (the redirect request itself) runs the task synchronously
        // instead of the task being silently dropped. Slightly slows that
        // one request under extreme load rather than losing a click
        // record outright - an intentional backpressure choice.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new LoggingAsyncUncaughtExceptionHandler();
    }

    @Slf4j
    static class LoggingAsyncUncaughtExceptionHandler implements AsyncUncaughtExceptionHandler {
        @Override
        public void handleUncaughtException(Throwable ex, Method method, Object... params) {
            log.error("Uncaught exception in @Async method '{}': {}", method.getName(), ex.getMessage(), ex);
        }
    }
}
