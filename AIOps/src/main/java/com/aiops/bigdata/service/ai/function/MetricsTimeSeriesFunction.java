package com.aiops.bigdata.service.ai.function;

import com.aiops.bigdata.service.context.MetricsTimeSeriesStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * Spring AI Function: 获取时序指标数据
 * Spring AI 会自动将其注册为 LLM 可调用的工具
 */
@Slf4j
@Component("getMetricsTimeSeries")
@RequiredArgsConstructor
public class MetricsTimeSeriesFunction 
        implements Function<MetricsTimeSeriesFunction.Request, MetricsTimeSeriesFunction.Response> {
    
    private final MetricsTimeSeriesStore metricsTimeSeriesStore;
    
    @Override
    public Response apply(Request request) {
        log.info("执行时序指标查询: storageId={}, metricName={}, minutes={}", 
            request.storageId(), request.metricName(), request.minutes());
        
        try {
            if (request.storageId() == null || request.storageId().isEmpty()) {
                return new Response("错误: storage_id 参数不能为空", false);
            }
            
            if (!metricsTimeSeriesStore.exists(request.storageId())) {
                return new Response("时序数据不存在: " + request.storageId() + 
                    "\n\n可能的原因:\n" +
                    "1. storage_id格式错误，正确格式为: cluster:service:component:instance\n" +
                    "2. 数据已过期被清理\n" +
                    "3. 数据尚未采集", false);
            }
            
            String result;
            if (request.metricName() != null && !request.metricName().isEmpty()) {
                // 返回指定指标的趋势
                result = metricsTimeSeriesStore.getMetricTrend(request.storageId(), request.metricName());
            } else if (request.minutes() != null && request.minutes() > 0) {
                // 返回最近N分钟的数据
                result = getRecentMetricsSummary(request.storageId(), request.minutes());
            } else {
                // 返回完整摘要
                result = metricsTimeSeriesStore.getTimeSeriesSummary(request.storageId());
            }
            
            return new Response(result, true);
            
        } catch (Exception e) {
            log.error("时序指标查询失败: {}", e.getMessage(), e);
            return new Response("查询失败: " + e.getMessage(), false);
        }
    }
    
    private String getRecentMetricsSummary(String storageId, int minutes) {
        var recentMetrics = metricsTimeSeriesStore.getRecentMetrics(storageId, minutes);
        
        if (recentMetrics.isEmpty()) {
            return String.format("最近%d分钟内无数据", minutes);
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("=== 最近%d分钟数据 (%d个数据点) ===\n\n", 
            minutes, recentMetrics.size()));
        
        // 获取所有指标名称并计算统计
        java.util.Set<String> metricNames = new java.util.HashSet<>();
        for (var m : recentMetrics) {
            if (m.getMetrics() != null) {
                metricNames.addAll(m.getMetrics().keySet());
            }
        }
        if (recentMetrics.get(0).getCpuUsage() != null) metricNames.add("cpu_usage");
        if (recentMetrics.get(0).getMemoryUsage() != null) metricNames.add("memory_usage");
        if (recentMetrics.get(0).getGcTime() != null) metricNames.add("gc_time");
        
        for (String metricName : metricNames) {
            java.util.List<Double> values = new java.util.ArrayList<>();
            for (var m : recentMetrics) {
                Double value = null;
                switch (metricName) {
                    case "cpu_usage": value = m.getCpuUsage(); break;
                    case "memory_usage": value = m.getMemoryUsage(); break;
                    case "gc_time": value = m.getGcTime() != null ? m.getGcTime().doubleValue() : null; break;
                    default: value = m.getMetricAsDouble(metricName);
                }
                if (value != null) {
                    values.add(value);
                }
            }
            
            if (!values.isEmpty()) {
                double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
                double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
                double avg = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                double current = values.get(values.size() - 1);
                
                sb.append(String.format("【%s】当前=%.2f, 最小=%.2f, 最大=%.2f, 平均=%.2f\n",
                    metricName, current, min, max, avg));
            }
        }
        
        return sb.toString();
    }
    
    /**
     * 请求参数
     */
    public record Request(
        String storageId,      // 时序数据存储ID（必填）
        String metricName,     // 指标名称（可选）
        Integer minutes        // 最近N分钟（可选）
    ) {}
    
    /**
     * 响应结果
     */
    public record Response(
        String content,        // 查询结果内容
        boolean success        // 是否成功
    ) {}
}
