package com.example.routing

import org.apache.hadoop.fs.Path
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.connector.catalog.{SupportsRead, Table, TableCapability}
import org.apache.spark.sql.connector.read.ScanBuilder
import org.apache.spark.sql.types.StructType
import org.apache.spark.sql.util.CaseInsensitiveStringMap
import org.apache.spark.sql.execution.datasources.{FileIndex, InMemoryFileIndex}
import org.apache.spark.sql.execution.datasources.v2.FileTable
import org.apache.spark.sql.execution.datasources.v2.parquet.ParquetTable
import org.apache.spark.sql.execution.datasources.v2.text.TextTable
import org.apache.spark.sql.execution.datasources.parquet.ParquetFileFormat
import org.apache.spark.sql.execution.datasources.orc.OrcFileFormat
import org.apache.spark.sql.execution.datasources.text.TextFileFormat
import org.slf4j.{Logger, LoggerFactory}

import java.util
import scala.collection.JavaConverters._

/**
 * Hive Routing Table - 直接委托给 Spark 内置 FileTable
 */
class HiveRoutingTable(
    val database: String,
    val tableName: String,
    val tableSchema: StructType,
    val partitionColumns: Seq[String],
    val location: String,
    val format: String,
    val cluster: String,
    @transient val clientPools: Map[String, HiveClientPool],
    @transient val metrics: RoutingMetrics
) extends Table with SupportsRead with Serializable {

    @transient private lazy val logger: Logger = LoggerFactory.getLogger(classOf[HiveRoutingTable])

    override def name(): String = s"$database.$tableName"
    override def schema(): StructType = tableSchema

    override def capabilities(): util.Set[TableCapability] = {
        Set(TableCapability.BATCH_READ).asJava
    }

    override def partitioning(): Array[org.apache.spark.sql.connector.expressions.Transform] = {
        partitionColumns.map { col =>
            org.apache.spark.sql.connector.expressions.Expressions.identity(col)
        }.toArray
    }

    override def properties(): util.Map[String, String] = {
        val props = new util.HashMap[String, String]()
        props.put("location", location)
        props.put("format", format)
        props.put("cluster", cluster)
        props
    }

    override def newScanBuilder(options: CaseInsensitiveStringMap): ScanBuilder = {
        logger.debug(s"Creating scan builder: $name, format=$format, location=$location")
        
        val spark = SparkSession.active
        
        // 获取分区路径
        val paths = if (partitionColumns.nonEmpty && clientPools != null) {
            val fetcher = new HivePartitionFetcher(database, tableName, cluster, clientPools)
            fetcher.fetchPartitions().map(p => new Path(p.location))
        } else {
            Seq(new Path(location))
        }
        
        // 创建 FileIndex
        val fileIndex = new InMemoryFileIndex(spark, paths, Map.empty[String, String], None)
        
        // 分区 schema
        val partitionSchema = StructType(partitionColumns.flatMap { col =>
            tableSchema.find(_.name == col)
        })
        
        // 数据 schema (排除分区列)
        val dataSchema = StructType(tableSchema.filterNot(f => partitionColumns.contains(f.name)))
        
        // 委托给对应的 FileTable
        createFileTable(spark, paths.map(_.toString), dataSchema, partitionSchema, options)
            .newScanBuilder(options)
    }
    
    private def createFileTable(
        spark: SparkSession,
        paths: Seq[String],
        dataSchema: StructType,
        partitionSchema: StructType,
        options: CaseInsensitiveStringMap
    ): FileTable = {
        
        format.toLowerCase match {
            case "parquet" =>
                new ParquetTable(
                    name(),
                    spark,
                    options,
                    paths,
                    Some(dataSchema),  // 使用 Hive schema
                    classOf[org.apache.spark.sql.execution.datasources.parquet.ParquetFileFormat]
                )
            case "orc" =>
                new ParquetTable(
                    name(),
                    spark,
                    options,
                    paths,
                    Some(dataSchema),  // 使用 Hive schema
                    classOf[org.apache.spark.sql.execution.datasources.orc.OrcFileFormat]
                )
            case "text" | "textfile" =>
                new TextTable(
                    name(),
                    spark,
                    options,
                    paths,
                    None,  // Text 只支持单列 "value"
                    classOf[org.apache.spark.sql.execution.datasources.text.TextFileFormat]
                )
            case _ =>
                new ParquetTable(
                    name(),
                    spark,
                    options,
                    paths,
                    Some(dataSchema),
                    classOf[org.apache.spark.sql.execution.datasources.parquet.ParquetFileFormat]
                )
        }
    }
}

/**
 * 分区信息
 */
case class PartitionInfo(
    location: String,
    values: Map[String, Any]
)
