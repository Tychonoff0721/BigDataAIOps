package com.aiops.bigdata.entity.feature;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spark作业特征
 * 从Spark作业指标中提取的特征
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SparkAppFeatures extends MetricFeatures {
    
    /**
     * 作业ID
     */
    private String jobId;
    
    /**
     * 应用名称
     */
    private String appName;
    
    // ============ 执行特征 ============
    
    /**
     * 执行时长（毫秒）
     */
    private Long duration;
    
    /**
     * 执行时长等级：short, medium, long, very_long
     */
    private String durationLevel;
    
    /**
     * 作业状态
     */
    private String status;
    
    /**
     * 是否成功
     */
    private boolean success;
    
    // ============ 资源特征 ============
    
    /**
     * Executor数量
     */
    private Integer executorCount;
    
    /**
     * Executor内存（GB）
     */
    private Double executorMemoryGB;
    
    /**
     * Executor核心数
     */
    private Integer executorCores;
    
    /**
     * 资源利用率评分（0-100）
     */
    private Double resourceUtilizationScore;
    
    /**
     * 资源配置是否合理
     */
    private boolean resourceConfigReasonable;
    
    // ============ 数据特征 ============
    
    /**
     * 输入数据量（GB）
     */
    private Double inputSizeGB;
    
    /**
     * 输出数据量（GB）
     */
    private Double outputSizeGB;
    
    /**
     * Shuffle读取量（GB）
     */
    private Double shuffleReadGB;
    
    /**
     * Shuffle写入量（GB）
     */
    private Double shuffleWriteGB;
    
    /**
     * 总数据量（GB）
     */
    private Double totalDataSizeGB;
    
    /**
     * Shuffle比例
     */
    private Double shuffleRatio;
    
    /**
     * Shuffle比例等级：low, medium, high, very_high
     */
    private String shuffleRatioLevel;
    
    // ============ 性能特征 ============
    
    /**
     * Stage数量
     */
    private Integer stageCount;
    
    /**
     * 任务总数
     */
    private Integer totalTasks;
    
    /**
     * 失败任务数
     */
    private Integer failedTasks;
    
    /**
     * 失败率
     */
    private Double failureRate;
    
    /**
     * 平均Stage执行时长（毫秒）
     */
    private Double avgStageDuration;
    
    /**
     * 最长Stage执行时长（毫秒）
     */
    private Long maxStageDuration;
    
    /**
     * Stage时长分布不均匀度（0-1）
     */
    private Double stageDurationSkewness;
    
    // ============ 数据倾斜特征 ============
    
    /**
     * 是否存在数据倾斜
     */
    private boolean hasDataSkew;
    
    /**
     * 最大倾斜率
     */
    private Double maxSkewRatio;
    
    /**
     * 倾斜Stage数量
     */
    private Integer skewedStageCount;
    
    /**
     * 倾斜Stage列表
     */
    private List<String> skewedStages = new ArrayList<>();
    
    // ============ GC特征 ============
    
    /**
     * 总GC时间（毫秒）
     */
    private Long totalGcTime;
    
    /**
     * GC时间占比
     */
    private Double gcTimeRatio;
    
    /**
     * GC时间占比等级：low, medium, high
     */
    private String gcTimeLevel;
    
    // ============ 内存特征 ============
    
    /**
     * 平均Executor内存使用率
     */
    private Double avgExecutorMemoryUsage;
    
    /**
     * 最大Executor内存使用率
     */
    private Double maxExecutorMemoryUsage;
    
    /**
     * 内存溢写总量（GB）
     */
    private Double totalMemorySpilledGB;
    
    /**
     * 磁盘溢写总量（GB）
     */
    private Double totalDiskSpilledGB;
    
    // ============ 瓶颈特征 ============
    
    /**
     * 主要瓶颈类型
     * cpu, memory, shuffle, skew, io, gc, none
     */
    private String mainBottleneck;
    
    /**
     * 瓶颈严重程度（0-100）
     */
    private Double bottleneckSeverity;
    
    /**
     * 检测到的瓶颈列表
     */
    private List<String> detectedBottlenecks = new ArrayList<>();
    
    // ============ 健康评分 ============
    
    /**
     * 整体健康评分（0-100）
     */
    private Integer healthScore;
    
    /**
     * 性能评分（0-100）
     */
    private Integer performanceScore;
    
    /**
     * 主要问题
     */
    private String mainIssue;
    
    /**
     * Stage特征列表
     */
    private List<StageFeatures> stageFeatures = new ArrayList<>();
    
    @Override
    public String getFeatureType() {
        return "spark_app";
    }
    
    @Override
    public String toSummaryText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Spark作业: ").append(appName != null ? appName : jobId).append("\n");
        sb.append("执行时长: ").append(formatDuration(duration)).append("\n");
        sb.append("状态: ").append(status).append("\n");
        
        if (healthScore != null) {
            sb.append("健康评分: ").append(healthScore).append("/100\n");
        }
        
        if (performanceScore != null) {
            sb.append("性能评分: ").append(performanceScore).append("/100\n");
        }
        
        sb.append("\n资源特征:\n");
        sb.append(String.format("  Executor: %d × %.1fGB × %d核\n", 
            executorCount != null ? executorCount : 0,
            executorMemoryGB != null ? executorMemoryGB : 0,
            executorCores != null ? executorCores : 0));
        
        sb.append("\n数据特征:\n");
        sb.append(String.format("  输入: %.2fGB, 输出: %.2fGB\n",
            inputSizeGB != null ? inputSizeGB : 0,
            outputSizeGB != null ? outputSizeGB : 0));
        sb.append(String.format("  Shuffle: 读取%.2fGB, 写入%.2fGB (占比%.1f%%)\n",
            shuffleReadGB != null ? shuffleReadGB : 0,
            shuffleWriteGB != null ? shuffleWriteGB : 0,
            shuffleRatio != null ? shuffleRatio * 100 : 0));
        
        if (hasDataSkew) {
            sb.append("\n数据倾斜:\n");
            sb.append(String.format("  倾斜Stage: %d个, 最大倾斜率: %.1f\n",
                skewedStageCount != null ? skewedStageCount : 0,
                maxSkewRatio != null ? maxSkewRatio : 0));
        }
        
        if (mainBottleneck != null && !"none".equals(mainBottleneck)) {
            sb.append("\n主要瓶颈: ").append(mainBottleneck);
            if (bottleneckSeverity != null) {
                sb.append(" (严重度: ").append(String.format("%.0f", bottleneckSeverity)).append(")");
            }
            sb.append("\n");
        }
        
        if (mainIssue != null) {
            sb.append("主要问题: ").append(mainIssue).append("\n");
        }
        
        return sb.toString();
    }
    
    private String formatDuration(Long ms) {
        if (ms == null) return "未知";
        if (ms < 1000) return ms + "ms";
        if (ms < 60000) return String.format("%.1fs", ms / 1000.0);
        if (ms < 3600000) return String.format("%.1fmin", ms / 60000.0);
        return String.format("%.1fh", ms / 3600000.0);
    }
    
    /**
     * Stage特征
     */
    @Data
    public static class StageFeatures {
        private String stageId;
        private String name;
        private Long duration;
        private Integer taskCount;
        private Integer failedTasks;
        private Long shuffleReadBytes;
        private Long shuffleWriteBytes;
        private Long gcTime;
        private Double gcRatio;
        private boolean hasSkew;
        private Double skewRatio;
        private boolean isBottleneck;
        private String bottleneckType;
    }
}
