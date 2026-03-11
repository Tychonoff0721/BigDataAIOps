package com.aiops.bigdata.service.context;

import com.aiops.bigdata.entity.metrics.ComponentMetrics;

import java.util.List;
import java.util.Optional;

/**
 * 指标时序数据存储服务
 * 将组件指标的历史时序数据存储到Redis，供LLM通过Tool查看
 */
public interface MetricsTimeSeriesStore {
    
    /**
     * Redis Key前缀
     */
    String KEY_PREFIX = "aiops:timeseries:";
    
    /**
     * 保存指标时序数据
     * 将一批时序数据存储到Redis，按唯一标识组织
     * 
     * @param metricsList 按时间排序的指标列表
     * @param retentionHours 数据保留时长（小时）
     * @return 存储的唯一标识
     */
    String saveTimeSeries(List<ComponentMetrics> metricsList, int retentionHours);
    
    /**
     * 追加指标数据到现有时序
     * 
     * @param storageId 存储ID
     * @param metrics 新的指标数据
     */
    void appendMetrics(String storageId, ComponentMetrics metrics);
    
    /**
     * 获取时序数据
     * 
     * @param storageId 存储ID
     * @return 时序数据列表
     */
    Optional<List<ComponentMetrics>> getTimeSeries(String storageId);
    
    /**
     * 获取时序数据（最近N分钟）
     * 
     * @param storageId 存储ID
     * @param minutes 分钟数
     * @return 时序数据列表
     */
    List<ComponentMetrics> getRecentMetrics(String storageId, int minutes);
    
    /**
     * 根据集群、服务、组件获取存储ID
     * 
     * @param cluster 集群名称
     * @param service 服务类型
     * @param component 组件类型
     * @param instance 实例标识（可选）
     * @return 存储ID
     */
    String getStorageId(String cluster, String service, String component, String instance);
    
    /**
     * 获取时序数据的统计摘要（供LLM理解数据概况）
     * 
     * @param storageId 存储ID
     * @return 数据摘要文本
     */
    String getTimeSeriesSummary(String storageId);
    
    /**
     * 获取指定指标的变化趋势描述
     * 
     * @param storageId 存储ID
     * @param metricName 指标名称
     * @return 趋势描述文本
     */
    String getMetricTrend(String storageId, String metricName);
    
    /**
     * 删除时序数据
     * 
     * @param storageId 存储ID
     */
    void delete(String storageId);
    
    /**
     * 检查时序数据是否存在
     * 
     * @param storageId 存储ID
     * @return 是否存在
     */
    boolean exists(String storageId);
    
    /**
     * 获取时序数据点数量
     * 
     * @param storageId 存储ID
     * @return 数据点数量
     */
    int getDataPointCount(String storageId);
}
