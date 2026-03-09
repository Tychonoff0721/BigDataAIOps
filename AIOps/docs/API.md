# AIOps API 接口文档

## 基础信息

- **Base URL**: `http://localhost:8080/v1`
- **Content-Type**: `application/json`

---

## 健康检查

### GET /health

检查服务是否正常运行。

**请求示例**
```bash
curl http://localhost:8080/v1/health
```

**响应示例**
```json
{
  "status": "UP",
  "timestamp": "2024-01-15T10:30:00",
  "service": "AIOps Server"
}
```

---

## 组件分析

### POST /analyze/component

分析组件健康状态。

**请求体**
```json
{
  "cluster": "bigdata-prod",
  "service": "hdfs",
  "component": "namenode",
  "instance": "nn1",
  "cpuUsage": 0.75,
  "memoryUsage": 0.82,
  "gcTime": 2500,
  "connectionCount": 150,
  "timestamp": 1705312800,
  "metrics": {}
}
```

**参数说明**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| cluster | string | 否 | 集群名称，默认default |
| service | string | 是 | 服务类型: hdfs, yarn, spark, kafka, flink, hive |
| component | string | 是 | 组件类型: namenode, datanode, resourcemanager等 |
| instance | string | 否 | 实例标识 |
| cpuUsage | double | 否 | CPU使用率(0-1) |
| memoryUsage | double | 否 | 内存使用率(0-1) |
| gcTime | long | 否 | GC时间(ms) |
| connectionCount | int | 否 | 连接数 |
| timestamp | long | 否 | 采集时间戳(秒) |
| metrics | map | 否 | 其他动态指标 |

**响应示例**
```json
{
  "resultId": "uuid-xxx",
  "cluster": "bigdata-prod",
  "analysisTime": "2024-01-15T10:30:00",
  "analysisType": "component",
  "targetId": "hdfs:namenode:nn1",
  "targetName": "hdfs/namenode",
  "healthStatus": "warning",
  "healthScore": 75,
  "diagnosis": {
    "summary": "检测到2个问题需要关注",
    "issues": [
      {
        "issueType": "resource",
        "severity": "warning",
        "title": "内存使用率过高",
        "description": "内存使用率超过阈值",
        "relatedMetrics": ["memory_usage"]
      }
    ],
    "rootCauseAnalysis": "资源使用率偏高"
  },
  "recommendations": [
    {
      "recommendationType": "configuration",
      "priority": 1,
      "title": "优化内存配置",
      "description": "建议检查内存配置",
      "actionSteps": ["检查内存泄漏", "增加堆内存"]
    }
  ],
  "modelName": "mock-llm-v1",
  "analysisDuration": 500
}
```

---

## Spark作业分析

### POST /analyze/spark

分析Spark作业性能。

**请求体**
```json
{
  "cluster": "bigdata-prod",
  "jobId": "job-001",
  "appName": "ETL-Job",
  "status": "RUNNING",
  "duration": 300000,
  "executorCount": 10,
  "executorMemoryGB": 4.0,
  "executorCores": 2,
  "inputSize": 10737418240,
  "outputSize": 5368709120,
  "shuffleRead": 2147483648,
  "shuffleWrite": 1073741824,
  "skewRatio": 5.5,
  "shuffleRatio": 0.45,
  "timestamp": 1705312800
}
```

**参数说明**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| cluster | string | 否 | 集群名称 |
| jobId | string | 是 | 作业ID |
| appName | string | 否 | 应用名称 |
| status | string | 否 | 状态: RUNNING, SUCCEEDED, FAILED |
| duration | long | 否 | 执行时长(ms) |
| executorCount | int | 否 | Executor数量 |
| executorMemoryGB | double | 否 | Executor内存(GB) |
| executorCores | int | 否 | Executor核心数 |
| inputSize | long | 否 | 输入数据量(bytes) |
| outputSize | long | 否 | 输出数据量(bytes) |
| shuffleRead | long | 否 | Shuffle读取量(bytes) |
| shuffleWrite | long | 否 | Shuffle写入量(bytes) |
| skewRatio | double | 否 | 数据倾斜率 |
| shuffleRatio | double | 否 | Shuffle比例 |

**响应示例**
```json
{
  "resultId": "uuid-xxx",
  "analysisType": "spark_app",
  "targetId": "job-001",
  "targetName": "ETL-Job",
  "healthStatus": "warning",
  "healthScore": 70,
  "diagnosis": {
    "summary": "检测到2个性能瓶颈: skew, shuffle",
    "issues": [
      {
        "issueType": "data_skew",
        "severity": "warning",
        "title": "存在数据倾斜",
        "description": "部分Stage存在数据倾斜"
      }
    ]
  },
  "recommendations": [
    {
      "recommendationType": "code",
      "priority": 1,
      "title": "解决数据倾斜",
      "actionSteps": ["对倾斜Key加盐", "使用broadcast join"]
    }
  ]
}
```

