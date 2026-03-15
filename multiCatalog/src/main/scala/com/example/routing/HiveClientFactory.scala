package com.example.routing

import org.apache.commons.pool2.{BasePooledObjectFactory, PooledObject}
import org.apache.commons.pool2.impl.{DefaultPooledObject, GenericObjectPool, GenericObjectPoolConfig}
import org.apache.hadoop.hive.conf.HiveConf
import org.apache.hadoop.hive.metastore.{HiveMetaStoreClient, IMetaStoreClient}
import org.slf4j.{Logger, LoggerFactory}

/**
 * Hive Metastore Client 工厂类
 *
 * 使用 Apache Commons Pool2 创建和管理 HiveMetaStoreClient 实例
 * HiveMetaStoreClient 不是线程安全的，必须使用连接池
 */
class HiveClientFactory(metastoreUri: String) extends BasePooledObjectFactory[IMetaStoreClient] {

    private val logger: Logger = LoggerFactory.getLogger(classOf[HiveClientFactory])

    /**
     * 创建新的 HiveMetaStoreClient 实例
     */
    override def create(): IMetaStoreClient = {
        logger.debug(s"Creating new HiveMetaStoreClient for uri: $metastoreUri")
        
        // 通过代码创建 HiveConf，不使用 hive-site.xml
        val hiveConf = new HiveConf()
        hiveConf.set("hive.metastore.uris", metastoreUri)
        
        // 设置其他必要的配置
        hiveConf.set("hive.metastore.connect.retries", "3")
        hiveConf.set("hive.metastore.failure.retries", "2")
        
        try {
            // 使用反射兼容不同 Hive 版本
            createHiveMetaStoreClient(hiveConf)
        } catch {
            case e: Exception =>
                logger.error(s"Failed to create HiveMetaStoreClient for uri: $metastoreUri", e)
                throw new RuntimeException(s"Failed to create HiveMetaStoreClient: ${e.getMessage}", e)
        }
    }

    /**
     * 使用反射创建 HiveMetaStoreClient，兼容不同 Hive 版本
     */
    private def createHiveMetaStoreClient(hiveConf: HiveConf): IMetaStoreClient = {
        val clientClass = classOf[HiveMetaStoreClient]
        
        // Hive 2.x 的构造函数: HiveMetaStoreClient(HiveConf)
        // 注意: HiveConf 继承自 Configuration
        try {
            logger.debug("Trying HiveMetaStoreClient(HiveConf) constructor...")
            val constructor = clientClass.getConstructor(classOf[HiveConf])
            return constructor.newInstance(hiveConf).asInstanceOf[IMetaStoreClient]
        } catch {
            case e: NoSuchMethodException =>
                logger.debug(s"HiveMetaStoreClient(HiveConf) not found: ${e.getMessage}")
        }
        
        // 尝试 HiveMetaStoreClient(Configuration) - 某些版本
        try {
            logger.debug("Trying HiveMetaStoreClient(Configuration) constructor...")
            val constructor = clientClass.getConstructor(classOf[org.apache.hadoop.conf.Configuration])
            return constructor.newInstance(hiveConf).asInstanceOf[IMetaStoreClient]
        } catch {
            case e: NoSuchMethodException =>
                logger.debug(s"HiveMetaStoreClient(Configuration) not found: ${e.getMessage}")
        }
        
        // 尝试 Hive 3.x 构造函数: HiveMetaStoreClient(HiveConf, HiveMetaHookLoader, boolean)
        try {
            logger.debug("Trying HiveMetaStoreClient(HiveConf, HiveMetaHookLoader, boolean) constructor...")
            val hookLoaderClass = Class.forName("org.apache.hadoop.hive.metastore.HiveMetaHookLoader")
            val constructor = clientClass.getConstructor(classOf[HiveConf], hookLoaderClass, classOf[Boolean])
            return constructor.newInstance(hiveConf, null, java.lang.Boolean.FALSE).asInstanceOf[IMetaStoreClient]
        } catch {
            case e: Exception =>
                logger.debug(s"HiveMetaStoreClient(HiveConf, HiveMetaHookLoader, boolean) not found: ${e.getMessage}")
        }
        
        // 最后尝试使用 RetryingMetaStoreClient
        try {
            logger.info("Trying RetryingMetaStoreClient.getProxy()...")
            val retryingClass = Class.forName("org.apache.hadoop.hive.metastore.RetryingMetaStoreClient")
            val method = retryingClass.getMethod("getProxy", classOf[HiveConf], classOf[Boolean])
            return method.invoke(null, hiveConf, java.lang.Boolean.TRUE).asInstanceOf[IMetaStoreClient]
        } catch {
            case e: Exception =>
                logger.error(s"All HiveMetaStoreClient creation methods failed", e)
                throw new RuntimeException(s"Failed to create HiveMetaStoreClient: no compatible constructor found")
        }
    }

    /**
     * 将对象包装为可池化的对象
     */
    override def wrap(client: IMetaStoreClient): PooledObject[IMetaStoreClient] = {
        new DefaultPooledObject[IMetaStoreClient](client)
    }

    /**
     * 销毁对象
     */
    override def destroyObject(p: PooledObject[IMetaStoreClient]): Unit = {
        val client = p.getObject
        if (client != null) {
            try {
                client.close()
                logger.debug("HiveMetaStoreClient closed successfully")
            } catch {
                case e: Exception =>
                    logger.warn("Error closing HiveMetaStoreClient", e)
            }
        }
    }

    /**
     * 验证对象是否有效
     */
    override def validateObject(p: PooledObject[IMetaStoreClient]): Boolean = {
        val client = p.getObject
        if (client == null) {
            return false
        }
        
        try {
            // 简单的连接验证 - 获取所有数据库名称
            client.getAllDatabases
            true
        } catch {
            case e: Exception =>
                logger.warn("HiveMetaStoreClient validation failed", e)
                false
        }
    }

    /**
     * 激活对象（从池中取出时调用）
     */
    override def activateObject(p: PooledObject[IMetaStoreClient]): Unit = {
        // 可以在这里做一些激活操作
        logger.debug("HiveMetaStoreClient activated from pool")
    }

    /**
     * 钝化对象（归还到池中时调用）
     */
    override def passivateObject(p: PooledObject[IMetaStoreClient]): Unit = {
        // 可以在这里做一些清理操作
        logger.debug("HiveMetaStoreClient passivated to pool")
    }
}
