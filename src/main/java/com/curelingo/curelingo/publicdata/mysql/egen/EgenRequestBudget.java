package com.curelingo.curelingo.publicdata.mysql.egen;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class EgenRequestBudget {
    private final JdbcTemplate jdbc;
    private final int dailyLimit;

    public EgenRequestBudget(JdbcTemplate jdbc, @Value("${curelingo.public-data.sync.daily-request-limit:1000}") int dailyLimit) {
        if (dailyLimit < 1) throw new IllegalArgumentException("Invalid daily request limit");
        this.jdbc = jdbc;
        this.dailyLimit = dailyLimit;
    }

    public void reserve() {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Seoul"));
        jdbc.update("INSERT IGNORE INTO egen_daily_budget (budget_date, requests) VALUES (?, 0)", date);
        int updated = jdbc.update("UPDATE egen_daily_budget SET requests = requests + 1 "
                + "WHERE budget_date = ? AND requests < ?", date, dailyLimit);
        if (updated != 1) throw new IllegalStateException("E-Gen daily request budget exhausted");
    }
}
