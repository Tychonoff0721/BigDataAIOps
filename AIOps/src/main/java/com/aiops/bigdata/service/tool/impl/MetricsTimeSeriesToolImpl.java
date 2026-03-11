package com.aiops.bigdata.service.tool.impl;

import com.aiops.bigdata.service.context.MetricsTimeSeriesStore;
import com.aiops.bigdata.service.tool.MetricsTimeSeriesTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 时序指标查询工具实现
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsTimeSeriesToolImpl implements MetricsTimeSeriesTool {
    
    private final MetricsTimeSeriesStore metricsTimeSeriesStore;
    
    @Override
    public String getTimeSeriesSummary(String storageId) {
        log.info("获取时序数据摘要: storageId={}", storageId);
        
        if (storageId == null || storageId.isEmpty()) {
            return "错误: storageId参数不能为空";
        }
        
        if (!metricsTimeSeriesStore.exists(storageId)) {
            return "时序数据不存在: " + storageId;
        }
        
        return metricsTimeSeriesStore.getTimeSeriesSummary(storageId);
    }
    
    @Override
    public String getMetricTrend(String storageId, String metricName) {
        log.info("获取指标趋势: storageId={}, metricName={}", storageId, metricName);
        
        if (storageId == null || storageId.isEmpty()) {
            return "错误: storageId参数不能为空";
        }
        
        if (metricName == null || metricName.isEmpty()) {
            return "错误: metricName参数不能为空";
        }
        
        if (!metricsTimeSeriesStore.exists(storageId)) {
            return "时序数据不存在: " + storageId;
        }
        
        return metricsTimeSeriesStore.getMetricTrend(storageId, metricName);
    }
    
    @Override
    public String getRecentMetrics(String storageId, int minutes) {
        log.info("获取最近指标: storageId={}, minutes={}", storageId, minutes);
        
        if (storageId == null || storageId.isEmpty()) {
            return "错误: storageId参数不能为空";
        }
        
        if (!metricsTimeSeriesStore.exists(storageId)) {
            return "时序数据不存在: " + storageId;
        }
        
        var recentMetrics = metricsTimeSeriesStore.getRecentMetrics(storageId, minutes);
        
        if (recentMetrics.isEmpty()) {
            return String.format("最近%d分钟内无数据", minutes);
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("=== 最近%d分钟数据 (%d个数据点) ===\n\n", 
            minutes, recentMetrics.size()));
        
        // 获取所有指标名称
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
    
    @Override
    public String execute(String storageId, String metricName, Integer minutes) {
        log.info("执行时序指标查询: storageId={}, metricName={}, minutes={}", 
            storageId, metricName, minutes);
        
        if (storageId == null || storageId.isEmpty()) {
            return "错误: storage_id参数不能为空";
        }
        
        if (!metricsTimeSeriesStore.exists(storageId)) {
            return "时序数据不存在: " + storageId + "\n\n可能的原因:\n" +
                   "1. storage_id格式错误，正确格式为: cluster:service:component:instance\n" +
                   "2. 数据已过期被清理\n" +
                   "3. 数据尚未采集\n" +
                   "\n请检查storage_id是否正确，或联系管理员确认数据采集状态。";
        }
        
        // 根据参数决定返回内容
        if (metricName != null && !metricName.isEmpty()) {
            // 返回指定指标的趋势
            if (minutes != null && minutes > 0) {
                // 先获取最近数据，再分析趋势
                var recentMetrics = metricsTimeSeriesStore.getRecentMetrics(storageId, minutes);
                if (recentMetrics.isEmpty()) {
                    return String.format("最近%d分钟内无%s指标数据", minutes, metricName);
                }
            }
            return metricsTimeSeriesStore.getMetricTrend(storageId, metricName);
        } else if (minutes != null && minutes > 0) {
            // 返回最近N分钟的数据
            return getRecentMetrics(storageId, minutes);
        } else {
            // 返回完整摘要
            return metricsTimeSeriesStore.getTimeSeriesSummary(storageId);
        }
    }
}
