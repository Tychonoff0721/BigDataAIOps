package com.aiops.bigdata.entity.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 长期状态实体（MySQL存储）
 * 存储集群的长期状态数据，按天更新
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "long_term_status", indexes = {
    @Index(name = "idx_cluster_date", columnList = "cluster_name, status_date", unique = true),
    @Index(name = "idx_status_date", columnList = "status_date")
})
public class LongTermStatusEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 集群名称
     */
    @Column(name = "cluster_name", nullable = false, length = 100)
    private String clusterName;
    
    /**
     * 状态日期
     */
    @Column(name = "status_date", nullable = false)
    private LocalDate statusDate;
    
    // ============ 基本信息 ============
    
    /**
     * 总节点数
     */
    @Column(name = "total_nodes")
    private Integer totalNodes;
    
    /**
     * 活跃节点数
     */
    @Column(name = "active_nodes")
    private Integer activeNodes;
    
    /**
     * 总CPU核心数
     */
    @Column(name = "total_cpu_cores")
    private Integer totalCpuCores;
    
    /**
     * 总内存(GB)
     */
    @Column(name = "total_memory_gb")
    private Long totalMemoryGB;
    
    // ============ HDFS状态 ============
    
    /**
     * HDFS容量(TB)
     */
    @Column(name = "hdfs_capacity_tb")
    private Long hdfsCapacityTB;
    
    /**
     * HDFS已使用(TB)
     */
    @Column(name = "hdfs_used_tb")
    private Long hdfsUsedTB;
    
    /**
     * HDFS使用率
     */
    @Column(name = "hdfs_usage_rate")
    private Double hdfsUsageRate;
    
    /**
     * HDFS文件数
     */
    @Column(name = "hdfs_file_count")
    private Long hdfsFileCount;
    
    /**
     * HDFS块数
     */
    @Column(name = "hdfs_block_count")
    private Long hdfsBlockCount;
    
    /**
     * NameNode数量
     */
    @Column(name = "namenode_count")
    private Integer nameNodeCount;
    
    /**
     * DataNode数量
     */
    @Column(name = "datanode_count")
    private Integer dataNodeCount;
    
    // ============ YARN状态 ============
    
    /**
     * ResourceManager数量
     */
    @Column(name = "rm_count")
    private Integer rmCount;
    
    /**
     * NodeManager数量
     */
    @Column(name = "nm_count")
    private Integer nmCount;
    
    /**
     * YARN总内存(GB)
     */
    @Column(name = "yarn_total_memory_gb")
    private Long yarnTotalMemoryGB;
    
    /**
     * YARN总vCores
     */
    @Column(name = "yarn_total_vcores")
    private Integer yarnTotalVcores;
    
    // ============ Kafka状态 ============
    
    /**
     * Kafka Broker数量
     */
    @Column(name = "kafka_broker_count")
    private Integer kafkaBrokerCount;
    
    /**
     * Kafka Topic数量
     */
    @Column(name = "kafka_topic_count")
    private Integer kafkaTopicCount;
    
    /**
     * Kafka分区数
     */
    @Column(name = "kafka_partition_count")
    private Integer kafkaPartitionCount;
    
    // ============ 统计信息 ============
    
    /**
     * 今日告警数
     */
    @Column(name = "alert_count_today")
    private Integer alertCountToday;
    
    /**
     * 今日异常数
     */
    @Column(name = "anomaly_count_today")
    private Integer anomalyCountToday;
    
    /**
     * 平均集群负载
     */
    @Column(name = "avg_cluster_load")
    private Double avgClusterLoad;
    
    // ============ 其他信息 ============
    
    /**
     * 组件版本(JSON格式)
     */
    @Column(name = "component_versions", columnDefinition = "TEXT")
    private String componentVersionsJson;
    
    /**
     * 更新时间
     */
    @Column(name = "update_time")
    private LocalDateTime updateTime;
    
    /**
     * 从领域对象转换
     */
    public static LongTermStatusEntity fromDomain(LongTermStatus status) {
        LongTermStatusEntity entity = new LongTermStatusEntity();
        entity.setClusterName(status.getCluster());
        entity.setStatusDate(status.getStatusDate());
        entity.setTotalNodes(status.getTotalNodes());
        entity.setActiveNodes(status.getActiveNodes());
        entity.setTotalCpuCores(status.getTotalCpuCores());
        entity.setTotalMemoryGB(status.getTotalMemoryGB());
        entity.setHdfsCapacityTB(status.getHdfsCapacityTB());
        entity.setHdfsUsedTB(status.getHdfsUsedTB());
        entity.setHdfsUsageRate(status.getHdfsUsageRate());
        entity.setHdfsFileCount(status.getHdfsFileCount());
        entity.setHdfsBlockCount(status.getHdfsBlockCount());
        entity.setNameNodeCount(status.getNameNodeCount());
        entity.setDataNodeCount(status.getDataNodeCount());
        entity.setRmCount(status.getRmCount());
        entity.setNmCount(status.getNmCount());
        entity.setYarnTotalMemoryGB(status.getYarnTotalMemoryGB());
        entity.setYarnTotalVcores(status.getYarnTotalVcores());
        entity.setKafkaBrokerCount(status.getKafkaBrokerCount());
        entity.setKafkaTopicCount(status.getKafkaTopicCount());
        entity.setKafkaPartitionCount(status.getKafkaPartitionCount());
        entity.setAlertCountToday(status.getAlertCountToday());
        entity.setAnomalyCountToday(status.getAnomalyCountToday());
        entity.setAvgClusterLoad(status.getAvgClusterLoad());
        entity.setUpdateTime(status.getUpdateTime());
        
        // 转换组件版本Map为JSON
        if (status.getComponentVersions() != null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                entity.setComponentVersionsJson(mapper.writeValueAsString(status.getComponentVersions()));
            } catch (Exception e) {
                entity.setComponentVersionsJson("{}");
            }
        }
        
        return entity;
    }
    
    /**
     * 转换为领域对象
     */
    public LongTermStatus toDomain() {
        LongTermStatus status = new LongTermStatus();
        status.setCluster(this.clusterName);
        status.setStatusDate(this.statusDate);
        status.setTotalNodes(this.totalNodes);
        status.setActiveNodes(this.activeNodes);
        status.setTotalCpuCores(this.totalCpuCores);
        status.setTotalMemoryGB(this.totalMemoryGB);
        status.setHdfsCapacityTB(this.hdfsCapacityTB);
        status.setHdfsUsedTB(this.hdfsUsedTB);
        status.setHdfsUsageRate(this.hdfsUsageRate);
        status.setHdfsFileCount(this.hdfsFileCount);
        status.setHdfsBlockCount(this.hdfsBlockCount);
        status.setNameNodeCount(this.nameNodeCount);
        status.setDataNodeCount(this.dataNodeCount);
        status.setRmCount(this.rmCount);
        status.setNmCount(this.nmCount);
        status.setYarnTotalMemoryGB(this.yarnTotalMemoryGB);
        status.setYarnTotalVcores(this.yarnTotalVcores);
        status.setKafkaBrokerCount(this.kafkaBrokerCount);
        status.setKafkaTopicCount(this.kafkaTopicCount);
        status.setKafkaPartitionCount(this.kafkaPartitionCount);
        status.setAlertCountToday(this.alertCountToday);
        status.setAnomalyCountToday(this.anomalyCountToday);
        status.setAvgClusterLoad(this.avgClusterLoad);
        status.setUpdateTime(this.updateTime);
        
        // 解析组件版本JSON
        if (this.componentVersionsJson != null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                @SuppressWarnings("unchecked")
                Map<String, String> versions = mapper.readValue(this.componentVersionsJson, Map.class);
                status.setComponentVersions(versions);
            } catch (Exception e) {
                status.setComponentVersions(new HashMap<>());
            }
        }
        
        return status;
    }
}
