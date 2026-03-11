#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
BigDataAIOps 测试数据生成脚本

用于生成符合 List<ComponentMetrics> 格式的测试数据，
支持一体化分析接口的测试验证。

使用方法:
    python generate_test_data.py [--points N] [--cluster NAME] [--output FILE]

示例:
    python generate_test_data.py --points 20 --cluster bigdata-prod
    python generate_test_data.py --points 10 --output test_metrics.json
    python generate_test_data.py --help
"""

import json
import random
import argparse
import time
from datetime import datetime, timedelta
from typing import List, Dict, Any, Optional


# 服务和组件映射
SERVICE_COMPONENTS = {
    "hdfs": ["namenode", "datanode", "journalnode", "zkfc"],
    "yarn": ["resourcemanager", "nodemanager", "proxyserver"],
    "spark": ["sparkmaster", "sparkworker", "historyserver"],
    "hive": ["hiveserver2", "metastore", "webhcat"],
    "kafka": ["broker", "controller"],
    "flink": ["jobmanager", "taskmanager"]
}

# 集群列表
CLUSTERS = ["bigdata-prod", "bigdata-test", "bigdata-dev"]


def generate_single_metric(
    cluster: str,
    service: str,
    component: str,
    instance: str,
    timestamp: int,
    cpu_base: float = 0.5,
    memory_base: float = 0.6,
    gc_base: int = 1000,
    add_noise: bool = True
) -> Dict[str, Any]:
    """
    生成单个时序数据点
    
    Args:
        cluster: 集群名称
        service: 服务类型
        component: 组件类型
        instance: 实例标识
        timestamp: 时间戳
        cpu_base: CPU使用率基准值
        memory_base: 内存使用率基准值
        gc_base: GC时间基准值
        add_noise: 是否添加随机噪声
    
    Returns:
        单个指标数据点
    """
    if add_noise:
        noise = random.uniform(-0.1, 0.1)
        cpu = max(0.1, min(0.95, cpu_base + noise))
        memory = max(0.1, min(0.95, memory_base + noise * 0.5))
        gc = max(100, gc_base + random.randint(-500, 500))
    else:
        cpu = cpu_base
        memory = memory_base
        gc = gc_base
    
    # 生成额外的自定义指标
    extra_metrics = {
        "connection_count": random.randint(50, 500),
        "thread_count": random.randint(50, 200),
        "heap_used_mb": int(memory * 4096),
        "heap_max_mb": 4096,
        "disk_io_read_mb": round(random.uniform(10, 500), 2),
        "disk_io_write_mb": round(random.uniform(5, 200), 2),
        "network_in_kb": round(random.uniform(100, 5000), 2),
        "network_out_kb": round(random.uniform(50, 2000), 2)
    }
    
    return {
        "cluster": cluster,
        "service": service,
        "component": component,
        "instance": instance,
        "cpuUsage": round(cpu, 4),
        "memoryUsage": round(memory, 4),
        "gcTime": gc,
        "timestamp": timestamp,
        "metrics": extra_metrics
    }


def generate_time_series(
    num_points: int = 10,
    cluster: str = "bigdata-prod",
    service: Optional[str] = None,
    component: Optional[str] = None,
    instance: Optional[str] = None,
    interval_seconds: int = 60,
    trend: str = "random",
    start_time: Optional[int] = None
) -> List[Dict[str, Any]]:
    """
    生成时序数据列表
    
    Args:
        num_points: 数据点数量
        cluster: 集群名称
        service: 服务类型（不指定则随机选择）
        component: 组件类型（不指定则随机选择）
        instance: 实例标识（不指定则自动生成）
        interval_seconds: 数据点间隔（秒）
        trend: 趋势类型 (random/ascending/descending/spike)
        start_time: 起始时间戳（不指定则使用当前时间）
    
    Returns:
        时序数据列表
    """
    # 随机选择服务和组件
    if service is None:
        service = random.choice(list(SERVICE_COMPONENTS.keys()))
    if component is None:
        component = random.choice(SERVICE_COMPONENTS.get(service, ["unknown"]))
    if instance is None:
        instance = f"{component[:2]}{random.randint(1, 5)}"
    
    # 设置起始时间
    if start_time is None:
        start_time = int(time.time()) - num_points * interval_seconds
    
    # 基准值
    cpu_base = random.uniform(0.3, 0.7)
    memory_base = random.uniform(0.4, 0.8)
    gc_base = random.randint(500, 2000)
    
    metrics_list = []
    
    for i in range(num_points):
        timestamp = start_time + i * interval_seconds
        
        # 根据趋势类型调整基准值
        if trend == "ascending":
            # 上升趋势：CPU和内存逐渐升高
            cpu = cpu_base + i * 0.03
            memory = memory_base + i * 0.02
            gc = gc_base + i * 100
        elif trend == "descending":
            # 下降趋势
            cpu = cpu_base + (num_points - i) * 0.02
            memory = memory_base + (num_points - i) * 0.015
            gc = gc_base + (num_points - i) * 80
        elif trend == "spike":
            # 尖峰趋势：中间出现峰值
            mid = num_points // 2
            spike_factor = 1 - abs(i - mid) / mid if mid > 0 else 0
            cpu = cpu_base + spike_factor * 0.3
            memory = memory_base + spike_factor * 0.2
            gc = int(gc_base + spike_factor * 2000)
        else:
            # 随机波动
            cpu = cpu_base
            memory = memory_base
            gc = gc_base
        
        metric = generate_single_metric(
            cluster=cluster,
            service=service,
            component=component,
            instance=instance,
            timestamp=timestamp,
            cpu_base=min(0.95, cpu),
            memory_base=min(0.95, memory),
            gc_base=gc,
            add_noise=(trend == "random")
        )
        
        metrics_list.append(metric)
    
    return metrics_list


def generate_multi_component_series(
    num_points: int = 10,
    cluster: str = "bigdata-prod",
    components: Optional[List[Dict[str, str]]] = None,
    interval_seconds: int = 60
) -> List[Dict[str, Any]]:
    """
    生成多组件的时序数据
    
    Args:
        num_points: 每个组件的数据点数量
        cluster: 集群名称
        components: 组件列表 [{"service": "hdfs", "component": "namenode", "instance": "nn1"}, ...]
        interval_seconds: 数据点间隔
    
    Returns:
        所有时序数据列表
    """
    if components is None:
        # 默认生成常见组件的数据
        components = [
            {"service": "hdfs", "component": "namenode", "instance": "nn1"},
            {"service": "hdfs", "component": "datanode", "instance": "dn1"},
            {"service": "yarn", "component": "resourcemanager", "instance": "rm1"},
            {"service": "spark", "component": "sparkmaster", "instance": "sm1"},
        ]
    
    all_metrics = []
    start_time = int(time.time()) - num_points * interval_seconds
    
    for comp in components:
        metrics = generate_time_series(
            num_points=num_points,
            cluster=cluster,
            service=comp["service"],
            component=comp["component"],
            instance=comp["instance"],
            interval_seconds=interval_seconds,
            start_time=start_time
        )
        all_metrics.extend(metrics)
    
    return all_metrics


def print_sample_output(metrics_list: List[Dict[str, Any]], limit: int = 3):
    """打印示例输出"""
    print("\n" + "=" * 60)
    print("示例数据 (前{}条):".format(min(limit, len(metrics_list))))
    print("=" * 60)
    
    for i, m in enumerate(metrics_list[:limit]):
        print(f"\n[{i+1}] {m['service']}/{m['component']}/{m['instance']}")
        print(f"    时间: {datetime.fromtimestamp(m['timestamp']).strftime('%Y-%m-%d %H:%M:%S')}")
        print(f"    CPU: {m['cpuUsage']*100:.1f}%")
        print(f"    内存: {m['memoryUsage']*100:.1f}%")
        print(f"    GC时间: {m['gcTime']}ms")
    
    if len(metrics_list) > limit:
        print(f"\n... 共 {len(metrics_list)} 条数据")


def main():
    parser = argparse.ArgumentParser(
        description="生成 BigDataAIOps 测试数据",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  python generate_test_data.py --points 20
  python generate_test_data.py --points 10 --cluster bigdata-test --output test.json
  python generate_test_data.py --points 15 --service hdfs --component namenode
  python generate_test_data.py --points 20 --trend ascending
  python generate_test_data.py --multi-component
        """
    )
    
    parser.add_argument(
        "--points", "-n",
        type=int,
        default=10,
        help="数据点数量 (默认: 10)"
    )
    
    parser.add_argument(
        "--cluster", "-c",
        type=str,
        default="bigdata-prod",
        help="集群名称 (默认: bigdata-prod)"
    )
    
    parser.add_argument(
        "--service", "-s",
        type=str,
        choices=list(SERVICE_COMPONENTS.keys()),
        help="服务类型 (不指定则随机)"
    )
    
    parser.add_argument(
        "--component",
        type=str,
        help="组件类型 (不指定则随机)"
    )
    
    parser.add_argument(
        "--instance", "-i",
        type=str,
        help="实例标识 (不指定则自动生成)"
    )
    
    parser.add_argument(
        "--interval",
        type=int,
        default=60,
        help="数据点间隔秒数 (默认: 60)"
    )
    
    parser.add_argument(
        "--trend", "-t",
        type=str,
        choices=["random", "ascending", "descending", "spike"],
        default="random",
        help="趋势类型: random(随机), ascending(上升), descending(下降), spike(尖峰) (默认: random)"
    )
    
    parser.add_argument(
        "--output", "-o",
        type=str,
        help="输出文件路径 (不指定则打印到控制台)"
    )
    
    parser.add_argument(
        "--multi-component", "-m",
        action="store_true",
        help="生成多组件数据"
    )
    
    parser.add_argument(
        "--curl",
        action="store_true",
        help="生成 curl 命令用于测试"
    )
    
    parser.add_argument(
        "--api-url",
        type=str,
        default="http://localhost:8080/v1/analyze/timeseries/direct",
        help="API地址 (默认: http://localhost:8080/v1/analyze/timeseries/direct)"
    )
    
    args = parser.parse_args()
    
    # 生成数据
    if args.multi_component:
        metrics_list = generate_multi_component_series(
            num_points=args.points,
            cluster=args.cluster,
            interval_seconds=args.interval
        )
    else:
        metrics_list = generate_time_series(
            num_points=args.points,
            cluster=args.cluster,
            service=args.service,
            component=args.component,
            instance=args.instance,
            interval_seconds=args.interval,
            trend=args.trend
        )
    
    # 输出
    json_str = json.dumps(metrics_list, indent=2, ensure_ascii=False)
    
    if args.output:
        with open(args.output, "w", encoding="utf-8") as f:
            f.write(json_str)
        print(f"已生成 {len(metrics_list)} 条数据，保存到: {args.output}")
        print_sample_output(metrics_list)
    elif args.curl:
        # 生成 curl 命令
        escaped_json = json_str.replace("'", "'\\''")
        print("\n" + "=" * 80)
        print("CURL 命令:")
        print("=" * 80)
        print(f"""
curl -X POST "{args.api_url}?cluster={args.cluster}" \\
  -H "Content-Type: application/json" \\
  -d '{json_str}'
""")
    else:
        print(f"\n生成 {len(metrics_list)} 条数据:")
        print_sample_output(metrics_list)
        print("\n" + "=" * 60)
        print("JSON 输出:")
        print("=" * 60)
        print(json_str)


if __name__ == "__main__":
    main()
