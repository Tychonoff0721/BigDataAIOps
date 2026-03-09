-- AIOps数据库初始化脚本

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS aiops 
DEFAULT CHARACTER SET utf8mb4 
COLLATE utf8mb4_unicode_ci;

USE aiops;

-- 长期状态表
CREATE TABLE IF NOT EXISTS long_term_status (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cluster_name VARCHAR(100) NOT NULL COMMENT '集群名称',
    status_date DATE NOT NULL COMMENT '状态日期',
    
    -- 基本信息
    total_nodes INT COMMENT '总节点数',
    active_nodes INT COMMENT '活跃节点数',
    total_cpu_cores INT COMMENT '总CPU核心数',
    total_memory_gb BIGINT COMMENT '总内存(GB)',
    
    -- HDFS状态
    hdfs_capacity_tb BIGINT COMMENT 'HDFS容量(TB)',
    hdfs_used_tb BIGINT COMMENT 'HDFS已使用(TB)',
    hdfs_usage_rate DOUBLE COMMENT 'HDFS使用率',
    hdfs_file_count BIGINT COMMENT 'HDFS文件数',
    hdfs_block_count BIGINT COMMENT 'HDFS块数',
    namenode_count INT COMMENT 'NameNode数量',
    datanode_count INT COMMENT 'DataNode数量',
    
    -- YARN状态
    rm_count INT COMMENT 'ResourceManager数量',
    nm_count INT COMMENT 'NodeManager数量',
    yarn_total_memory_gb BIGINT COMMENT 'YARN总内存(GB)',
    yarn_total_vcores INT COMMENT 'YARN总vCores',
    
    -- Kafka状态
    kafka_broker_count INT COMMENT 'Kafka Broker数量',
    kafka_topic_count INT COMMENT 'Kafka Topic数量',
    kafka_partition_count INT COMMENT 'Kafka分区数',
    
    -- 统计信息
    alert_count_today INT COMMENT '今日告警数',
    anomaly_count_today INT COMMENT '今日异常数',
    avg_cluster_load DOUBLE COMMENT '平均集群负载',
    
    -- 其他
    component_versions TEXT COMMENT '组件版本(JSON)',
    update_time DATETIME COMMENT '更新时间',
    
    UNIQUE KEY uk_cluster_date (cluster_name, status_date),
    INDEX idx_status_date (status_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='集群长期状态表';

-- 分析结果表
CREATE TABLE IF NOT EXISTS analysis_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    result_id VARCHAR(64) NOT NULL COMMENT '结果ID',
    cluster VARCHAR(100) COMMENT '集群名称',
    analysis_time DATETIME NOT NULL COMMENT '分析时间',
    analysis_type VARCHAR(50) COMMENT '分析类型: component/spark_app',
    target_id VARCHAR(200) COMMENT '目标ID',
    target_name VARCHAR(200) COMMENT '目标名称',
    health_status VARCHAR(20) COMMENT '健康状态',
    health_score INT COMMENT '健康评分',
    diagnosis TEXT COMMENT '诊断信息(JSON)',
    recommendations TEXT COMMENT '建议列表(JSON)',
    raw_response TEXT COMMENT '原始响应',
    analysis_duration BIGINT COMMENT '分析耗时(ms)',
    model_name VARCHAR(100) COMMENT '模型名称',
    
    INDEX idx_cluster_time (cluster, analysis_time),
    INDEX idx_analysis_type (analysis_type),
    INDEX idx_health_status (health_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分析结果表';

-- 插入示例数据
INSERT INTO long_term_status (cluster_name, status_date, total_nodes, active_nodes, total_cpu_cores, total_memory_gb,
    hdfs_capacity_tb, hdfs_used_tb, hdfs_usage_rate, hdfs_file_count, hdfs_block_count, namenode_count, datanode_count,
    rm_count, nm_count, yarn_total_memory_gb, yarn_total_vcores,
    kafka_broker_count, kafka_topic_count, kafka_partition_count,
    alert_count_today, anomaly_count_today, avg_cluster_load, update_time)
VALUES 
('bigdata-prod', CURDATE(), 50, 48, 800, 3200,
    500, 350, 0.70, 5000000, 15000000, 2, 48,
    2, 48, 2400, 800,
    5, 200, 1000,
    5, 3, 0.65, NOW()),
('bigdata-test', CURDATE(), 20, 18, 320, 1280,
    200, 80, 0.40, 1000000, 3000000, 2, 18,
    2, 18, 960, 320,
    3, 50, 200,
    2, 1, 0.45, NOW())
ON DUPLICATE KEY UPDATE 
    total_nodes = VALUES(total_nodes),
    update_time = NOW();
