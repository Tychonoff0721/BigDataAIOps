package com.example.routing

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.connector.catalog._
import org.apache.spark.sql.connector.expressions.Transform
import org.apache.spark.sql.types.StructType
import org.apache.spark.sql.util.CaseInsensitiveStringMap
import org.slf4j.{Logger, LoggerFactory}

import java.util
import scala.collection.JavaConverters._

/**
 * Routing Catalog - Spark DataSource V2 Catalog 实现
 *
 * 实现跨多个 Hive Metastore 集群的统一 SQL 访问能力
 *
 * 必须实现接口:
 * - CatalogPlugin
 * - TableCatalog
 * - SupportsNamespaces
 *
 * Spark 配置示例:
 * spark.sql.catalog.spark_catalog=com.example.routing.RoutingCatalog
 * spark.routing.hive.clusters=hiveA,hiveB
 * spark.routing.hive.hiveA.uri=thrift://hmsA:9083
 * spark.routing.hive.hiveB.uri=thrift://hmsB:9083
 * spark.routing.metadata.service=http://localhost:8080
 */
class RoutingCatalog extends CatalogPlugin with TableCatalog with SupportsNamespaces {

    private val logger: Logger = LoggerFactory.getLogger(classOf[RoutingCatalog])

    // Catalog 名称
    private var _name: String = _

    // 配置
    private var config: RoutingConfig = _

    // Metadata Provider (支持服务模式和文件模式)
    private var metadataProvider: MetadataProvider = _

    // Hive Client 连接池映射 (cluster name -> pool)
    private var clientPools: Map[String, HiveClientPool] = _

    // Metrics
    private val metrics: RoutingMetrics = RoutingMetrics.get

    // Table 缓存
    private var tableCache: TableCache = _

    // Spark Session
    private var sparkSession: SparkSession = _

    /**
     * 初始化 Catalog
     */
    override def initialize(name: String, options: CaseInsensitiveStringMap): Unit = {
        logger.info(s"Initializing RoutingCatalog with name: $name")
        
        _name = name
        
        // 获取 SparkConf
        sparkSession = SparkSession.active
        val sparkConf = sparkSession.sparkContext.getConf
        
        // 解析配置
        config = RoutingConfig.fromSparkConf(sparkConf)
        
        // 初始化 Metadata Provider (根据配置选择服务模式或文件模式)
        if (config.metadataUseFile) {
            logger.info(s"Using file-based metadata provider, file: ${config.metadataFilePath}")
            metadataProvider = new FileMetadataProvider(
                config.metadataFilePath,
                config.metadataCacheTtlSeconds,
                metrics
            )
        } else {
            logger.info(s"Using service-based metadata provider, url: ${config.metadataServiceUrl}")
            metadataProvider = new MetadataServiceClient(
                config.metadataServiceUrl,
                config.metadataCacheTtlSeconds,
                metrics
            )
        }
        
        // 初始化 Hive Client 连接池
        clientPools = config.clusters.map { case (clusterName, clusterConfig) =>
            logger.info(s"Initializing HiveClientPool for cluster: $clusterName, uri: ${clusterConfig.metastoreUri}")
            clusterName -> new HiveClientPool(
                clusterConfig.metastoreUri,
                config.poolMaxTotal,
                config.poolMaxIdle,
                config.poolMinIdle
            )
        }
        
        // 初始化 Table 缓存
        tableCache = new TableCache(config.schemaCacheTtlSeconds)
        
        logger.info(s"RoutingCatalog initialized successfully with ${clientPools.size} clusters")
    }

    /**
     * 获取 Catalog 名称
     */
    override def name(): String = _name

    // ==================== TableCatalog 接口实现 ====================

    /**
     * 加载表
     *
     * 这是核心方法，实现流程:
     * 1. 解析 Identifier 获取 db 和 table
     * 2. 查询 MetadataServiceClient 获取 cluster
     * 3. 从 HiveClientPool 获取 HiveMetaStoreClient
     * 4. 查询 Hive 表信息
     * 5. 构造 Spark Table
     */
    override def loadTable(ident: Identifier): org.apache.spark.sql.connector.catalog.Table = {
        val db = ident.namespace().headOption.getOrElse("default")
        val tableName = ident.name()
        
        logger.debug(s"Loading table: $db.$tableName")
        metrics.recordTableLoad()
        metrics.recordDatabaseQuery(db)

        try {
            // 先查缓存
            tableCache.get(db, tableName) match {
                case Some(cached) => cached
                case None =>
                    // 查询 Metadata Provider 获取表所在集群
                    val tableMetadata = metadataProvider.getTableMetadata(db, tableName)
                    val cluster = tableMetadata.cluster
                    
                    logger.debug(s"Table $db.$tableName belongs to cluster: $cluster")
                    
                    // 获取对应的连接池
                    val pool = clientPools.getOrElse(cluster, 
                        throw new RuntimeException(s"Unknown cluster: $cluster"))
                    
                    // 从 HMS 获取表信息
                    val hiveTable = pool.withClient { client =>
                        metrics.recordHmsQuery(cluster)
                        client.getTable(db, tableName)
                    }
                    
                    // 构造 Spark Table
                    val sparkTable = buildSparkTable(hiveTable, tableMetadata, cluster)
                    
                    // 缓存
                    tableCache.put(db, tableName, sparkTable)
                    
                    sparkTable
            }
        } catch {
            case e: Exception =>
                metrics.recordTableLoadError()
                logger.error(s"Failed to load table: $db.$tableName", e)
                throw new RuntimeException(s"Failed to load table $db.$tableName: ${e.getMessage}", e)
        }
    }

