package com.aiops.bigdata.entity.context;

import com.aiops.bigdata.entity.common.enums.HealthStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 近期事件 - Layer 3
 * 存储近期的警告和事件
 * 建议存储：Redis List（设置过期时间，如7天）
 */
@Data
public class RecentEvents {
    
    /**
     * 集群名称
     */
    private String cluster;
    
    /**
     * 最后更新时间
     */
    private LocalDateTime lastUpdated;
    
    /**
     * 事件列表（按时间倒序）
     */
    private List<ClusterEvent> events = new ArrayList<>();
    
    /**
     * 未处理告警数量
     */
    private Integer unresolvedAlertCount = 0;
    
    /**
     * 最近24小时告警数量
     */
    private Integer alertCount24h = 0;
    
    /**
     * 最近24小时异常数量
     */
    private Integer anomalyCount24h = 0;
    
    /**
     * 集群事件
     */
    @Data
    public static class ClusterEvent {
        
        /**
         * 事件ID
         */
        private String eventId;
        
        /**
         * 事件时间
         */
        private LocalDateTime eventTime;
        
        /**
         * 事件类型
         * - alert: 告警
         * - anomaly: 异常
         * - failure: 故障
         * - recovery: 恢复
         * - maintenance: 维护
         * - config_change: 配置变更
         */
        private String eventType;
        
        /**
         * 严重程度
         */
        private HealthStatus severity;
        
        /**
         * 相关服务
         */
        private String service;
        
        /**
         * 相关组件
         */
        private String component;
        
        /**
         * 相关实例
         */
        private String instance;
        
        /**
         * 事件标题
         */
        private String title;
        
        /**
         * 事件描述
         */
        private String description;
        
        /**
         * 触发指标
         */
        private String triggerMetric;
        
        /**
         * 指标值
         */
        private String metricValue;
        
        /**
         * 阈值
         */
        private String threshold;
        
        /**
         * 是否已处理
         */
        private Boolean resolved = false;
        
        /**
         * 处理时间
         */
        private LocalDateTime resolvedTime;
        
        /**
         * 处理人
         */
        private String resolvedBy;
        
        /**
         * 处理说明
         */
        private String resolution;
        
        /**
         * 建议操作
         */
        private List<String> suggestedActions = new ArrayList<>();
        
        /**
         * 扩展属性
         */
        private Map<String, Object> properties = new HashMap<>();
        
        /**
         * 获取Redis Key
         */
        public String getRedisKey(String cluster) {
            return "aiops:events:" + cluster + ":" + eventId;
        }
    }
    
    /**
     * 获取Redis Key
     */
    public String getRedisKey() {
        return "aiops:events:" + cluster;
    }
    
    /**
     * 添加事件
     */
    public void addEvent(ClusterEvent event) {
        this.events.add(0, event); // 添加到头部
        this.lastUpdated = LocalDateTime.now();
        
        // 更新计数
        if (!Boolean.TRUE.equals(event.getResolved())) {
            unresolvedAlertCount++;
        }
    }
    
    /**
     * 获取未处理事件
     */
    public List<ClusterEvent> getUnresolvedEvents() {
        List<ClusterEvent> unresolved = new ArrayList<>();
        for (ClusterEvent event : events) {
            if (!Boolean.TRUE.equals(event.getResolved())) {
                unresolved.add(event);
            }
        }
        return unresolved;
    }
    
    /**
     * 获取指定严重程度的事件
     */
    public List<ClusterEvent> getEventsBySeverity(HealthStatus severity) {
        List<ClusterEvent> result = new ArrayList<>();
        for (ClusterEvent event : events) {
            if (event.getSeverity() == severity) {
                result.add(event);
            }
        }
        return result;
    }
    
    /**
     * 获取指定服务的事件
     */
    public List<ClusterEvent> getEventsByService(String service) {
        List<ClusterEvent> result = new ArrayList<>();
        for (ClusterEvent event : events) {
            if (service.equals(event.getService())) {
                result.add(event);
            }
        }
        return result;
    }
    
    /**
     * 清理过期事件
     */
    public void cleanupExpiredEvents(int retentionDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        events.removeIf(event -> 
            event.getEventTime() != null && event.getEventTime().isBefore(cutoff));
    }
    
    /**
     * 标记事件为已处理
     */
    public boolean resolveEvent(String eventId, String resolvedBy, String resolution) {
        for (ClusterEvent event : events) {
            if (eventId.equals(event.getEventId())) {
                event.setResolved(true);
                event.setResolvedTime(LocalDateTime.now());
                event.setResolvedBy(resolvedBy);
                event.setResolution(resolution);
                unresolvedAlertCount = Math.max(0, unresolvedAlertCount - 1);
                return true;
            }
        }
        return false;
    }
}
