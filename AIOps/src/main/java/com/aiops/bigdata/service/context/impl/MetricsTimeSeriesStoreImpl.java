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
        sb.append("=== 时间序列数据摘要 ===\n");
        sb.append("【重要说明】这是一组时间序列数据，包含同一组件在不同时间点的多个指标采样。\n");
        sb.append("请分析指标随时间的变化趋势，而不仅仅是单个数值。\n\n");
        
        sb.append("## 组件信息\n");
        sb.append(String.format("服务: %s\n", first.getService()));
        sb.append(String.format("组件: %s\n", first.getComponent()));
        if (first.getInstance() != null) {
            sb.append(String.format("实例: %s\n", first.getInstance()));
        }
        sb.append(String.format("集群: %s\n", first.getCluster()));
        sb.append("\n");
        
        sb.append("## 时序数据特征\n");
        sb.append(String.format("数据点数量: %d 个\n", metricsList.size()));
        
        // 时间范围和采样间隔
        if (first.getTimestamp() != null && latest.getTimestamp() != null) {
            long duration = latest.getTimestamp() - first.getTimestamp();
            long interval = metricsList.size() > 1 ? duration / (metricsList.size() - 1) : 0;
            sb.append(String.format("时间跨度: %d 秒 (%.1f 分钟)\n", duration, duration / 60.0));
            sb.append(String.format("采样间隔: 约 %d 秒\n", interval));
            sb.append(String.format("起始时间: %s\n", formatTimestamp(first.getTimestamp())));
            sb.append(String.format("结束时间: %s\n", formatTimestamp(latest.getTimestamp())));
        }
        sb.append("\n");
        
        // 收集所有指标名称
        Set<String> metricNames = new HashSet<>();
        for (ComponentMetrics m : metricsList) {
            if (m.getMetrics() != null) {
                metricNames.addAll(m.getMetrics().keySet());
            }
        }
        
        // 添加直接字段
        if (metricsList.get(0).getCpuUsage() != null) metricNames.add("cpu_usage");
        if (metricsList.get(0).getMemoryUsage() != null) metricNames.add("memory_usage");
        if (metricsList.get(0).getGcTime() != null) metricNames.add("gc_time");
        if (metricsList.get(0).getConnectionCount() != null) metricNames.add("connection_count");
        
        sb.append(String.format("## 监控指标 (%d个)\n", metricNames.size()));
        sb.append("指标列表: ").append(String.join(", ", metricNames)).append("\n\n");
        
        // 各指标统计
        sb.append("## 各指标时序统计\n");
        sb.append("(以下数据展示各指标在时间序列中的变化情况)\n\n");
        for (String metricName : metricNames) {
            sb.append(getMetricTrend(storageId, metricName)).append("\n");
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
