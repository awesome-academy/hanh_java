package com.psms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Cấu hình async execution cho email service.
 *
 * <p><b>Tại sao không dùng SimpleAsyncTaskExecutor?</b>
 * {@code SimpleAsyncTaskExecutor} tạo thread mới cho mỗi task — không giới hạn,
 * có thể gây OOM khi nhiều email được trigger cùng lúc (spike traffic).
 * {@code ThreadPoolTaskExecutor} với corePoolSize + queueCapacity giới hạn tài nguyên
 * và tái sử dụng thread (cost tạo thread là O(1) thay vì tạo mới liên tục).
 *
 * <p><b>Sizing rationale:</b>
 * <ul>
 *   <li>{@code corePoolSize=2}: 2 thread luôn sẵn sàng – đủ cho traffic bình thường</li>
 *   <li>{@code maxPoolSize=5}: tối đa 5 thread khi queue đầy – tránh OOM</li>
 *   <li>{@code queueCapacity=100}: buffer 100 task trước khi tăng thread – tránh spike ngắn tạo thread thừa</li>
 *   <li>{@code keepAliveSeconds=60}: thread nhàn rỗi > 60s sẽ bị destroy (chỉ giữ corePoolSize)</li>
 * </ul>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * ThreadPoolTaskExecutor dành riêng cho email — tách khỏi default task executor
     * để email không tranh giành thread với các async task khác (future use).
     *
     * <p>Dùng bằng {@code @Async("mailTaskExecutor")} trên method.
     */
    @Bean(name = "mailTaskExecutor")
    public Executor mailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("async-mail-");
        // CALLER_RUNS: khi queue đầy và max thread đạt ngưỡng → chạy trên calling thread
        // thay vì throw RejectedExecutionException — đảm bảo email không bao giờ bị silent drop
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        // Graceful shutdown: chờ task đang chạy (gửi email dở) hoàn thành trước khi JVM exit
        // Quan trọng khi deploy/restart — tránh kill email đang gửi giữa chừng
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);   // timeout 30s — không chờ mãi mãi
        executor.initialize();
        return executor;
    }
}

