package com.aiops.bigdata.service.context.impl;

import com.aiops.bigdata.entity.metrics.SparkAppMetrics;
import com.aiops.bigdata.service.context.SparkJobStore;
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
 * Spark作业存储服务实现类
 */
@Slf4j
@Service
public class SparkJobStoreImpl implements SparkJobStore {
    
    private static final String CLUSTER_KEY_PREFIX = "aiops:spark:cluster:";
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    @Autowired
    public SparkJobStoreImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    @Override
    public String saveJob(SparkAppMetrics metrics, int retentionHours) {
        if (metrics == null || metrics.getJobId() == null) {
            log.warn("Spark作业数据无效，无法保存");
            return null;
        }
        
        String jobId = metrics.getJobId();
        String key = KEY_PREFIX + jobId;
        
        try {
            redisTemplate.opsForValue().set(key, metrics, retentionHours, TimeUnit.HOURS);
            
            // 同时添加到集群作业列表
            if (metrics.getCluster() != null) {
                String clusterKey = CLUSTER_KEY_PREFIX + metrics.getCluster();
                redisTemplate.opsForList().leftPush(clusterKey, jobId);
                redisTemplate.expire(clusterKey, retentionHours, TimeUnit.HOURS);
            }
            
            log.info("保存Spark作业到Redis: jobId={}, appName={}", jobId, metrics.getAppName());
            return jobId;
            
        } catch (Exception e) {
            log.error("保存Spark作业失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    @Override
    public void saveJobs(List<SparkAppMetrics> metricsList, int retentionHours) {
        for (SparkAppMetrics metrics : metricsList) {
            saveJob(metrics, retentionHours);
        }
        log.info("批量保存Spark作业: count={}", metricsList.size());
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public Optional<SparkAppMetrics> getJob(String jobId) {
        if (jobId == null) {
            return Optional.empty();
        }
        
        String key = KEY_PREFIX + jobId;
        
        try {
            Object value = redisTemplate.opsForValue().get(key);
            
            if (value == null) {
                return Optional.empty();
            }
            
            if (value instanceof Map) {
                SparkAppMetrics metrics = objectMapper.convertValue(value, SparkAppMetrics.class);
                return Optional.of(metrics);
            } else if (value instanceof SparkAppMetrics) {
                return Optional.of((SparkAppMetrics) value);
            }
            
            return Optional.empty();
            
        } catch (Exception e) {
            log.error("获取Spark作业失败: jobId={}, error={}", jobId, e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    @Override
    public List<SparkAppMetrics> getJobsByCluster(String cluster) {
        if (cluster == null) {
            return Collections.emptyList();
        }
        
        String clusterKey = CLUSTER_KEY_PREFIX + cluster;
        
        try {
            List<Object> jobIds = redisTemplate.opsForList().range(clusterKey, 0, -1);
            
            if (jobIds == null || jobIds.isEmpty()) {
                return Collections.emptyList();
            }
            
            List<SparkAppMetrics> result = new ArrayList<>();
            for (Object id : jobIds) {
                String jobId = id.toString();
                getJob(jobId).ifPresent(result::add);
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("获取集群Spark作业失败: cluster={}, error={}", cluster, e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    @Override
    public List<SparkAppMetrics> getRecentJobs(String cluster, int limit) {
        List<SparkAppMetrics> jobs = getJobsByCluster(cluster);
        
        // 按提交时间排序
        return jobs.stream()
            .filter(j -> j.getSubmitTime() != null)
            .sorted(Comparator.comparingLong(SparkAppMetrics::getSubmitTime).reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    @Override
    public String getJobSummary(String jobId) {
        Optional<SparkAppMetrics> optJob = getJob(jobId);
        
        if (optJob.isEmpty()) {
            return "Spark作业不存在: " + jobId;
        }
        
        SparkAppMetrics job = optJob.get();
        StringBuilder sb = new StringBuilder();
        
        sb.append("=== Spark作业摘要 ===\n");
        sb.append(String.format("作业ID: %s\n", job.getJobId()));
        sb.append(String.format("应用名称: %s\n", job.getAppName() != null ? job.getAppName() : "N/A"));
        sb.append(String.format("集群: %s\n", job.getCluster() != null ? job.getCluster() : "N/A"));
        sb.append(String.format("状态: %s\n", job.getStatus() != null ? job.getStatus() : "N/A"));
        
        // 执行时长
        if (job.getDuration() != null) {
            sb.append(String.format("执行时长: %s\n", formatDuration(job.getDuration())));
        }
        
        // 资源配置
        sb.append("\n--- 资源配置 ---\n");
        sb.append(String.format("Executor数量: %s\n", 
            job.getExecutorCount() != null ? job.getExecutorCount() : "N/A"));
        sb.append(String.format("Executor内存: %s\n", 
            job.getExecutorMemory() != null ? job.getExecutorMemory() : "N/A"));
        sb.append(String.format("Executor核心数: %s\n", 
            job.getExecutorCores() != null ? job.getExecutorCores() : "N/A"));
        
        // 数据量
        sb.append("\n--- 数据量 ---\n");
        sb.append(String.format("输入数据量: %s\n", 
            job.getInputSize() != null ? formatBytes(job.getInputSize()) : "N/A"));
        sb.append(String.format("输出数据量: %s\n", 
            job.getOutputSize() != null ? formatBytes(job.getOutputSize()) : "N/A"));
        sb.append(String.format("Shuffle读取: %s\n", 
            job.getShuffleRead() != null ? formatBytes(job.getShuffleRead()) : "N/A"));
        sb.append(String.format("Shuffle写入: %s\n", 
            job.getShuffleWrite() != null ? formatBytes(job.getShuffleWrite()) : "N/A"));
        
        // Stage统计
        if (job.getStages() != null && !job.getStages().isEmpty()) {
            sb.append("\n--- Stage统计 ---\n");
            sb.append(String.format("Stage数量: %d\n", job.getStages().size()));
            
            int totalTasks = 0;
            int failedTasks = 0;
            for (SparkAppMetrics.StageInfo stage : job.getStages()) {
                if (stage.getNumTasks() != null) totalTasks += stage.getNumTasks();
                if (stage.getFailedTasks() != null) failedTasks += stage.getFailedTasks();
            }
            
            sb.append(String.format("总任务数: %d\n", totalTasks));
            sb.append(String.format("失败任务数: %d\n", failedTasks));
            if (totalTasks > 0) {
                sb.append(String.format("失败率: %.2f%%\n", (double) failedTasks / totalTasks * 100));
            }
        }
        
        return sb.toString();
    }
    
    @Override
    public String getStageDetails(String jobId) {
        Optional<SparkAppMetrics> optJob = getJob(jobId);
        
        if (optJob.isEmpty()) {
            return "Spark作业不存在: " + jobId;
        }
        
        SparkAppMetrics job = optJob.get();
        
        if (job.getStages() == null || job.getStages().isEmpty()) {
            return "无Stage信息";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("=== Stage详情 ===\n\n");
        
        for (SparkAppMetrics.StageInfo stage : job.getStages()) {
            sb.append(String.format("【Stage %s】%s\n", 
                stage.getStageId(), 
                stage.getName() != null ? stage.getName() : ""));
            sb.append(String.format("  任务数: %d", 
                stage.getNumTasks() != null ? stage.getNumTasks() : 0));
            sb.append(String.format(", 完成率: %.1f%%\n", 
                stage.getCompletionRate() * 100));
            
            if (stage.getDuration() != null) {
                sb.append(String.format("  执行时长: %s\n", formatDuration(stage.getDuration())));
            }
            
            if (stage.getFailedTasks() != null && stage.getFailedTasks() > 0) {
                sb.append(String.format("  [警告] 失败任务: %d\n", stage.getFailedTasks()));
            }
            
            if (stage.getHasSkew() != null && stage.getHasSkew()) {
                sb.append(String.format("  [警告] 存在数据倾斜, 倾斜率: %.1f\n", 
                    stage.getSkewRatio()));
            }
            
            if (stage.getGcTime() != null && stage.getDuration() != null) {
                double gcRatio = (double) stage.getGcTime() / stage.getDuration();
                if (gcRatio > 0.1) {
                    sb.append(String.format("  [警告] GC时间占比过高: %.1f%%\n", gcRatio * 100));
                }
            }
            
            // 数据量
            if (stage.getShuffleReadBytes() != null || stage.getShuffleWriteBytes() != null) {
                sb.append("  Shuffle: ");
                if (stage.getShuffleReadBytes() != null) {
                    sb.append(String.format("读%s ", formatBytes(stage.getShuffleReadBytes())));
                }
                if (stage.getShuffleWriteBytes() != null) {
                    sb.append(String.format("写%s", formatBytes(stage.getShuffleWriteBytes())));
                }
                sb.append("\n");
            }
            
            sb.append("\n");
        }
        
        return sb.toString();
    }
    
    @Override
    public String getExecutorDetails(String jobId) {
        Optional<SparkAppMetrics> optJob = getJob(jobId);
        
        if (optJob.isEmpty()) {
            return "Spark作业不存在: " + jobId;
        }
        
        SparkAppMetrics job = optJob.get();
        
        if (job.getExecutors() == null || job.getExecutors().isEmpty()) {
            return "无Executor信息";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("=== Executor详情 ===\n\n");
        sb.append(String.format("Executor数量: %d\n\n", job.getExecutors().size()));
        
        double totalMemUsage = 0;
        double maxMemUsage = 0;
        long totalGcTime = 0;
        
        for (SparkAppMetrics.ExecutorInfo executor : job.getExecutors()) {
            double memUsage = executor.getMemoryUsageRate();
            totalMemUsage += memUsage;
            maxMemUsage = Math.max(maxMemUsage, memUsage);
            if (executor.getGcTime() != null) {
                totalGcTime += executor.getGcTime();
            }
            
            sb.append(String.format("【Executor %s】%s:%d\n", 
                executor.getExecutorId(),
                executor.getHost() != null ? executor.getHost() : "N/A",
                executor.getPort() != null ? executor.getPort() : 0));
            sb.append(String.format("  内存使用率: %.1f%%\n", memUsage * 100));
            sb.append(String.format("  完成任务: %d, 失败任务: %d\n",
                executor.getCompletedTasks() != null ? executor.getCompletedTasks() : 0,
                executor.getFailedTasks() != null ? executor.getFailedTasks() : 0));
            if (executor.getGcTime() != null) {
                sb.append(String.format("  GC时间: %s\n", formatDuration(executor.getGcTime())));
            }
            sb.append("\n");
        }
        
        // 汇总
        sb.append("=== Executor汇总 ===\n");
        sb.append(String.format("平均内存使用率: %.1f%%\n", 
            totalMemUsage / job.getExecutors().size() * 100));
        sb.append(String.format("最大内存使用率: %.1f%%\n", maxMemUsage * 100));
        sb.append(String.format("总GC时间: %s\n", formatDuration(totalGcTime)));
        
        return sb.toString();
    }
    
    @Override
    public String getBottleneckAnalysis(String jobId) {
        Optional<SparkAppMetrics> optJob = getJob(jobId);
        
        if (optJob.isEmpty()) {
            return "Spark作业不存在: " + jobId;
        }
        
        SparkAppMetrics job = optJob.get();
        StringBuilder sb = new StringBuilder();
        
        sb.append("=== 瓶颈分析 ===\n\n");
        
        List<String> bottlenecks = new ArrayList<>();
        
        // 1. 检查失败任务
        if (job.getStages() != null) {
            int failedTasks = 0;
            int totalTasks = 0;
            for (SparkAppMetrics.StageInfo stage : job.getStages()) {
                if (stage.getFailedTasks() != null) failedTasks += stage.getFailedTasks();
                if (stage.getNumTasks() != null) totalTasks += stage.getNumTasks();
            }
            
            if (failedTasks > 0) {
                double failRate = totalTasks > 0 ? (double) failedTasks / totalTasks : 0;
                sb.append(String.format("【任务失败】失败任务数: %d, 失败率: %.2f%%\n", 
                    failedTasks, failRate * 100));
                bottlenecks.add("failure");
            }
        }
        
        // 2. 检查数据倾斜
        if (job.getStages() != null) {
            List<SparkAppMetrics.StageInfo> skewedStages = job.getStages().stream()
                .filter(s -> s.getHasSkew() != null && s.getHasSkew())
                .collect(Collectors.toList());
            
            if (!skewedStages.isEmpty()) {
                sb.append(String.format("【数据倾斜】存在倾斜的Stage数: %d\n", skewedStages.size()));
                for (SparkAppMetrics.StageInfo stage : skewedStages) {
                    sb.append(String.format("  - Stage %s: 倾斜率 %.1f\n", 
                        stage.getStageId(), stage.getSkewRatio()));
                }
                bottlenecks.add("skew");
            }
        }
        
        // 3. 检查Shuffle
        long shuffleTotal = (job.getShuffleRead() != null ? job.getShuffleRead() : 0)
                          + (job.getShuffleWrite() != null ? job.getShuffleWrite() : 0);
        long dataTotal = job.getTotalDataSize();
        
        if (dataTotal > 0 && shuffleTotal > dataTotal * 0.5) {
            double shuffleRatio = (double) shuffleTotal / dataTotal;
            sb.append(String.format("【Shuffle瓶颈】Shuffle比例: %.1f%%\n", shuffleRatio * 100));
            bottlenecks.add("shuffle");
        }
        
        // 4. 检查GC
        if (job.getStages() != null) {
            long totalGcTime = 0;
            long totalDuration = 0;
            for (SparkAppMetrics.StageInfo stage : job.getStages()) {
                if (stage.getGcTime() != null) totalGcTime += stage.getGcTime();
                if (stage.getDuration() != null) totalDuration += stage.getDuration();
            }
            
            if (totalDuration > 0 && totalGcTime > totalDuration * 0.1) {
                double gcRatio = (double) totalGcTime / totalDuration;
                sb.append(String.format("【GC瓶颈】GC时间占比: %.1f%%\n", gcRatio * 100));
                bottlenecks.add("gc");
            }
        }
        
        // 5. 检查内存
        if (job.getExecutors() != null && !job.getExecutors().isEmpty()) {
            double maxMemUsage = job.getExecutors().stream()
                .mapToDouble(SparkAppMetrics.ExecutorInfo::getMemoryUsageRate)
                .max().orElse(0);
            
            if (maxMemUsage > 0.9) {
                sb.append(String.format("【内存瓶颈】最大内存使用率: %.1f%%\n", maxMemUsage * 100));
                bottlenecks.add("memory");
            }
        }
        
        // 6. 检查长尾Stage
        if (job.getStages() != null && job.getStages().size() >= 2) {
            List<Long> durations = job.getStages().stream()
                .map(SparkAppMetrics.StageInfo::getDuration)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            
            if (durations.size() >= 2) {
                double avg = durations.stream().mapToLong(Long::longValue).average().orElse(0);
                long max = durations.stream().mapToLong(Long::longValue).max().orElse(0);
                
                if (max > avg * 2) {
                    sb.append(String.format("【长尾Stage】最长Stage耗时是平均值的 %.1f 倍\n", max / avg));
                    bottlenecks.add("long_tail");
                }
            }
        }
        
        if (bottlenecks.isEmpty()) {
            sb.append("未检测到明显瓶颈，作业运行正常。\n");
        } else {
            sb.append(String.format("\n检测到的瓶颈类型: %s\n", String.join(", ", bottlenecks)));
        }
        
        return sb.toString();
    }
    
    @Override
    public void deleteJob(String jobId) {
        if (jobId == null) {
            return;
        }
        
        String key = KEY_PREFIX + jobId;
        redisTemplate.delete(key);
        log.info("删除Spark作业: jobId={}", jobId);
    }
    
    @Override
    public boolean exists(String jobId) {
        if (jobId == null) {
            return false;
        }
        
        String key = KEY_PREFIX + jobId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
    
    @Override
    public String getKey(String jobId) {
        return KEY_PREFIX + jobId;
    }
    
    // ============ 辅助方法 ============
    
    private String formatDuration(Long ms) {
        if (ms == null) return "N/A";
        if (ms < 1000) return ms + "ms";
        if (ms < 60000) return String.format("%.1fs", ms / 1000.0);
        if (ms < 3600000) return String.format("%.1fmin", ms / 60000.0);
        return String.format("%.1fh", ms / 3600000.0);
    }
    
    private String formatBytes(Long bytes) {
        if (bytes == null) return "N/A";
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1fMB", bytes / (1024.0 * 1024));
        return String.format("%.1fGB", bytes / (1024.0 * 1024 * 1024));
    }
}
