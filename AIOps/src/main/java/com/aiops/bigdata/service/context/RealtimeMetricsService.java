package com.aiops.bigdata.service.context;

import com.aiops.bigdata.entity.context.RealtimeMetrics;

import java.util.List;
import java.util.Optional;

/**
 * 实时指标服务接口 - Layer 1
 * 管理秒级/分钟级变化的实时数据
 * 建议使用Redis存储
 */
public interface RealtimeMetricsService {
    
    /**
     * 保存实时指标
     * 
     * @param metrics 实时指标数据
     */
    void save(RealtimeMetrics metrics);
    
    /**
     * 批量保存实时指标
     * 
     * @param metricsList 实时指标列表
     */
    void saveBatch(List<RealtimeMetrics> metricsList);
    
    /**
     * 获取实时指标
     * 
     * @param cluster 集群名称
     * @param service 服务类型
     * @param component 组件类型
     * @param instance 实例标识（可选）
     * @return 实时指标
     */
    Optional<RealtimeMetrics> get(String cluster, String service, String component, String instance);
    
    /**
     * 获取集群所有组件的实时指标
     * 
     * @param cluster 集群名称
     * @return 实时指标列表
     */
    List<RealtimeMetrics> getByCluster(String cluster);
    
    /**
     * 获取指定服务的所有实时指标
     * 
     * @param cluster 集群名称
     * @param service 服务类型
     * @return 实时指标列表
     */
    List<RealtimeMetrics> getByService(String cluster, String service);
    
    /**
     * 获取指定组件的所有实例实时指标
     * 
     * @param cluster 集群名称
     * @param service 服务类型
     * @param component 组件类型
     * @return 实时指标列表
     */
    List<RealtimeMetrics> getByComponent(String cluster, String service, String component);
    
    /**
     * 获取历史实时指标（最近N分钟）
     * 
     * @param cluster 集群名称
     * @param service 服务类型
     * @param component 组件类型
     * @param instance 实例标识
     * @param minutes 分钟数
     * @return 历史实时指标列表
     */
    List<RealtimeMetrics> getHistory(String cluster, String service, String component, 
                                      String instance, int minutes);
    
    /**
     * 删除实时指标
     * 
     * @param cluster 集群名称
     * @param service 服务类型
     * @param component 组件类型
     * @param instance 实例标识
     */
    void delete(String cluster, String service, String component, String instance);
    
    /**
     * 清理过期数据
     * 
     * @param retentionMinutes 保留分钟数
     * @return 清理的记录数
     */
    int cleanupExpired(int retentionMinutes);
}
