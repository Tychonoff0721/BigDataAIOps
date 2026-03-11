package com.aiops.bigdata.controller;

import com.aiops.bigdata.entity.analysis.AnalysisResult;
import com.aiops.bigdata.entity.context.LongTermStatus;
import com.aiops.bigdata.entity.context.RealtimeMetrics;
import com.aiops.bigdata.entity.context.RecentEvents;
import com.aiops.bigdata.entity.metrics.ComponentMetrics;
import com.aiops.bigdata.entity.metrics.SparkAppMetrics;
import com.aiops.bigdata.service.ai.AIAnalysisService;
import com.aiops.bigdata.service.context.LongTermStatusService;
import com.aiops.bigdata.service.context.MetricsTimeSeriesStore;
import com.aiops.bigdata.service.context.RealtimeMetricsService;
import com.aiops.bigdata.service.context.RecentEventsService;
import com.aiops.bigdata.service.context.SparkJobStore;
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
    private final RealtimeMetricsService realtimeMetricsService;
    private final LongTermStatusService longTermStatusService;
    private final RecentEventsService recentEventsService;
    private final MetricsTimeSeriesStore metricsTimeSeriesStore;
    private final SparkJobStore sparkJobStore;
    
    // ============ 新版分析接口（基于Redis存储） ============
    
    // ============ 一体化分析接口（存储+分析一步完成） ============
    
    /**
     * 一体化时序数据分析接口（REST形式）
     * 接收时序数据列表，内部完成存储和分析，一步返回结果
     * 
     * @param metricsList 时序数据列表
     * @param cluster 集群名称
     * @param retentionHours 数据保留时间（小时）
     * @return 分析结果
     */
    @PostMapping("/analyze/timeseries/direct")
    public ResponseEntity<?> analyzeTimeSeriesDirect(
            @RequestBody List<ComponentMetrics> metricsList,
            @RequestParam(required = false, defaultValue = "bigdata-prod") String cluster,
            @RequestParam(required = false, defaultValue = "24") int retentionHours) {
        
        if (metricsList == null || metricsList.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "时序数据不能为空"
            ));
        }
        
        log.info("收到一体化时序分析请求: 数据点数={}, cluster={}", metricsList.size(), cluster);
        
        // 步骤1: 存储时序数据
        String storageId = metricsTimeSeriesStore.saveTimeSeries(metricsList, retentionHours);
        if (storageId == null) {
            AnalysisResult errorResult = new AnalysisResult();
            errorResult.setResultId(UUID.randomUUID().toString());
            errorResult.setAnalysisTime(LocalDateTime.now());
            AnalysisResult.Diagnosis diagnosis = new AnalysisResult.Diagnosis();
            diagnosis.setSummary("存储时序数据失败");
            errorResult.setDiagnosis(diagnosis);
            return ResponseEntity.internalServerError().body(errorResult);
        }
        
        log.info("时序数据已存储: storageId={}", storageId);
        
        // 步骤2: 分析
        AnalysisResult result = analysisService.analyzeTimeSeries(storageId, cluster);
        
        // 在结果中添加存储信息
        result.setTargetId(storageId);
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 一体化Spark作业分析接口（REST形式）
     * 接收Spark作业数据，内部完成存储和分析，一步返回结果
     */
    @PostMapping("/analyze/spark/direct")
    public ResponseEntity<?> analyzeSparkDirect(
            @RequestBody SparkAppMetrics metrics,
            @RequestParam(required = false, defaultValue = "bigdata-prod") String cluster,
            @RequestParam(required = false, defaultValue = "24") int retentionHours) {
        
        if (metrics == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Spark作业数据不能为空"
            ));
        }
        
        // 自动生成jobId如果未提供
        if (metrics.getJobId() == null || metrics.getJobId().isEmpty()) {
            metrics.setJobId("job-" + System.currentTimeMillis());
        }
        
        log.info("收到一体化Spark分析请求: jobId={}, appName={}", metrics.getJobId(), metrics.getAppName());
        
        // 步骤1: 存储作业数据
        String jobId = sparkJobStore.saveJob(metrics, retentionHours);
        if (jobId == null) {
            AnalysisResult errorResult = new AnalysisResult();
            errorResult.setResultId(UUID.randomUUID().toString());
            errorResult.setAnalysisTime(LocalDateTime.now());
            errorResult.setAnalysisType("spark_app");
            AnalysisResult.Diagnosis diagnosis = new AnalysisResult.Diagnosis();
            diagnosis.setSummary("存储Spark作业数据失败");
            errorResult.setDiagnosis(diagnosis);
            return ResponseEntity.internalServerError().body(errorResult);
        }
        
        log.info("Spark作业数据已存储: jobId={}", jobId);
        
        // 步骤2: 分析
        AnalysisResult result = analysisService.analyzeSparkJob(jobId, cluster);
        result.setTargetId(jobId);
        
        return ResponseEntity.ok(result);
    }
    
    // ============ 原有分步接口（保留作为底层支持） ============
    
    /**
     * 保存组件指标时序数据
     * 将时序数据存储到Redis，返回storageId供后续分析使用
     */
    @PostMapping("/metrics/timeseries")
    public ResponseEntity<Map<String, Object>> saveTimeSeriesMetrics(
            @RequestBody List<ComponentMetrics> metricsList,
            @RequestParam(required = false, defaultValue = "24") int retentionHours) {
        
        if (metricsList == null || metricsList.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "时序数据不能为空"
            ));
        }
        
        log.info("收到时序数据存储请求: 数据点数={}, retentionHours={}", metricsList.size(), retentionHours);
        
        String storageId = metricsTimeSeriesStore.saveTimeSeries(metricsList, retentionHours);
        
        if (storageId == null) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "存储时序数据失败"
            ));
        }
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "时序数据存储成功",
            "storage_id", storageId,
            "data_points", metricsList.size(),
            "retention_hours", retentionHours
        ));
    }
    
    /**
     * 分析时序数据
     * 根据storageId从Redis获取时序数据，让LLM通过Tool自行查看分析
     */
    @PostMapping("/analyze/timeseries")
    public ResponseEntity<AnalysisResult> analyzeTimeSeries(
            @RequestParam String storageId,
            @RequestParam(required = false, defaultValue = "default") String cluster) {
        
        log.info("收到时序数据分析请求: storageId={}", storageId);
        
        if (!metricsTimeSeriesStore.exists(storageId)) {
            AnalysisResult errorResult = new AnalysisResult();
            errorResult.setResultId(UUID.randomUUID().toString());
            errorResult.setAnalysisTime(LocalDateTime.now());
            AnalysisResult.Diagnosis diagnosis = new AnalysisResult.Diagnosis();
            diagnosis.setSummary("时序数据不存在: " + storageId);
            errorResult.setDiagnosis(diagnosis);
            return ResponseEntity.badRequest().body(errorResult);
        }
        
        AnalysisResult result = analysisService.analyzeTimeSeries(storageId, cluster);
        return ResponseEntity.ok(result);
    }
    
    /**
     * 保存Spark作业信息
     * 将作业信息存储到Redis，返回jobId供后续分析使用
     */
    @PostMapping("/spark/job")
    public ResponseEntity<Map<String, Object>> saveSparkJob(
            @RequestBody SparkAppMetrics metrics,
            @RequestParam(required = false, defaultValue = "24") int retentionHours) {
        
        if (metrics == null || metrics.getJobId() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Spark作业数据无效，jobId不能为空"
            ));
        }
        
        log.info("收到Spark作业存储请求: jobId={}, appName={}", metrics.getJobId(), metrics.getAppName());
        
        String jobId = sparkJobStore.saveJob(metrics, retentionHours);
        
        if (jobId == null) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "存储Spark作业失败"
            ));
        }
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Spark作业存储成功",
            "job_id", jobId,
            "retention_hours", retentionHours
        ));
    }
    
    /**
     * 分析Spark作业
     * 根据jobId从Redis获取作业信息，让LLM通过Tool自行查看分析
     */
    @PostMapping("/analyze/spark/{jobId}")
    public ResponseEntity<AnalysisResult> analyzeSparkJob(
            @PathVariable String jobId,
            @RequestParam(required = false, defaultValue = "default") String cluster) {
        
        log.info("收到Spark作业分析请求: jobId={}", jobId);
        
        if (!sparkJobStore.exists(jobId)) {
            AnalysisResult errorResult = new AnalysisResult();
            errorResult.setResultId(UUID.randomUUID().toString());
            errorResult.setAnalysisTime(LocalDateTime.now());
            errorResult.setAnalysisType("spark_app");
            AnalysisResult.Diagnosis diagnosis = new AnalysisResult.Diagnosis();
            diagnosis.setSummary("Spark作业不存在: " + jobId);
            errorResult.setDiagnosis(diagnosis);
            return ResponseEntity.badRequest().body(errorResult);
        }
        
        AnalysisResult result = analysisService.analyzeSparkJob(jobId, cluster);
        return ResponseEntity.ok(result);
    }
    
    /**
     * 分析集群整体状态
     */
    @GetMapping("/analyze/cluster")
    public ResponseEntity<AnalysisResult> analyzeCluster(
            @RequestParam(required = false, defaultValue = "bigdata-prod") String cluster) {
        log.info("收到集群分析请求: cluster={}", cluster);
        AnalysisResult result = analysisService.analyzeCluster(cluster);
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
    
    /**
     * 生成测试时序数据
     */
    @PostMapping("/test/timeseries")
    public ResponseEntity<Map<String, Object>> generateTestTimeSeries(
            @RequestParam(required = false, defaultValue = "bigdata-prod") String cluster,
            @RequestParam(required = false, defaultValue = "10") int points) {
        
        List<ComponentMetrics> metricsList = new ArrayList<>();
        long now = System.currentTimeMillis() / 1000;
        
        for (int i = 0; i < points; i++) {
            ComponentMetrics m = new ComponentMetrics();
            m.setCluster(cluster);
            m.setService("hdfs");
            m.setComponent("namenode");
            m.setInstance("nn1");
            // 模拟上升趋势
            double trend = i * 0.02;
            m.setCpuUsage(Math.min(0.95, 0.5 + trend + Math.random() * 0.1));
            m.setMemoryUsage(Math.min(0.95, 0.6 + trend * 0.8 + Math.random() * 0.1));
            m.setGcTime((long)(1000 + i * 150 + Math.random() * 500));
            m.setTimestamp(now - (points - i) * 60);
            metricsList.add(m);
        }
        
        String storageId = metricsTimeSeriesStore.saveTimeSeries(metricsList, 24);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "测试时序数据生成成功",
            "storage_id", storageId,
            "data_points", points
        ));
    }
    
    /**
     * 生成测试Spark作业数据
     */
    @PostMapping("/test/spark-job")
    public ResponseEntity<Map<String, Object>> generateTestSparkJob(
            @RequestParam(required = false, defaultValue = "bigdata-prod") String cluster) {
        
        SparkAppMetrics metrics = new SparkAppMetrics();
        metrics.setCluster(cluster);
        metrics.setJobId("test-job-" + System.currentTimeMillis());
        metrics.setAppName("ETL-Test-Job");
        metrics.setStatus("COMPLETED");
        metrics.setDuration(180000L + (long)(Math.random() * 120000));
        metrics.setExecutorCount(10);
        metrics.setExecutorMemoryGB(4.0 + Math.random() * 4.0);
        metrics.setExecutorCores(2);
        metrics.setInputSize((long)(500 + Math.random() * 200) * 1024 * 1024 * 1024);
        metrics.setOutputSize((long)(50 + Math.random() * 30) * 1024 * 1024 * 1024);
        metrics.setShuffleRead((long)(200 + Math.random() * 100) * 1024 * 1024 * 1024);
        metrics.setShuffleWrite((long)(100 + Math.random() * 50) * 1024 * 1024 * 1024);
        metrics.setSkewRatio(3.0 + Math.random() * 3.0); // 3-6倍倾斜
        metrics.setTimestamp(System.currentTimeMillis() / 1000);
        
        String jobId = sparkJobStore.saveJob(metrics, 24);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "测试Spark作业数据生成成功",
            "job_id", jobId,
            "app_name", metrics.getAppName()
        ));
    }
}
