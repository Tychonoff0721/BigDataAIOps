package com.aiops.bigdata.service.ai.function;

import com.aiops.bigdata.entity.common.enums.HealthStatus;
import com.aiops.bigdata.service.context.RecentEventsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * Spring AI Function: 获取最近告警和事件
 */
@Slf4j
@Component("getRecentEvents")
@Description("获取集群最近的告警和异常事件，用于了解集群当前存在的问题。参数: cluster(集群名称，必填), severity(严重程度: warning/critical，可选), limit(返回数量限制，可选)")
@RequiredArgsConstructor
public class RecentEventsFunction 
        implements Function<RecentEventsFunction.Request, RecentEventsFunction.Response> {
    
    private final RecentEventsService recentEventsService;
    
    @Override
    public Response apply(Request request) {
        log.info("执行最近事件查询: cluster={}, severity={}", request.cluster(), request.severity());
        
        try {
            if (request.cluster() == null || request.cluster().isEmpty()) {
                return new Response("错误: cluster 参数不能为空", false);
            }
            
            int limit = request.limit() != null ? request.limit() : 10;
            HealthStatus severity = request.severity() != null 
                ? HealthStatus.fromCode(request.severity()) : null;
            
            var events = recentEventsService.getUnresolvedEvents(request.cluster());
            
            // 过滤严重程度
            if (severity != null) {
                events = events.stream()
                    .filter(e -> e.getSeverity() == severity)
                    .toList();
            }
            
            // 限制数量
            if (events.size() > limit) {
                events = events.subList(0, limit);
            }
            
            if (events.isEmpty()) {
                return new Response("未找到符合条件的告警事件", true);
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== 最近告警事件 (%d条) ===\n\n", events.size()));
            
            for (var event : events) {
                sb.append(String.format("【%s】%s\n", 
                    event.getSeverity(), event.getTitle()));
                sb.append(String.format("  服务: %s/%s\n", 
                    event.getService(), event.getComponent()));
                sb.append(String.format("  时间: %s\n", event.getEventTime()));
                sb.append(String.format("  描述: %s\n", event.getDescription()));
                sb.append(String.format("  状态: %s\n\n", 
                    Boolean.TRUE.equals(event.getResolved()) ? "已解决" : "未解决"));
            }
            
            return new Response(sb.toString(), true);
            
        } catch (Exception e) {
            log.error("最近事件查询失败: {}", e.getMessage(), e);
            return new Response("查询失败: " + e.getMessage(), false);
        }
    }
    
    /**
     * 请求参数
     */
    public record Request(
        String cluster,        // 集群名称（必填）
        String severity,       // 严重程度过滤（可选）: warning, critical
        Integer limit          // 返回数量限制（可选）
    ) {}
    
    /**
     * 响应结果
     */
    public record Response(
        String content,        // 查询结果内容
        boolean success        // 是否成功
    ) {}
}
