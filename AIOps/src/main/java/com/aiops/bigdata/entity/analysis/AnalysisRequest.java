package com.aiops.bigdata.entity.analysis;

import com.aiops.bigdata.entity.metrics.ComponentMetrics;
import com.aiops.bigdata.entity.metrics.SparkAppMetrics;
import lombok.Data;

import java.util.List;

/**
 * 分析请求DTO
 * 用于接收AI分析请求
 */
@Data
public class AnalysisRequest {
    
    /**
     * 分析类型
     * - component: 组件健康分析
     * - spark_app: Spark作业分析
     * - cluster_overview: 集群整体分析
     */
    private String analysisType;
    
    /**
     * 集群名称
     */
    private String cluster;
    
    /**
     * 组件指标（组件分析时使用）
     */
    private ComponentMetrics componentMetrics;
    
    /**
     * Spark作业指标（Spark分析时使用）
     */
    private SparkAppMetrics sparkAppMetrics;
    
    /**
     * 是否包含历史上下文
     */
    private Boolean includeContext = true;
    
    /**
     * 是否返回详细分析
     */
    private Boolean detailedAnalysis = true;
    
    /**
     * 指定分析的指标名称列表（可选，不指定则分析全部）
     */
    private List<String> targetMetrics;
    
    /**
     * 自定义分析提示（可选）
     */
    private String customPrompt;
    
    /**
     * 分析深度（quick, normal, deep）
     */
    private String analysisDepth = "normal";
}