    /**
     * 构造 Spark Table
     */
    private def buildSparkTable(
        hiveTable: org.apache.hadoop.hive.metastore.api.Table, 
        tableMetadata: TableMetadata,
        cluster: String
    ): org.apache.spark.sql.connector.catalog.Table = {
        // 解析 Schema
        val schema = parseHiveSchema(hiveTable)
        
        // 解析分区信息
        val partitionColumns = Option(hiveTable.getPartitionKeys)
            .map(_.asScala.map(_.getName).toSeq)
            .getOrElse(Seq.empty)
        
        // 从 HMS 的 StorageDescriptor 获取 location 和 format
        val sd = hiveTable.getSd
        val location = sd.getLocation
        val format = sd.getInputFormat match {
            case "org.apache.hadoop.hive.ql.io.parquet.MapredParquetInputFormat" => "parquet"
            case "org.apache.hadoop.hive.ql.io.orc.OrcInputFormat" => "orc"
            case "org.apache.hadoop.mapred.TextInputFormat" => "text"
            case _ => "parquet"  // 默认
        }
        
        // 构造 HiveRoutingTable
        new HiveRoutingTable(
            database = hiveTable.getDbName,
            tableName = hiveTable.getTableName,
            tableSchema = schema,
            partitionColumns = partitionColumns,
            location = location,
            format = format,
            cluster = cluster,
            clientPools = clientPools,
            metrics = metrics
        )
    }

    /**
     * 解析 Hive 表 Schema
     */
    private def parseHiveSchema(hiveTable: org.apache.hadoop.hive.metastore.api.Table): StructType = {
        val sd = hiveTable.getSd
        val cols = sd.getCols
        val partitionKeys = hiveTable.getPartitionKeys
        
        // 构建 fields
        val fieldsBuilder = Seq.newBuilder[org.apache.spark.sql.types.StructField]
        
        // 添加普通列
        if (cols != null) {
            cols.asScala.foreach { col =>
                val fieldType = hiveTypeToSparkType(col.getType)
                fieldsBuilder += org.apache.spark.sql.types.StructField(
                    col.getName,
                    fieldType,
                    true  // nullable
                )
            }
        }
        
        // 添加分区列
        if (partitionKeys != null) {
            partitionKeys.asScala.foreach { col =>
                val fieldType = hiveTypeToSparkType(col.getType)
                fieldsBuilder += org.apache.spark.sql.types.StructField(
                    col.getName,
                    fieldType,
                    true
                )
            }
        }
        
        new StructType(fieldsBuilder.result().toArray)
    }

    /**
     * Hive 类型转 Spark 类型
     */
    private def hiveTypeToSparkType(hiveType: String): org.apache.spark.sql.types.DataType = {
        import org.apache.spark.sql.types._
        
        hiveType.toLowerCase match {
            case "tinyint" => ByteType
            case "smallint" => ShortType
            case "int" | "integer" => IntegerType
            case "bigint" => LongType
            case "float" => FloatType
            case "double" => DoubleType
            case "decimal" => DecimalType.SYSTEM_DEFAULT
            case "string" | "varchar" | "char" => StringType
            case "boolean" => BooleanType
            case "date" => DateType
            case "timestamp" => TimestampType
            case "binary" => BinaryType
            case _ => StringType  // 默认为 String
        }
    }

    /**
     * 列出所有表
     */
    override def listTables(namespace: Array[String]): Array[Identifier] = {
        val db = namespace.headOption.getOrElse("default")
        
        logger.debug(s"Listing tables in database: $db")
        
        // 需要从所有集群收集表
        val allTables = scala.collection.mutable.ArrayBuffer[Identifier]()
        
        clientPools.foreach { case (cluster, pool) =>
            try {
                val tables = pool.withClient { client =>
                    metrics.recordHmsQuery(cluster)
                    client.getAllTables(db)
                }
                
                if (tables != null) {
                    tables.asScala.foreach { tableName =>
                        allTables += Identifier.of(namespace, tableName)
                    }
                }
            } catch {
                case e: Exception =>
                    logger.warn(s"Failed to list tables from cluster $cluster for db $db", e)
            }
        }
        
        allTables.toArray
    }

