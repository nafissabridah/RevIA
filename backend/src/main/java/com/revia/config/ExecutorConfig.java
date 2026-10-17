package com.revia.config;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Pool borné pour les appels au LLM : au-delà, la demande est refusée proprement (service sollicité). */
@Configuration
public class ExecutorConfig {

    @Bean(name = "analyseExecutor", destroyMethod = "shutdownNow")
    public ExecutorService analyseExecutor() {
        AtomicInteger n = new AtomicInteger();
        return new ThreadPoolExecutor(4, 16, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(64), r -> {
            Thread t = new Thread(r, "revia-llm-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        }, new ThreadPoolExecutor.AbortPolicy());
    }
}
