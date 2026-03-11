package com.aiops.bigdata.service.tool;

/**
 * LLM Tool: Spark作业查询工具
 * 供大模型调用来查看Spark作业的详细信息
 * 
 * @deprecated 请使用 {@link com.aiops.bigdata.service.ai.function.SparkJobFunction} 替代
 *             新版本基于 Spring AI Function 实现
 */
@Deprecated
public interface SparkJobTool {
    
    /**
     * Tool名称
     */
    String NAME = "get_spark_job";
    
    /**
     * Tool描述（供LLM理解）
     */
    String DESCRIPTION = """
        获取Spark作业的详细信息，用于分析作业执行情况和性能瓶颈。
        
        使用场景:
        - 分析Spark作业的执行情况
        - 查看Stage和Executor详情
        - 识别性能瓶颈（数据倾斜、GC、Shuffle等）
        - 获取优化建议
        
        参数说明:
        - job_id: 作业ID（必填）
        - detail_type: 详情类型（可选），可选值:
          - "summary": 作业摘要（默认）
          - "stages": Stage详情
          - "executors": Executor详情
          - "bottleneck": 瓶颈分析
        
        返回内容:
        - 作业基本信息：状态、时长、资源配置
        - 数据量统计：输入输出、Shuffle
        - Stage执行情况
        - Executor状态
        - 瓶颈分析和优化建议
        """;
    
    /**
     * 获取作业摘要
     * 
     * @param jobId 作业ID
     * @return 作业摘要文本
     */
    String getJobSummary(String jobId);
    
    /**
     * 获取Stage详情
     * 
     * @param jobId 作业ID
     * @return Stage详情文本
     */
    String getStageDetails(String jobId);
    
    /**
     * 获取Executor详情
     * 
     * @param jobId 作业ID
     * @return Executor详情文本
     */
    String getExecutorDetails(String jobId);
    
    /**
     * 获取瓶颈分析
     * 
     * @param jobId 作业ID
     * @return 瓶颈分析文本
     */
    String getBottleneckAnalysis(String jobId);
    
    /**
     * 执行工具调用
     * 
     * @param jobId 作业ID
     * @param detailType 详情类型
     * @return 查询结果
     */
    String execute(String jobId, String detailType);
}
