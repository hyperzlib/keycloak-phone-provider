#!/usr/bin/env python3
import csv
import json
from typing import List, Dict, Any

def read_csv_file(filename: str) -> List[Dict[str, str]]:
    """
    读取CSV文件并返回字典列表
    """
    data = []
    try:
        with open(filename, 'r', encoding='utf-8') as file:
            # 使用制表符作为分隔符
            reader = csv.DictReader(file, delimiter='\t')
            for row in reader:
                # 移除引号并清理数据
                cleaned_row = {}
                for key, value in row.items():
                    if key and value:
                        cleaned_row[key.strip().replace('"', '')] = value.strip().replace('"', '')
                data.append(cleaned_row)
    except FileNotFoundError:
        print(f"错误: 找不到文件 {filename}")
        return []
    except Exception as e:
        print(f"读取文件时发生错误: {e}")
        return []
    
    return data

def convert_to_json(csv_data: List[Dict[str, str]]) -> List[Dict[str, Any]]:
    """
    将CSV数据转换为JSON格式
    """
    json_data = []
    
    for row in csv_data:
        if all(key in row for key in ['areacode', 'code', 'country', 'countrycn']):
            try:
                json_data.append({
                    'areaCode': int(row['areacode']),
                    'countryCode': row['code'],
                    'name': {
                        'en': row['country'],
                        'zh-cn': row['countrycn']
                    }
                })
            except ValueError:
                # 如果areacode不是有效的整数，跳过这一行
                print(f"警告: 跳过无效的areacode: {row.get('areacode', 'N/A')}")
                continue
    
    return json_data

def save_json_file(data: List[Dict[str, Any]], filename: str) -> None:
    """
    将数据保存为JSON文件
    """
    try:
        with open(filename, 'w', encoding='utf-8') as file:
            json.dump(data, file, ensure_ascii=False, indent=2)
        print(f"成功将数据保存到 {filename}")
    except Exception as e:
        print(f"保存文件时发生错误: {e}")

def main():
    """
    主函数
    """
    input_file = "isms_areacode.csv"
    output_file = "areacode.json"
    
    print(f"正在读取 {input_file}...")
    csv_data = read_csv_file(input_file)
    
    if not csv_data:
        print("没有读取到有效数据")
        return
    
    print(f"读取到 {len(csv_data)} 条记录")
    
    print("正在转换数据格式...")
    json_data = convert_to_json(csv_data)
    
    print(f"转换完成，共 {len(json_data)} 条有效记录")
    
    print(f"正在保存到 {output_file}...")
    save_json_file(json_data, output_file)

if __name__ == "__main__":
    main()