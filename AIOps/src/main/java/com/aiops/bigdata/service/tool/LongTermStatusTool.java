package com.aiops.bigdata.service.tool;

import com.aiops.bigdata.entity.context.LongTermStatus;

import java.time.LocalDate;
import java.util.List;

/**
 * LLM Tool: 长期状态查询工具
 * 供大模型调用来查询长期状态数据
 */
public interface LongTermStatusTool {
    
    /**
     * Tool名称
     */
    String NAME = "get_long_term_status";
    
    /**
     * Tool描述（供LLM理解）
     */
    String DESCRIPTION = """
        获取集群的长期状态数据。
        包括集群基本信息、总节点数、总资源、存储容量等变化缓慢的数据。
        这些数据按天更新，反映集群的整体配置和容量情况。
        
        参数说明:
        - cluster: 集群名称（必填）
        - date: 日期，格式 yyyy-MM-dd（可选，不填则返回最新数据）
        - days: 查询最近N天的数据（可选，与date二选一）
        
        返回信息包括:
        - 集群基本信息（节点数、CPU、内存）
        - HDFS状态（容量、使用率、文件数）
        - YARN状态（资源、队列配置）
        - 组件版本信息
        """;
    
    /**
     * 查询长期状态
     * 
     * @param cluster 集群名称
     * @param date 日期（可选）
     * @return 长期状态
     */
    LongTermStatus execute(String cluster, LocalDate date);
    
    /**
     * 查询最近N天的长期状态
     * 
     * @param cluster 集群名称
     * @param days 天数
     * @return 长期状态列表
     */
    List<LongTermStatus> executeRecent(String cluster, int days);
    
    /**
     * 获取集群概览摘要
     * 返回精简的数据，减少token消耗
     * 
     * @param cluster 集群名称
     * @return 集群概览摘要
     */
    String getClusterOverview(String cluster);
    
    /**
     * 获取资源使用趋势
     * 
     * @param cluster 集群名称
     * @param days 天数
     * @return 资源使用趋势摘要
     */
    String getResourceTrend(String cluster, int days);
}
