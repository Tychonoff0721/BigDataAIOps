package com.aiops.bigdata.service.context.impl;

import com.aiops.bigdata.entity.context.RealtimeMetrics;
import com.aiops.bigdata.service.context.RealtimeMetricsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 实时指标服务实现类（Redis存储）
 */
@Slf4j
@Service
public class RealtimeMetricsServiceImpl implements RealtimeMetricsService {
    
    private static final String KEY_PREFIX = "aiops:realtime:";
    private static final int DEFAULT_RETENTION_MINUTES = 60;
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    @Autowired
    public RealtimeMetricsServiceImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    @Override
    public void save(RealtimeMetrics metrics) {
        String key = KEY_PREFIX + metrics.getUniqueId();
        
        try {
            // 存储指标，设置过期时间
            redisTemplate.opsForValue().set(key, metrics, DEFAULT_RETENTION_MINUTES, TimeUnit.MINUTES);
            log.debug("保存实时指标到Redis: key={}", key);
        } catch (Exception e) {
            log.error("保存实时指标失败: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public void saveBatch(List<RealtimeMetrics> metricsList) {
        for (RealtimeMetrics metrics : metricsList) {
            save(metrics);
        }
        log.info("批量保存实时指标到Redis: count={}", metricsList.size());
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public Optional<RealtimeMetrics> get(String cluster, String service, String component, String instance) {
        String key = KEY_PREFIX + buildUniqueId(cluster, service, component, instance);
        
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                // 处理LinkedHashMap转换
                if (value instanceof Map) {
                    RealtimeMetrics metrics = objectMapper.convertValue(value, RealtimeMetrics.class);
                    return Optional.of(metrics);
                } else if (value instanceof RealtimeMetrics) {
                    return Optional.of((RealtimeMetrics) value);
                }
            }
        } catch (Exception e) {
            log.error("获取实时指标失败: {}", e.getMessage(), e);
        }
        
        return Optional.empty();
    }
    
    @Override
    public List<RealtimeMetrics> getByCluster(String cluster) {
        String pattern = KEY_PREFIX + (cluster != null ? cluster : "default") + ":*";
        return getByPattern(pattern);
    }
    
    @Override
    public List<RealtimeMetrics> getByService(String cluster, String service) {
        String pattern = KEY_PREFIX + (cluster != null ? cluster : "default") + ":" + service + ":*";
        return getByPattern(pattern);
    }
    
    @Override
    public List<RealtimeMetrics> getByComponent(String cluster, String service, String component) {
        String pattern = KEY_PREFIX + (cluster != null ? cluster : "default") + ":" + service + ":" + component + "*";
        return getByPattern(pattern);
    }
    
    @Override
    public List<RealtimeMetrics> getHistory(String cluster, String service, String component,
            String instance, int minutes) {
        // Redis中只存储最新数据，历史数据需要从时序数据库获取
        // 这里返回最近的数据
        return getByComponent(cluster, service, component);
    }
    
    @Override
    public void delete(String cluster, String service, String component, String instance) {
        String key = KEY_PREFIX + buildUniqueId(cluster, service, component, instance);
        redisTemplate.delete(key);
        log.info("删除实时指标: key={}", key);
    }
    
    @Override
    public int cleanupExpired(int retentionMinutes) {
        // Redis自动过期，无需手动清理
        log.info("Redis自动过期清理，retentionMinutes={}", retentionMinutes);
        return 0;
    }
    
    @SuppressWarnings("unchecked")
    private List<RealtimeMetrics> getByPattern(String pattern) {
        List<RealtimeMetrics> result = new ArrayList<>();
        
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys == null || keys.isEmpty()) {
                return result;
            }
            
            for (String key : keys) {
                Object value = redisTemplate.opsForValue().get(key);
                if (value != null) {
                    if (value instanceof Map) {
                        RealtimeMetrics metrics = objectMapper.convertValue(value, RealtimeMetrics.class);
                        result.add(metrics);
                    } else if (value instanceof RealtimeMetrics) {
                        result.add((RealtimeMetrics) value);
                    }
                }
            }
            
            // 按时间排序
            result.sort(Comparator.comparing(
                RealtimeMetrics::getTimestamp, 
                Comparator.nullsLast(Comparator.reverseOrder())
            ));
            
        } catch (Exception e) {
            log.error("查询实时指标失败: pattern={}, error={}", pattern, e.getMessage(), e);
        }
        
        return result;
    }
    
    private String buildUniqueId(String cluster, String service, String component, String instance) {
        StringBuilder sb = new StringBuilder();
        sb.append(cluster != null ? cluster : "default");
        sb.append(":").append(service);
        sb.append(":").append(component);
        if (instance != null && !instance.isEmpty()) {
            sb.append(":").append(instance);
        }
        return sb.toString();
    }
}
