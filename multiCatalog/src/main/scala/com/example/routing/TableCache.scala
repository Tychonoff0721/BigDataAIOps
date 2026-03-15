package com.example.routing

import com.github.benmanes.caffeine.cache.{Cache, Caffeine}
import org.apache.spark.sql.connector.catalog.Table
import org.slf4j.{Logger, LoggerFactory}

import java.util.concurrent.TimeUnit

/**
 * Table 缓存
 *
 * Spark 在 Analyzer 阶段会频繁调用 loadTable()
 * 需要缓存 Table 对象以避免重复查询 HMS
 *
 * 默认 TTL: 5 分钟
 */
class TableCache(ttlSeconds: Long) {

    private val logger: Logger = LoggerFactory.getLogger(classOf[TableCache])

    // Caffeine 缓存
    private val cache: Cache[String, Table] = Caffeine.newBuilder()
        .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
        .maximumSize(5000)
        .recordStats()
        .build()

    /**
     * 获取缓存的 Table
     *
     * @param db    数据库名
     * @param table 表名
     * @return 缓存的 Table，如果不存在返回 None
     */
    def get(db: String, table: String): Option[Table] = {
        val key = cacheKey(db, table)
        val cached = cache.getIfPresent(key)
        if (cached != null) {
            logger.debug(s"Table cache hit: $key")
            Some(cached)
        } else {
            logger.debug(s"Table cache miss: $key")
            None
        }
    }

    /**
     * 缓存 Table
     *
     * @param db    数据库名
     * @param table 表名
     * @param value Table 对象
     */
    def put(db: String, table: String, value: Table): Unit = {
        val key = cacheKey(db, table)
        cache.put(key, value)
        logger.debug(s"Table cached: $key")
    }

    /**
     * 获取或计算 Table
     *
     * @param db      数据库名
     * @param table   表名
     * @param loader  加载函数
     * @return Table 对象
     */
    def getOrElse(db: String, table: String)(loader: => Table): Table = {
        get(db, table) match {
            case Some(cached) => cached
            case None =>
                val value = loader
                put(db, table, value)
                value
        }
    }

    /**
     * 使缓存失效
     */
    def invalidate(db: String, table: String): Unit = {
        val key = cacheKey(db, table)
        cache.invalidate(key)
        logger.info(s"Table cache invalidated: $key")
    }

    /**
     * 使所有缓存失效
     */
    def invalidateAll(): Unit = {
        cache.invalidateAll()
        logger.info("All table cache invalidated")
    }

    /**
     * 获取缓存大小
     */
    def size: Long = cache.estimatedSize()

    /**
     * 获取缓存统计
     */
    def stats: (Long, Long, Double) = {
        val s = cache.stats()
        (s.hitCount(), s.missCount(), s.hitRate())
    }

    /**
     * 生成缓存 key
     */
    private def cacheKey(db: String, table: String): String = s"$db.$table"
}

/**
 * Hive 表信息缓存
 *
 * 缓存 Hive 表的元数据信息，避免频繁查询 HMS
 */
class HiveTableInfoCache(ttlSeconds: Long) {

    private val logger: Logger = LoggerFactory.getLogger(classOf[HiveTableInfoCache])

    // 缓存 Hive 表信息
    private val cache: Cache[String, HiveTableInfo] = Caffeine.newBuilder()
        .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
        .maximumSize(10000)
        .recordStats()
        .build()

    /**
     * 获取缓存的表信息
     */
    def get(db: String, table: String): Option[HiveTableInfo] = {
        val key = s"$db.$table"
        Option(cache.getIfPresent(key))
    }

    /**
     * 缓存表信息
     */
    def put(db: String, table: String, info: HiveTableInfo): Unit = {
        val key = s"$db.$table"
        cache.put(key, info)
        logger.debug(s"HiveTableInfo cached: $key")
    }

    /**
     * 使缓存失效
     */
    def invalidate(db: String, table: String): Unit = {
        val key = s"$db.$table"
        cache.invalidate(key)
    }

    /**
     * 获取缓存统计
     */
    def stats: (Long, Long, Double) = {
        val s = cache.stats()
        (s.hitCount(), s.missCount(), s.hitRate())
    }
}

/**
 * Hive 表信息
 *
 * @param database      数据库名
 * @param tableName     表名
 * @param location      表存储路径
 * @param inputFormat   输入格式
 * @param outputFormat  输出格式
 * @param serde         序列化/反序列化类
 * @param isPartitioned 是否分区表
 * @param schema        表 Schema (JSON 格式)
 */
case class HiveTableInfo(
    database: String,
    tableName: String,
    location: String,
    inputFormat: String,
    outputFormat: String,
    serde: String,
    isPartitioned: Boolean,
    schema: String,
    partitionColumns: Seq[String]
)
