package com.example.routing

/**
 * Metadata Provider 统一接口
 *
 * 支持两种实现:
 * 1. ServiceMetadataProvider - 通过 HTTP 调用 Metadata Service
 * 2. FileMetadataProvider - 从本地配置文件读取
 */
trait MetadataProvider {

    /**
     * 获取表的元数据信息
     *
     * @param db    数据库名
     * @param table 表名
     * @return 表元数据信息
     */
    def getTableMetadata(db: String, table: String): TableMetadata

    /**
     * 清除缓存
     */
    def invalidateCache(db: String, table: String): Unit

    /**
     * 清除所有缓存
     */
    def invalidateAllCache(): Unit

    /**
     * 关闭 Provider，释放资源
     */
    def close(): Unit
}
