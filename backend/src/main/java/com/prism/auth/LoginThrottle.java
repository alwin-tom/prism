package com.prism.auth;

import com.prism.shared.exception.ApiException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginThrottle {

    private static final int LIMIT = 10;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final ConcurrentHashMap<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    public void check(String key) {
        prune(key, Instant.now());
        Deque<Instant> deque = attempts.get(key);
        if (deque != null && deque.size() >= LIMIT) {
            throw ApiException.tooMany("Too many sign-in attempts. Wait a few minutes and try again.");
        }
    }

    public void fail(String key) {
        attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>()).addLast(Instant.now());
        prune(key, Instant.now());
    }

    public void clear(String key) {
        attempts.remove(key);
    }

    private void prune(String key, Instant now) {
        Deque<Instant> deque = attempts.get(key);
        if (deque == null) {
            return;
        }
        Instant cutoff = now.minus(WINDOW);
        while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
            deque.removeFirst();
        }
    }
}
