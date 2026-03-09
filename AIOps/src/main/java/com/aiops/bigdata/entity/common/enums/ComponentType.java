package com.aiops.bigdata.entity.common.enums;

/**
 * 组件类型枚举
 * 定义各服务下的具体组件
 */
public enum ComponentType {
    
    // HDFS组件
    NAMENODE("namenode", ServiceType.HDFS, "NameNode"),
    DATANODE("datanode", ServiceType.HDFS, "DataNode"),
    JOURNALNODE("journalnode", ServiceType.HDFS, "JournalNode"),
    ZKFC("zkfc", ServiceType.HDFS, "ZKFailoverController"),
    
    // YARN组件
    RESOURCEMANAGER("resourcemanager", ServiceType.YARN, "ResourceManager"),
    NODEMANAGER("nodemanager", ServiceType.YARN, "NodeManager"),
    
    // Spark组件
    SPARK_MASTER("master", ServiceType.SPARK, "Spark Master"),
    SPARK_HISTORY_SERVER("history-server", ServiceType.SPARK, "Spark History Server"),
    SPARK_DRIVER("driver", ServiceType.SPARK, "Spark Driver"),
    SPARK_EXECUTOR("executor", ServiceType.SPARK, "Spark Executor"),
    
    // Hive组件
    HIVESERVER2("hiveserver2", ServiceType.HIVE, "HiveServer2"),
    HIVEMETASTORE("metastore", ServiceType.HIVE, "Hive Metastore"),
    
    // Kafka组件
    KAFKA_BROKER("broker", ServiceType.KAFKA, "Kafka Broker"),
    
    // Flink组件
    FLINK_JOBMANAGER("jobmanager", ServiceType.FLINK, "Flink JobManager"),
    FLINK_TASKMANAGER("taskmanager", ServiceType.FLINK, "Flink TaskManager"),
    
    // HBase组件
    HMASTER("hmaster", ServiceType.HBASE, "HBase Master"),
    HREGIONSERVER("regionserver", ServiceType.HBASE, "HBase RegionServer"),
    
    // ZooKeeper组件
    ZOOKEEPER_SERVER("server", ServiceType.ZOOKEEPER, "ZooKeeper Server");
    
    private final String code;
    private final ServiceType service;
    private final String description;
    
    ComponentType(String code, ServiceType service, String description) {
        this.code = code;
        this.service = service;
        this.description = description;
    }
    
    public String getCode() {
        return code;
    }
    
    public ServiceType getService() {
        return service;
    }
    
    public String getDescription() {
        return description;
    }
    
    public static ComponentType fromCode(String code) {
        for (ComponentType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }
}
