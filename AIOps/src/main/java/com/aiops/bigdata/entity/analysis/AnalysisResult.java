package com.aiops.bigdata.entity.analysis;

import com.aiops.bigdata.entity.common.enums.HealthStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AI分析结果实体类
 * 用于存储大模型分析后的输出结果
 */
@Data
public class AnalysisResult {
    
    private String resultId;
    private String cluster;
    private LocalDateTime analysisTime;
    private String analysisType;
    private String targetId;
    private String targetName;
    private HealthStatus healthStatus;
    private Integer healthScore;
    private Diagnosis diagnosis;
    private List<Recommendation> recommendations = new ArrayList<>();
    private String rawResponse;
    private Long analysisDuration;
    private String modelName;
    
    /**
     * 诊断信息
     */
    @Data
    public static class Diagnosis {
        private String summary;
        private List<DetectedIssue> issues = new ArrayList<>();
        private String rootCauseAnalysis;
        private String impactAssessment;
    }
    
    /**
     * 检测到的问题
     */
    @Data
    public static class DetectedIssue {
        private String issueType;
        private HealthStatus severity;
        private String title;
        private String description;
        private List<String> relatedMetrics = new ArrayList<>();
        private String relatedComponent;
        private LocalDateTime detectedTime;
        private String metricValue;
        private String normalRange;
    }
    
    /**
     * 优化建议
     */
    @Data
    public static class Recommendation {
        private String recommendationType;
        private Integer priority;
        private String title;
        private String description;
        private List<String> actionSteps = new ArrayList<>();
        private String expectedOutcome;
        private List<String> relatedIssues = new ArrayList<>();
        private List<ConfigSuggestion> configSuggestions = new ArrayList<>();
    }
    
    /**
     * 配置建议
     */
    @Data
    public static class ConfigSuggestion {
        private String configKey;
        private String currentValue;
        private String suggestedValue;
        private String reason;
    }
    
    public AnalysisResult addRecommendation(Recommendation recommendation) {
        this.recommendations.add(recommendation);
        return this;
    }
    
    public List<Recommendation> getHighPriorityRecommendations() {
        List<Recommendation> highPriority = new ArrayList<>();
        for (Recommendation rec : recommendations) {
            if (rec.getPriority() != null && rec.getPriority() <= 2) {
                highPriority.add(rec);
            }
        }
        return highPriority;
    }
    
    public void calculateHealthScore() {
        if (healthStatus == null) {
            healthScore = 0;
            return;
        }
        
        switch (healthStatus) {
            case HEALTHY:
                healthScore = 90 + (int)(Math.random() * 10);
                break;
            case WARNING:
                healthScore = 60 + (int)(Math.random() * 30);
                break;
            case CRITICAL:
                healthScore = (int)(Math.random() * 40);
                break;
            default:
                healthScore = 50;
        }
        
        if (diagnosis != null && diagnosis.getIssues() != null) {
            int issueCount = diagnosis.getIssues().size();
            healthScore = Math.max(0, healthScore - issueCount * 5);
        }
    }
}
