package com.aiops.bigdata.service.context;

import com.aiops.bigdata.entity.common.enums.HealthStatus;
import com.aiops.bigdata.entity.context.RecentEvents;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 近期事件服务接口 - Layer 3
 * 管理近期的警告和事件
 * 建议使用Redis List存储
 */
public interface RecentEventsService {
    
    /**
     * 添加事件
     * 
     * @param cluster 集群名称
     * @param event 事件
     */
    void addEvent(String cluster, RecentEvents.ClusterEvent event);
    
    /**
     * 批量添加事件
     * 
     * @param cluster 集群名称
     * @param events 事件列表
     */
    void addEvents(String cluster, List<RecentEvents.ClusterEvent> events);
    
    /**
     * 获取近期事件
     * 
     * @param cluster 集群名称
     * @return 近期事件
     */
    Optional<RecentEvents> get(String cluster);
    
    /**
     * 获取指定数量的事件
     * 
     * @param cluster 集群名称
     * @param limit 数量限制
     * @return 事件列表
     */
    List<RecentEvents.ClusterEvent> getEvents(String cluster, int limit);
    
    /**
     * 获取未处理的事件
     * 
     * @param cluster 集群名称
     * @return 未处理事件列表
     */
    List<RecentEvents.ClusterEvent> getUnresolvedEvents(String cluster);
    
    /**
     * 获取指定严重程度的事件
     * 
     * @param cluster 集群名称
     * @param severity 严重程度
     * @return 事件列表
     */
    List<RecentEvents.ClusterEvent> getEventsBySeverity(String cluster, HealthStatus severity);
    
    /**
     * 获取指定服务的事件
     * 
     * @param cluster 集群名称
     * @param service 服务类型
     * @return 事件列表
     */
    List<RecentEvents.ClusterEvent> getEventsByService(String cluster, String service);
    
    /**
     * 获取指定时间范围的事件
     * 
     * @param cluster 集群名称
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 事件列表
     */
    List<RecentEvents.ClusterEvent> getEventsByTimeRange(String cluster, 
            LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * 标记事件为已处理
     * 
     * @param cluster 集群名称
     * @param eventId 事件ID
     * @param resolvedBy 处理人
     * @param resolution 处理说明
     * @return 是否成功
     */
    boolean resolveEvent(String cluster, String eventId, String resolvedBy, String resolution);
    
    /**
     * 获取未处理告警数量
     * 
     * @param cluster 集群名称
     * @return 未处理告警数量
     */
    int getUnresolvedCount(String cluster);
    
    /**
     * 清理过期事件
     * 
     * @param cluster 集群名称
     * @param retentionDays 保留天数
     * @return 清理的事件数
     */
    int cleanupExpired(String cluster, int retentionDays);
    
    /**
     * 删除集群的所有事件
     * 
     * @param cluster 集群名称
     */
    void deleteAll(String cluster);
}
