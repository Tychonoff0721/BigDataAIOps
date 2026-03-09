package com.aiops.bigdata.service.analyzer;

import com.aiops.bigdata.entity.analysis.AnalysisResult;
import com.aiops.bigdata.entity.metrics.SparkAppMetrics;

import java.util.List;

/**
 * Spark作业分析服务接口
 * 负责分析Spark应用程序的性能和健康状态
 */
public interface SparkAppAnalyzer {
    
    /**
     * 分析Spark作业
     * 
     * @param metrics Spark作业指标数据
     * @return 分析结果
     */
    AnalysisResult analyze(SparkAppMetrics metrics);
    
    /**
     * 分析Spark作业（带上下文）
     * 上下文数据由LLM通过Tool按需查询，不直接传入
     * 
     * @param metrics Spark作业指标数据
     * @param cluster 集群名称（用于LLM查询上下文）
     * @return 分析结果
     */
    AnalysisResult analyze(SparkAppMetrics metrics, String cluster);
    
    /**
     * 批量分析Spark作业
     * 
     * @param metricsList Spark作业指标列表
     * @return 分析结果列表
     */
    List<AnalysisResult> analyzeBatch(List<SparkAppMetrics> metricsList);
    
    /**
     * 分析Stage瓶颈
     * 识别作业中的性能瓶颈Stage
     * 
     * @param metrics Spark作业指标数据
     * @return 瓶颈Stage列表及分析
     */
    AnalysisResult analyzeStageBottlenecks(SparkAppMetrics metrics);
    
    /**
     * 分析数据倾斜
     * 检测作业中是否存在数据倾斜问题
     * 
     * @param metrics Spark作业指标数据
     * @return 数据倾斜分析结果
     */
    AnalysisResult analyzeDataSkew(SparkAppMetrics metrics);
    
    /**
     * 分析资源配置
     * 评估Executor资源配置是否合理
     * 
     * @param metrics Spark作业指标数据
     * @return 资源配置分析结果
     */
    AnalysisResult analyzeResourceConfiguration(SparkAppMetrics metrics);
    
    /**
     * 生成优化建议
     * 基于作业指标生成优化建议
     * 
     * @param metrics Spark作业指标数据
     * @return 优化建议
     */
    AnalysisResult generateOptimizationSuggestions(SparkAppMetrics metrics);
}
