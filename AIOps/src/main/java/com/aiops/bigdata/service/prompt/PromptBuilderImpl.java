package com.aiops.bigdata.service.prompt;

import com.aiops.bigdata.entity.feature.ComponentMetricFeatures;
import com.aiops.bigdata.entity.feature.SparkAppFeatures;
import com.aiops.bigdata.entity.feature.TimeSeriesFeatures;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.StringJoiner;

/**
 * Prompt构建器实现类
 * 将特征数据转换为结构化的LLM Prompt
 */
@Component
public class PromptBuilderImpl implements PromptBuilder {
    
    @Override
    public String buildComponentAnalysisPrompt(ComponentMetricFeatures features, String cluster) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("请分析以下大数据集群组件的健康状态。\n\n");
        
        // 组件基本信息
        prompt.append("## 组件信息\n");
        prompt.append(String.format("- 集群: %s\n", cluster != null ? cluster : "default"));
        prompt.append(String.format("- 服务: %s\n", features.getService()));
        prompt.append(String.format("- 组件: %s\n", features.getComponent()));
        if (features.getInstance() != null) {
            prompt.append(String.format("- 实例: %s\n", features.getInstance()));
        }
        prompt.append("\n");
        
        // 已提取的特征摘要
        prompt.append("## 已提取的指标特征\n");
        prompt.append(features.toSummaryText());
        prompt.append("\n");
        
        // 详细指标特征（精简版）
        prompt.append("## 指标详细特征\n");
        prompt.append("```\n");
        for (Map.Entry<String, TimeSeriesFeatures> entry : features.getMetricFeatures().entrySet()) {
            String metricName = entry.getKey();
            TimeSeriesFeatures f = entry.getValue();
            
            // 只输出关键特征，减少Token
            prompt.append(String.format("%s: current=%.3f, mean=%.3f, stddev=%.3f, trend=%s, anomaly=%s\n",
                metricName,
                f.getCurrent() != null ? f.getCurrent() : 0,
                f.getMean() != null ? f.getMean() : 0,
                f.getStddev() != null ? f.getStddev() : 0,
                f.getTrendDirection() != null ? f.getTrendDirection() : "stable",
                f.isAnomaly() ? "YES" : "no"
            ));
        }
        prompt.append("```\n\n");
        
        // 分析要求
        prompt.append("## 分析要求\n");
        prompt.append("请基于以上特征数据，完成以下分析：\n");
        prompt.append("1. **健康状态判断**: 给出组件当前健康状态（healthy/warning/critical）\n");
        prompt.append("2. **问题诊断**: 识别存在的问题及其严重程度\n");
        prompt.append("3. **根因分析**: 分析可能导致问题的根本原因\n");
        prompt.append("4. **优化建议**: 给出具体的优化或运维建议\n\n");
        
        // 提示使用Tool获取更多上下文
        prompt.append("## 可用工具\n");
        prompt.append("如果需要更多上下文信息，可以使用以下工具查询：\n");
        prompt.append("- `get_realtime_metrics`: 查询其他组件的实时指标\n");
        prompt.append("- `get_long_term_status`: 查询集群长期状态（容量、配置等）\n");
        prompt.append("- `get_recent_events`: 查询近期告警和事件\n\n");
        
        prompt.append("请以JSON格式输出分析结果。\n");
        
