package com.aiops.bigdata.service.ai.function;

import com.aiops.bigdata.service.context.LongTermStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * Spring AI Function: 获取集群长期状态
 */
@Slf4j
@Component("getLongTermStatus")
@Description("获取集群的长期运行状态，包括节点资源、HDFS状态、YARN状态、Spark作业统计等。参数: cluster(集群名称，必填)")
@RequiredArgsConstructor
public class LongTermStatusFunction 
        implements Function<LongTermStatusFunction.Request, LongTermStatusFunction.Response> {
    
    private final LongTermStatusService longTermStatusService;
    
    @Override
    public Response apply(Request request) {
        log.info("执行长期状态查询: cluster={}", request.cluster());
        
        try {
            if (request.cluster() == null || request.cluster().isEmpty()) {
                return new Response("错误: cluster 参数不能为空", false);
            }
            
            var statusOpt = longTermStatusService.getLatest(request.cluster());
            
            if (statusOpt.isEmpty()) {
                return new Response("未找到集群长期状态数据: " + request.cluster(), true);
            }
            
            var status = statusOpt.get();
            StringBuilder sb = new StringBuilder();
            sb.append("=== 集群长期状态 ===\n\n");
            sb.append(String.format("集群: %s\n", status.getCluster()));
            sb.append(String.format("状态日期: %s\n", status.getStatusDate()));
            sb.append(String.format("更新时间: %s\n\n", status.getUpdateTime()));
            
            // 集群资源信息
            sb.append("=== 集群资源 ===\n");
            sb.append(String.format("节点: %d/%d 活跃\n", 
                status.getActiveNodes(), status.getTotalNodes()));
            sb.append(String.format("CPU核心: %d\n", status.getTotalCpuCores()));
            sb.append(String.format("内存: %d GB\n", status.getTotalMemoryGB()));
            sb.append(String.format("磁盘: %d TB\n\n", status.getTotalDiskTB()));
            
            // HDFS状态
            sb.append("=== HDFS状态 ===\n");
            sb.append(String.format("容量: %d/%d TB (使用率: %.1f%%)\n",
                status.getHdfsUsedTB(), status.getHdfsCapacityTB(), 
                status.getHdfsUsageRate() != null ? status.getHdfsUsageRate() * 100 : 0));
            sb.append(String.format("文件数: %d, 块数: %d\n",
                status.getHdfsFileCount(), status.getHdfsBlockCount()));
            sb.append(String.format("NameNode: %d, DataNode: %d\n\n",
                status.getNameNodeCount(), status.getDataNodeCount()));
            
            // YARN状态
            sb.append("=== YARN状态 ===\n");
            sb.append(String.format("ResourceManager: %d, NodeManager: %d\n",
                status.getRmCount(), status.getNmCount()));
            sb.append(String.format("总内存: %d GB, 总vCore: %d\n\n",
                status.getYarnTotalMemoryGB(), status.getYarnTotalVcores()));
            
            // Spark状态
            sb.append("=== Spark状态 ===\n");
            sb.append(String.format("今日作业: %d (失败: %d)\n",
                status.getSparkJobCountToday(), status.getSparkFailedJobsToday()));
            sb.append(String.format("平均执行时长: %.1f 分钟\n\n",
                status.getSparkAvgJobDuration() != null ? status.getSparkAvgJobDuration() : 0));
            
            // 告警统计
            sb.append("=== 告警统计 ===\n");
            sb.append(String.format("今日告警: %d, 今日异常: %d\n",
                status.getAlertCountToday(), status.getAnomalyCountToday()));
            sb.append(String.format("平均集群负载: %.1f%%\n",
                status.getAvgClusterLoad() != null ? status.getAvgClusterLoad() : 0));
            
            return new Response(sb.toString(), true);
            
        } catch (Exception e) {
            log.error("长期状态查询失败: {}", e.getMessage(), e);
            return new Response("查询失败: " + e.getMessage(), false);
        }
    }
    
    /**
     * 请求参数
     */
    public record Request(
        String cluster        // 集群名称（必填）
    ) {}
    
    /**
     * 响应结果
     */
    public record Response(
        String content,        // 查询结果内容
        boolean success        // 是否成功
    ) {}
}
