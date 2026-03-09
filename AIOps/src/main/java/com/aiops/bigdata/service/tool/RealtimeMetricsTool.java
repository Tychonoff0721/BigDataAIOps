package com.aiops.bigdata.service.tool;

import com.aiops.bigdata.entity.context.RealtimeMetrics;

import java.util.List;

/**
 * LLM Tool: 实时指标查询工具
 * 供大模型调用来查询实时指标数据
 */
public interface RealtimeMetricsTool {
    
    /**
     * Tool名称
     */
    String NAME = "get_realtime_metrics";
    
    /**
     * Tool描述（供LLM理解）
     */
    String DESCRIPTION = """
        获取集群组件的实时指标数据。
        包括CPU使用率、内存使用率、GC时间等实时监控数据。
        这些数据变化频繁（秒级/分钟级），反映组件当前状态。
        
        参数说明:
        - cluster: 集群名称（必填）
        - service: 服务类型，如 hdfs, yarn, spark, kafka 等（必填）
        - component: 组件类型，如 namenode, datanode, resourcemanager 等（可选，不填则返回该服务所有组件）
        - instance: 实例标识（可选，不填则返回所有实例）
        """;
    
    /**
     * 查询实时指标
     * 
     * @param cluster 集群名称
     * @param service 服务类型
     * @param component 组件类型（可选）
     * @param instance 实例标识（可选）
     * @return 实时指标列表
     */
    List<RealtimeMetrics> execute(String cluster, String service, String component, String instance);
    
    /**
     * 获取集群所有组件的实时指标摘要
     * 返回精简的数据，减少token消耗
     * 
     * @param cluster 集群名称
     * @return 精简的实时指标摘要
     */
    String getClusterMetricsSummary(String cluster);
    
    /**
     * 获取指定组件的健康状态摘要
     * 
     * @param cluster 集群名称
     * @param service 服务类型
     * @param component 组件类型
     * @return 健康状态摘要
     */
    String getComponentHealthSummary(String cluster, String service, String component);
}
