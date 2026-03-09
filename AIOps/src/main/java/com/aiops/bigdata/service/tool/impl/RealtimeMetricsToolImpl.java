package com.aiops.bigdata.service.tool.impl;

import com.aiops.bigdata.entity.context.RealtimeMetrics;
import com.aiops.bigdata.service.context.RealtimeMetricsService;
import com.aiops.bigdata.service.tool.RealtimeMetricsTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 实时指标查询工具实现
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeMetricsToolImpl implements RealtimeMetricsTool {
    
    private final RealtimeMetricsService realtimeMetricsService;
    
    @Override
    public List<RealtimeMetrics> execute(String cluster, String service, String component, String instance) {
        log.info("执行RealtimeMetricsTool: cluster={}, service={}, component={}, instance={}", 
            cluster, service, component, instance);
        
        try {
            if (component != null) {
                // 查询特定组件
                if (instance != null) {
                    // 查询特定实例
                    return realtimeMetricsService.get(cluster, service, component, instance)
                        .map(List::of)
                        .orElse(Collections.emptyList());
                } else {
                    // 查询组件所有实例
                    return realtimeMetricsService.getByComponent(cluster, service, component);
                }
            } else {
                // 查询服务所有组件
                return realtimeMetricsService.getByService(cluster, service);
            }
        } catch (Exception e) {
            log.error("查询实时指标失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    @Override
    public String getClusterMetricsSummary(String cluster) {
        log.info("获取集群实时指标摘要: cluster={}", cluster);
        
        try {
            List<RealtimeMetrics> allMetrics = realtimeMetricsService.getByCluster(cluster);
            
            if (allMetrics.isEmpty()) {
                return "暂无实时指标数据";
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append("集群实时指标摘要 (").append(allMetrics.size()).append("个组件)\n");
            
            // 按服务分组统计
            allMetrics.stream()
                .collect(Collectors.groupingBy(RealtimeMetrics::getService))
                .forEach((service, metrics) -> {
                    sb.append("\n").append(service.toUpperCase()).append(":\n");
                    
                    // 计算平均值
                    double avgCpu = metrics.stream()
                        .filter(m -> m.getCpuUsage() != null)
                        .mapToDouble(RealtimeMetrics::getCpuUsage)
                        .average().orElse(0);
                    
                    double avgMemory = metrics.stream()
                        .filter(m -> m.getMemoryUsage() != null)
                        .mapToDouble(RealtimeMetrics::getMemoryUsage)
                        .average().orElse(0);
                    
                    sb.append(String.format("  组件数: %d, 平均CPU: %.1f%%, 平均内存: %.1f%%\n",
                        metrics.size(), avgCpu * 100, avgMemory * 100));
                    
                    // 标记异常组件
                    metrics.stream()
                        .filter(m -> (m.getCpuUsage() != null && m.getCpuUsage() > 0.8) ||
                                    (m.getMemoryUsage() != null && m.getMemoryUsage() > 0.8))
                        .forEach(m -> sb.append(String.format("  [警告] %s/%s: CPU=%.1f%%, 内存=%.1f%%\n",
                            m.getComponent(), 
                            m.getInstance() != null ? m.getInstance() : "N/A",
                            m.getCpuUsage() != null ? m.getCpuUsage() * 100 : 0,
                            m.getMemoryUsage() != null ? m.getMemoryUsage() * 100 : 0)));
                });
            
            return sb.toString();
            
        } catch (Exception e) {
            log.error("获取集群指标摘要失败: {}", e.getMessage(), e);
            return "获取指标摘要失败: " + e.getMessage();
        }
    }
    
    @Override
    public String getComponentHealthSummary(String cluster, String service, String component) {
        log.info("获取组件健康摘要: cluster={}, service={}, component={}", cluster, service, component);
        
        try {
            List<RealtimeMetrics> metrics = component != null 
                ? realtimeMetricsService.getByComponent(cluster, service, component)
                : realtimeMetricsService.getByService(cluster, service);
            
            if (metrics.isEmpty()) {
                return "暂无" + service + (component != null ? "/" + component : "") + "的实时数据";
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append(service.toUpperCase());
            if (component != null) {
                sb.append("/").append(component);
            }
            sb.append(" 健康摘要:\n\n");
            
            for (RealtimeMetrics m : metrics) {
                sb.append("实例: ").append(m.getInstance() != null ? m.getInstance() : "N/A").append("\n");
                sb.append(String.format("  CPU: %.1f%%, 内存: %.1f%%", 
                    m.getCpuUsage() != null ? m.getCpuUsage() * 100 : 0,
                    m.getMemoryUsage() != null ? m.getMemoryUsage() * 100 : 0));
                
                if (m.getGcTime() != null) {
                    sb.append(String.format(", GC: %dms", m.getGcTime()));
                }
                sb.append("\n");
                
                // 判断健康状态
                String status = "健康";
                if ((m.getCpuUsage() != null && m.getCpuUsage() > 0.9) ||
                    (m.getMemoryUsage() != null && m.getMemoryUsage() > 0.9)) {
                    status = "严重";
                } else if ((m.getCpuUsage() != null && m.getCpuUsage() > 0.8) ||
                          (m.getMemoryUsage() != null && m.getMemoryUsage() > 0.8)) {
                    status = "警告";
                }
                sb.append("  状态: ").append(status).append("\n\n");
            }
            
            return sb.toString();
            
        } catch (Exception e) {
            log.error("获取组件健康摘要失败: {}", e.getMessage(), e);
            return "获取健康摘要失败: " + e.getMessage();
        }
    }
}
