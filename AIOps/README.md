# BigDataAIOps - AI驱动的大数据集群智能运维平台

## 目录

- [项目概述](#项目概述)
- [技术架构](#技术架构)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [关键配置](#关键配置)
- [快速开始](#快速开始)
- [API接口说明](#api接口说明)
- [Prompt设计与时序分析](#prompt设计与时序分析)
- [扩展开发指南](#扩展开发指南)

---

## 项目概述

BigDataAIOps 是一个基于 AI 的大数据集群智能运维分析平台，旨在通过大语言模型（LLM）实现对大数据集群的自动化监控、异常检测和智能诊断。

### 核心功能

| 功能模块 | 描述 |
|---------|------|
| **时序数据分析** | 分析组件指标的时序变化趋势，识别异常波动，预测潜在问题 |
| **Spark作业分析** | 分析Spark作业执行情况，识别数据倾斜、GC瓶颈等性能问题 |
| **集群整体分析** | 综合评估集群健康状态，包括资源使用、组件状态、告警情况 |
| **智能诊断** | 基于LLM自动分析问题根因，生成优化建议和操作步骤 |
| **告警管理** | 集中管理集群告警，支持告警聚合和智能降噪 |
| **JSON数据输入** | 支持直接输入JSON数组格式的时序数据进行分析 |

### 系统特点

- **LLM驱动**：利用大语言模型进行智能分析，自动生成诊断报告
- **Function Calling**：LLM可自主调用工具获取实时数据进行分析
- **一体化接口**：支持单次API调用完成数据存储和分析全流程
- **Mock模式**：支持离线Mock模式，无需真实LLM即可演示功能
- **时序数据优化**：针对时间序列数据专门优化的Prompt设计
- **可扩展架构**：模块化设计，易于扩展新的分析类型和LLM Provider

---

## 技术架构

### 系统架构图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              前端展示层                                      │
│                         (HTML/CSS/JavaScript)                               │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐           │
│  │  集群分析    │ │  时序分析    │ │ Spark分析   │ │ JSON输入    │           │
│  │    Tab      │ │    Tab      │ │    Tab      │ │    Tab      │           │
│  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘           │
│                    http://localhost:8080                                    │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            REST API 层                                       │
│                       AIOpsController (/v1/*)                               │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    一体化分析接口 (推荐)                               │   │
│  │  /analyze/timeseries/direct  - 时序数据一步分析                       │   │
│  │  /analyze/spark/direct       - Spark作业一步分析                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    分步接口 (底层支持)                                 │   │
│  │  /metrics/timeseries  → 存储 → 返回storageId                          │   │
│  │  /analyze/timeseries  → 分析 → 返回结果                               │   │
│  │  /spark/job           → 存储 → 返回jobId                              │   │
│  │  /analyze/spark/{id}  → 分析 → 返回结果                               │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            业务服务层                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    AIAnalysisService                                 │   │
│  │              (时序分析 / Spark分析 / 集群分析)                         │   │
│  │                                                                      │   │
│  │  【Prompt优化】专门针对时序数据设计的System Prompt:                    │   │
│  │  - 明确告知LLM这是时间序列数据                                        │   │
│  │  - 强调分析趋势而非单个数值                                           │   │
│  │  - 提供时序分析的具体步骤                                             │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                      │                                      │
│  ┌───────────────────┐ ┌───────────────────┐ ┌───────────────────┐         │
│  │MetricsTimeSeries  │ │  SparkJobStore    │ │RecentEventsService│         │
│  │     Store         │ │                   │ │                   │         │
│  │  (时序数据存储)    │ │  (作业数据存储)    │ │  (告警事件管理)    │         │
│  │                   │ │                   │ │                   │         │
│  │ 【时序上下文增强】 │ │                   │ │                   │         │
│  │ 返回数据时包含:    │ │                   │ │                   │         │
│  │ - 时间跨度说明     │ │                   │ │                   │         │
│  │ - 采样间隔        │ │                   │ │                   │         │
│  │ - 趋势判断        │ │                   │ │                   │         │
│  └───────────────────┘ └───────────────────┘ └───────────────────┘         │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            LLM 服务层                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                      Spring AI ChatClient                            │   │
│  │                    (OpenAI 兼容接口)                                  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                      │                                      │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    Function Calling (Tools)                          │   │
│  │  ┌───────────────┐ ┌───────────────┐ ┌───────────────┐              │   │
│  │  │getMetrics     │ │getSparkJob    │ │getRealtime    │              │   │
│  │  │TimeSeries     │ │               │ │Metrics        │              │   │
│  │  │               │ │               │ │               │              │   │
│  │  │【时序数据专用】│ │               │ │               │              │   │
│  │  │返回带时间上下文│ │               │ │               │              │   │
│  │  │的结构化数据    │ │               │ │               │              │   │
│  │  └───────────────┘ └───────────────┘ └───────────────┘              │   │
│  │  ┌───────────────┐ ┌───────────────┐                                │   │
│  │  │getLongTerm    │ │getRecentEvents│                                │   │
│  │  │Status         │ │               │                                │   │
│  │  └───────────────┘ └───────────────┘                                │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            数据存储层                                        │
│  ┌─────────────────────────┐ ┌─────────────────────────┐                   │
│  │        MySQL            │ │         Redis           │                   │
│  │  ┌─────────────────┐    │ │  ┌─────────────────┐    │                   │
│  │  │long_term_status │    │ │  │timeseries:*     │    │                   │
│  │  │analysis_result  │    │ │  │spark_job:*      │    │                   │
│  │  └─────────────────┘    │ │  │realtime:*       │    │                   │
│  │  (持久化数据存储)        │ │  │events:*         │    │                   │
│  └─────────────────────────┘ │  └─────────────────┘    │                   │
│                              │  (时序/缓存数据存储)      │                   │
│                              └─────────────────────────┘                   │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 数据流程

```
1. 一体化时序分析流程 (推荐):
   前端 → POST /v1/analyze/timeseries/direct 
        → 内部: 存储数据到Redis → 获取storageId → 调用分析服务
        → LLM调用getMetricsTimeSeries工具(带时序上下文)
        → 返回分析结果

2. 一体化Spark分析流程 (推荐):
   前端 → POST /v1/analyze/spark/direct
        → 内部: 存储作业到Redis → 获取jobId → 调用分析服务
        → LLM调用getSparkJob工具 → 返回分析结果

3. 分步接口流程 (底层支持):
   前端 → POST /v1/metrics/timeseries → Redis存储 → 返回storageId
   前端 → POST /v1/analyze/timeseries?storageId=xxx → AIAnalysisService
        → LLM调用工具 → Redis获取数据 → LLM分析 → 返回结果
```

---

## 技术栈

### 后端技术

| 技术 | 版本 | 用途 |
|-----|------|------|
| **Spring Boot** | 3.2.x | 应用框架，提供自动配置和快速开发能力 |
| **Spring AI** | 1.0.0-M2 | LLM集成框架，支持OpenAI兼容接口 |
| **Spring Data JPA** | - | ORM框架，简化数据库操作 |
| **Spring Data Redis** | - | Redis集成，用于时序数据存储 |
| **MySQL** | 8.0+ | 关系型数据库，存储持久化数据 |
| **Redis** | 7.0+ | 内存数据库，存储时序数据和缓存 |
| **Lombok** | - | 简化Java代码，自动生成getter/setter |
| **Jackson** | - | JSON序列化/反序列化 |
| **Maven** | 3.8+ | 项目构建和依赖管理 |

### 前端技术

| 技术 | 用途 |
|-----|------|
| **HTML5** | 页面结构 |
| **CSS3** | 样式设计，渐变、动画效果 |
| **JavaScript (ES6+)** | 交互逻辑，Fetch API调用 |

### 支持的LLM Provider

| Provider | 配置方式 | 说明 |
|----------|---------|------|
| **OpenAI** | api-key + base-url | 官方OpenAI API |
| **ModelScope** | api-key + base-url | 阿里云ModelScope平台 |
| **通义千问** | api-key + base-url | 阿里云通义千问API |
| **DeepSeek** | api-key + base-url | DeepSeek API |
| **Ollama** | 本地部署 | 本地运行的开源模型 |
| **Mock模式** | use-mock: true | 无需真实LLM的演示模式 |

---

## 项目结构

```
AIOps/
├── docs/                          # 文档目录
│   ├── API.md                     # API详细文档
│   └── USER_MANUAL.md             # 用户手册
├── scripts/                       # 脚本目录
│   └── generate_test_data.py      # Python测试数据生成脚本
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/aiops/bigdata/
│   │   │       ├── Application.java           # Spring Boot启动类
│   │   │       │
│   │   │       ├── config/                    # 配置类
│   │   │       │   ├── LLMConfig.java         # LLM相关配置
│   │   │       │   ├── LLMProperties.java     # LLM属性配置
│   │   │       │   ├── RedisConfig.java       # Redis配置
│   │   │       │   └── WebConfig.java         # Web配置(CORS/静态资源)
│   │   │       │
│   │   │       ├── controller/                # REST API控制器
│   │   │       │   └── AIOpsController.java   # 主控制器
│   │   │       │
│   │   │       ├── entity/                    # 实体类
│   │   │       │   ├── analysis/              # 分析相关实体
│   │   │       │   ├── context/               # 上下文数据实体
│   │   │       │   ├── feature/               # 特征提取实体
│   │   │       │   └── metrics/               # 指标实体
│   │   │       │
│   │   │       ├── service/                   # 服务层
│   │   │       │   ├── ai/                    # AI分析服务
│   │   │       │   │   └── impl/
│   │   │       │   │       └── AIAnalysisServiceImpl.java  # 核心实现
│   │   │       │   │           ├── SYSTEM_PROMPT  # 时序数据优化Prompt
│   │   │       │   │           └── callLLM()      # LLM调用逻辑
│   │   │       │   │
│   │   │       │   ├── context/               # 上下文服务
│   │   │       │   │   └── impl/
│   │   │       │   │       └── MetricsTimeSeriesStoreImpl.java
│   │   │       │   │           └── getTimeSeriesSummary() # 带时序上下文
│   │   │       │   │
│   │   │       │   └── tool/                  # Spring AI Function工具
│   │   │       │       └── impl/
│   │   │       │           └── MetricsTimeSeriesToolImpl.java
│   │   │       │
│   │   │       └── repository/                # 数据访问层
│   │   │
│   │   └── resources/
│   │       ├── static/
│   │       │   └── index.html                 # 前端页面
│   │       ├── application.yml                # 主配置文件
│   │       ├── schema.sql                     # 数据库初始化脚本
│   │       └── thresholds.yml                 # 阈值配置
│   │
│   └── 需求文档.txt
│
├── target/                         # 编译输出目录
├── pom.xml                         # Maven配置文件
└── README.md                       # 本文档
```

---

## 关键配置

### application.yml 完整配置说明

```yaml
spring:
  application:
    name: aiops-server

  # ==================== MySQL 数据源配置 ====================
  datasource:
    url: jdbc:mysql://localhost:3306/aiops?useSSL=false&serverTimezone=Asia/Shanghai
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: root
    password: your_password
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5

  # ==================== Redis 配置 ====================
  data:
    redis:
      host: localhost
      port: 6379
      database: 0
      timeout: 10000ms

  # ==================== Spring AI 配置 ====================
  ai:
    openai:
      api-key: ${OPENAI_API_KEY:sk-mock}
      base-url: ${OPENAI_BASE_URL:https://api.openai.com}
      chat:
        options:
          model: ${OPENAI_MODEL:gpt-4o-mini}
          temperature: 0.7
          max-tokens: 2000

# ==================== AIOps 业务配置 ====================
aiops:
  metrics:
    retention-days: 30
    max-cache-size: 10000
  
  analysis:
    default-depth: normal
    timeout-seconds: 60
  
  # LLM 配置
  llm:
    use-mock: true           # 是否使用Mock模式
    mock-delay-ms: 500       # Mock模式延迟(毫秒)
```

---

## 快速开始

### 环境要求

- **JDK**: 17+
- **Maven**: 3.8+
- **MySQL**: 8.0+
- **Redis**: 7.0+

### 1. 安装依赖服务

```bash
# MySQL
brew install mysql && brew services start mysql
mysql -u root -p -e "CREATE DATABASE aiops CHARACTER SET utf8mb4;"

# Redis
brew install redis && brew services start redis
redis-cli ping
```

### 2. 构建并启动

```bash
cd AIOps
mvn clean install -DskipTests
mvn spring-boot:run
```

### 3. 访问应用

- **前端页面**: http://localhost:8080
- **健康检查**: http://localhost:8080/v1/health

### 4. 快速测试

```bash
# 一体化时序分析
curl -X POST "http://localhost:8080/v1/analyze/timeseries/direct?cluster=bigdata-prod" \
  -H "Content-Type: application/json" \
  -d '[{"cluster":"bigdata-prod","service":"hdfs","component":"namenode","instance":"nn1","cpuUsage":0.75,"memoryUsage":0.82,"gcTime":2500,"timestamp":1700000000}]'

# 一体化Spark分析
curl -X POST "http://localhost:8080/v1/analyze/spark/direct?cluster=bigdata-prod" \
  -H "Content-Type: application/json" \
  -d '{"appName":"test-job","status":"COMPLETED","duration":180000,"executorCount":10,"skewRatio":5.5}'
```

---

## API接口说明

### 一体化分析接口（推荐）

| 方法 | 路径 | 说明 |
|-----|------|------|
| POST | `/analyze/timeseries/direct` | 时序数据一步分析（存储+分析） |
| POST | `/analyze/spark/direct` | Spark作业一步分析（存储+分析） |

**一体化时序分析示例**:

```bash
POST /v1/analyze/timeseries/direct?cluster=bigdata-prod
Content-Type: application/json

[
  {
    "cluster": "bigdata-prod",
    "service": "hdfs",
    "component": "namenode",
    "instance": "nn1",
    "cpuUsage": 0.75,
    "memoryUsage": 0.82,
    "gcTime": 2500,
    "timestamp": 1700000000
  }
]
```

### 分步接口（底层支持）

| 方法 | 路径 | 说明 |
|-----|------|------|
| POST | `/metrics/timeseries` | 存储时序数据，返回storageId |
| POST | `/analyze/timeseries` | 根据storageId分析时序数据 |
| POST | `/spark/job` | 存储Spark作业数据 |
| POST | `/analyze/spark/{jobId}` | 分析Spark作业 |
| GET | `/analyze/cluster` | 分析集群整体状态 |

### 其他接口

| 方法 | 路径 | 说明 |
|-----|------|------|
| GET | `/status/overview` | 获取集群概览 |
| GET | `/events/alerts` | 获取未处理告警 |
| POST | `/test/timeseries` | 生成测试时序数据 |
| POST | `/test/spark-job` | 生成测试Spark作业 |
| GET | `/health` | 健康检查 |

---

## Prompt设计与时序分析

### 问题背景

当 `List<ComponentMetrics>` 存储到 Redis 后，大模型通过 Tool 调用查看时可能无法理解这是**时间序列数据**的集合，而误认为是单个指标数据。这会导致分析结果缺乏对趋势和时序特征的识别。

### 解决方案

#### 1. System Prompt 增强

在 `AIAnalysisServiceImpl.java` 中，System Prompt 专门增加了时序数据说明：

```java
private static final String SYSTEM_PROMPT = """
    你是一个专业的大数据运维分析专家。
    
    ## 关于时序数据的重要说明
    
    当你调用 getMetricsTimeSeries 工具时，返回的数据是**时间序列数据（Time Series Data）**：
    - 这是同一个组件在不同时间点采集的指标数据集合
    - 数据按时间顺序排列，每个数据点包含时间戳(timestamp)
    - 你需要分析指标随时间的变化趋势，而不仅仅是单个数值
    - 重点关注：上升趋势、下降趋势、异常波动、周期性模式
    
    时序数据分析要点：
    1. 趋势分析：CPU/内存使用率是否持续上升或下降
    2. 异常检测：是否存在突增或突降的异常点
    3. 阈值判断：当前值是否接近或超过告警阈值
    4. 关联分析：多个指标之间是否存在关联变化
    ...
    """;
```

#### 2. 用户提示词优化

分析时序数据时，User Prompt 明确指出这是时间序列数据：

```java
String userPrompt = String.format("""
    请分析以下组件的时间序列指标数据：
    
    存储ID: %s
    集群: %s
    
    【重要】这是一组时间序列数据，包含同一组件在不同时间点的多个指标采样。
    
    请按以下步骤分析：
    1. 使用 getMetricsTimeSeries 工具获取时序数据详情
    2. 分析数据的时间跨度和采样间隔
    3. 识别各指标（CPU、内存、GC等）随时间的变化趋势
    4. 检测是否存在异常波动或突变点
    5. 综合评估组件健康状态并给出优化建议
    """, storageId, cluster);
```

#### 3. Tool 返回数据增强

`MetricsTimeSeriesStoreImpl.getTimeSeriesSummary()` 返回的数据包含丰富的时序上下文：

```
=== 时间序列数据摘要 ===
【重要说明】这是一组时间序列数据，包含同一组件在不同时间点的多个指标采样。
请分析指标随时间的变化趋势，而不仅仅是单个数值。

## 组件信息
服务: hdfs
组件: namenode
实例: nn1
集群: bigdata-prod

## 时序数据特征
数据点数量: 10 个
时间跨度: 540 秒 (9.0 分钟)
采样间隔: 约 60 秒
起始时间: 2024-01-01 10:00:00
结束时间: 2024-01-01 10:09:00

## 监控指标 (3个)
指标列表: cpu_usage, memory_usage, gc_time

## 各指标时序统计
(以下数据展示各指标在时间序列中的变化情况)

【cpu_usage】
  当前值: 0.68
  最小值: 0.50, 最大值: 0.72
  平均值: 0.61, 标准差: 0.07
  趋势: 上升
```

### 关键优化点总结

| 优化点 | 位置 | 说明 |
|-------|------|------|
| System Prompt | AIAnalysisServiceImpl | 明确告知LLM时序数据特性 |
| User Prompt | AIAnalysisServiceImpl | 提供时序分析的具体步骤 |
| 数据摘要 | MetricsTimeSeriesStoreImpl | 返回带时间上下文的结构化数据 |
| 趋势判断 | MetricsTimeSeriesStoreImpl | 自动计算上升/下降/稳定趋势 |
| 时间格式化 | MetricsTimeSeriesStoreImpl | 将时间戳转为可读格式 |

---

## 扩展开发指南

### 添加新的LLM Provider

```java
@Configuration
public class NewLLMConfig {
    @Bean
    @ConditionalOnProperty(name = "aiops.llm.provider", havingValue = "new-provider")
    public ChatClient.Builder newProviderChatClientBuilder(NewLLMProperties properties) {
        return ChatClient.builder(/* 配置 */);
    }
}
```

### 添加新的Tool

```java
@Component
public class NewDataTool {
    @Tool(description = "获取XXX数据，用于分析YYY场景")
    public String getNewData(
            @ToolParam(description = "集群名称") String cluster) {
        // 返回JSON格式数据
    }
}
```

### 添加新的分析类型

1. 扩展 `AIAnalysisService` 接口
2. 在 `AIAnalysisServiceImpl` 中实现
3. 在 `AIOpsController` 中添加端点

---

## 常见问题

### Q: 启动时报 "The string did not match the expected pattern" 错误

A: 启用Mock模式：
```yaml
aiops:
  llm:
    use-mock: true
```

### Q: 前端请求返回HTML而不是JSON

A: 直接访问 `http://localhost:8080`，不要使用IDEA内置预览。

### Q: 大模型无法正确理解时序数据

A: 已通过Prompt优化解决，确保使用最新版本的代码。

---

## 许可证

MIT License
