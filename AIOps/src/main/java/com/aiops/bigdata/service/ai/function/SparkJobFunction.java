package com.aiops.bigdata.service.ai.function;

import com.aiops.bigdata.service.context.SparkJobStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * Spring AI Function: 获取Spark作业详情
 * Spring AI 会自动将其注册为 LLM 可调用的工具
 */
@Slf4j
@Component("getSparkJob")
@RequiredArgsConstructor
public class SparkJobFunction 
        implements Function<SparkJobFunction.Request, SparkJobFunction.Response> {
    
    private final SparkJobStore sparkJobStore;
    
    @Override
    public Response apply(Request request) {
        log.info("执行Spark作业查询: jobId={}, detailType={}", 
            request.jobId(), request.detailType());
        
        try {
            if (request.jobId() == null || request.jobId().isEmpty()) {
                return new Response("错误: job_id 参数不能为空", false);
            }
            
            if (!sparkJobStore.exists(request.jobId())) {
                return new Response("Spark作业不存在: " + request.jobId() + 
                    "\n\n可能的原因:\n" +
                    "1. job_id输入错误\n" +
                    "2. 作业数据已过期被清理\n" +
                    "3. 作业尚未提交或数据未采集", false);
            }
            
            String result;
            String detailType = request.detailType() != null ? request.detailType() : "summary";
            
            result = switch (detailType.toLowerCase()) {
                case "summary" -> sparkJobStore.getJobSummary(request.jobId());
                case "stages" -> sparkJobStore.getStageDetails(request.jobId());
                case "executors" -> sparkJobStore.getExecutorDetails(request.jobId());
                case "bottleneck" -> sparkJobStore.getBottleneckAnalysis(request.jobId());
                default -> "不支持的详情类型: " + detailType + 
                    "\n支持的类型: summary, stages, executors, bottleneck";
            };
            
            return new Response(result, true);
            
        } catch (Exception e) {
            log.error("Spark作业查询失败: {}", e.getMessage(), e);
            return new Response("查询失败: " + e.getMessage(), false);
        }
    }
    
    /**
     * 请求参数
     */
    public record Request(
        String jobId,          // 作业ID（必填）
        String detailType      // 详情类型: summary/stages/executors/bottleneck（可选）
    ) {}
    
    /**
     * 响应结果
     */
    public record Response(
        String content,        // 查询结果内容
        boolean success        // 是否成功
    ) {}
}
