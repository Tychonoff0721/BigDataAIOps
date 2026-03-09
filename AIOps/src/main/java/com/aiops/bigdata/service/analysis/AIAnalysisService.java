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
 */
public interface AIAnalysisService {
    
    /**
     * 分析组件指标
     * 
     * @param metrics 组件指标
     * @return 分析结果
     */
    AnalysisResult analyzeComponent(ComponentMetrics metrics);
    
    /**
     * 分析组件指标（时间序列）
     * 
     * @param metricsHistory 历史指标列表
     * @return 分析结果
     */
    AnalysisResult analyzeComponentTimeSeries(List<ComponentMetrics> metricsHistory);
    
    /**
     * 分析Spark作业
     * 
     * @param metrics Spark作业指标
     * @return 分析结果
     */
    AnalysisResult analyzeSparkApp(SparkAppMetrics metrics);
    
    /**
     * 使用特征数据直接分析（跳过特征提取）
     * 
     * @param features 组件指标特征
     * @param cluster 集群名称
     * @return 分析结果
     */
    AnalysisResult analyzeWithFeatures(ComponentMetricFeatures features, String cluster);
    
    /**
     * 使用Spark特征数据直接分析
     * 
     * @param features Spark作业特征
     * @param cluster 集群名称
     * @return 分析结果
     */
    AnalysisResult analyzeSparkWithFeatures(SparkAppFeatures features, String cluster);
}
