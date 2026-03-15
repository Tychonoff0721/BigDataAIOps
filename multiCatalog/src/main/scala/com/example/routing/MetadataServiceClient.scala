package com.example.routing

import com.github.benmanes.caffeine.cache.{Cache, Caffeine}
import okhttp3.{OkHttpClient, Request, Response}
import org.slf4j.{Logger, LoggerFactory}

import java.util.concurrent.TimeUnit
import scala.util.{Failure, Success, Try}

/**
 * 表元数据信息
 *
 * 只包含集群信息，location和format从HMS获取
 *
 * @param cluster 表所在的 Hive 集群名称
 */
case class TableMetadata(
    cluster: String
)

/**
 * Metadata Service 客户端
 *
 * 用于查询数据库所在的 Hive 集群信息
 * 使用 OkHttp 进行 HTTP 调用
 * 使用 Caffeine 进行本地缓存
 *
 * HTTP 接口:
 * GET /database?db={db}
 *
 * 返回 JSON:
 * {
 *   "cluster": "hiveA"
 * }
 */
class MetadataServiceClient(
    serviceUrl: String,
    cacheTtlSeconds: Long,
    metrics: RoutingMetrics
) extends MetadataProvider {

    private val logger: Logger = LoggerFactory.getLogger(classOf[MetadataServiceClient])

    // OkHttp 客户端 - 线程安全
    private val httpClient: OkHttpClient = new OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    // Caffeine 缓存 - 线程安全
    private val metadataCache: Cache[String, TableMetadata] = Caffeine.newBuilder()
        .expireAfterWrite(cacheTtlSeconds, TimeUnit.SECONDS)
        .maximumSize(10000)
        .recordStats()  // 开启统计
        .build()

    /**
     * 获取数据库所在的集群
     *
     * @param db    数据库名
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
        logger.debug(s"Cache miss for database: $cacheKey, querying metadata service")

        // 调用 Metadata Service
        val metadata = fetchClusterFromService(db)
        
        // 写入缓存
        metadataCache.put(cacheKey, metadata)
        
        metadata
    }

    /**
     * 从 Metadata Service 获取数据库所在集群
     */
    private def fetchClusterFromService(db: String): TableMetadata = {
        val url = s"$serviceUrl/database?db=$db"
        logger.debug(s"Requesting cluster info from: $url")

        val request = new Request.Builder()
            .url(url)
            .get()
            .build()

        var response: Response = null
        try {
            response = httpClient.newCall(request).execute()
            metrics.recordMetadataServiceCall()

            if (!response.isSuccessful) {
                val errorMsg = s"Failed to get cluster for db $db, status: ${response.code}"
                logger.error(errorMsg)
                throw new RuntimeException(errorMsg)
            }

            val responseBody = response.body()
            if (responseBody == null) {
                throw new RuntimeException(s"Empty response body for db $db")
            }

            val json = responseBody.string()
            parseClusterMetadata(json)

        } catch {
            case e: Exception =>
                logger.error(s"Error fetching cluster for db $db", e)
                throw new RuntimeException(s"Failed to get cluster: ${e.getMessage}", e)
        } finally {
            if (response != null) {
                response.close()
            }
        }
    }

    /**
     * 解析 JSON 响应，只提取 cluster 字段
     */
    private def parseClusterMetadata(json: String): TableMetadata = {
        val cluster = extractJsonValue(json, "cluster")
        TableMetadata(cluster = cluster)
    }

    /**
     * 从 JSON 字符串中提取指定字段的值
     */
    private def extractJsonValue(json: String, field: String): String = {
        val pattern = s""""$field"\s*:\s*"([^"]+)"""".r
        pattern.findFirstMatchIn(json) match {
            case Some(m) => m.group(1)
            case None =>
                // 尝试非字符串值
                val pattern2 = s""""$field"\s*:\s*([^,}\\s]+)""".r
                pattern2.findFirstMatchIn(json) match {
                    case Some(m) => m.group(1).trim
                    case None => throw new RuntimeException(s"Field '$field' not found in JSON: $json")
                }
        }
    }

    /**
     * 获取缓存统计信息
     */
    def getCacheStats: (Long, Long, Double) = {
        val stats = metadataCache.stats()
        (stats.hitCount(), stats.missCount(), stats.hitRate())
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
     * 关闭客户端，释放资源
     */
    override def close(): Unit = {
        httpClient.dispatcher().executorService().shutdown()
        httpClient.connectionPool().evictAll()
        logger.info("MetadataServiceClient closed")
    }
}
