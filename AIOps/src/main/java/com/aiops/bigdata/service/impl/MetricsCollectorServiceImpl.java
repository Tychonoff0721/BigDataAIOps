package com.aiops.bigdata.service.impl;

import com.aiops.bigdata.entity.context.RealtimeMetrics;
import com.aiops.bigdata.entity.metrics.ComponentMetrics;
import com.aiops.bigdata.entity.metrics.SparkAppMetrics;
import com.aiops.bigdata.service.MetricsCollectorService;
import com.aiops.bigdata.service.context.RealtimeMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 指标收集服务实现类
 * 负责接收和存储各类指标数据
 * 
 * TODO: 后续集成持久化存储（MySQL/时序数据库）和消息队列（Kafka）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsCollectorServiceImpl implements MetricsCollectorService {
    
    private final RealtimeMetricsService realtimeMetricsService;
    
    /**
     * 组件指标缓存（临时方案）
     * Key: 组件唯一标识, Value: 指标列表（按时间排序）
     * TODO: 替换为持久化存储
     */
    private final Map<String, List<ComponentMetrics>> componentMetricsCache = new ConcurrentHashMap<>();
    
    /**
     * Spark作业指标缓存（临时方案）
     * Key: 作业ID, Value: 作业指标
     * TODO: 替换为持久化存储
     */
    private final Map<String, SparkAppMetrics> sparkAppMetricsCache = new ConcurrentHashMap<>();
    
    @Override
    public boolean collectComponentMetrics(ComponentMetrics metrics) {
        try {
            String uniqueId = metrics.getUniqueId();
            
            // 存储指标
            componentMetricsCache.computeIfAbsent(uniqueId, k -> new ArrayList<>())
                .add(metrics);
            
            // 保持最近1000条记录（临时方案）
            List<ComponentMetrics> metricsList = componentMetricsCache.get(uniqueId);
            if (metricsList.size() > 1000) {
                metricsList.remove(0);
            }
            
            // 同时保存到实时指标服务（Layer 1）
            RealtimeMetrics realtimeMetrics = convertToRealtimeMetrics(metrics);
            realtimeMetricsService.save(realtimeMetrics);
            
            log.debug("收集组件指标: uniqueId={}, metricsCount={}", 
                uniqueId, metrics.getMetrics().size());
            
            return true;
        } catch (Exception e) {
            log.error("收集组件指标失败: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 将ComponentMetrics转换为RealtimeMetrics
     */
    private RealtimeMetrics convertToRealtimeMetrics(ComponentMetrics metrics) {
        RealtimeMetrics realtime = new RealtimeMetrics();
        realtime.setCluster(metrics.getCluster());
        realtime.setService(metrics.getService());
        realtime.setComponent(metrics.getComponent());
        realtime.setInstance(metrics.getInstance());
        realtime.setTimestamp(metrics.getTimestamp());
        realtime.setCollectTime(LocalDateTime.now());
        
        // 转换常用指标
        realtime.setCpuUsage(metrics.getMetricAsDouble("cpu_usage"));
        realtime.setMemoryUsage(metrics.getMetricAsDouble("memory_usage"));
        realtime.setHeapUsage(metrics.getMetricAsDouble("heap_usage"));
        realtime.setGcTime(metrics.getMetricAsLong("gc_time"));
        realtime.setConnectionCount(metrics.getMetricAsLong("connection_count") != null 
            ? metrics.getMetricAsLong("connection_count").intValue() : null);
        realtime.setThreadCount(metrics.getMetricAsLong("thread_count") != null 
            ? metrics.getMetricAsLong("thread_count").intValue() : null);
        
        // 其他指标放入extraMetrics
        metrics.getMetrics().forEach((k, v) -> {
            if (!isCoreMetric(k)) {
                realtime.addExtraMetric(k, v);
            }
        });
        
        return realtime;
    }
    
    private boolean isCoreMetric(String metricName) {
        return "cpu_usage".equals(metricName) || "memory_usage".equals(metricName)
            || "heap_usage".equals(metricName) || "gc_time".equals(metricName)
            || "connection_count".equals(metricName) || "thread_count".equals(metricName);
    }
    
    @Override
    public int collectComponentMetricsBatch(List<ComponentMetrics> metricsList) {
        int successCount = 0;
        for (ComponentMetrics metrics : metricsList) {
            if (collectComponentMetrics(metrics)) {
                successCount++;
            }
        }
        log.info("批量收集组件指标: total={}, success={}", metricsList.size(), successCount);
        return successCount;
    }
    
    @Override
    public boolean collectSparkAppMetrics(SparkAppMetrics metrics) {
        try {
            String uniqueId = metrics.getUniqueId();
            sparkAppMetricsCache.put(uniqueId, metrics);
            
            log.debug("收集Spark作业指标: jobId={}, appName={}", 
                metrics.getJobId(), metrics.getAppName());
            
            return true;
        } catch (Exception e) {
            log.error("收集Spark作业指标失败: {}", e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public int collectSparkAppMetricsBatch(List<SparkAppMetrics> metricsList) {
        int successCount = 0;
        for (SparkAppMetrics metrics : metricsList) {
            if (collectSparkAppMetrics(metrics)) {
                successCount++;
            }
        }
        log.info("批量收集Spark作业指标: total={}, success={}", metricsList.size(), successCount);
        return successCount;
    }
    
    @Override
    public List<ComponentMetrics> queryComponentMetrics(String cluster, String service,
            String component, Long startTime, Long endTime) {
        
        // 构建唯一标识前缀
        String prefix = (cluster != null ? cluster : "default") + ":" + service + ":" + component;
        
        return componentMetricsCache.entrySet().stream()
            .filter(e -> e.getKey().startsWith(prefix))
            .flatMap(e -> e.getValue().stream())
            .filter(m -> {
                Long ts = m.getTimestamp();
                if (ts == null) return true;
                if (startTime != null && ts < startTime) return false;
                if (endTime != null && ts > endTime) return false;
                return true;
            })
            .sorted((a, b) -> {
                if (a.getTimestamp() == null) return 1;
                if (b.getTimestamp() == null) return -1;
                return a.getTimestamp().compareTo(b.getTimestamp());
            })
            .collect(Collectors.toList());
    }
    
    @Override
    public List<SparkAppMetrics> querySparkAppMetrics(String cluster, String jobId,
            String appName, Long startTime, Long endTime) {
        
        return sparkAppMetricsCache.values().stream()
            .filter(m -> {
                if (cluster != null && !cluster.equals(m.getCluster())) return false;
                if (jobId != null && !jobId.equals(m.getJobId())) return false;
                if (appName != null && !appName.equals(m.getAppName())) return false;
                if (startTime != null && m.getSubmitTime() != null && m.getSubmitTime() < startTime) 
                    return false;
                if (endTime != null && m.getSubmitTime() != null && m.getSubmitTime() > endTime) 
                    return false;
                return true;
            })
            .sorted((a, b) -> {
                if (a.getSubmitTime() == null) return 1;
                if (b.getSubmitTime() == null) return -1;
                return b.getSubmitTime().compareTo(a.getSubmitTime()); // 降序，最新的在前
            })
            .collect(Collectors.toList());
    }
    
    @Override
    public ComponentMetrics getLatestComponentMetrics(String uniqueId) {
        List<ComponentMetrics> metricsList = componentMetricsCache.get(uniqueId);
        if (metricsList == null || metricsList.isEmpty()) {
            return null;
        }
        return metricsList.get(metricsList.size() - 1);
    }
    
    @Override
    public int cleanupExpiredMetrics(int retentionDays) {
        long cutoffTime = System.currentTimeMillis() / 1000 - retentionDays * 24L * 60 * 60;
        int totalRemoved = 0;
        
        // 清理组件指标
        for (Map.Entry<String, List<ComponentMetrics>> entry : componentMetricsCache.entrySet()) {
            int beforeSize = entry.getValue().size();
            entry.getValue().removeIf(m -> 
                m.getTimestamp() != null && m.getTimestamp() < cutoffTime);
            totalRemoved += beforeSize - entry.getValue().size();
        }
        
        // 清理Spark作业指标
        int sparkBeforeSize = sparkAppMetricsCache.size();
        sparkAppMetricsCache.entrySet().removeIf(e -> 
            e.getValue().getSubmitTime() != null && e.getValue().getSubmitTime() < cutoffTime);
        totalRemoved += sparkBeforeSize - sparkAppMetricsCache.size();
        
        log.info("清理过期指标数据: retentionDays={}, removed={}", retentionDays, totalRemoved);
        
        return totalRemoved;
    }
}