---

## 集群状态查询

### GET /status/overview

获取集群概览。

**请求参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| cluster | string | 否 | 集群名称，默认bigdata-prod |

**请求示例**
```bash
curl "http://localhost:8080/v1/status/overview?cluster=bigdata-prod"
```

**响应示例**
```json
{
  "cluster": {
    "cluster": "bigdata-prod",
    "statusDate": "2024-01-15",
    "totalNodes": 50,
    "activeNodes": 48,
    "totalCpuCores": 800,
    "totalMemoryGB": 3200,
    "hdfsCapacityTB": 500,
    "hdfsUsedTB": 350,
    "hdfsUsageRate": 0.70,
    "hdfsFileCount": 5000000,
    "hdfsBlockCount": 15000000,
    "nameNodeCount": 2,
    "dataNodeCount": 48,
    "rmCount": 2,
    "nmCount": 48,
    "yarnTotalMemoryGB": 2400,
    "yarnTotalVcores": 800,
    "kafkaBrokerCount": 5,
    "kafkaTopicCount": 200,
    "kafkaPartitionCount": 1000,
    "alertCountToday": 5,
    "anomalyCountToday": 3,
    "avgClusterLoad": 0.65
  }
}
```

### GET /status/clusters

获取所有集群列表。

**请求示例**
```bash
curl http://localhost:8080/v1/status/clusters
```

**响应示例**
```json
[
  {
    "cluster": "bigdata-prod",
    "statusDate": "2024-01-15",
    "totalNodes": 50,
    "activeNodes": 48
  },
  {
    "cluster": "bigdata-test",
    "statusDate": "2024-01-15",
    "totalNodes": 20,
    "activeNodes": 18
  }
]
```

---

## 告警事件

### GET /events/alerts

获取未处理告警。

**请求参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| cluster | string | 否 | 集群名称 |

**请求示例**
```bash
curl "http://localhost:8080/v1/events/alerts?cluster=bigdata-prod"
```

**响应示例**
```json
{
  "cluster": "bigdata-prod",
  "unresolvedCount": 3,
  "alerts": [
    {
      "eventId": "evt-001",
      "eventType": "alert",
      "severity": "critical",
      "title": "内存使用率过高",
      "description": "namenode内存使用率达到92%",
      "service": "hdfs",
      "component": "namenode",
      "eventTime": "2024-01-15T10:00:00",
      "resolved": false
    }
  ]
}
```

### POST /events

添加事件。

**请求参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| cluster | string | 是 | 集群名称 |

**请求体**
```json
{
  "eventId": "evt-002",
  "eventType": "alert",
  "severity": "warning",
  "title": "CPU使用率偏高",
  "description": "resourcemanager CPU使用率达到85%",
  "service": "yarn",
  "component": "resourcemanager",
  "eventTime": "2024-01-15T10:30:00",
  "resolved": false
}
```

---

## 实时指标

### POST /metrics/realtime

保存实时指标。

**请求体**
```json
{
  "cluster": "bigdata-prod",
  "service": "hdfs",
  "component": "namenode",
  "instance": "nn1",
  "cpuUsage": 0.75,
  "memoryUsage": 0.82,
  "gcTime": 2500,
  "connectionCount": 150,
  "timestamp": 1705312800
}
```

### GET /metrics/realtime

查询实时指标。

**请求参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| cluster | string | 是 | 集群名称 |
| service | string | 否 | 服务类型 |
| component | string | 否 | 组件类型 |

---

## 测试接口

### POST /test/generate-data

生成测试数据（用于开发测试）。

**请求参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| cluster | string | 否 | 集群名称 |

**请求示例**
```bash
curl -X POST "http://localhost:8080/v1/test/generate-data?cluster=bigdata-prod"
```

**响应示例**
```json
{
  "success": true,
  "message": "测试数据生成成功",
  "generated": {
    "metrics": "bigdata-prod:hdfs:namenode:nn1",
    "event": "evt-xxx"
  }
}
```

---

## 错误码

| 状态码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 500 | 服务器内部错误 |

## 健康状态枚举

| 值 | 说明 |
|----|------|
| healthy | 健康 |
| warning | 警告 |
| critical | 严重 |
| unknown | 未知 |
