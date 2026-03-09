package com.aiops.bigdata.entity.context;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 实时指标 - Layer 1
 * 存储秒级/分钟级变化的实时数据
 * 建议存储：Redis（设置短期过期，如5分钟）
 */
@Data
public class RealtimeMetrics {
    
    /**
     * 集群名称
     */
    private String cluster;
    
    /**
     * 服务类型
     */
    private String service;
    
    /**
     * 组件类型
     */
    private String component;
    
    /**
     * 实例标识
     */
    private String instance;
    
    /**
     * 采集时间
     */
    private LocalDateTime collectTime;
    
    /**
     * Unix时间戳（用于排序和过期判断）
     */
    private Long timestamp;
    
    // ============ 核心实时指标 ============
    
    /**
     * CPU使用率 (0-1)
     */
    private Double cpuUsage;
    
    /**
     * 内存使用率 (0-1)
     */
    private Double memoryUsage;
    
    /**
     * 堆内存使用率 (0-1)
     */
    private Double heapUsage;
    
    /**
     * GC时间（毫秒）
     */
    private Long gcTime;
    
    /**
     * 磁盘IO速率 (MB/s)
     */
    private Double diskIO;
    
    /**
     * 网络IO速率 (MB/s)
     */
    private Double networkIO;
    
    /**
     * 连接数
     */
    private Integer connectionCount;
    
    /**
     * 线程数
     */
    private Integer threadCount;
    
    /**
     * 系统负载
     */
    private Double systemLoad;
    
    // ============ YARN专用 ============
    
    /**
     * 活跃容器数
     */
    private Integer activeContainers;
    
    /**
     * 运行中应用数
     */
    private Integer runningApplications;
    
    /**
     * 待分配内存
     */
    private Long availableMemory;
    
    /**
     * 待分配CPU核心数
     */
    private Integer availableCores;
    
    // ============ HDFS专用 ============
    
    /**
     * 块数量
     */
    private Long blockCount;
    
    /**
     * 文件数量
     */
    private Long fileCount;
    
    /**
     * 滞后DataNode数量
     */
    private Integer staleDataNodes;
    
    /**
     * 缺失块数量
     */
    private Integer missingBlocks;
    
    // ============ Kafka专用 ============
    
    /**
     * 消息生产速率
     */
    private Double messageRate;
    
    /**
     * 消费者滞后
     */
    private Long consumerLag;
    
    /**
     * 分区数量
     */
    private Integer partitionCount;
    
    // ============ 扩展指标 ============
    
    /**
     * 其他动态指标（支持扩展）
     */
    private Map<String, Object> extraMetrics = new HashMap<>();
    
    /**
     * 获取唯一标识
     */
    public String getUniqueId() {
        StringBuilder sb = new StringBuilder();
        sb.append(cluster != null ? cluster : "default");
        sb.append(":").append(service);
        sb.append(":").append(component);
        if (instance != null) {
            sb.append(":").append(instance);
        }
        return sb.toString();
    }
    
    /**
     * 获取Redis Key
     */
    public String getRedisKey() {
        return "aiops:realtime:" + getUniqueId();
    }
    
    /**
     * 添加扩展指标
     */
    public RealtimeMetrics addExtraMetric(String key, Object value) {
        this.extraMetrics.put(key, value);
        return this;
    }
}
