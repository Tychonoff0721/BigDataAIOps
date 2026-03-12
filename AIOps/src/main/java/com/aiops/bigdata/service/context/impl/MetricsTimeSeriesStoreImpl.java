package com.aiops.bigdata.service.context.impl;

import com.aiops.bigdata.entity.metrics.ComponentMetrics;
import com.aiops.bigdata.service.context.MetricsTimeSeriesStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 指标时序数据存储服务实现类
 * 使用Redis List存储时序数据，支持LLM通过Tool查看
 */
@Slf4j
@Service
public class MetricsTimeSeriesStoreImpl implements MetricsTimeSeriesStore {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    @Autowired
    public MetricsTimeSeriesStoreImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    @Override
    public String saveTimeSeries(List<ComponentMetrics> metricsList, int retentionHours) {
        if (metricsList == null || metricsList.isEmpty()) {
            log.warn("时序数据为空，无法保存");
            return null;
        }
        
        // 按时间排序
        List<ComponentMetrics> sorted = metricsList.stream()
            .filter(m -> m.getTimestamp() != null)
            .sorted(Comparator.comparingLong(ComponentMetrics::getTimestamp))
            .collect(Collectors.toList());
        
        if (sorted.isEmpty()) {
            log.warn("没有有效的时间戳数据");
            return null;
        }
        
        ComponentMetrics first = sorted.get(0);
        String storageId = getStorageId(first.getCluster(), first.getService(), 
            first.getComponent(), first.getInstance());
        String key = KEY_PREFIX + storageId;
        
        try {
            // 删除旧数据
            redisTemplate.delete(key);
            
            // 批量添加时序数据（使用Redis List）
            for (ComponentMetrics metrics : sorted) {
                redisTemplate.opsForList().rightPush(key, metrics);
            }
            
            // 设置过期时间
            redisTemplate.expire(key, retentionHours, TimeUnit.HOURS);
            
            log.info("保存时序数据到Redis: key={}, 数据点数={}, 保留{}小时", 
                key, sorted.size(), retentionHours);
            
            return storageId;
            
        } catch (Exception e) {
            log.error("保存时序数据失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    @Override
    public void appendMetrics(String storageId, ComponentMetrics metrics) {
        if (storageId == null || metrics == null) {
            return;
        }
        
        String key = KEY_PREFIX + storageId;
        
        try {
            redisTemplate.opsForList().rightPush(key, metrics);
            log.debug("追加指标数据: key={}", key);
        } catch (Exception e) {
            log.error("追加指标数据失败: {}", e.getMessage(), e);
        }
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public Optional<List<ComponentMetrics>> getTimeSeries(String storageId) {
        if (storageId == null) {
            return Optional.empty();
        }
        
        String key = KEY_PREFIX + storageId;
        
        try {
            List<Object> values = redisTemplate.opsForList().range(key, 0, -1);
            
            if (values == null || values.isEmpty()) {
                return Optional.empty();
            }
            
            List<ComponentMetrics> result = new ArrayList<>();
            for (Object value : values) {
                if (value instanceof Map) {
                    ComponentMetrics metrics = objectMapper.convertValue(value, ComponentMetrics.class);
                    result.add(metrics);
                } else if (value instanceof ComponentMetrics) {
                    result.add((ComponentMetrics) value);
                }
            }
            
            return Optional.of(result);
            
        } catch (Exception e) {
            log.error("获取时序数据失败: storageId={}, error={}", storageId, e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public List<ComponentMetrics> getRecentMetrics(String storageId, int minutes) {
        if (storageId == null) {
            return Collections.emptyList();
        }
        
        String key = KEY_PREFIX + storageId;
        long cutoffTime = System.currentTimeMillis() / 1000 - minutes * 60L;
        
        try {
            List<Object> values = redisTemplate.opsForList().range(key, 0, -1);
            
            if (values == null || values.isEmpty()) {
                return Collections.emptyList();
            }
            
            List<ComponentMetrics> result = new ArrayList<>();
            for (Object value : values) {
                ComponentMetrics metrics = null;
                if (value instanceof Map) {
                    metrics = objectMapper.convertValue(value, ComponentMetrics.class);
                } else if (value instanceof ComponentMetrics) {
                    metrics = (ComponentMetrics) value;
                }
                
                if (metrics != null && metrics.getTimestamp() != null 
                    && metrics.getTimestamp() >= cutoffTime) {
                    result.add(metrics);
                }
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("获取最近指标失败: storageId={}, error={}", storageId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    @Override
    public String getStorageId(String cluster, String service, String component, String instance) {
        StringBuilder sb = new StringBuilder();
        sb.append(cluster != null ? cluster : "default");
        sb.append(":").append(service);
        sb.append(":").append(component);
        if (instance != null && !instance.isEmpty()) {
            sb.append(":").append(instance);
        }
        return sb.toString();
    }
    
    @Override
    public String getTimeSeriesSummary(String storageId) {
        Optional<List<ComponentMetrics>> optMetrics = getTimeSeries(storageId);
        
        if (optMetrics.isEmpty() || optMetrics.get().isEmpty()) {
            return "时序数据不存在或为空";
        }
        
        List<ComponentMetrics> metricsList = optMetrics.get();
        ComponentMetrics first = metricsList.get(0);
        ComponentMetrics latest = metricsList.get(metricsList.size() - 1);
        
        StringBuilder sb = new StringBuilder();
        sb.append("=== 时序数据特征摘要 ===\n\n");
        
        // 1. 组件信息（精简）
        sb.append(String.format("组件: %s/%s", first.getService(), first.getComponent()));
        if (first.getInstance() != null) {
            sb.append(":").append(first.getInstance());
        }
        sb.append(String.format(" | 数据点: %d | ", metricsList.size()));
        
        // 时间范围
        if (first.getTimestamp() != null && latest.getTimestamp() != null) {
            long duration = latest.getTimestamp() - first.getTimestamp();
            sb.append(String.format("跨度: %.1f分钟", duration / 60.0));
        }
        sb.append("\n\n");
        
        // 2. 一次性计算所有指标的统计特征
        Map<String, List<Double>> allMetricValues = new LinkedHashMap<>();
        
        // 收集所有指标值
        for (ComponentMetrics m : metricsList) {
            // 直接字段
            if (m.getCpuUsage() != null) {
                allMetricValues.computeIfAbsent("cpu_usage", k -> new ArrayList<>()).add(m.getCpuUsage());
            }
            if (m.getMemoryUsage() != null) {
                allMetricValues.computeIfAbsent("memory_usage", k -> new ArrayList<>()).add(m.getMemoryUsage());
            }
            if (m.getGcTime() != null) {
                allMetricValues.computeIfAbsent("gc_time", k -> new ArrayList<>()).add(m.getGcTime().doubleValue());
            }
            if (m.getConnectionCount() != null) {
                allMetricValues.computeIfAbsent("connection_count", k -> new ArrayList<>()).add(m.getConnectionCount().doubleValue());
            }
            // 动态指标
            if (m.getMetrics() != null) {
                for (Map.Entry<String, Object> entry : m.getMetrics().entrySet()) {
                    if (entry.getValue() instanceof Number) {
                        allMetricValues.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                            .add(((Number) entry.getValue()).doubleValue());
                    }
                }
            }
        }
        
        // 3. 输出精简的指标特征（每指标一行）
        sb.append("【指标特征】当前值 | 最小-最大 | 平均 | 趋势 | 状态\n");
        sb.append("-".repeat(50)).append("\n");
        
        for (Map.Entry<String, List<Double>> entry : allMetricValues.entrySet()) {
            String name = entry.getKey();
            List<Double> values = entry.getValue();
            
            if (values.isEmpty()) continue;
            
            double current = values.get(values.size() - 1);
            double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            double avg = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            
            // 趋势判断
            String trend = "→";  // 稳定
            if (values.size() >= 3) {
                int mid = values.size() / 2;
                double firstHalf = values.subList(0, mid).stream().mapToDouble(Double::doubleValue).average().orElse(0);
                double secondHalf = values.subList(mid, values.size()).stream().mapToDouble(Double::doubleValue).average().orElse(0);
                if (secondHalf > firstHalf * 1.1) trend = "↑";  // 上升
                else if (secondHalf < firstHalf * 0.9) trend = "↓";  // 下降
            }
            
            // 状态判断
            String status = "正常";
            if (name.contains("cpu") || name.contains("memory")) {
                if (current > 0.9) status = "⚠️告警";
                else if (current > 0.8) status = "⚠️警告";
            }
            
            sb.append(String.format("%-15s %.2f | %.2f-%.2f | %.2f | %s | %s\n", 
                name, current, min, max, avg, trend, status));
        }
        
        // 4. 异常检测摘要
        sb.append("\n【异常检测】\n");
        List<String> anomalies = new ArrayList<>();
        for (Map.Entry<String, List<Double>> entry : allMetricValues.entrySet()) {
            String name = entry.getKey();
            List<Double> values = entry.getValue();
            double current = values.get(values.size() - 1);
            
            if (name.contains("cpu") && current > 0.8) {
                anomalies.add(String.format("CPU使用率过高: %.1f%%", current * 100));
            }
            if (name.contains("memory") && current > 0.8) {
                anomalies.add(String.format("内存使用率过高: %.1f%%", current * 100));
            }
        }
        
        if (anomalies.isEmpty()) {
            sb.append("未检测到明显异常\n");
        } else {
            for (String anomaly : anomalies) {
                sb.append("- ").append(anomaly).append("\n");
            }
        }
        
        return sb.toString();
    }
    
    private String formatTimestamp(long timestamp) {
        try {
            java.time.Instant instant = java.time.Instant.ofEpochSecond(timestamp);
            return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .format(java.time.ZonedDateTime.ofInstant(instant, java.time.ZoneId.systemDefault()));
        } catch (Exception e) {
            return String.valueOf(timestamp);
        }
    }
    
    @Override
    public String getMetricTrend(String storageId, String metricName) {
        Optional<List<ComponentMetrics>> optMetrics = getTimeSeries(storageId);
        
        if (optMetrics.isEmpty() || optMetrics.get().isEmpty()) {
            return "数据不存在";
        }
        
        List<ComponentMetrics> metricsList = optMetrics.get();
        List<Double> values = new ArrayList<>();
        List<Long> timestamps = new ArrayList<>();
        
        for (ComponentMetrics m : metricsList) {
            Double value = null;
            
            // 尝试从直接字段获取
            switch (metricName) {
                case "cpu_usage":
                    value = m.getCpuUsage();
                    break;
                case "memory_usage":
                    value = m.getMemoryUsage();
                    break;
                case "gc_time":
                    value = m.getGcTime() != null ? m.getGcTime().doubleValue() : null;
                    break;
                case "connection_count":
                    value = m.getConnectionCount() != null ? m.getConnectionCount().doubleValue() : null;
                    break;
                default:
                    // 从metrics Map获取
                    value = m.getMetricAsDouble(metricName);
            }
            
            if (value != null) {
                values.add(value);
                if (m.getTimestamp() != null) {
                    timestamps.add(m.getTimestamp());
                }
            }
        }
        
        if (values.isEmpty()) {
            return String.format("%s: 无数据", metricName);
        }
        
        // 计算统计信息
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double avg = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double sum = values.stream().mapToDouble(Double::doubleValue).sum();
        
        // 计算标准差
        double variance = values.stream()
            .mapToDouble(v -> (v - avg) * (v - avg))
            .average().orElse(0);
        double stddev = Math.sqrt(variance);
        
        // 判断趋势
        String trend = "稳定";
        if (values.size() >= 3) {
            double firstHalf = values.subList(0, values.size() / 2).stream()
                .mapToDouble(Double::doubleValue).average().orElse(0);
            double secondHalf = values.subList(values.size() / 2, values.size()).stream()
                .mapToDouble(Double::doubleValue).average().orElse(0);
            
            if (secondHalf > firstHalf * 1.1) {
                trend = "上升";
            } else if (secondHalf < firstHalf * 0.9) {
                trend = "下降";
            }
        }
        
        // 当前值
        double current = values.get(values.size() - 1);
        
        // 格式化输出
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("【%s】\n", metricName));
        sb.append(String.format("  当前值: %.2f\n", current));
        sb.append(String.format("  最小值: %.2f, 最大值: %.2f\n", min, max));
        sb.append(String.format("  平均值: %.2f, 标准差: %.2f\n", avg, stddev));
        sb.append(String.format("  趋势: %s\n", trend));
        
        return sb.toString();
    }
    
    @Override
    public void delete(String storageId) {
        if (storageId == null) {
            return;
        }
        
        String key = KEY_PREFIX + storageId;
        redisTemplate.delete(key);
        log.info("删除时序数据: key={}", key);
    }
    
    @Override
    public boolean exists(String storageId) {
        if (storageId == null) {
            return false;
        }
        
        String key = KEY_PREFIX + storageId;
        Long size = redisTemplate.opsForList().size(key);
        return size != null && size > 0;
    }
    
    @Override
    public int getDataPointCount(String storageId) {
        if (storageId == null) {
            return 0;
        }
        
        String key = KEY_PREFIX + storageId;
        Long size = redisTemplate.opsForList().size(key);
        return size != null ? size.intValue() : 0;
    }
}