    /**
     * 创建表 - 不支持
     */
    override def createTable(
        ident: Identifier, 
        schema: StructType, 
        partitions: Array[Transform], 
        properties: util.Map[String, String]
    ): Table = {
        throw new UnsupportedOperationException("RoutingCatalog does not support table creation")
    }

    /**
     * 修改表 - 不支持
     */
    override def alterTable(ident: Identifier, changes: TableChange*): Table = {
        throw new UnsupportedOperationException("RoutingCatalog does not support table alteration")
    }

    /**
     * 删除表 - 不支持
     */
    override def dropTable(ident: Identifier): Boolean = {
        throw new UnsupportedOperationException("RoutingCatalog does not support table dropping")
    }

    /**
     * 重命名表 - 不支持
     */
    override def renameTable(oldIdent: Identifier, newIdent: Identifier): Unit = {
        throw new UnsupportedOperationException("RoutingCatalog does not support table renaming")
    }

    /**
     * 表是否存在
     */
    override def tableExists(ident: Identifier): Boolean = {
        try {
            loadTable(ident)
            true
        } catch {
            case _: Exception => false
        }
    }

    // ==================== SupportsNamespaces 接口实现 ====================

    /**
     * 列出所有数据库
     */
    override def listNamespaces(): Array[Array[String]] = {
        logger.debug("Listing all namespaces (databases)")
        
        // 从所有集群收集数据库
        val allDbs = scala.collection.mutable.Set[String]()
        
        clientPools.foreach { case (cluster, pool) =>
            try {
                val dbs = pool.withClient { client =>
                    metrics.recordHmsQuery(cluster)
                    client.getAllDatabases
                }
                
                if (dbs != null) {
                    dbs.asScala.foreach(db => allDbs.add(db))
                }
            } catch {
                case e: Exception =>
                    logger.warn(s"Failed to list databases from cluster $cluster", e)
            }
        }
        
        allDbs.map(db => Array(db)).toArray
    }

    /**
     * 列出子命名空间
     */
    override def listNamespaces(namespace: Array[String]): Array[Array[String]] = {
        // Hive 不支持嵌套命名空间
        Array.empty
    }

    /**
     * 加载命名空间元数据
     */
    override def loadNamespaceMetadata(namespace: Array[String]): util.Map[String, String] = {
        val db = namespace.headOption.getOrElse("default")
        
        logger.debug(s"Loading namespace metadata: $db")
        
        // 从任一集群获取数据库信息
        val result = new util.HashMap[String, String]()
        
        clientPools.values.foreach { pool =>
            try {
                val dbInfo = pool.withClient { client =>
                    client.getDatabase(db)
                }
                
                if (dbInfo != null) {
                    result.put("name", dbInfo.getName)
                    result.put("location", dbInfo.getLocationUri)
                    Option(dbInfo.getDescription).foreach(desc => result.put("description", desc))
                    return result
                }
            } catch {
                case _: Exception => // 继续尝试下一个集群
            }
        }
        
        result
    }

    /**
     * 创建命名空间 - 不支持
     */
    override def createNamespace(namespace: Array[String], metadata: util.Map[String, String]): Unit = {
        throw new UnsupportedOperationException("RoutingCatalog does not support namespace creation")
    }

    /**
     * 修改命名空间 - 不支持
     */
    override def alterNamespace(namespace: Array[String], changes: NamespaceChange*): Unit = {
        throw new UnsupportedOperationException("RoutingCatalog does not support namespace alteration")
    }

    /**
     * 删除命名空间 - 不支持
     */
    override def dropNamespace(namespace: Array[String], cascade: Boolean): Boolean = {
        throw new UnsupportedOperationException("RoutingCatalog does not support namespace dropping")
    }

    // ==================== 资源清理 ====================

    /**
     * 关闭 Catalog，释放资源
     */
    def close(): Unit = {
        logger.info("Closing RoutingCatalog")
        
        // 关闭 Metadata Provider
        if (metadataProvider != null) {
            metadataProvider.close()
        }
        
        // 关闭所有连接池
        if (clientPools != null) {
            clientPools.values.foreach(_.close())
        }
        
        logger.info("RoutingCatalog closed")
    }

    /**
     * 获取 Metrics
     */
    def getMetrics: RoutingMetrics = metrics

    /**
     * 打印 Metrics 摘要
     */
    def printMetrics(): String = metrics.printSummary()
}
