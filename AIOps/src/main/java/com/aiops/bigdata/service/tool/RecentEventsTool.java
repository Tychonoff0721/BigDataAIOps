package com.aiops.bigdata.service.tool;

import com.aiops.bigdata.entity.common.enums.HealthStatus;
import com.aiops.bigdata.entity.context.RecentEvents;

import java.time.LocalDateTime;
import java.util.List;

/**
 * LLM Tool: 近期事件查询工具
 * 供大模型调用来查询近期事件和告警
 * 
 * @deprecated 请使用 {@link com.aiops.bigdata.service.ai.function.RecentEventsFunction} 替代
 *             新版本基于 Spring AI Function 实现
 */
@Deprecated
public interface RecentEventsTool {
    
    /**
     * Tool名称
     */
    String NAME = "get_recent_events";
    
    /**
     * Tool描述（供LLM理解）
     */
    String DESCRIPTION = """
        获取集群近期的告警和事件数据。
        包括未处理的告警、异常事件、故障记录等。
        这些数据用于了解集群近期发生的问题和运维事件。
        
        参数说明:
        - cluster: 集群名称（必填）
        - severity: 严重程度过滤，如 critical, warning（可选）
        - service: 服务类型过滤（可选）
        - resolved: 是否已处理，true/false（可选，默认返回未处理）
        - limit: 返回数量限制（可选，默认10）
        - hours: 最近N小时内的事件（可选，默认24小时）
        
        返回信息包括:
        - 事件时间、类型、严重程度
        - 相关组件和实例
        - 事件描述和触发指标
        - 建议操作
        """;
    
    /**
     * 查询近期事件
     * 
     * @param cluster 集群名称
     * @param severity 严重程度（可选）
     * @param service 服务类型（可选）
     * @param resolved 是否已处理（可选）
     * @param limit 数量限制
     * @return 事件列表
     */
    List<RecentEvents.ClusterEvent> execute(String cluster, HealthStatus severity, 
            String service, Boolean resolved, int limit);
    
    /**
     * 获取未处理告警摘要
     * 返回精简的数据，减少token消耗
     * 
     * @param cluster 集群名称
     * @return 未处理告警摘要
     */
    String getUnresolvedAlertsSummary(String cluster);
    
    /**
     * 获取最近N小时的事件摘要
     * 
     * @param cluster 集群名称
     * @param hours 小时数
     * @return 事件摘要
     */
    String getRecentEventsSummary(String cluster, int hours);
    
    /**
     * 获取指定服务的事件摘要
     * 
     * @param cluster 集群名称
     * @param service 服务类型
     * @return 事件摘要
     */
    String getServiceEventsSummary(String cluster, String service);
    
    /**
     * 获取关键事件（严重级别）
     * 
     * @param cluster 集群名称
     * @param limit 数量限制
     * @return 关键事件列表
     */
    List<RecentEvents.ClusterEvent> getCriticalEvents(String cluster, int limit);
}
