package com.aiops.bigdata.service.tool.impl;

import com.aiops.bigdata.entity.context.LongTermStatus;
import com.aiops.bigdata.service.context.LongTermStatusService;
import com.aiops.bigdata.service.tool.LongTermStatusTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 长期状态查询工具实现
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LongTermStatusToolImpl implements LongTermStatusTool {
    
    private final LongTermStatusService longTermStatusService;
    
    @Override
    public LongTermStatus execute(String cluster, LocalDate date) {
        log.info("执行LongTermStatusTool: cluster={}, date={}", cluster, date);
        
        try {
            if (date != null) {
                return longTermStatusService.get(cluster, date).orElse(null);
            } else {
                return longTermStatusService.getLatest(cluster).orElse(null);
            }
        } catch (Exception e) {
            log.error("查询长期状态失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    @Override
    public List<LongTermStatus> executeRecent(String cluster, int days) {
        log.info("执行LongTermStatusTool(Recent): cluster={}, days={}", cluster, days);
        
        try {
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(days);
            return longTermStatusService.getByDateRange(cluster, startDate, endDate);
        } catch (Exception e) {
            log.error("查询长期状态失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    @Override
    public String getClusterOverview(String cluster) {
        log.info("获取集群概览: cluster={}", cluster);
        
        try {
            Optional<LongTermStatus> statusOpt = longTermStatusService.getLatest(cluster);
            
            if (statusOpt.isEmpty()) {
                return "暂无集群 " + cluster + " 的长期状态数据";
            }
            
            LongTermStatus status = statusOpt.get();
            
            StringBuilder sb = new StringBuilder();
            sb.append("集群概览: ").append(cluster).append("\n");
            sb.append("更新时间: ").append(status.getStatusDate()).append("\n\n");
            
            sb.append("【基本信息】\n");
            sb.append(String.format("节点数: %d (活跃: %d)\n", 
                status.getTotalNodes() != null ? status.getTotalNodes() : 0,
                status.getActiveNodes() != null ? status.getActiveNodes() : 0));
            sb.append(String.format("总CPU: %d核, 总内存: %dGB\n",
                status.getTotalCpuCores() != null ? status.getTotalCpuCores() : 0,
                status.getTotalMemoryGB() != null ? status.getTotalMemoryGB() : 0));
            
            if (status.getHdfsCapacityTB() != null) {
                sb.append("\n【HDFS状态】\n");
                sb.append(String.format("容量: %dTB (使用: %dTB, %.1f%%)\n",
                    status.getHdfsCapacityTB(),
                    status.getHdfsUsedTB() != null ? status.getHdfsUsedTB() : 0,
                    status.getHdfsUsageRate() != null ? status.getHdfsUsageRate() * 100 : 0));
                sb.append(String.format("文件数: %d, 块数: %d\n",
                    status.getHdfsFileCount() != null ? status.getHdfsFileCount() : 0,
                    status.getHdfsBlockCount() != null ? status.getHdfsBlockCount() : 0));
                sb.append(String.format("NameNode: %d, DataNode: %d\n",
                    status.getNameNodeCount() != null ? status.getNameNodeCount() : 0,
                    status.getDataNodeCount() != null ? status.getDataNodeCount() : 0));
            }
            
            if (status.getYarnTotalMemoryGB() != null) {
                sb.append("\n【YARN状态】\n");
                sb.append(String.format("总内存: %dGB, 总vCores: %d\n",
                    status.getYarnTotalMemoryGB(),
                    status.getYarnTotalVcores() != null ? status.getYarnTotalVcores() : 0));
                sb.append(String.format("ResourceManager: %d, NodeManager: %d\n",
                    status.getRmCount() != null ? status.getRmCount() : 0,
                    status.getNmCount() != null ? status.getNmCount() : 0));
            }
            
            if (status.getKafkaBrokerCount() != null) {
                sb.append("\n【Kafka状态】\n");
                sb.append(String.format("Broker: %d, Topic: %d, 分区: %d\n",
                    status.getKafkaBrokerCount(),
                    status.getKafkaTopicCount() != null ? status.getKafkaTopicCount() : 0,
                    status.getKafkaPartitionCount() != null ? status.getKafkaPartitionCount() : 0));
            }
            
            if (status.getComponentVersions() != null && !status.getComponentVersions().isEmpty()) {
                sb.append("\n【组件版本】\n");
                status.getComponentVersions().forEach((comp, version) -> 
                    sb.append("  ").append(comp).append(": ").append(version).append("\n"));
            }
            
            sb.append("\n【今日统计】\n");
            sb.append(String.format("告警: %d, 异常: %d, 平均负载: %.1f%%\n",
                status.getAlertCountToday() != null ? status.getAlertCountToday() : 0,
                status.getAnomalyCountToday() != null ? status.getAnomalyCountToday() : 0,
                status.getAvgClusterLoad() != null ? status.getAvgClusterLoad() * 100 : 0));
            
            return sb.toString();
            
        } catch (Exception e) {
            log.error("获取集群概览失败: {}", e.getMessage(), e);
            return "获取集群概览失败: " + e.getMessage();
        }
    }
    
    @Override
    public String getResourceTrend(String cluster, int days) {
        log.info("获取资源趋势: cluster={}, days={}", cluster, days);
        
        try {
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(days);
            List<LongTermStatus> history = longTermStatusService.getByDateRange(cluster, startDate, endDate);
            
            if (history.isEmpty()) {
                return "暂无历史数据";
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append("资源使用趋势 (最近").append(days).append("天):\n\n");
            
            // HDFS趋势
            sb.append("HDFS使用率:\n");
            history.forEach(s -> {
                if (s.getHdfsUsageRate() != null) {
                    sb.append(String.format("  %s: %.1f%%\n", s.getStatusDate(), s.getHdfsUsageRate() * 100));
                }
            });
            
            // 平均负载趋势
            sb.append("\n平均集群负载:\n");
            history.forEach(s -> {
                if (s.getAvgClusterLoad() != null) {
                    sb.append(String.format("  %s: %.1f%%\n", s.getStatusDate(), s.getAvgClusterLoad() * 100));
                }
            });
            
            return sb.toString();
            
        } catch (Exception e) {
            log.error("获取资源趋势失败: {}", e.getMessage(), e);
            return "获取资源趋势失败: " + e.getMessage();
        }
    }
}
