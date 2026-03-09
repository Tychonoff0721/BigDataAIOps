# AIOps 大数据集群智能运维平台

## 项目简介

AIOps是一个基于AI的大数据集群智能运维平台，能够自动分析集群组件健康状态和Spark作业性能，提供智能诊断和优化建议。

## 核心功能

- **组件健康分析**: 自动分析HDFS、YARN、Spark、Kafka、Flink等组件的健康状态
- **Spark作业分析**: 分析Spark作业性能瓶颈，识别数据倾斜、GC问题等
- **智能诊断**: 基于LLM的智能根因分析和优化建议
- **Tool Calling**: LLM可主动查询集群状态获取更多上下文
- **多模型支持**: 支持OpenAI、Claude、本地模型等多种LLM

## 技术架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端展示层                                │
│                    (静态HTML + JavaScript)                      │
└─────────────────────────────────────────────────────────────────┘
                               │
┌─────────────────────────────────────────────────────────────────┐
│                        REST API层                                │
│                      AIOpsController                             │
└─────────────────────────────────────────────────────────────────┘
                               │
┌─────────────────────────────────────────────────────────────────┐
│                        业务服务层                                │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │AIAnalysisSvc │  │FeatureExtractor│  │PromptBuilder│          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└─────────────────────────────────────────────────────────────────┘
                               │
┌─────────────────────────────────────────────────────────────────┐
│                        LLM服务层                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │OpenAIProvider│  │ClaudeProvider│  │LocalProvider │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
│  ┌──────────────┐                                                │
│  │ MockProvider │  (开发测试用)                                   │
│  └──────────────┘                                                │
└─────────────────────────────────────────────────────────────────┘
                               │
┌─────────────────────────────────────────────────────────────────┐
│                        数据存储层                                │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │Redis(实时)   │  │MySQL(长期)   │  │Redis(事件)   │          │
│  │RealtimeMetrics│ │LongTermStatus│  │RecentEvents  │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└─────────────────────────────────────────────────────────────────┘
```

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0+
- Redis 6.0+

### 安装步骤

1. **克隆项目**
```bash
git clone <repository-url>
cd BigDataAIOps/AIOps
```

2. **创建数据库**
```sql
CREATE DATABASE aiops DEFAULT CHARACTER SET utf8mb4;
```

3. **启动Redis**
```bash
redis-server
```

4. **修改配置** (如需要)
编辑 `src/main/resources/application.yml`，修改数据库和Redis连接信息。

5. **启动应用**
```bash
mvn spring-boot:run
```

6. **访问系统**
- 前端页面: http://localhost:8080/
- 健康检查: http://localhost:8080/v1/health

## 配置说明

### LLM配置

在 `application.yml` 中配置大模型：

```yaml
aiops:
  llm:
    provider: mock  # openai, claude, local, mock
    
    openai:
      enabled: true
      api-key: ${OPENAI_API_KEY}
      model: gpt-4
      
    claude:
      enabled: true
      api-key: ${CLAUDE_API_KEY}
      model: claude-3-opus-20240229
      
    local:
      enabled: true
      base-url: http://localhost:8000/v1
      model: local-model
```

### 阈值配置

在 `thresholds.yml` 中配置告警阈值：

```yaml
component:
  memory-usage:
    warning: 0.80
    critical: 0.90
  cpu-usage:
    warning: 0.80
    critical: 0.90

spark-app:
  skew-ratio:
    warning: 3.0
    critical: 10.0
  shuffle-ratio:
    warning: 0.5
    critical: 0.8
```

## 项目结构

```
AIOps/
├── src/main/java/com/aiops/bigdata/
│   ├── config/                 # 配置类
│   │   ├── LLMConfig.java      # LLM配置
│   │   ├── LLMProperties.java  # LLM属性
│   │   ├── RedisConfig.java    # Redis配置
│   │   └── WebConfig.java      # Web配置
│   │
│   ├── controller/             # 控制器
│   │   └── AIOpsController.java
│   │
│   ├── entity/                 # 实体类
│   │   ├── analysis/           # 分析结果
│   │   ├── common/             # 公共类
│   │   ├── config/             # 配置实体
│   │   ├── context/            # 上下文实体
│   │   ├── feature/            # 特征实体
│   │   └── metrics/            # 指标实体
│   │
│   ├── repository/             # 数据访问层
│   │   └── LongTermStatusRepository.java
│   │
│   └── service/                # 服务层
│       ├── analysis/           # 分析服务
│       ├── context/            # 上下文服务
│       ├── feature/            # 特征提取
│       ├── llm/                # LLM服务
│       ├── prompt/             # Prompt构建
│       └── tool/               # Tool实现
│
├── src/main/resources/
│   ├── application.yml         # 应用配置
│   ├── thresholds.yml          # 阈值配置
│   ├── schema.sql              # 数据库脚本
│   └── static/
│       └── index.html          # 前端页面
│
└── pom.xml
```

## 扩展开发

### 添加新的LLM Provider

1. 实现 `LLMProvider` 接口：

```java
public class CustomProvider implements LLMProvider {
    @Override
    public ChatResponse chat(String systemPrompt, String userPrompt, List<ToolDefinition> tools) {
        // 实现调用逻辑
    }
    
    @Override
    public String getProviderName() {
        return "custom";
    }
}
```

2. 在 `LLMConfig.java` 中注册：

```java
case "custom" -> createCustomProvider();
```

### 添加新的Tool

1. 创建Tool接口：

```java
public interface CustomTool {
    String NAME = "get_custom_data";
    String execute(String param);
}
```

2. 实现Tool：

```java
@Service
public class CustomToolImpl implements CustomTool {
    @Override
    public String execute(String param) {
        // 实现逻辑
    }
}
```

3. 在 `LLMServiceImpl` 中注册Tool处理逻辑。

## 许可证

MIT License
