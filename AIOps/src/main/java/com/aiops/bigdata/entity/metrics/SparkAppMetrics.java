package com.aiops.bigdata.entity.metrics;

import com.aiops.bigdata.entity.base.Metrics;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spark作业指标实体类
 * 用于接收Spark应用程序的执行指标和运行信息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SparkAppMetrics extends Metrics {
    
    private String jobId;
    private String appName;
    private Long submitTime;
    private Long duration;
    private Integer executorCount;
    private String executorMemory;
    private Integer executorCores;
    private Double executorMemoryGB;  // Executor内存(GB)
    private String driverMemory;
    private Long shuffleRead;
    private Long shuffleWrite;
    private Long inputSize;
    private Long outputSize;
    private String physicalPlan;
    private String status;
    private String appId;
    private String user;
    private String queue;
    
    // 额外字段
    private Double skewRatio;          // 数据倾斜率
    private Double shuffleRatio;       // Shuffle比例
    
    private List<StageInfo> stages = new ArrayList<>();
    private List<TimelineEvent> timeline = new ArrayList<>();
    private List<ExecutorInfo> executors = new ArrayList<>();
    private Map<String, Object> extraMetrics = new HashMap<>();
    
    @Override
    public String getMetricsType() {
        return "spark_app";
    }
    
    public String getUniqueId() {
        return jobId != null ? jobId : appName + "_" + submitTime;
    }
    
    public long getTotalDataSize() {
        long total = 0;
        if (inputSize != null) total += inputSize;
        if (outputSize != null) total += outputSize;
        if (shuffleRead != null) total += shuffleRead;
        if (shuffleWrite != null) total += shuffleWrite;
        return total;
    }
    
    public double getShuffleRatio() {
        long total = getTotalDataSize();
        if (total == 0) return 0;
        long shuffleTotal = (shuffleRead != null ? shuffleRead : 0) + 
                           (shuffleWrite != null ? shuffleWrite : 0);
        return (double) shuffleTotal / total;
    }
    
    /**
     * Stage信息
     */
    @Data
    public static class StageInfo {
        private String stageId;
        private String name;
        private Integer numTasks;
        private Integer completedTasks;
        private Integer failedTasks;
        private Long duration;
        private Long shuffleReadBytes;
        private Long shuffleWriteBytes;
        private Long inputBytes;
        private Long outputBytes;
        private Long gcTime;
        private Long memoryBytesSpilled;
        private Long diskBytesSpilled;
        private Boolean hasSkew;
        private Long maxTaskTime;
        private Long medianTaskTime;
        
        public double getSkewRatio() {
            if (medianTaskTime == null || medianTaskTime == 0) return 1.0;
            if (maxTaskTime == null) return 1.0;
            return (double) maxTaskTime / medianTaskTime;
        }
        
        public double getCompletionRate() {
            if (numTasks == null || numTasks == 0) return 0;
            if (completedTasks == null) return 0;
            return (double) completedTasks / numTasks;
        }
    }
    
    /**
     * 时间线事件
     */
    @Data
    public static class TimelineEvent {
        private Long timestamp;
        private String eventType;
        private String description;
        private String stageId;
        private Map<String, Object> properties = new HashMap<>();
    }
    
    /**
     * Executor信息
     */
    @Data
    public static class ExecutorInfo {
        private String executorId;
        private String host;
        private Integer port;
        private Integer cores;
        private Long memoryUsed;
        private Long maxMemory;
        private Integer completedTasks;
        private Integer failedTasks;
        private Long gcTime;
        private Long shuffleRead;
        private Long shuffleWrite;
        
        public double getMemoryUsageRate() {
            if (maxMemory == null || maxMemory == 0) return 0;
            if (memoryUsed == null) return 0;
            return (double) memoryUsed / maxMemory;
        }
    }
}
