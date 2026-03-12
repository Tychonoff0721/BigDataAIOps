package com.aiops.bigdata.service.ai;

import com.aiops.bigdata.entity.analysis.AnalysisResult;

/**
 * AI 分析服务接口（基于 Spring AI）
 */
public interface AIAnalysisService {
    
    /**
     * 分析时序数据
     * 
     * @param storageId 时序数据存储ID
     * @param cluster 集群名称
     * @return 分析结果
     */
    AnalysisResult analyzeTimeSeries(String storageId, String cluster);
    
    /**
     * 分析 Spark 作业
     * 
     * @param jobId 作业ID
     * @param cluster 集群名称
     * @return 分析结果
     */
    AnalysisResult analyzeSparkJob(String jobId, String cluster);
    
    /**
     * 分析集群整体状态
     * 
     * @param cluster 集群名称
     * @return 分析结果
     */
    AnalysisResult analyzeCluster(String cluster);
    
    /**
     * 测试简单调用（用于调试）
     * 
     * @param prompt 提示词
     * @return 响应内容
     */
    String testSimpleCall(String prompt);
}
