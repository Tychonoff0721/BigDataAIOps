package com.aiops.bigdata.repository;

import com.aiops.bigdata.entity.context.LongTermStatusEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 长期状态Repository
 */
@Repository
public interface LongTermStatusRepository extends JpaRepository<LongTermStatusEntity, Long> {
    
    /**
     * 根据集群名称和日期查询
     */
    Optional<LongTermStatusEntity> findByClusterNameAndStatusDate(String clusterName, LocalDate statusDate);
    
    /**
     * 查询指定集群最新的状态
     */
    @Query("SELECT l FROM LongTermStatusEntity l WHERE l.clusterName = :clusterName ORDER BY l.statusDate DESC LIMIT 1")
    Optional<LongTermStatusEntity> findLatestByClusterName(@Param("clusterName") String clusterName);
    
    /**
     * 查询指定集群在日期范围内的状态
     */
    @Query("SELECT l FROM LongTermStatusEntity l WHERE l.clusterName = :clusterName " +
           "AND l.statusDate >= :startDate AND l.statusDate <= :endDate ORDER BY l.statusDate")
    List<LongTermStatusEntity> findByClusterNameAndDateRange(
        @Param("clusterName") String clusterName,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
    
    /**
     * 查询所有集群的最新状态
     */
    @Query("SELECT l FROM LongTermStatusEntity l WHERE l.id IN " +
           "(SELECT MAX(l2.id) FROM LongTermStatusEntity l2 GROUP BY l2.clusterName)")
    List<LongTermStatusEntity> findAllLatest();
    
    /**
     * 删除指定日期之前的数据
     */
    void deleteByStatusDateBefore(LocalDate beforeDate);
}
