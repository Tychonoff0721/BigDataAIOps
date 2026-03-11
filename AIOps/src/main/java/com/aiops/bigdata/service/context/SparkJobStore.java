package com.aiops.bigdata.service.context;

import com.aiops.bigdata.entity.metrics.SparkAppMetrics;

import java.util.List;
import java.util.Optional;

/**
 * Spark作业存储服务
 * 将Spark作业信息存储到Redis，供LLM通过Tool查看分析
 */
public interface SparkJobStore {
    
    /**
     * Redis Key前缀
     */
    String KEY_PREFIX = "aiops:spark:job:";
    
    /**
     * 保存Spark作业信息
     * 
     * @param metrics Spark作业指标
     * @param retentionHours 数据保留时长（小时）
     * @return 存储ID（jobId）
     */
    String saveJob(SparkAppMetrics metrics, int retentionHours);
    
    /**
     * 批量保存Spark作业信息
     * 
     * @param metricsList Spark作业列表
     * @param retentionHours 数据保留时长（小时）
     */
    void saveJobs(List<SparkAppMetrics> metricsList, int retentionHours);
    
    /**
     * 获取Spark作业信息
     * 
     * @param jobId 作业ID
     * @return Spark作业指标
     */
    Optional<SparkAppMetrics> getJob(String jobId);
    
    /**
     * 获取集群的所有Spark作业
     * 
     * @param cluster 集群名称
     * @return Spark作业列表
     */
    List<SparkAppMetrics> getJobsByCluster(String cluster);
    
    /**
     * 获取最近的Spark作业
     * 
     * @param cluster 集群名称
     * @param limit 数量限制
     * @return Spark作业列表
     */
    List<SparkAppMetrics> getRecentJobs(String cluster, int limit);
    
    /**
     * 获取Spark作业摘要（供LLM理解作业概况）
     * 
     * @param jobId 作业ID
     * @return 作业摘要文本
     */
    String getJobSummary(String jobId);
    
    /**
     * 获取Stage详情
     * 
     * @param jobId 作业ID
     * @return Stage详情文本
     */
    String getStageDetails(String jobId);
    
    /**
     * 获取Executor详情
     * 
     * @param jobId 作业ID
     * @return Executor详情文本
     */
    String getExecutorDetails(String jobId);
    
    /**
     * 获取作业瓶颈分析
     * 
     * @param jobId 作业ID
     * @return 瓶颈分析文本
     */
    String getBottleneckAnalysis(String jobId);
    
    /**
     * 删除Spark作业
     * 
     * @param jobId 作业ID
     */
    void deleteJob(String jobId);
    
    /**
     * 检查作业是否存在
     * 
     * @param jobId 作业ID
     * @return 是否存在
     */
    boolean exists(String jobId);
    
    /**
     * 获取存储Key
     * 
     * @param jobId 作业ID
     * @return Redis Key
     */
    String getKey(String jobId);
}
