package com.aiops.bigdata.service.context;

import com.aiops.bigdata.entity.context.LongTermStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 长期状态服务接口 - Layer 2
 * 管理天级变化的长期数据
 * 建议使用MySQL存储
 */
public interface LongTermStatusService {
    
    /**
     * 保存长期状态
     * 
     * @param status 长期状态数据
     */
    void save(LongTermStatus status);
    
    /**
     * 获取指定日期的长期状态
     * 
     * @param cluster 集群名称
     * @param date 日期
     * @return 长期状态
     */
    Optional<LongTermStatus> get(String cluster, LocalDate date);
    
    /**
     * 获取最新的长期状态
     * 
     * @param cluster 集群名称
     * @return 长期状态
     */
    Optional<LongTermStatus> getLatest(String cluster);
    
    /**
     * 获取日期范围内的长期状态
     * 
     * @param cluster 集群名称
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 长期状态列表
     */
    List<LongTermStatus> getByDateRange(String cluster, LocalDate startDate, LocalDate endDate);
    
    /**
     * 获取所有集群的最新状态
     * 
     * @return 长期状态列表
     */
    List<LongTermStatus> getAllClustersLatest();
    
    /**
     * 更新集群基本信息
     * 
     * @param cluster 集群名称
     * @param totalNodes 总节点数
     * @param totalCpuCores 总CPU核心数
     * @param totalMemoryGB 总内存（GB）
     */
    void updateClusterInfo(String cluster, Integer totalNodes, Integer totalCpuCores, Long totalMemoryGB);
    
    /**
     * 更新HDFS状态
     * 
     * @param cluster 集群名称
     * @param capacityTB 总容量（TB）
     * @param usedTB 已使用容量（TB）
     * @param fileCount 文件数
     * @param blockCount 块数
     */
    void updateHdfsStatus(String cluster, Long capacityTB, Long usedTB, Long fileCount, Long blockCount);
    
    /**
     * 更新YARN状态
     * 
     * @param cluster 集群名称
     * @param nmCount NodeManager数量
     * @param totalMemoryGB 总内存（GB）
     * @param totalVcores 总CPU核心数
     */
    void updateYarnStatus(String cluster, Integer nmCount, Long totalMemoryGB, Integer totalVcores);
    
    /**
     * 更新今日统计
     * 
     * @param cluster 集群名称
     * @param alertCount 告警数
     * @param anomalyCount 异常数
     * @param avgLoad 平均负载
     */
    void updateDailyStats(String cluster, Integer alertCount, Integer anomalyCount, Double avgLoad);
    
    /**
     * 删除指定日期之前的数据
     * 
     * @param beforeDate 截止日期
     * @return 删除的记录数
     */
    int deleteBeforeDate(LocalDate beforeDate);
}
