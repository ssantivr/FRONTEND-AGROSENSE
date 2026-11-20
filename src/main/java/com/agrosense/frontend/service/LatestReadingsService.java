package com.agrosense.frontend.service;

import com.agrosense.frontend.entity.enums.ReadingSource;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * The polling fallback of the live badge (events/live.ts) while the event service is not deployed: only the readings of
 * the user's own sensors recorded after {@code since}, oldest first. The backend has no equivalent endpoint, so this
 * reads the shared database like the other JPA reads of the frontend, filtered by owner.
 */
@Service
public class LatestReadingsService {

    /** A page that slept for a day does not pull the whole history: at most this far back, and this many rows. */
    static final Duration LONGEST_LOOKBACK = Duration.ofHours(1);
    static final Duration DEFAULT_LOOKBACK = Duration.ofMinutes(2);
    static final int MAX_ROWS = 200;

    private final EntityManager entityManager;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public LatestReadingsService(EntityManager entityManager) {
        this(entityManager, Clock.systemDefaultZone());
    }

    LatestReadingsService(EntityManager entityManager, Clock clock) {
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<LatestReading> since(String email, String since) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime from = parse(since, now);
        LocalDateTime earliest = now.minus(LONGEST_LOOKBACK);
        if (from.isBefore(earliest)) {
            from = earliest;
        }
        return entityManager.createQuery("select r.sensor.sensorCode, r.value, r.unit, r.recordedAt, r.source "
                        + "from SensorReading r where r.sensor.crop.estate.user.email = :email "
                        + "and r.recordedAt > :since order by r.recordedAt", Object[].class)
                .setParameter("email", email)
                .setParameter("since", from)
                .setMaxResults(MAX_ROWS)
                .getResultList().stream()
                .map(row -> new LatestReading((String) row[0], ((BigDecimal) row[1]).doubleValue(), (String) row[2],
                        (LocalDateTime) row[3], row[4] == null ? ReadingSource.DEVICE : (ReadingSource) row[4]))
                .toList();
    }

    /** ISO-8601 with an offset (the browser's toISOString) is taken to this server's zone; without one, as is. */
    LocalDateTime parse(String since, LocalDateTime now) {
        if (since == null || since.isBlank()) {
            return now.minus(DEFAULT_LOOKBACK);
        }
        String text = since.strip();
        try {
            return OffsetDateTime.parse(text).atZoneSameInstant(clock.getZone()).toLocalDateTime();
        } catch (DateTimeParseException withoutOffset) {
            try {
                return LocalDateTime.parse(text);
            } catch (DateTimeParseException notADate) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "since must be ISO-8601");
            }
        }
    }

    public record LatestReading(String sensorCode, double value, String unit, LocalDateTime recordedAt,
            ReadingSource source) {
    }
}
