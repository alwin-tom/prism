package com.prism.shared.jobs;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AdvisoryLock {

    private final JdbcTemplate jdbc;

    public AdvisoryLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean tryLock(long key) {
        Boolean locked = jdbc.queryForObject("select pg_try_advisory_lock(?)", Boolean.class, key);
        return Boolean.TRUE.equals(locked);
    }

    public void unlock(long key) {
        jdbc.queryForObject("select pg_advisory_unlock(?)", Boolean.class, key);
    }
}
