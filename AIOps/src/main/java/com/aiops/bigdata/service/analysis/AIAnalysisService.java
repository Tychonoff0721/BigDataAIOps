package com.aiops.bigdata.service.analysis;

import com.aiops.bigdata.entity.analysis.AnalysisResult;
import com.aiops.bigdata.entity.feature.ComponentMetricFeatures;
import com.aiops.bigdata.entity.feature.SparkAppFeatures;
import com.aiops.bigdata.entity.metrics.ComponentMetrics;
import com.aiops.bigdata.entity.metrics.SparkAppMetrics;

import java.util.List;

/**
 * AI分析服务接口
 * 整合特征提取、Prompt构建、LLM调用的顶层服务
 * 
 * @deprecated 请使用 {@link com.aiops.bigdata.service.ai.AIAnalysisService} 替代
 *             新版本基于 Spring AI 框架，代码更简洁，功能更强大
 */
@Deprecated
public interface AIAnalysisService {
    
    // ============ 新版接口（基于Redis存储，LLM通过Tool自行查看数据） ============
    
    /**
     * 分析时序数据
     * 根据storageId从Redis获取时序数据，让LLM通过Tool自行查看分析
     * 
     * @param storageId 时序数据存储ID
     * @param cluster 集群名称
     * @return 分析结果
     */
    AnalysisResult analyzeTimeSeries(String storageId, String cluster);
    
    /**
     * 分析Spark作业
     * 根据jobId从Redis获取作业信息，让LLM通过Tool自行查看分析
     * 
     * @param jobId Spark作业ID
     * @param cluster 集群名称
     * @return 分析结果
     */
    AnalysisResult analyzeSparkJob(String jobId, String cluster);
    
    // ============ 旧版接口（保留向后兼容） ============
    
    /**
     * 分析组件指标
     * @deprecated 建议使用 analyzeTimeSeries
     * 
     * @param metrics 组件指标
     * @return 分析结果
     */
    @Deprecated
    AnalysisResult analyzeComponent(ComponentMetrics metrics);
    
    /**
     * 分析组件指标（时间序列）
     * @deprecated 建议使用 analyzeTimeSeries
     * 
     * @param metricsHistory 历史指标列表
     * @return 分析结果
     */
    @Deprecated
    AnalysisResult analyzeComponentTimeSeries(List<ComponentMetrics> metricsHistory);
    
    /**
     * 分析Spark作业
     * @deprecated 建议使用 analyzeSparkJob
     * 
     * @param metrics Spark作业指标
     * @return 分析结果
     */
    @Deprecated
    AnalysisResult analyzeSparkApp(SparkAppMetrics metrics);
    
    /**
     * 使用特征数据直接分析（跳过特征提取）
     * @deprecated 建议使用 analyzeTimeSeries
     * 
     * @param features 组件指标特征
     * @param cluster 集群名称
     * @return 分析结果
     */
    @Deprecated
    AnalysisResult analyzeWithFeatures(ComponentMetricFeatures features, String cluster);
    
    /**
     * 使用Spark特征数据直接分析
     * @deprecated 建议使用 analyzeSparkJob
     * 
     * @param features Spark作业特征
     * @param cluster 集群名称
     * @return 分析结果
     */
    @Deprecated
    AnalysisResult analyzeSparkWithFeatures(SparkAppFeatures features, String cluster);
}
