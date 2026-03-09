package com.aiops.bigdata.entity.common.enums;

/**
 * 大数据服务类型枚举
 */
public enum ServiceType {
    
    HDFS("hdfs", "Hadoop分布式文件系统"),
    YARN("yarn", "Yet Another Resource Negotiator"),
    SPARK("spark", "Apache Spark"),
    HIVE("hive", "Apache Hive"),
    KAFKA("kafka", "Apache Kafka"),
    FLINK("flink", "Apache Flink"),
    HBASE("hbase", "Apache HBase"),
    ZOOKEEPER("zookeeper", "Apache ZooKeeper");
    
    private final String code;
    private final String description;
    
    ServiceType(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    public static ServiceType fromCode(String code) {
        for (ServiceType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }
}
