package com.example.routing

import org.apache.spark.SparkConf

/**
 * Routing Catalog 配置类
 * 解析 Spark 配置中的路由相关参数
 *
 * 配置示例:
 * spark.routing.hive.clusters=hiveA,hiveB
 * spark.routing.hive.hiveA.uri=thrift://hmsA:9083
 * spark.routing.hive.hiveB.uri=thrift://hmsB:9083
 * spark.routing.metadata.service=http://localhost:8080
 * spark.routing.metadata.useFile=false
 * spark.routing.metadata.file=/path/to/routing-metadata.json
 */
case class RoutingConfig(
    clusters: Map[String, HiveClusterConfig],
    metadataServiceUrl: String,
    metadataUseFile: Boolean,
    metadataFilePath: String,
    metadataCacheTtlSeconds: Long,
    schemaCacheTtlSeconds: Long,
    broadcastMaxTableSize: Long,
    poolMaxTotal: Int,
    poolMaxIdle: Int,
    poolMinIdle: Int
)

case class HiveClusterConfig(
    name: String,
    metastoreUri: String
)

object RoutingConfig {

    // 配置键前缀
    private val PREFIX = "spark.routing"
    private val HIVE_PREFIX = s"$PREFIX.hive"

    // 默认值
    private val DEFAULT_METADATA_CACHE_TTL = 60L      // 60秒
    private val DEFAULT_SCHEMA_CACHE_TTL = 300L       // 5分钟
    private val DEFAULT_BROADCAST_MAX_SIZE = 100L * 1024 * 1024  // 100MB
    private val DEFAULT_POOL_MAX_TOTAL = 10
    private val DEFAULT_POOL_MAX_IDLE = 5
    private val DEFAULT_POOL_MIN_IDLE = 1

    /**
     * 从 SparkConf 解析配置
     */
    def fromSparkConf(conf: SparkConf): RoutingConfig = {
        val clusters = parseClusterConfigs(conf)
        val metadataUseFile = conf.getBoolean(s"$PREFIX.metadata.useFile", false)
        val metadataServiceUrl = conf.get(s"$PREFIX.metadata.service", "")
        val metadataFilePath = conf.get(s"$PREFIX.metadata.file", "")
        
        require(clusters.nonEmpty, 
            s"At least one Hive cluster must be configured via $HIVE_PREFIX.clusters")
        
        // 根据模式校验配置
        if (metadataUseFile) {
            require(metadataFilePath.nonEmpty, 
                s"Metadata file path must be configured when useFile=true via $PREFIX.metadata.file")
        } else {
            require(metadataServiceUrl.nonEmpty, 
                s"Metadata service URL must be configured via $PREFIX.metadata.service")
        }

        RoutingConfig(
            clusters = clusters,
            metadataServiceUrl = metadataServiceUrl,
            metadataUseFile = metadataUseFile,
            metadataFilePath = metadataFilePath,
            metadataCacheTtlSeconds = conf.getLong(s"$PREFIX.cache.metadata.ttl", DEFAULT_METADATA_CACHE_TTL),
            schemaCacheTtlSeconds = conf.getLong(s"$PREFIX.cache.schema.ttl", DEFAULT_SCHEMA_CACHE_TTL),
            broadcastMaxTableSize = conf.getLong(s"$PREFIX.broadcast.maxTableSize", DEFAULT_BROADCAST_MAX_SIZE),
            poolMaxTotal = conf.getInt(s"$PREFIX.pool.maxTotal", DEFAULT_POOL_MAX_TOTAL),
            poolMaxIdle = conf.getInt(s"$PREFIX.pool.maxIdle", DEFAULT_POOL_MAX_IDLE),
            poolMinIdle = conf.getInt(s"$PREFIX.pool.minIdle", DEFAULT_POOL_MIN_IDLE)
        )
    }

    /**
     * 解析 Hive 集群配置
     */
    private def parseClusterConfigs(conf: SparkConf): Map[String, HiveClusterConfig] = {
        val clusterNamesStr = conf.get(s"$HIVE_PREFIX.clusters", "")
        if (clusterNamesStr.isEmpty) {
            return Map.empty
        }

        val clusterNames = clusterNamesStr.split(",").map(_.trim).filter(_.nonEmpty)
        
        clusterNames.map { name =>
            val uriKey = s"$HIVE_PREFIX.$name.uri"
            val uri = conf.get(uriKey, "")
            require(uri.nonEmpty, 
                s"Hive cluster '$name' must have metastore URI configured via $uriKey")
            name -> HiveClusterConfig(name, uri)
        }.toMap
    }
}
