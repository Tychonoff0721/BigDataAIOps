package com.aiops.bigdata.service.prompt;

import com.aiops.bigdata.entity.feature.ComponentMetricFeatures;
import com.aiops.bigdata.entity.feature.SparkAppFeatures;

/**
 * Prompt构建器接口
 * 负责将特征数据转换为LLM可理解的Prompt
 */
public interface PromptBuilder {
    
    /**
     * 构建组件健康分析Prompt
     * 
     * @param features 组件指标特征
     * @param cluster 集群名称（用于Tool查询上下文）
     * @return 构建好的Prompt
     */
    String buildComponentAnalysisPrompt(ComponentMetricFeatures features, String cluster);
    
    /**
     * 构建Spark作业分析Prompt
     * 
     * @param features Spark作业特征
     * @param cluster 集群名称
     * @return 构建好的Prompt
     */
    String buildSparkAnalysisPrompt(SparkAppFeatures features, String cluster);
    
    /**
     * 构建系统Prompt（包含Tool定义）
     * 
     * @return 系统Prompt
     */
    String buildSystemPrompt();
    
    /**
     * 构建Tool定义Prompt
     * 告诉LLM有哪些Tool可用及其用法
     * 
     * @return Tool定义Prompt
     */
    String buildToolDefinitionsPrompt();
}
