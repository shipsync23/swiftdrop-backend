package com.swiftdrop.admin;

import com.swiftdrop.common.exception.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin use case "View reports": deliveries per day and success/failure rate (report 2.7).
 * Plain SQL with GROUP BY is the simplest correct tool for aggregate reports.
 * Days are counted in Sri Lanka time.
 */
@Service
public class ReportService {

    private static final String TZ = "Asia/Colombo";

    private final JdbcTemplate jdbc;

    public ReportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public DeliveryReport deliveries(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw ApiException.badRequest("'from' must be on or before 'to'");
        }
        if (from.plusDays(366).isBefore(to)) {
            throw ApiException.badRequest("The date range can be at most one year");
        }

        Map<LocalDate, DayRow> days = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            days.put(d, new DayRow(d, 0, 0, 0));
        }

        // Parcels created per day
        jdbc.query("""
                select (created_at at time zone ?)::date as day, count(*) as n
                from parcels
                where (created_at at time zone ?)::date between ? and ?
                group by day
                """, rs -> {
            LocalDate day = rs.getDate("day").toLocalDate();
            long n = rs.getLong("n");
            days.computeIfPresent(day, (k, v) -> new DayRow(k, n, v.delivered(), v.failed()));
        }, TZ, TZ, Date.valueOf(from), Date.valueOf(to));

        // Deliveries completed (DELIVERED / FAILED) per day, from the status history
        jdbc.query("""
                select (changed_at at time zone ?)::date as day,
                       count(*) filter (where new_status = 'DELIVERED') as delivered,
                       count(*) filter (where new_status = 'FAILED') as failed
                from status_history
                where new_status in ('DELIVERED', 'FAILED')
                  and (changed_at at time zone ?)::date between ? and ?
                group by day
                """, rs -> {
            LocalDate day = rs.getDate("day").toLocalDate();
            long delivered = rs.getLong("delivered");
            long failed = rs.getLong("failed");
            days.computeIfPresent(day, (k, v) -> new DayRow(k, v.created(), delivered, failed));
        }, TZ, TZ, Date.valueOf(from), Date.valueOf(to));

        List<DayRow> rows = List.copyOf(days.values());
        long created = rows.stream().mapToLong(DayRow::created).sum();
        long delivered = rows.stream().mapToLong(DayRow::delivered).sum();
        long failed = rows.stream().mapToLong(DayRow::failed).sum();
        long completed = delivered + failed;

        return new DeliveryReport(from, to, created, delivered, failed,
                percent(delivered, completed), percent(failed, completed), rows);
    }

    private static BigDecimal percent(long part, long whole) {
        if (whole == 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(part * 100.0 / whole).setScale(1, RoundingMode.HALF_UP);
    }

    public record DayRow(LocalDate date, long created, long delivered, long failed) {
    }

    public record DeliveryReport(LocalDate from, LocalDate to, long totalCreated, long totalDelivered,
                                 long totalFailed, BigDecimal successRatePercent, BigDecimal failureRatePercent,
                                 List<DayRow> days) {
    }
}
