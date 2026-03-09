package com.aiops.bigdata.entity.context;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 长期状态 - Layer 2
 * 存储天级变化的长期数据
 * 建议存储：MySQL（持久化存储）
 */
@Data
public class LongTermStatus {
    
    /**
     * 记录ID
     */
    private Long id;
    
    /**
     * 集群名称
     */
    private String cluster;
    
    /**
     * 状态日期
     */
    private LocalDate statusDate;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    
    // ============ 集群基本信息 ============
    
    /**
     * 总节点数
     */
    private Integer totalNodes;
    
    /**
     * 活跃节点数
     */
    private Integer activeNodes;
    
    /**
     * 总CPU核心数
     */
    private Integer totalCpuCores;
    
    /**
     * 总内存（GB）
     */
    private Long totalMemoryGB;
    
    /**
     * 总磁盘容量（TB）
     */
    private Long totalDiskTB;
    
    // ============ HDFS状态 ============
    
    /**
     * HDFS总容量（TB）
     */
    private Long hdfsCapacityTB;
    
    /**
     * HDFS已使用容量（TB）
     */
    private Long hdfsUsedTB;
    
    /**
     * HDFS使用率
     */
    private Double hdfsUsageRate;
    
    /**
     * 文件总数
     */
    private Long hdfsFileCount;
    
    /**
     * 块总数
     */
    private Long hdfsBlockCount;
    
    /**
     * 副本数配置
     */
    private Integer hdfsReplicationFactor;
    
    /**
     * NameNode数量
     */
    private Integer nameNodeCount;
    
    /**
     * DataNode数量
     */
    private Integer dataNodeCount;
    
    // ============ YARN状态 ============
    
    /**
     * ResourceManager数量
     */
    private Integer rmCount;
    
    /**
     * NodeManager数量
     */
    private Integer nmCount;
    
    /**
     * YARN总内存（GB）
     */
    private Long yarnTotalMemoryGB;
    
    /**
     * YARN总CPU核心数
     */
    private Integer yarnTotalVcores;
    
    /**
     * 队列配置
     */
    private String yarnQueueConfig;
    
    // ============ Spark状态 ============
    
    /**
     * 今日作业总数
     */
    private Integer sparkJobCountToday;
    
    /**
     * 今日失败作业数
     */
    private Integer sparkFailedJobsToday;
    
    /**
     * 平均作业执行时长（分钟）
     */
    private Double sparkAvgJobDuration;
    
    // ============ Kafka状态 ============
    
    /**
     * Broker数量
     */
    private Integer kafkaBrokerCount;
    
    /**
     * Topic数量
     */
    private Integer kafkaTopicCount;
    
    /**
     * 分区总数
     */
    private Integer kafkaPartitionCount;
    
    // ============ 组件版本信息 ============
    
    /**
     * 组件版本
     * Key: 组件名, Value: 版本号
     */
    private Map<String, String> componentVersions = new HashMap<>();
    
    // ============ 配置摘要 ============
    
    /**
     * 关键配置参数
     */
    private Map<String, String> keyConfigs = new HashMap<>();
    
    // ============ 统计数据 ============
    
    /**
     * 今日告警总数
     */
    private Integer alertCountToday;
    
    /**
     * 今日异常事件数
     */
    private Integer anomalyCountToday;
    
    /**
     * 平均集群负载
     */
    private Double avgClusterLoad;
    
    /**
     * 获取Redis Key（用于缓存）
     */
    public String getRedisKey() {
        return "aiops:longterm:" + cluster + ":" + statusDate;
    }
    
    /**
     * 计算HDFS使用率
     */
    public void calculateHdfsUsageRate() {
        if (hdfsCapacityTB != null && hdfsCapacityTB > 0 && hdfsUsedTB != null) {
            this.hdfsUsageRate = (double) hdfsUsedTB / hdfsCapacityTB;
        }
    }
}
