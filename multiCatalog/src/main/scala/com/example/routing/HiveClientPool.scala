package com.example.routing

import org.apache.commons.pool2.impl.{GenericObjectPool, GenericObjectPoolConfig}
import org.apache.hadoop.hive.metastore.IMetaStoreClient
import org.slf4j.{Logger, LoggerFactory}

import scala.util.{Failure, Success, Try}

/**
 * Hive Metastore Client 连接池
 *
 * 封装 Commons Pool2 的 GenericObjectPool
 * 提供便捷的 borrow/return 操作
 */
class HiveClientPool(
    metastoreUri: String,
    maxTotal: Int,
    maxIdle: Int,
    minIdle: Int
) {

    private val logger: Logger = LoggerFactory.getLogger(classOf[HiveClientPool])

    // 创建工厂
    private val factory = new HiveClientFactory(metastoreUri)

    // 配置连接池参数
    private val poolConfig = new GenericObjectPoolConfig[IMetaStoreClient]()
    poolConfig.setMaxTotal(maxTotal)
    poolConfig.setMaxIdle(maxIdle)
    poolConfig.setMinIdle(minIdle)
    poolConfig.setTestOnBorrow(true)        // 借出时验证
    poolConfig.setTestOnReturn(false)       // 归还时不验证
    poolConfig.setTestWhileIdle(true)       // 空闲时验证
    poolConfig.setTimeBetweenEvictionRunsMillis(60000)  // 每分钟检查一次空闲连接
    poolConfig.setMinEvictableIdleTimeMillis(300000)    // 5分钟空闲后可被驱逐
    poolConfig.setBlockWhenExhausted(true)  // 连接耗尽时阻塞等待
    poolConfig.setMaxWaitMillis(30000)      // 最大等待30秒

    // 创建连接池
    private val pool: GenericObjectPool[IMetaStoreClient] = 
        new GenericObjectPool[IMetaStoreClient](factory, poolConfig)

    /**
     * 从连接池获取客户端
     *
     * 使用模式:
     * val client = pool.borrowClient()
     * try {
     *   // 使用 client
     * } finally {
     *   pool.returnClient(client)
     * }
     *
     * 或者使用 withClient 方法自动管理
     */
    def borrowClient(): IMetaStoreClient = {
        try {
            val client = pool.borrowObject()
            logger.debug(s"Borrowed HiveMetaStoreClient from pool, active: ${pool.getNumActive}, idle: ${pool.getNumIdle}")
            client
        } catch {
            case e: Exception =>
                logger.error(s"Failed to borrow HiveMetaStoreClient from pool", e)
                throw new RuntimeException(s"Failed to get Hive client from pool: ${e.getMessage}", e)
        }
    }

    /**
     * 归还客户端到连接池
     */
    def returnClient(client: IMetaStoreClient): Unit = {
        if (client != null) {
            try {
                pool.returnObject(client)
                logger.debug(s"Returned HiveMetaStoreClient to pool, active: ${pool.getNumActive}, idle: ${pool.getNumIdle}")
            } catch {
                case e: Exception =>
                    logger.warn("Error returning HiveMetaStoreClient to pool", e)
            }
        }
    }

    /**
     * 处理异常的客户端（使其失效）
     */
    def invalidateClient(client: IMetaStoreClient): Unit = {
        if (client != null) {
            try {
                pool.invalidateObject(client)
                logger.warn("Invalidated HiveMetaStoreClient from pool")
            } catch {
                case e: Exception =>
                    logger.warn("Error invalidating HiveMetaStoreClient", e)
            }
        }
    }

    /**
     * 使用客户端执行操作（自动管理借出和归还）
     *
     * @param f 使用客户端执行的函数
     * @return 函数执行结果
     */
    def withClient[T](f: IMetaStoreClient => T): T = {
        var client: IMetaStoreClient = null
        try {
            client = borrowClient()
            f(client)
        } catch {
            case e: Exception =>
                if (client != null) {
                    invalidateClient(client)
                    client = null
                }
                throw e
        } finally {
            if (client != null) {
                returnClient(client)
            }
        }
    }

    /**
     * 获取连接池状态
     */
    def getPoolStats: (Int, Int) = {
        (pool.getNumActive, pool.getNumIdle)
    }

    /**
     * 获取活跃连接数
     */
    def getNumActive: Int = pool.getNumActive

    /**
     * 获取空闲连接数
     */
    def getNumIdle: Int = pool.getNumIdle

    /**
     * 关闭连接池
     */
    def close(): Unit = {
        try {
            pool.close()
            logger.info(s"HiveClientPool closed for uri: $metastoreUri")
        } catch {
            case e: Exception =>
                logger.error("Error closing HiveClientPool", e)
        }
    }
}
