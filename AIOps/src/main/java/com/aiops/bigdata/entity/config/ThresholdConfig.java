package com.aiops.bigdata.entity.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * 阈值配置类
 * 从thresholds.yml加载配置，支持动态调整
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "")
@PropertySource(value = "classpath:thresholds.yml", factory = YamlPropertySourceFactory.class)
public class ThresholdConfig {
    
    /**
     * 通用组件阈值
     */
    private Map<String, ThresholdValue> component = new HashMap<>();
    
    /**
     * HDFS专用阈值
     */
    private ServiceThreshold hdfs;
    
    /**
     * YARN专用阈值
     */
    private ServiceThreshold yarn;
    
    /**
     * Kafka专用阈值
     */
    private ServiceThreshold kafka;
    
    /**
     * Flink专用阈值
     */
    private ServiceThreshold flink;
    
    /**
     * Spark作业分析阈值
     */
    private Map<String, ThresholdValue> sparkApp = new HashMap<>();
    
    /**
     * 时间序列特征提取配置
     */
    private TimeSeriesConfig timeSeries;
    
    /**
     * 单个阈值值
     */
    @Data
    public static class ThresholdValue {
        private Double warning;
        private Double critical;
        private String description;
        
        public boolean isExceeded(double value, boolean checkCritical) {
            if (checkCritical && critical != null) {
                return value >= critical;
            } else if (!checkCritical && warning != null) {
                return value >= warning;
            }
            return false;
        }
        
        public Double getThreshold(boolean isCritical) {
            return isCritical ? critical : warning;
        }
    }
    
    /**
     * 服务级阈值（包含多个组件）
     */
    @Data
    public static class ServiceThreshold {
        private Map<String, ThresholdValue> namenode;
        private Map<String, ThresholdValue> datanode;
        private Map<String, ThresholdValue> resourcemanager;
        private Map<String, ThresholdValue> nodemanager;
        private Map<String, ThresholdValue> broker;
        private Map<String, ThresholdValue> jobmanager;
        private Map<String, ThresholdValue> taskmanager;
        
        /**
         * 根据组件名获取阈值Map
         */
        public Map<String, ThresholdValue> getByComponent(String componentName) {
            switch (componentName.toLowerCase()) {
                case "namenode": return namenode;
                case "datanode": return datanode;
                case "resourcemanager": return resourcemanager;
                case "nodemanager": return nodemanager;
                case "broker": return broker;
                case "jobmanager": return jobmanager;
                case "taskmanager": return taskmanager;
                default: return null;
            }
        }
    }
    
    /**
     * 时间序列配置
     */
    @Data
    public static class TimeSeriesConfig {
        private Integer windowSize = 60;
        private Integer trendWindow = 10;
        private Double anomalySensitivity = 2.0;
        private Double spikeThreshold = 0.5;
    }
    
    /**
     * 获取组件阈值
     * 优先级：服务专用阈值 > 通用组件阈值
     */
    public ThresholdValue getThreshold(String service, String componentName, String metricName) {
        // 先尝试获取服务专用阈值
        ThresholdValue threshold = getServiceSpecificThreshold(service, componentName, metricName);
        if (threshold != null) {
            return threshold;
        }
        // 再尝试获取通用阈值
        return this.component.get(metricName);
    }
    
    /**
     * 获取服务专用阈值
     */
    private ThresholdValue getServiceSpecificThreshold(String service, String componentName, String metricName) {
        ServiceThreshold serviceThreshold = null;
        
        switch (service.toLowerCase()) {
            case "hdfs": serviceThreshold = hdfs; break;
            case "yarn": serviceThreshold = yarn; break;
            case "kafka": serviceThreshold = kafka; break;
            case "flink": serviceThreshold = flink; break;
        }
        
        if (serviceThreshold == null) {
            return null;
        }
        
        Map<String, ThresholdValue> componentThresholds = serviceThreshold.getByComponent(componentName);
        if (componentThresholds == null) {
            return null;
        }
        
        return componentThresholds.get(metricName);
    }
    
    /**
     * 获取Spark作业阈值
     */
    public ThresholdValue getSparkAppThreshold(String metricName) {
        return sparkApp.get(metricName);
    }
}
