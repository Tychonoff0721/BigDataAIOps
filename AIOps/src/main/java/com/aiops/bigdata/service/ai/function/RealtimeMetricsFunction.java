package com.aiops.bigdata.service.ai.function;

import com.aiops.bigdata.entity.context.RealtimeMetrics;
import com.aiops.bigdata.service.context.RealtimeMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

/**
 * Spring AI Function: 获取实时指标数据
 */
@Slf4j
@Component("getRealtimeMetrics")
@Description("获取集群组件的实时监控指标，包括CPU、内存、GC时间等。参数: cluster(集群名称，必填), service(服务类型，可选), component(组件类型，可选)")
@RequiredArgsConstructor
public class RealtimeMetricsFunction 
        implements Function<RealtimeMetricsFunction.Request, RealtimeMetricsFunction.Response> {
    
    private final RealtimeMetricsService realtimeMetricsService;
    
    @Override
    public Response apply(Request request) {
        log.info("执行实时指标查询: cluster={}, service={}, component={}", 
            request.cluster(), request.service(), request.component());
        
        try {
            if (request.cluster() == null || request.cluster().isEmpty()) {
                return new Response("错误: cluster 参数不能为空", false);
            }
            
            List<RealtimeMetrics> metrics;
            if (request.component() != null && !request.component().isEmpty()) {
                metrics = realtimeMetricsService.getByComponent(
                    request.cluster(), request.service(), request.component());
            } else if (request.service() != null && !request.service().isEmpty()) {
                metrics = realtimeMetricsService.getByService(request.cluster(), request.service());
            } else {
                metrics = realtimeMetricsService.getByCluster(request.cluster());
            }
            
            if (metrics.isEmpty()) {
                return new Response("未找到实时指标数据", true);
            }
            
            String summary = buildMetricsSummary(metrics);
            return new Response(summary, true);
            
        } catch (Exception e) {
            log.error("实时指标查询失败: {}", e.getMessage(), e);
            return new Response("查询失败: " + e.getMessage(), false);
        }
    }
    
    private String buildMetricsSummary(List<RealtimeMetrics> metrics) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("=== 实时指标摘要 (%d个组件) ===\n\n", metrics.size()));
        
        for (RealtimeMetrics m : metrics) {
            sb.append(String.format("【%s/%s", m.getService(), m.getComponent()));
            if (m.getInstance() != null) {
                sb.append(":").append(m.getInstance());
            }
            sb.append("】\n");
            
            if (m.getCpuUsage() != null) {
                sb.append(String.format("  CPU: %.1f%%\n", m.getCpuUsage() * 100));
            }
            if (m.getMemoryUsage() != null) {
                sb.append(String.format("  内存: %.1f%%\n", m.getMemoryUsage() * 100));
            }
            if (m.getGcTime() != null) {
                sb.append(String.format("  GC时间: %dms\n", m.getGcTime()));
            }
            if (m.getConnectionCount() != null) {
                sb.append(String.format("  连接数: %d\n", m.getConnectionCount()));
            }
            
            // 判断健康状态
            String status = "健康";
            if ((m.getCpuUsage() != null && m.getCpuUsage() > 0.9) ||
                (m.getMemoryUsage() != null && m.getMemoryUsage() > 0.9)) {
                status = "严重";
            } else if ((m.getCpuUsage() != null && m.getCpuUsage() > 0.8) ||
                      (m.getMemoryUsage() != null && m.getMemoryUsage() > 0.8)) {
                status = "警告";
            }
            sb.append(String.format("  状态: %s\n\n", status));
        }
        
        return sb.toString();
    }
    
    /**
     * 请求参数
     */
    public record Request(
        String cluster,        // 集群名称（必填）
        String service,        // 服务类型（可选）
        String component       // 组件类型（可选）
    ) {}
    
    /**
     * 响应结果
     */
    public record Response(
        String content,        // 查询结果内容
        boolean success        // 是否成功
    ) {}
    
    /**
     * 注册 FunctionCallback Bean，供 Spring AI 调用
     */
    @Bean
    public FunctionCallback realtimeMetricsFunctionCallback() {
        return FunctionCallback.builder()
            .function("getRealtimeMetrics", this)
            .description("获取集群组件的实时监控指标，包括CPU、内存、GC时间等。参数: cluster(集群名称，必填), service(服务类型，可选), component(组件类型，可选)")
            .inputType(Request.class)
            .build();
    }
}
