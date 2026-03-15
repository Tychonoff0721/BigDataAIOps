package com.example.routing

import com.github.benmanes.caffeine.cache.{Cache, Caffeine}
import org.slf4j.{Logger, LoggerFactory}

import java.io.{File, FileInputStream, InputStreamReader}
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import scala.collection.concurrent.TrieMap

/**
 * 文件模式 Metadata Provider
 *
 * 从本地 JSON 配置文件读取库→集群映射关系
 * 表的 location 和 format 从对应集群的 HMS 获取
 *
 * 配置文件格式示例 (routing-metadata.json):
 * {
 *   "defaultCluster": "hiveA",
 *   "databases": {
 *     "orders_db": "hiveA",
 *     "users_db": "hiveB",
 *     "logs_db": "hiveC"
 *   }
 * }
 */
class FileMetadataProvider(
    filePath: String,
    cacheTtlSeconds: Long,
    metrics: RoutingMetrics
) extends MetadataProvider {

    private val logger: Logger = LoggerFactory.getLogger(classOf[FileMetadataProvider])

    // 数据库→集群映射
    private val databaseToCluster: TrieMap[String, String] = TrieMap.empty

    // 默认集群
    private var defaultCluster: String = "hiveA"

    // Caffeine 缓存（缓存数据库查询结果）
    private val metadataCache: Cache[String, TableMetadata] = Caffeine.newBuilder()
        .expireAfterWrite(cacheTtlSeconds, TimeUnit.SECONDS)
        .maximumSize(10000)
        .recordStats()
        .build()

    // 初始化时加载配置文件
    loadConfig()

    /**
     * 加载配置文件
     */
    private def loadConfig(): Unit = {
        logger.info(s"Loading metadata config from file: $filePath")
        
        val file = new File(filePath)
        if (!file.exists()) {
            throw new RuntimeException(s"Metadata config file not found: $filePath")
        }

        logger.info(s"File exists, absolute path: ${file.getAbsolutePath}")

        // 使用 java.nio.file.Files 读取文件内容
        import java.nio.file.{Files, Paths}
        val path = Paths.get(filePath)
        val bytes = Files.readAllBytes(path)
        val content = new String(bytes, StandardCharsets.UTF_8)
        
        logger.info(s"File content length: ${content.length}")
        logger.info(s"File content: [$content]")
        
        parseConfig(content)
        logger.info(s"Loaded ${databaseToCluster.size} database mappings")
    }

    /**
     * 解析配置文件内容
     */
    private def parseConfig(json: String): Unit = {
        logger.info(s"Parsing config, json length: ${json.length}")
        
        // 解析默认集群
        defaultCluster = extractJsonValue(json, "defaultCluster", "hiveA")
        logger.info(s"Default cluster: $defaultCluster")

        // 解析 databases 块
        val databasesStart = json.indexOf("\"databases\"")
        logger.info(s"'databases' search result: $databasesStart")
        
        if (databasesStart == -1) {
            // 尝试不同的查找方式
            val altStart = json.indexOf("databases")
            logger.warn(s"Alternative 'databases' search result: $altStart")
            // 使用 substring 代替 take
            val preview = if (json.length > 200) json.substring(0, 200) else json
            logger.warn(s"JSON content preview: [$preview]")
            logger.warn("No 'databases' section found in config file")
            return
        }

        // 找到 databases 对象的起始和结束位置
        val databasesObjStart = json.indexOf("{", databasesStart + 11)
        var braceCount = 0
        var databasesObjEnd = databasesObjStart
        
        for (i <- databasesObjStart until json.length) {
            if (json.charAt(i) == '{') braceCount += 1
            else if (json.charAt(i) == '}') {
                braceCount -= 1
                if (braceCount == 0) {
                    databasesObjEnd = i
                }
            }
        }

        val databasesJson = json.substring(databasesObjStart, databasesObjEnd + 1)
        parseDatabases(databasesJson)
    }

    /**
     * 解析 databases 对象
     */
    private def parseDatabases(json: String): Unit = {
        // 匹配 "db_name": "cluster_name" 格式
        val pattern = "\"([a-zA-Z_][a-zA-Z0-9_]*)\"\\s*:\\s*\"([a-zA-Z_][a-zA-Z0-9_]*)\"".r
        
        pattern.findAllMatchIn(json).foreach { m =>
            val dbName = m.group(1)
            val clusterName = m.group(2)
            databaseToCluster.put(dbName, clusterName)
            logger.debug(s"Mapped database '$dbName' to cluster '$clusterName'")
        }
    }

    /**
     * 从 JSON 中提取字符串值，带默认值
     */
    private def extractJsonValue(json: String, field: String, default: String): String = {
        try {
            val patternStr = "\"" + field + "\"\\s*:\\s*\"([^\"]+)\""
            val pattern = patternStr.r
            pattern.findFirstMatchIn(json) match {
                case Some(m) => m.group(1)
                case None => default
            }
        } catch {
            case _: Exception => default
        }
    }

    /**
     * 获取数据库所在的集群
     *
     * @param db    数据库名
     * @param table 表名（文件模式下不使用，按数据库粒度路由）
     * @return 集群名称
     */
    override def getTableMetadata(db: String, table: String): TableMetadata = {
        val cacheKey = db

        // 先查缓存
        val cached = metadataCache.getIfPresent(cacheKey)
        if (cached != null) {
            metrics.recordMetadataCacheHit()
            logger.debug(s"Cache hit for database: $cacheKey")
            return cached
        }

        metrics.recordMetadataCacheMiss()
        logger.debug(s"Cache miss for database: $cacheKey, looking up cluster mapping")

        // 从映射中获取集群
        val cluster = databaseToCluster.getOrElse(db, {
            logger.warn(s"Database '$db' not found in config, using default cluster: $defaultCluster")
            defaultCluster
        })

        val metadata = TableMetadata(cluster = cluster)
        
        // 写入缓存
        metadataCache.put(cacheKey, metadata)
        metadata
    }

    /**
     * 清除数据库缓存
     */
    override def invalidateCache(db: String, table: String): Unit = {
        val cacheKey = db
        metadataCache.invalidate(cacheKey)
        logger.info(s"Cache invalidated for database: $cacheKey")
    }

    /**
     * 清除所有缓存
     */
    override def invalidateAllCache(): Unit = {
        metadataCache.invalidateAll()
        logger.info("All cache invalidated")
    }

    /**
     * 重新加载配置文件
     */
    def reloadConfig(): Unit = {
        databaseToCluster.clear()
        loadConfig()
        invalidateAllCache()
    }

    /**
     * 关闭 Provider
     */
    override def close(): Unit = {
        databaseToCluster.clear()
        logger.info("FileMetadataProvider closed")
    }
}
