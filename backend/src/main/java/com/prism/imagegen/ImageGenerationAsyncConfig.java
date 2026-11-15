package com.prism.imagegen;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class ImageGenerationAsyncConfig {

    @Bean(name = "imageGenerationExecutor")
    public Executor imageGenerationExecutor() {
        AtomicInteger sequence = new AtomicInteger();
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1,
                3,
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(64),
                runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("image-generation-" + sequence.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }
        );
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }
}
