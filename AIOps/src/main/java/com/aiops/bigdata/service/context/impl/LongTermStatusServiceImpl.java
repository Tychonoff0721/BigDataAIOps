package com.aiops.bigdata.service.context.impl;

import com.aiops.bigdata.entity.context.LongTermStatus;
import com.aiops.bigdata.entity.context.LongTermStatusEntity;
import com.aiops.bigdata.repository.LongTermStatusRepository;
import com.aiops.bigdata.service.context.LongTermStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 长期状态服务实现类（MySQL存储）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LongTermStatusServiceImpl implements LongTermStatusService {
    
    private final LongTermStatusRepository repository;
    
    @Override
    @Transactional
    public void save(LongTermStatus status) {
        LongTermStatusEntity entity = LongTermStatusEntity.fromDomain(status);
        if (entity.getUpdateTime() == null) {
            entity.setUpdateTime(LocalDateTime.now());
        }
        repository.save(entity);
        log.info("保存长期状态到MySQL: cluster={}, date={}", status.getCluster(), status.getStatusDate());
    }
    
    @Override
    @Transactional(readOnly = true)
    public Optional<LongTermStatus> get(String cluster, LocalDate date) {
        return repository.findByClusterNameAndStatusDate(cluster, date)
            .map(LongTermStatusEntity::toDomain);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Optional<LongTermStatus> getLatest(String cluster) {
        return repository.findLatestByClusterName(cluster)
            .map(LongTermStatusEntity::toDomain);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<LongTermStatus> getByDateRange(String cluster, LocalDate startDate, LocalDate endDate) {
        return repository.findByClusterNameAndDateRange(cluster, startDate, endDate)
            .stream()
            .map(LongTermStatusEntity::toDomain)
            .collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<LongTermStatus> getAllClustersLatest() {
        return repository.findAllLatest()
            .stream()
            .map(LongTermStatusEntity::toDomain)
            .collect(Collectors.toList());
    }
    
    @Override
    @Transactional
    public void updateClusterInfo(String cluster, Integer totalNodes, Integer totalCpuCores, Long totalMemoryGB) {
        Optional<LongTermStatusEntity> existingOpt = repository.findLatestByClusterName(cluster);
        
        LongTermStatusEntity entity;
        if (existingOpt.isPresent()) {
            entity = existingOpt.get();
        } else {
            entity = new LongTermStatusEntity();
            entity.setClusterName(cluster);
            entity.setStatusDate(LocalDate.now());
        }
        
        entity.setTotalNodes(totalNodes);
        entity.setTotalCpuCores(totalCpuCores);
        entity.setTotalMemoryGB(totalMemoryGB);
        entity.setUpdateTime(LocalDateTime.now());
        
        repository.save(entity);
        log.info("更新集群信息: cluster={}, nodes={}, cpu={}, memory={}GB", 
            cluster, totalNodes, totalCpuCores, totalMemoryGB);
    }
    
    @Override
    @Transactional
    public void updateHdfsStatus(String cluster, Long capacityTB, Long usedTB, 
            Long fileCount, Long blockCount) {
        Optional<LongTermStatusEntity> existingOpt = repository.findLatestByClusterName(cluster);
        
        if (existingOpt.isEmpty()) {
            log.warn("未找到集群{}的长期状态，跳过HDFS状态更新", cluster);
            return;
        }
        
        LongTermStatusEntity entity = existingOpt.get();
        entity.setHdfsCapacityTB(capacityTB);
        entity.setHdfsUsedTB(usedTB);
        entity.setHdfsFileCount(fileCount);
        entity.setHdfsBlockCount(blockCount);
        
        if (capacityTB != null && capacityTB > 0 && usedTB != null) {
            entity.setHdfsUsageRate((double) usedTB / capacityTB);
        }
        
        entity.setUpdateTime(LocalDateTime.now());
        repository.save(entity);
    }
    
    @Override
    @Transactional
    public void updateYarnStatus(String cluster, Integer nmCount, Long totalMemoryGB, Integer totalVcores) {
        Optional<LongTermStatusEntity> existingOpt = repository.findLatestByClusterName(cluster);
        
        if (existingOpt.isEmpty()) {
            log.warn("未找到集群{}的长期状态，跳过YARN状态更新", cluster);
            return;
        }
        
        LongTermStatusEntity entity = existingOpt.get();
        entity.setNmCount(nmCount);
        entity.setYarnTotalMemoryGB(totalMemoryGB);
        entity.setYarnTotalVcores(totalVcores);
        entity.setUpdateTime(LocalDateTime.now());
        repository.save(entity);
    }
    
    @Override
    @Transactional
    public void updateDailyStats(String cluster, Integer alertCount, Integer anomalyCount, Double avgLoad) {
        Optional<LongTermStatusEntity> existingOpt = repository.findLatestByClusterName(cluster);
        
        LongTermStatusEntity entity;
        if (existingOpt.isPresent()) {
            entity = existingOpt.get();
        } else {
            entity = new LongTermStatusEntity();
            entity.setClusterName(cluster);
            entity.setStatusDate(LocalDate.now());
        }
        
        entity.setAlertCountToday(alertCount);
        entity.setAnomalyCountToday(anomalyCount);
        entity.setAvgClusterLoad(avgLoad);
        entity.setUpdateTime(LocalDateTime.now());
        
        repository.save(entity);
    }
    
    @Override
    @Transactional
    public int deleteBeforeDate(LocalDate beforeDate) {
        int count = 0;
        List<LongTermStatusEntity> toDelete = repository.findAll()
            .stream()
            .filter(e -> e.getStatusDate() != null && e.getStatusDate().isBefore(beforeDate))
            .toList();
        
        count = toDelete.size();
        repository.deleteAll(toDelete);
        
        log.info("删除过期长期状态: beforeDate={}, count={}", beforeDate, count);
        return count;
    }
}