        return prompt.toString();
    }
    
    @Override
    public String buildSparkAnalysisPrompt(SparkAppFeatures features, String cluster) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("请分析以下Spark作业的性能和健康状态。\n\n");
        
        // 作业基本信息
        prompt.append("## 作业信息\n");
        prompt.append(String.format("- 作业ID: %s\n", features.getJobId()));
        prompt.append(String.format("- 应用名称: %s\n", features.getAppName()));
        prompt.append(String.format("- 执行时长: %s\n", formatDuration(features.getDuration())));
        prompt.append(String.format("- 状态: %s\n", features.getStatus()));
        prompt.append("\n");
        
        // 已提取的特征摘要
        prompt.append("## 特征摘要\n");
        prompt.append(features.toSummaryText());
        prompt.append("\n");
        
        // 资源配置
        prompt.append("## 资源配置\n");
        prompt.append("```\n");
        prompt.append(String.format("Executor: %d × %.1fGB × %d核\n",
            features.getExecutorCount() != null ? features.getExecutorCount() : 0,
            features.getExecutorMemoryGB() != null ? features.getExecutorMemoryGB() : 0,
            features.getExecutorCores() != null ? features.getExecutorCores() : 0));
        prompt.append("```\n\n");
        
        // 数据特征
        prompt.append("## 数据特征\n");
        prompt.append("```\n");
        prompt.append(String.format("输入: %.2fGB, 输出: %.2fGB\n",
            features.getInputSizeGB() != null ? features.getInputSizeGB() : 0,
            features.getOutputSizeGB() != null ? features.getOutputSizeGB() : 0));
        prompt.append(String.format("Shuffle: 读%.2fGB, 写%.2fGB (占比%.1f%%)\n",
            features.getShuffleReadGB() != null ? features.getShuffleReadGB() : 0,
            features.getShuffleWriteGB() != null ? features.getShuffleWriteGB() : 0,
            features.getShuffleRatio() != null ? features.getShuffleRatio() * 100 : 0));
        prompt.append("```\n\n");
        
        // 瓶颈信息
        if (!"none".equals(features.getMainBottleneck())) {
            prompt.append("## 检测到的瓶颈\n");
            prompt.append(String.format("- 主要瓶颈: %s\n", features.getMainBottleneck()));
            prompt.append(String.format("- 严重程度: %.0f/100\n", 
                features.getBottleneckSeverity() != null ? features.getBottleneckSeverity() : 0));
            if (!features.getDetectedBottlenecks().isEmpty()) {
                prompt.append("- 所有瓶颈: ").append(String.join(", ", features.getDetectedBottlenecks())).append("\n");
            }
            prompt.append("\n");
        }
        
        // 数据倾斜
        if (features.isHasDataSkew()) {
            prompt.append("## 数据倾斜\n");
            prompt.append(String.format("- 倾斜Stage数: %d\n", features.getSkewedStageCount()));
            prompt.append(String.format("- 最大倾斜率: %.1f\n", features.getMaxSkewRatio()));
            prompt.append("- 倾斜Stage: ").append(String.join(", ", features.getSkewedStages())).append("\n\n");
        }
        
        // 分析要求
        prompt.append("## 分析要求\n");
        prompt.append("请基于以上特征数据，完成以下分析：\n");
        prompt.append("1. **健康评分**: 给出作业整体健康评分（0-100）\n");
        prompt.append("2. **性能瓶颈分析**: 详细分析各瓶颈对性能的影响\n");
        prompt.append("3. **数据倾斜分析**: 如存在倾斜，分析原因和影响\n");
        prompt.append("4. **资源配置评估**: 评估当前资源配置是否合理\n");
        prompt.append("5. **优化建议**: 给出具体的优化建议（代码、配置、资源）\n\n");
        
        prompt.append("## 可用工具\n");
        prompt.append("如果需要更多上下文，可使用工具查询集群状态和历史事件。\n\n");
        
        prompt.append("请以JSON格式输出分析结果。\n");
        
        return prompt.toString();
    }
    
    @Override
    public String buildSystemPrompt() {
        return """
            你是一个专业的大数据平台运维AI助手，负责分析集群组件和Spark作业的健康状态。
            
            ## 你的职责
            1. 分析组件指标，判断健康状态
            2. 识别性能瓶颈和潜在问题
            3. 提供根因分析和优化建议
            4. 给出可操作的运维建议
            
            ## 分析原则
            - 基于数据和特征进行分析，不要臆测
            - 优先关注异常和告警指标
            - 建议要具体可执行
            - 考虑集群整体上下文
            
            ## 输出格式
            请以JSON格式输出分析结果，包含以下字段：
            ```json
            {
              "health_status": "healthy|warning|critical",
              "health_score": 0-100,
              "diagnosis": {
                "summary": "诊断摘要",
                "issues": [
                  {
                    "type": "问题类型",
                    "severity": "warning|critical",
                    "title": "问题标题",
                    "description": "问题描述",
                    "related_metrics": ["相关指标"]
                  }
                ],
                "root_cause": "根因分析"
              },
              "recommendations": [
                {
                  "type": "建议类型",
                  "priority": 1-5,
                  "title": "建议标题",
                  "description": "建议描述",
                  "actions": ["具体操作步骤"]
                }
              ]
            }
            ```
            
            ## 工具使用
            你可以使用以下工具获取更多信息：
            - get_realtime_metrics: 获取实时指标
            - get_long_term_status: 获取长期状态
            - get_recent_events: 获取近期事件
            
            使用工具时，请按以下格式输出：
            ```json
            {"tool": "工具名称", "arguments": {"参数名": "参数值"}}
            ```
            """;
    }
    
    @Override
    public String buildToolDefinitionsPrompt() {
        return """
            ## 可用工具定义
            
            ### 1. get_realtime_metrics
            获取集群组件的实时指标数据。
            
            **参数**:
            - cluster (必填): 集群名称
            - service (必填): 服务类型，如 hdfs, yarn, spark, kafka
            - component (可选): 组件类型，如 namenode, datanode
            - instance (可选): 实例标识
            
            **返回**: 实时指标数据列表
            
            ### 2. get_long_term_status
            获取集群的长期状态数据。
            
            **参数**:
            - cluster (必填): 集群名称
            - days (可选): 查询最近N天的数据
            
            **返回**: 集群容量、配置、版本等信息
            
            ### 3. get_recent_events
            获取集群近期的告警和事件。
            
            **参数**:
            - cluster (必填): 集群名称
            - severity (可选): 过滤严重程度 (critical, warning)
            - limit (可选): 返回数量限制
            
            **返回**: 事件列表，包含时间、类型、描述等
            """;
    }
    
    private String formatDuration(Long ms) {
        if (ms == null) return "未知";
        if (ms < 1000) return ms + "ms";
        if (ms < 60000) return String.format("%.1fs", ms / 1000.0);
        if (ms < 3600000) return String.format("%.1fmin", ms / 60000.0);
        return String.format("%.1fh", ms / 3600000.0);
    }
}
