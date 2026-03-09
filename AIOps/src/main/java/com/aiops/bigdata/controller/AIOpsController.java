package com.aiops.bigdata.controller;

import com.aiops.bigdata.entity.analysis.AnalysisResult;
import com.aiops.bigdata.entity.context.LongTermStatus;
import com.aiops.bigdata.entity.context.RealtimeMetrics;
import com.aiops.bigdata.entity.context.RecentEvents;
import com.aiops.bigdata.entity.feature.ComponentMetricFeatures;
import com.aiops.bigdata.entity.feature.SparkAppFeatures;
import com.aiops.bigdata.entity.metrics.ComponentMetrics;
import com.aiops.bigdata.entity.metrics.SparkAppMetrics;
import com.aiops.bigdata.service.analysis.AIAnalysisService;
import com.aiops.bigdata.service.context.LongTermStatusService;
import com.aiops.bigdata.service.context.RealtimeMetricsService;
import com.aiops.bigdata.service.context.RecentEventsService;
import com.aiops.bigdata.service.feature.FeatureExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * AIOps REST API控制器
 */
@Slf4j
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class AIOpsController {
    
    private final AIAnalysisService analysisService;
    private final FeatureExtractor featureExtractor;
    private final RealtimeMetricsService realtimeMetricsService;
    private final LongTermStatusService longTermStatusService;
    private final RecentEventsService recentEventsService;
    
    // ============ 分析接口 ============
    
    /**
     * 分析组件健康状态
     */
    @PostMapping("/analyze/component")
    public ResponseEntity<AnalysisResult> analyzeComponent(@RequestBody ComponentMetrics metrics) {
        log.info("收到组件分析请求: service={}, component={}", metrics.getService(), metrics.getComponent());
        AnalysisResult result = analysisService.analyzeComponent(metrics);
        return ResponseEntity.ok(result);
    }
    
    /**
     * 分析Spark作业
     */
    @PostMapping("/analyze/spark")
    public ResponseEntity<AnalysisResult> analyzeSparkApp(@RequestBody SparkAppMetrics metrics) {
        log.info("收到Spark作业分析请求: jobId={}, appName={}", metrics.getJobId(), metrics.getAppName());
        AnalysisResult result = analysisService.analyzeSparkApp(metrics);
        return ResponseEntity.ok(result);
    }
    
    /**
     * 使用特征数据直接分析
     */
    @PostMapping("/analyze/features/component")
    public ResponseEntity<AnalysisResult> analyzeWithFeatures(
            @RequestBody ComponentMetricFeatures features,
            @RequestParam(required = false, defaultValue = "default") String cluster) {
        log.info("收到组件特征分析请求: service={}", features.getService());
        AnalysisResult result = analysisService.analyzeWithFeatures(features, cluster);
        return ResponseEntity.ok(result);
    }
    
    // ============ 实时指标接口 ============
    
    /**
     * 保存实时指标
     */
    @PostMapping("/metrics/realtime")
    public ResponseEntity<Map<String, Object>> saveRealtimeMetrics(@RequestBody RealtimeMetrics metrics) {
        realtimeMetricsService.save(metrics);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "保存成功",
            "uniqueId", metrics.getUniqueId()
        ));
    }
    
    /**
     * 查询实时指标
     */
    @GetMapping("/metrics/realtime")
    public ResponseEntity<List<RealtimeMetrics>> getRealtimeMetrics(
            @RequestParam String cluster,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String component) {
        
        List<RealtimeMetrics> metrics;
        if (component != null) {
            metrics = realtimeMetricsService.getByComponent(cluster, service, component);
        } else if (service != null) {
            metrics = realtimeMetricsService.getByService(cluster, service);
        } else {
            metrics = realtimeMetricsService.getByCluster(cluster);
        }
        return ResponseEntity.ok(metrics);
    }
    
    // ============ 长期状态接口 ============
    
    /**
     * 获取集群概览
     */
    @GetMapping("/status/overview")
    public ResponseEntity<Map<String, Object>> getClusterOverview(
            @RequestParam(required = false, defaultValue = "bigdata-prod") String cluster) {
        
        Map<String, Object> result = new HashMap<>();
        
        longTermStatusService.getLatest(cluster).ifPresent(status -> {
            result.put("cluster", status);
        });
        
        if (result.isEmpty()) {
            result.put("message", "未找到集群状态数据");
        }
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 获取所有集群列表
     */
    @GetMapping("/status/clusters")
    public ResponseEntity<List<LongTermStatus>> getAllClusters() {
        return ResponseEntity.ok(longTermStatusService.getAllClustersLatest());
    }
    
    // ============ 事件接口 ============
    
    /**
     * 获取未处理告警
     */
    @GetMapping("/events/alerts")
    public ResponseEntity<Map<String, Object>> getUnresolvedAlerts(
            @RequestParam(required = false, defaultValue = "bigdata-prod") String cluster) {
        
        Map<String, Object> result = new HashMap<>();
        result.put("cluster", cluster);
        result.put("unresolvedCount", recentEventsService.getUnresolvedCount(cluster));
        result.put("alerts", recentEventsService.getUnresolvedEvents(cluster));
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 添加测试事件
     */
    @PostMapping("/events")
    public ResponseEntity<Map<String, Object>> addEvent(
            @RequestParam String cluster,
            @RequestBody RecentEvents.ClusterEvent event) {
        
        recentEventsService.addEvent(cluster, event);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "事件添加成功"
        ));
    }
    
    // ============ 测试接口 ============
    
    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "timestamp", LocalDateTime.now(),
            "service", "AIOps Server"
        ));
    }
    
    /**
     * 生成测试数据
     */
    @PostMapping("/test/generate-data")
    public ResponseEntity<Map<String, Object>> generateTestData(
            @RequestParam(required = false, defaultValue = "bigdata-prod") String cluster) {
        
        // 生成测试实时指标
        RealtimeMetrics metrics = new RealtimeMetrics();
        metrics.setCluster(cluster);
        metrics.setService("hdfs");
        metrics.setComponent("namenode");
        metrics.setInstance("nn1");
        metrics.setCpuUsage(0.75 + Math.random() * 0.2);
        metrics.setMemoryUsage(0.80 + Math.random() * 0.15);
        metrics.setGcTime((long)(1000 + Math.random() * 4000));
        metrics.setConnectionCount((int)(100 + Math.random() * 200));
        metrics.setTimestamp(System.currentTimeMillis() / 1000);
        realtimeMetricsService.save(metrics);
        
        // 生成测试事件
        RecentEvents.ClusterEvent event = new RecentEvents.ClusterEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setEventType("alert");
        event.setSeverity(Math.random() > 0.7 ? 
            com.aiops.bigdata.entity.common.enums.HealthStatus.CRITICAL : 
            com.aiops.bigdata.entity.common.enums.HealthStatus.WARNING);
        event.setTitle("测试告警");
        event.setDescription("这是一个测试告警事件");
        event.setService("hdfs");
        event.setComponent("namenode");
        event.setEventTime(LocalDateTime.now());
        event.setResolved(false);
        recentEventsService.addEvent(cluster, event);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "测试数据生成成功",
            "generated", Map.of(
                "metrics", metrics.getUniqueId(),
                "event", event.getEventId()
            )
        ));
    }
}
