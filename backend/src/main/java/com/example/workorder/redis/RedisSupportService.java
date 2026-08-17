package com.example.workorder.redis;

import com.example.workorder.workorder.WorkOrderStatisticsQuery;
import com.example.workorder.workorder.WorkOrderStatisticsResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisSupportService {

    private static final Duration STATISTICS_TTL = Duration.ofMinutes(5);
    private static final Duration LOGIN_LOCK_TTL = Duration.ofMinutes(15);
    private static final String STATISTICS_KEY_PREFIX = "work-order:statistics:v1:";
    private static final String LOGIN_FAIL_KEY_PREFIX = "work-order:login:fail:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public RedisSupportService(ObjectProvider<StringRedisTemplate> redisTemplateProvider, ObjectMapper objectMapper) {
        this(redisTemplateProvider.getIfAvailable(), objectMapper);
    }

    public RedisSupportService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public WorkOrderStatisticsResponse getStatistics(WorkOrderStatisticsQuery query) {
        if (redisTemplate == null) {
            return null;
        }
        try {
            String json = redisTemplate.opsForValue().get(statisticsKey(query));
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, WorkOrderStatisticsResponse.class);
        } catch (RuntimeException | java.io.IOException ex) {
            return null;
        }
    }

    public void putStatistics(WorkOrderStatisticsQuery query, WorkOrderStatisticsResponse response) {
        if (redisTemplate == null || response == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(statisticsKey(query), objectMapper.writeValueAsString(response), STATISTICS_TTL);
        } catch (RuntimeException | java.io.IOException ignored) {
            // Redis is an optional accelerator; DB remains the source of truth.
        }
    }

    public void evictStatistics() {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.delete(redisTemplate.keys(STATISTICS_KEY_PREFIX + "*"));
        } catch (RuntimeException ignored) {
            // Cache invalidation failure must not block work-order state changes.
        }
    }

    public int recordLoginFailure(String username) {
        if (redisTemplate == null) {
            return -1;
        }
        try {
            String key = loginFailKey(username);
            Long failures = redisTemplate.opsForValue().increment(key);
            if (failures != null) {
                redisTemplate.expire(key, LOGIN_LOCK_TTL);
            }
            return failures == null ? -1 : failures.intValue();
        } catch (RuntimeException ex) {
            return -1;
        }
    }

    public boolean isLoginLocked(String username, int maxFailedAttempts) {
        if (redisTemplate == null) {
            return false;
        }
        try {
            String value = redisTemplate.opsForValue().get(loginFailKey(username));
            if (value == null) {
                return false;
            }
            return Integer.parseInt(value) >= maxFailedAttempts;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public void clearLoginFailures(String username) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.delete(loginFailKey(username));
        } catch (RuntimeException ignored) {
            // Successful login should not fail because Redis cleanup failed.
        }
    }

    private String statisticsKey(WorkOrderStatisticsQuery query) {
        String createdFrom = query == null || query.createdFrom() == null ? "" : query.createdFrom().trim();
        String createdTo = query == null || query.createdTo() == null ? "" : query.createdTo().trim();
        return STATISTICS_KEY_PREFIX + "createdFrom=" + createdFrom + "|createdTo=" + createdTo;
    }

    private String loginFailKey(String username) {
        return LOGIN_FAIL_KEY_PREFIX + username.trim().toLowerCase(Locale.ROOT);
    }
}
