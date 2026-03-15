package com.example.routing

import org.apache.hadoop.hive.metastore.api.Partition
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._

/**
 * Hive 分区获取器
 *
 * 用于获取分区表的分区信息
 * 支持 partition pruning
 */
class HivePartitionFetcher(
    database: String,
    tableName: String,
    cluster: String,
    clientPools: Map[String, HiveClientPool]
) {

    private val logger: Logger = LoggerFactory.getLogger(classOf[HivePartitionFetcher])

    /**
     * 获取所有分区
     */
    def fetchPartitions(): Seq[PartitionInfo] = {
        logger.debug(s"Fetching partitions for table: $database.$tableName from cluster: $cluster")
        
        val pool = clientPools.getOrElse(cluster, 
            throw new RuntimeException(s"Unknown cluster: $cluster"))
        
        pool.withClient { client =>
            // 获取所有分区
            val partitions = client.listPartitions(database, tableName, -1)
            
            if (partitions == null || partitions.isEmpty) {
                logger.debug(s"No partitions found for table: $database.$tableName")
                return Seq.empty
            }
            
            partitions.asScala.map { partition =>
                convertToPartitionInfo(partition)
            }.toSeq
        }
    }

    /**
     * 获取指定分区
     *
     * @param partitionValues 分区值列表 (按分区列顺序)
     */
    def fetchPartition(partitionValues: Seq[String]): Option[PartitionInfo] = {
        logger.debug(s"Fetching partition: ${partitionValues.mkString("/")} for table: $database.$tableName")
        
        val pool = clientPools.getOrElse(cluster, 
            throw new RuntimeException(s"Unknown cluster: $cluster"))
        
        pool.withClient { client =>
            try {
                val partition = client.getPartition(database, tableName, partitionValues.asJava)
                Some(convertToPartitionInfo(partition))
            } catch {
                case _: org.apache.hadoop.hive.metastore.api.NoSuchObjectException =>
                    logger.debug(s"Partition not found: ${partitionValues.mkString("/")}")
                    None
                case e: Exception =>
                    logger.error(s"Error fetching partition: ${partitionValues.mkString("/")}", e)
                    throw e
            }
        }
    }

    /**
     * 根据分区过滤条件获取分区
     * 注意：IMetaStoreClient 没有直接的 getPartitionsByFilter 方法
     * 这里先获取所有分区，然后在内存中过滤
     *
     * @param filter 分区过滤条件 (例如: "country='US' AND year=2023")
     */
    def fetchPartitionsByFilter(filter: String): Seq[PartitionInfo] = {
        logger.debug(s"Fetching partitions with filter: $filter for table: $database.$tableName")
        
        // 简化实现：获取所有分区后过滤
        // 生产环境可以使用 MetastoreClient 的更高级 API
        val allPartitions = fetchPartitions()
        
        if (filter.isEmpty) {
            return allPartitions
        }
        
        // 简单的内存过滤实现
        // 生产环境应该使用更复杂的解析逻辑
        allPartitions.filter { partition =>
            // 简单匹配逻辑
            filter.split("AND").forall { condition =>
                val parts = condition.trim.split("=")
                if (parts.length == 2) {
                    val col = parts(0).trim
                    val value = parts(1).trim.replace("'", "")
                    partition.values.get(col).contains(value)
                } else true
            }
        }
    }

    /**
     * 获取分区名称列表
     */
    def fetchPartitionNames(): Seq[String] = {
        logger.debug(s"Fetching partition names for table: $database.$tableName")
        
        val pool = clientPools.getOrElse(cluster, 
            throw new RuntimeException(s"Unknown cluster: $cluster"))
        
        pool.withClient { client =>
            val names = client.listPartitionNames(database, tableName, -1)
            
            if (names == null) {
                return Seq.empty
            }
            
            names.asScala.toSeq
        }
    }

    /**
     * 将 Hive Partition 转换为 PartitionInfo
     */
    private def convertToPartitionInfo(partition: Partition): PartitionInfo = {
        val location = partition.getSd.getLocation
        val values = partition.getValues
        
        // 获取分区列名和值的映射
        // 注意：这里假设分区列名在表元数据中，需要额外获取
        // 简化实现：使用索引作为 key
        val partitionValues: Map[String, Any] = if (values != null) {
            values.asScala.zipWithIndex.map { case (value, idx) =>
                s"partition_$idx" -> (value: Any)
            }.toMap
        } else {
            Map.empty[String, Any]
        }
        
        PartitionInfo(
            location = location,
            values = partitionValues
        )
    }
}

/**
 * 分区剪枝器
 *
 * 根据查询条件过滤分区
 */
class PartitionPruner(
    partitionFetcher: HivePartitionFetcher,
    partitionColumns: Seq[String]
) {

    private val logger: Logger = LoggerFactory.getLogger(classOf[PartitionPruner])

    /**
     * 根据过滤条件剪枝分区
     *
     * @param filters 过滤条件列表
     * @return 过滤后的分区列表
     */
    def prune(filters: Seq[PartitionFilter]): Seq[PartitionInfo] = {
        if (filters.isEmpty) {
            return partitionFetcher.fetchPartitions()
        }
        
        logger.debug(s"Pruning partitions with filters: ${filters.mkString(", ")}")
        
        // 构建 Hive 过滤表达式
        val filterExpr = filters.map(_.toHiveExpr).mkString(" AND ")
        
        partitionFetcher.fetchPartitionsByFilter(filterExpr)
    }

    /**
     * 检查分区列是否在过滤条件中
     */
    def hasPartitionFilter(filters: Seq[PartitionFilter]): Boolean = {
        filters.exists(f => partitionColumns.contains(f.column))
    }
}

/**
 * 分区过滤条件
 */
case class PartitionFilter(
    column: String,
    operator: FilterOperator,
    value: Any
) {

    /**
     * 转换为 Hive 过滤表达式
     */
    def toHiveExpr: String = {
        val escapedValue = value match {
            case s: String => s"'$s'"
            case v => v.toString
        }
        
        operator match {
            case FilterOperator.EQUALS => s"$column = $escapedValue"
            case FilterOperator.NOT_EQUALS => s"$column != $escapedValue"
            case FilterOperator.LESS_THAN => s"$column < $escapedValue"
            case FilterOperator.LESS_THAN_OR_EQUAL => s"$column <= $escapedValue"
            case FilterOperator.GREATER_THAN => s"$column > $escapedValue"
            case FilterOperator.GREATER_THAN_OR_EQUAL => s"$column >= $escapedValue"
            case FilterOperator.IN => s"$column IN ($escapedValue)"
            case FilterOperator.LIKE => s"$column LIKE $escapedValue"
        }
    }
}

/**
 * 过滤操作符
 */
sealed trait FilterOperator
object FilterOperator {
    case object EQUALS extends FilterOperator
    case object NOT_EQUALS extends FilterOperator
    case object LESS_THAN extends FilterOperator
    case object LESS_THAN_OR_EQUAL extends FilterOperator
    case object GREATER_THAN extends FilterOperator
    case object GREATER_THAN_OR_EQUAL extends FilterOperator
    case object IN extends FilterOperator
    case object LIKE extends FilterOperator
}
