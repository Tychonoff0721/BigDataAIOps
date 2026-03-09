package com.aiops.bigdata.service;

import com.aiops.bigdata.entity.metrics.ComponentMetrics;
import com.aiops.bigdata.entity.metrics.SparkAppMetrics;

import java.util.List;

/**
 * 指标收集服务接口
 * 负责接收和存储各类指标数据
 */
public interface MetricsCollectorService {
    
    /**
     * 收集组件指标
     * 
     * @param metrics 组件指标数据
     * @return 是否成功
     */
    boolean collectComponentMetrics(ComponentMetrics metrics);
    
    /**
     * 批量收集组件指标
     * 
     * @param metricsList 组件指标列表
     * @return 成功收集的数量
     */
    int collectComponentMetricsBatch(List<ComponentMetrics> metricsList);
    
    /**
     * 收集Spark作业指标
     * 
     * @param metrics Spark作业指标数据
     * @return 是否成功
     */
    boolean collectSparkAppMetrics(SparkAppMetrics metrics);
    
    /**
     * 批量收集Spark作业指标
     * 
     * @param metricsList Spark作业指标列表
     * @return 成功收集的数量
     */
    int collectSparkAppMetricsBatch(List<SparkAppMetrics> metricsList);
    
    /**
     * 查询组件历史指标
     * 
     * @param cluster 集群名称
     * @param service 服务类型
     * @param component 组件类型
     * @param startTime 开始时间戳
     * @param endTime 结束时间戳
     * @return 历史指标列表
     */
    List<ComponentMetrics> queryComponentMetrics(String cluster, String service, 
                                                  String component, Long startTime, Long endTime);
    
    /**
     * 查询Spark作业历史指标
     * 
     * @param cluster 集群名称
     * @param jobId 作业ID（可选）
     * @param appName 应用名称（可选）
     * @param startTime 开始时间戳
     * @param endTime 结束时间戳
     * @return 历史作业指标列表
     */
    List<SparkAppMetrics> querySparkAppMetrics(String cluster, String jobId, 
                                                String appName, Long startTime, Long endTime);
    
    /**
     * 获取组件最新指标
     * 
     * @param uniqueId 组件唯一标识
     * @return 最新指标
     */
    ComponentMetrics getLatestComponentMetrics(String uniqueId);
    
    /**
     * 删除过期指标数据
     * 
     * @param retentionDays 保留天数
     * @return 删除的记录数
     */
    int cleanupExpiredMetrics(int retentionDays);
}
