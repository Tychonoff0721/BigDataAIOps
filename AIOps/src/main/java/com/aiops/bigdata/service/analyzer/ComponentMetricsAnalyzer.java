package com.aiops.bigdata.service.analyzer;

import com.aiops.bigdata.entity.analysis.AnalysisResult;
import com.aiops.bigdata.entity.context.RealtimeMetrics;
import com.aiops.bigdata.entity.context.LongTermStatus;
import com.aiops.bigdata.entity.context.RecentEvents;
import com.aiops.bigdata.entity.metrics.ComponentMetrics;

import java.util.List;

/**
 * 组件指标分析服务接口
 * 负责分析大数据集群组件（HDFS、YARN、Spark等）的健康状态
 */
public interface ComponentMetricsAnalyzer {
    
    /**
     * 分析单个组件指标
     * 
     * @param metrics 组件指标数据
     * @return 分析结果
     */
    AnalysisResult analyze(ComponentMetrics metrics);
    
    /**
     * 分析单个组件指标（带上下文）
     * 上下文数据由LLM通过Tool按需查询，不直接传入
     * 
     * @param metrics 组件指标数据
     * @param cluster 集群名称（用于LLM查询上下文）
     * @return 分析结果
     */
    AnalysisResult analyze(ComponentMetrics metrics, String cluster);
    
    /**
     * 批量分析组件指标
     * 
     * @param metricsList 组件指标列表
     * @return 分析结果列表
     */
    List<AnalysisResult> analyzeBatch(List<ComponentMetrics> metricsList);
    
    /**
     * 分析时间序列指标
     * 用于分析某个组件一段时间内的指标变化趋势
     * 
     * @param metricsHistory 历史指标列表（按时间排序）
     * @return 分析结果
     */
    AnalysisResult analyzeTimeSeries(List<ComponentMetrics> metricsHistory);
    
    /**
     * 快速健康检查
     * 仅返回健康状态，不进行深度分析
     * 
     * @param metrics 组件指标数据
     * @return 健康状态（HEALTHY/WARNING/CRITICAL）
     */
    String quickHealthCheck(ComponentMetrics metrics);
}
