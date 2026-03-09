package com.aiops.bigdata.service.context.impl;

import com.aiops.bigdata.entity.common.enums.HealthStatus;
import com.aiops.bigdata.entity.context.RecentEvents;
import com.aiops.bigdata.service.context.RecentEventsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 近期事件服务实现类（Redis List存储）
 */
@Slf4j
@Service
public class RecentEventsServiceImpl implements RecentEventsService {
    
    private static final String KEY_PREFIX = "aiops:events:";
    private static final int DEFAULT_RETENTION_DAYS = 7;
    private static final int MAX_EVENTS_PER_CLUSTER = 1000;
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    @Autowired
    public RecentEventsServiceImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    @Override
    public void addEvent(String cluster, RecentEvents.ClusterEvent event) {
        String key = KEY_PREFIX + cluster;
        
        try {
            // 将事件转换为Map存储
            Map<String, Object> eventMap = convertEventToMap(event);
            
            // 使用LPUSH添加到列表头部（最新的在前）
            redisTemplate.opsForList().leftPush(key, eventMap);
            
            // 限制列表长度
            redisTemplate.opsForList().trim(key, 0, MAX_EVENTS_PER_CLUSTER - 1);
            
            // 设置过期时间
            redisTemplate.expire(key, DEFAULT_RETENTION_DAYS, TimeUnit.DAYS);
            
            log.info("添加事件到Redis: cluster={}, type={}, severity={}", 
                cluster, event.getEventType(), event.getSeverity());
            
        } catch (Exception e) {
            log.error("添加事件失败: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public void addEvents(String cluster, List<RecentEvents.ClusterEvent> events) {
        for (RecentEvents.ClusterEvent event : events) {
            addEvent(cluster, event);
        }
    }
    
    @Override
    public Optional<RecentEvents> get(String cluster) {
        String key = KEY_PREFIX + cluster;
        
        try {
            List<Object> eventMaps = redisTemplate.opsForList().range(key, 0, -1);
            
            if (eventMaps == null || eventMaps.isEmpty()) {
                return Optional.empty();
            }
            
            RecentEvents events = new RecentEvents();
            events.setCluster(cluster);
            events.setLastUpdated(LocalDateTime.now());
            
            List<RecentEvents.ClusterEvent> eventList = new ArrayList<>();
            for (Object obj : eventMaps) {
                RecentEvents.ClusterEvent event = convertMapToEvent(obj);
                if (event != null) {
                    eventList.add(event);
                }
            }
            events.setEvents(eventList);
            
            return Optional.of(events);
            
        } catch (Exception e) {
            log.error("获取事件失败: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    @Override
    public List<RecentEvents.ClusterEvent> getEvents(String cluster, int limit) {
        String key = KEY_PREFIX + cluster;
        
        try {
            List<Object> eventMaps = redisTemplate.opsForList().range(key, 0, limit - 1);
            
            if (eventMaps == null) {
                return Collections.emptyList();
            }
            
            return eventMaps.stream()
                .map(this::convertMapToEvent)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("获取事件列表失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    @Override
    public List<RecentEvents.ClusterEvent> getUnresolvedEvents(String cluster) {
        return getEvents(cluster, MAX_EVENTS_PER_CLUSTER).stream()
            .filter(e -> !Boolean.TRUE.equals(e.getResolved()))
            .collect(Collectors.toList());
    }
    
    @Override
    public List<RecentEvents.ClusterEvent> getEventsBySeverity(String cluster, HealthStatus severity) {
        return getEvents(cluster, MAX_EVENTS_PER_CLUSTER).stream()
            .filter(e -> severity.equals(e.getSeverity()))
            .collect(Collectors.toList());
    }
    
    @Override
    public List<RecentEvents.ClusterEvent> getEventsByService(String cluster, String service) {
        return getEvents(cluster, MAX_EVENTS_PER_CLUSTER).stream()
            .filter(e -> service.equals(e.getService()))
            .collect(Collectors.toList());
    }
    
    @Override
    public List<RecentEvents.ClusterEvent> getEventsByTimeRange(String cluster, 
            LocalDateTime startTime, LocalDateTime endTime) {
        return getEvents(cluster, MAX_EVENTS_PER_CLUSTER).stream()
            .filter(e -> e.getEventTime() != null)
            .filter(e -> !e.getEventTime().isBefore(startTime) && !e.getEventTime().isAfter(endTime))
            .collect(Collectors.toList());
    }
    
    @Override
    public boolean resolveEvent(String cluster, String eventId, String resolvedBy, String resolution) {
        String key = KEY_PREFIX + cluster;
        
        try {
            // 获取所有事件
            List<Object> eventMaps = redisTemplate.opsForList().range(key, 0, -1);
            if (eventMaps == null) {
                return false;
            }
            
            // 查找并更新事件
            for (int i = 0; i < eventMaps.size(); i++) {
                Object obj = eventMaps.get(i);
                RecentEvents.ClusterEvent event = convertMapToEvent(obj);
                
                if (event != null && eventId.equals(event.getEventId())) {
                    event.setResolved(true);
                    event.setResolvedBy(resolvedBy);
                    event.setResolution(resolution);
                    event.setResolvedTime(LocalDateTime.now());
                    
                    // 更新Redis中的事件
                    Map<String, Object> updatedMap = convertEventToMap(event);
                    redisTemplate.opsForList().set(key, i, updatedMap);
                    
                    log.info("事件已处理: cluster={}, eventId={}, resolvedBy={}", 
                        cluster, eventId, resolvedBy);
                    return true;
                }
            }
            
        } catch (Exception e) {
            log.error("处理事件失败: {}", e.getMessage(), e);
        }
        
        return false;
    }
    
    @Override
    public int getUnresolvedCount(String cluster) {
        return getUnresolvedEvents(cluster).size();
    }
    
    @Override
    public int cleanupExpired(String cluster, int retentionDays) {
        // Redis自动过期，这里可以手动清理过期事件
        String key = KEY_PREFIX + cluster;
        
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusDays(retentionDays);
            
            List<Object> eventMaps = redisTemplate.opsForList().range(key, 0, -1);
            if (eventMaps == null) {
                return 0;
            }
            
            int removed = 0;
            for (Object obj : eventMaps) {
                RecentEvents.ClusterEvent event = convertMapToEvent(obj);
                if (event != null && event.getEventTime() != null && 
                    event.getEventTime().isBefore(cutoffTime)) {
                    redisTemplate.opsForList().remove(key, 1, obj);
                    removed++;
                }
            }
            
            log.info("清理过期事件: cluster={}, retentionDays={}, removed={}", 
                cluster, retentionDays, removed);
            return removed;
            
        } catch (Exception e) {
            log.error("清理过期事件失败: {}", e.getMessage(), e);
            return 0;
        }
    }
    
    @Override
    public void deleteAll(String cluster) {
        String key = KEY_PREFIX + cluster;
        redisTemplate.delete(key);
        log.info("删除集群所有事件: cluster={}", cluster);
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> convertEventToMap(RecentEvents.ClusterEvent event) {
        return objectMapper.convertValue(event, Map.class);
    }
    
    @SuppressWarnings("unchecked")
    private RecentEvents.ClusterEvent convertMapToEvent(Object obj) {
        try {
            if (obj instanceof Map) {
                return objectMapper.convertValue(obj, RecentEvents.ClusterEvent.class);
            }
        } catch (Exception e) {
            log.warn("转换事件失败: {}", e.getMessage());
        }
        return null;
    }
}
