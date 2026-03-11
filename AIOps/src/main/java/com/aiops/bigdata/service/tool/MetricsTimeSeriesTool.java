package com.aiops.bigdata.service.tool;

/**
 * LLM Tool: 时序指标查询工具
 * 供大模型调用来查看组件指标的历史时序数据
 * 
 * @deprecated 请使用 {@link com.aiops.bigdata.service.ai.function.MetricsTimeSeriesFunction} 替代
 *             新版本基于 Spring AI Function 实现
 */
@Deprecated
public interface MetricsTimeSeriesTool {
    
    /**
     * Tool名称
     */
    String NAME = "get_metrics_timeseries";
    
    /**
     * Tool描述（供LLM理解）
     */
    String DESCRIPTION = """
        获取组件指标的历史时序数据，用于分析指标随时间的变化趋势。
        
        使用场景:
        - 分析CPU、内存、GC等指标的历史变化
        - 查看指标的趋势（上升、下降、稳定）
        - 发现异常波动和尖峰
        - 理解指标随时间的分布情况
        
        参数说明:
        - storage_id: 时序数据存储ID（必填），格式如 "cluster:service:component:instance"
        - metric_name: 指标名称（可选），如 cpu_usage, memory_usage, gc_time 等。
                      不填则返回所有指标的摘要。
        - minutes: 最近N分钟的数据（可选），不填则返回全部数据
        
        返回内容:
        - 指标的统计信息：最小值、最大值、平均值、标准差
        - 趋势分析：上升、下降、稳定
        - 异常检测：是否存在异常波动
        """;
    
    /**
     * 获取时序数据摘要
     * 
     * @param storageId 存储ID
     * @return 数据摘要文本
     */
    String getTimeSeriesSummary(String storageId);
    
    /**
     * 获取指定指标的趋势分析
     * 
     * @param storageId 存储ID
     * @param metricName 指标名称
     * @return 趋势分析文本
     */
    String getMetricTrend(String storageId, String metricName);
    
    /**
     * 获取最近N分钟的数据
     * 
     * @param storageId 存储ID
     * @param minutes 分钟数
     * @return 最近数据摘要
     */
    String getRecentMetrics(String storageId, int minutes);
    
    /**
     * 执行工具调用
     * 
     * @param storageId 存储ID
     * @param metricName 指标名称（可选）
     * @param minutes 分钟数（可选）
     * @return 查询结果
     */
    String execute(String storageId, String metricName, Integer minutes);
}
