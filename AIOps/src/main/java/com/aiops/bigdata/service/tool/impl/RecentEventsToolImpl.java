package com.aiops.bigdata.service.tool.impl;

import com.aiops.bigdata.entity.common.enums.HealthStatus;
import com.aiops.bigdata.entity.context.RecentEvents;
import com.aiops.bigdata.service.context.RecentEventsService;
import com.aiops.bigdata.service.tool.RecentEventsTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 近期事件查询工具实现
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecentEventsToolImpl implements RecentEventsTool {
    
    private final RecentEventsService recentEventsService;
    
    @Override
    public List<RecentEvents.ClusterEvent> execute(String cluster, HealthStatus severity, 
            String service, Boolean resolved, int limit) {
        log.info("执行RecentEventsTool: cluster={}, severity={}, service={}, resolved={}, limit={}", 
            cluster, severity, service, resolved, limit);
        
        try {
            List<RecentEvents.ClusterEvent> events;
            
            if (severity != null) {
                events = recentEventsService.getEventsBySeverity(cluster, severity);
            } else if (service != null) {
                events = recentEventsService.getEventsByService(cluster, service);
            } else {
                events = recentEventsService.getEvents(cluster, limit);
            }
            
            // 过滤已处理/未处理
            if (resolved != null) {
                events = events.stream()
                    .filter(e -> resolved.equals(e.getResolved()))
                    .collect(Collectors.toList());
            }
            
            return events.stream().limit(limit).collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("查询近期事件失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    @Override
    public String getUnresolvedAlertsSummary(String cluster) {
        log.info("获取未处理告警摘要: cluster={}", cluster);
        
        try {
            List<RecentEvents.ClusterEvent> unresolved = recentEventsService.getUnresolvedEvents(cluster);
            
            if (unresolved.isEmpty()) {
                return "无未处理的告警";
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append("未处理告警摘要 (共").append(unresolved.size()).append("个):\n\n");
            
            // 按严重程度分组
            unresolved.stream()
                .collect(Collectors.groupingBy(RecentEvents.ClusterEvent::getSeverity))
                .forEach((severity, events) -> {
                    sb.append("【").append(severity.getDescription()).append("】\n");
                    events.stream().limit(5).forEach(e -> {
                        sb.append(String.format("  - %s/%s: %s\n",
                            e.getService() != null ? e.getService() : "N/A",
                            e.getComponent() != null ? e.getComponent() : "N/A",
                            e.getTitle()));
                        sb.append("    ").append(e.getDescription()).append("\n");
                        if (e.getSuggestedActions() != null && !e.getSuggestedActions().isEmpty()) {
                            sb.append("    建议: ").append(e.getSuggestedActions().get(0)).append("\n");
                        }
                    });
                    if (events.size() > 5) {
                        sb.append("  ... 还有").append(events.size() - 5).append("个\n");
                    }
                    sb.append("\n");
                });
            
            return sb.toString();
            
        } catch (Exception e) {
            log.error("获取未处理告警摘要失败: {}", e.getMessage(), e);
            return "获取告警摘要失败: " + e.getMessage();
        }
    }
    
    @Override
    public String getRecentEventsSummary(String cluster, int hours) {
        log.info("获取近期事件摘要: cluster={}, hours={}", cluster, hours);
        
        try {
            LocalDateTime startTime = LocalDateTime.now().minusHours(hours);
            LocalDateTime endTime = LocalDateTime.now();
            
            List<RecentEvents.ClusterEvent> events = recentEventsService
                .getEventsByTimeRange(cluster, startTime, endTime);
            
            if (events.isEmpty()) {
                return String.format("最近%d小时无事件记录", hours);
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("最近%d小时事件摘要 (共%d个):\n\n", hours, events.size()));
            
            // 按事件类型分组
            events.stream()
                .collect(Collectors.groupingBy(RecentEvents.ClusterEvent::getEventType))
                .forEach((type, typeEvents) -> {
                    sb.append("【").append(type).append("】\n");
                    typeEvents.stream().limit(5).forEach(e -> {
                        sb.append(String.format("  %s - %s: %s\n",
                            e.getEventTime() != null ? e.getEventTime().toString() : "N/A",
                            e.getService() != null ? e.getService() : "N/A",
                            e.getTitle()));
                    });
                    if (typeEvents.size() > 5) {
                        sb.append("  ... 还有").append(typeEvents.size() - 5).append("个\n");
                    }
                    sb.append("\n");
                });
            
            return sb.toString();
            
        } catch (Exception e) {
            log.error("获取近期事件摘要失败: {}", e.getMessage(), e);
            return "获取事件摘要失败: " + e.getMessage();
        }
    }
    
    @Override
    public String getServiceEventsSummary(String cluster, String service) {
        log.info("获取服务事件摘要: cluster={}, service={}", cluster, service);
        
        try {
            List<RecentEvents.ClusterEvent> events = recentEventsService.getEventsByService(cluster, service);
            
            if (events.isEmpty()) {
                return service + "服务无事件记录";
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append(service.toUpperCase()).append("服务事件摘要:\n\n");
            
            events.stream().limit(10).forEach(e -> {
                sb.append(String.format("[%s] %s - %s\n",
                    e.getSeverity().getDescription(),
                    e.getEventTime() != null ? e.getEventTime().toString() : "N/A",
                    e.getTitle()));
                sb.append("  ").append(e.getDescription()).append("\n");
                sb.append("  状态: ").append(Boolean.TRUE.equals(e.getResolved()) ? "已处理" : "未处理").append("\n\n");
            });
            
            return sb.toString();
            
        } catch (Exception e) {
            log.error("获取服务事件摘要失败: {}", e.getMessage(), e);
            return "获取事件摘要失败: " + e.getMessage();
        }
    }
    
    @Override
    public List<RecentEvents.ClusterEvent> getCriticalEvents(String cluster, int limit) {
        log.info("获取关键事件: cluster={}, limit={}", cluster, limit);
        
        try {
            return recentEventsService.getEventsBySeverity(cluster, HealthStatus.CRITICAL)
                .stream()
                .limit(limit)
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("获取关键事件失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
}
