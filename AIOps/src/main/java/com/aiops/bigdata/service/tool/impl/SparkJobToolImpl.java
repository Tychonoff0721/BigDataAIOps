package com.aiops.bigdata.service.tool.impl;

import com.aiops.bigdata.service.context.SparkJobStore;
import com.aiops.bigdata.service.tool.SparkJobTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Spark作业查询工具实现
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SparkJobToolImpl implements SparkJobTool {
    
    private final SparkJobStore sparkJobStore;
    
    @Override
    public String getJobSummary(String jobId) {
        log.info("获取Spark作业摘要: jobId={}", jobId);
        
        if (jobId == null || jobId.isEmpty()) {
            return "错误: job_id参数不能为空";
        }
        
        if (!sparkJobStore.exists(jobId)) {
            return "Spark作业不存在: " + jobId + "\n\n可能的原因:\n" +
                   "1. job_id输入错误\n" +
                   "2. 作业数据已过期被清理\n" +
                   "3. 作业尚未提交或数据未采集\n" +
                   "\n请检查job_id是否正确。";
        }
        
        return sparkJobStore.getJobSummary(jobId);
    }
    
    @Override
    public String getStageDetails(String jobId) {
        log.info("获取Stage详情: jobId={}", jobId);
        
        if (jobId == null || jobId.isEmpty()) {
            return "错误: job_id参数不能为空";
        }
        
        if (!sparkJobStore.exists(jobId)) {
            return "Spark作业不存在: " + jobId;
        }
        
        return sparkJobStore.getStageDetails(jobId);
    }
    
    @Override
    public String getExecutorDetails(String jobId) {
        log.info("获取Executor详情: jobId={}", jobId);
        
        if (jobId == null || jobId.isEmpty()) {
            return "错误: job_id参数不能为空";
        }
        
        if (!sparkJobStore.exists(jobId)) {
            return "Spark作业不存在: " + jobId;
        }
        
        return sparkJobStore.getExecutorDetails(jobId);
    }
    
    @Override
    public String getBottleneckAnalysis(String jobId) {
        log.info("获取瓶颈分析: jobId={}", jobId);
        
        if (jobId == null || jobId.isEmpty()) {
            return "错误: job_id参数不能为空";
        }
        
        if (!sparkJobStore.exists(jobId)) {
            return "Spark作业不存在: " + jobId;
        }
        
        return sparkJobStore.getBottleneckAnalysis(jobId);
    }
    
    @Override
    public String execute(String jobId, String detailType) {
        log.info("执行Spark作业查询: jobId={}, detailType={}", jobId, detailType);
        
        if (jobId == null || jobId.isEmpty()) {
            return "错误: job_id参数不能为空";
        }
        
        if (!sparkJobStore.exists(jobId)) {
            return "Spark作业不存在: " + jobId + "\n\n可能的原因:\n" +
                   "1. job_id输入错误\n" +
                   "2. 作业数据已过期被清理\n" +
                   "3. 作业尚未提交或数据未采集\n" +
                   "\n请检查job_id是否正确。";
        }
        
        // 根据详情类型返回不同内容
        if (detailType == null || detailType.isEmpty() || "summary".equalsIgnoreCase(detailType)) {
            return sparkJobStore.getJobSummary(jobId);
        }
        
        return switch (detailType.toLowerCase()) {
            case "stages" -> sparkJobStore.getStageDetails(jobId);
            case "executors" -> sparkJobStore.getExecutorDetails(jobId);
            case "bottleneck" -> sparkJobStore.getBottleneckAnalysis(jobId);
            default -> "不支持的详情类型: " + detailType + "\n支持的类型: summary, stages, executors, bottleneck";
        };
    }
}
