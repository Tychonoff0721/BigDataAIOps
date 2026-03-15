package com.example.routing

import java.util.concurrent.atomic.{AtomicLong, LongAdder}
import java.util.concurrent.ConcurrentHashMap
import scala.collection.mutable

/**
 * Routing Catalog 统计指标
 *
 * 统计:
 * - Metadata Service 调用次数
 * - HMS 查询次数
 * - Cache 命中率
 *
 * 线程安全实现
 */
class RoutingMetrics {

    // Metadata Service 统计
    private val metadataServiceCallCount = new LongAdder()
    private val metadataCacheHitCount = new LongAdder()
    private val metadataCacheMissCount = new LongAdder()

    // HMS 查询统计
    private val hmsQueryCount = new LongAdder()
    private val hmsQueryErrorCount = new LongAdder()

    // 表加载统计
    private val tableLoadCount = new LongAdder()
    private val tableLoadErrorCount = new LongAdder()

    // 按集群统计 HMS 查询次数
    private val hmsQueryByCluster = new ConcurrentHashMap[String, LongAdder]()

    // 按数据库统计查询次数
    private val queryByDatabase = new ConcurrentHashMap[String, LongAdder]()

    // 启动时间
    private val startTime = System.currentTimeMillis()

    /**
     * 记录 Metadata Service 调用
     */
    def recordMetadataServiceCall(): Unit = {
        metadataServiceCallCount.increment()
    }

    /**
     * 记录 Metadata Cache 命中
     */
    def recordMetadataCacheHit(): Unit = {
        metadataCacheHitCount.increment()
    }

    /**
     * 记录 Metadata Cache 未命中
     */
    def recordMetadataCacheMiss(): Unit = {
        metadataCacheMissCount.increment()
    }

    /**
     * 记录 HMS 查询
     */
    def recordHmsQuery(cluster: String): Unit = {
        hmsQueryCount.increment()
        
        // 按集群统计
        hmsQueryByCluster.computeIfAbsent(cluster, _ => new LongAdder()).increment()
    }

    /**
     * 记录 HMS 查询错误
     */
    def recordHmsQueryError(): Unit = {
        hmsQueryErrorCount.increment()
    }

    /**
     * 记录表加载
     */
    def recordTableLoad(): Unit = {
        tableLoadCount.increment()
    }

    /**
     * 记录表加载错误
     */
    def recordTableLoadError(): Unit = {
        tableLoadErrorCount.increment()
    }

    /**
     * 记录数据库查询
     */
    def recordDatabaseQuery(db: String): Unit = {
        queryByDatabase.computeIfAbsent(db, _ => new LongAdder()).increment()
    }

    /**
     * 获取 Metadata Cache 命中率
     */
    def getMetadataCacheHitRate: Double = {
        val hits = metadataCacheHitCount.sum()
        val misses = metadataCacheMissCount.sum()
        val total = hits + misses
        if (total == 0) 0.0 else hits.toDouble / total
    }

    /**
     * 获取统计摘要
     */
    def getSummary: MetricsSummary = {
        MetricsSummary(
            startTime = startTime,
            uptimeMs = System.currentTimeMillis() - startTime,
            metadataServiceCalls = metadataServiceCallCount.sum(),
            metadataCacheHits = metadataCacheHitCount.sum(),
            metadataCacheMisses = metadataCacheMissCount.sum(),
            metadataCacheHitRate = getMetadataCacheHitRate,
            hmsQueries = hmsQueryCount.sum(),
            hmsQueryErrors = hmsQueryErrorCount.sum(),
            tableLoads = tableLoadCount.sum(),
            tableLoadErrors = tableLoadErrorCount.sum(),
            hmsQueriesByCluster = getHmsQueriesByCluster,
            queriesByDatabase = getQueriesByDatabase
        )
    }

    /**
     * 获取各集群 HMS 查询次数
     */
    private def getHmsQueriesByCluster: Map[String, Long] = {
        val result = mutable.Map[String, Long]()
        hmsQueryByCluster.forEach { (cluster, counter) =>
            result.put(cluster, counter.sum())
        }
        result.toMap
    }

    /**
     * 获取各数据库查询次数
     */
    private def getQueriesByDatabase: Map[String, Long] = {
        val result = mutable.Map[String, Long]()
        queryByDatabase.forEach { (db, counter) =>
            result.put(db, counter.sum())
        }
        result.toMap
    }

    /**
     * 重置所有统计
     */
    def reset(): Unit = {
        metadataServiceCallCount.reset()
        metadataCacheHitCount.reset()
        metadataCacheMissCount.reset()
        hmsQueryCount.reset()
        hmsQueryErrorCount.reset()
        tableLoadCount.reset()
        tableLoadErrorCount.reset()
        hmsQueryByCluster.clear()
        queryByDatabase.clear()
    }

    /**
     * 打印统计信息
     */
    def printSummary(): String = {
        val summary = getSummary
        s"""
           |=== Routing Catalog Metrics ===
           |Uptime: ${summary.uptimeMs / 1000} seconds
           |
           |Metadata Service:
           |  Calls: ${summary.metadataServiceCalls}
           |  Cache Hits: ${summary.metadataCacheHits}
           |  Cache Misses: ${summary.metadataCacheMisses}
           |  Cache Hit Rate: ${"%.2f".format(summary.metadataCacheHitRate * 100)}%
           |
           |HMS Queries:
           |  Total: ${summary.hmsQueries}
           |  Errors: ${summary.hmsQueryErrors}
           |  By Cluster: ${summary.hmsQueriesByCluster}
           |
           |Table Operations:
           |  Loads: ${summary.tableLoads}
           |  Errors: ${summary.tableLoadErrors}
           |
           |Queries by Database: ${summary.queriesByDatabase}
           |===============================
           |""".stripMargin
    }
}

/**
 * 统计摘要
 */
case class MetricsSummary(
    startTime: Long,
    uptimeMs: Long,
    metadataServiceCalls: Long,
    metadataCacheHits: Long,
    metadataCacheMisses: Long,
    metadataCacheHitRate: Double,
    hmsQueries: Long,
    hmsQueryErrors: Long,
    tableLoads: Long,
    tableLoadErrors: Long,
    hmsQueriesByCluster: Map[String, Long],
    queriesByDatabase: Map[String, Long]
)

/**
 * 全局 Metrics 实例
 */
object RoutingMetrics {
    private val instance = new RoutingMetrics()

    def apply(): RoutingMetrics = instance
    def get: RoutingMetrics = instance
}
