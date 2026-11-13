package com.prism.creation;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class CreationAsyncConfig {

    @Bean(name = "creationExecutor")
    public Executor creationExecutor() {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 2, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(32));
        pool.setThreadFactory(runnable -> {
            Thread thread = new Thread(runnable, "website-generation");
            thread.setDaemon(true);
            return thread;
        });
        return pool;
    }
}
