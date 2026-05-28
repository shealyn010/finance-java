package com.fininsight.transaction.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 本地RAG知识检索（轻量版，不依赖Chroma/Embedding）
 * 基于关键词匹配检索维修知识库
 */
@Service
public class RagService {

    // 维修知识库
    private static final Map<String, String> KNOWLEDGE = new LinkedHashMap<>();
    static {
        KNOWLEDGE.put("轴承 6205", """
            【深沟球轴承 6205-2RS 规格说明】
            型号: 6205-2RS（双面橡胶密封）
            内径: 25mm | 外径: 52mm | 宽度: 15mm
            基本额定动载荷: 14.0kN | 静载荷: 7.85kN
            极限转速: 12000rpm（脂润滑）
            用途: 电动机、水泵、风机、变速箱等通用机械设备
            2RS含义: 双面接触式橡胶密封，防尘防水，终身润滑免维护
            更换周期: 连续运转2000小时或每6个月检查一次
            常见故障: 异响(润滑脂干涸)、振动增大(滚道剥落)、温升异常(密封失效)
            """);
        KNOWLEDGE.put("空调不制冷", """
            【空调不制冷排查指南】
            1. 检查电源和遥控器设置（模式是否制冷、温度是否低于室温）
            2. 清洗室外机冷凝器翅片（高压水枪+翅片清洗剂）
            3. 检测制冷剂压力（R410A正常低压0.6-0.8MPa，高压2.0-2.8MPa）
            4. 检查压缩机电容是否鼓包或漏液
            5. 排查四通阀是否卡滞（制热模式下切换测试）
            """);
        KNOWLEDGE.put("LED灯不亮", """
            【LED灯具故障排查】
            1. 先测输入电压（正常220V±10%）
            2. 拆开检查驱动电源输出（恒流源通常12V/24V/36V）
            3. 用万用表二极管档逐个测LED灯珠（正常正向压降1.8-3.3V）
            4. 常见故障：驱动电源电容鼓包(占60%)、灯珠开路(占30%)、接头氧化(占10%)
            5. 更换配件时注意功率匹配（驱动电源功率≥灯珠总功率×1.2）
            """);
        KNOWLEDGE.put("数控机床", """
            【数控机床常见故障】
            1. 伺服驱动器报警：检查编码器线缆是否松动、驱动器散热风扇是否停转
            2. 主轴异响：检查主轴轴承（每2000小时加注润滑脂）、皮带张紧度
            3. 定位精度偏差：重新校准回零开关、检查丝杆反向间隙（正常<0.02mm）
            4. 冷却液系统：定期更换（每3个月）、过滤网清洗（每周）
            """);
        KNOWLEDGE.put("电路板", """
            【电路板维修通用流程】
            1. 目检：查找烧焦痕迹、鼓包电容、断裂焊点
            2. 测电源：各级电压是否正常（5V/3.3V/1.8V）
            3. 关键信号：用示波器测晶振起振（正常正弦波）、复位信号电平
            4. 分区隔离：逐级断开负载定位短路区域
            5. 更换元件后务必做老化测试（满载运行2小时）
            """);
        KNOWLEDGE.put("SLA", """
            【SLA服务等级协议-工单响应标准】
            - 紧急工单(生产设备停机): 30分钟内响应, 4小时内到场
            - 高优先级(影响生产但未停机): 2小时内响应, 8小时内到场
            - 普通工单: 4小时内响应, 24小时内到场
            - 巡检保养: 按计划执行, 超时48小时升级处理
            超时处罚: 紧急工单每超1小时扣服务费5%, 最高扣30%
            """);
    }

    /** 关键词检索（生产环境应换 Chroma/ES） */
    public String search(String question) {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> e : KNOWLEDGE.entrySet()) {
            if (question.contains(e.getKey()) || containsAny(question, e.getKey())) {
                result.append(e.getValue()).append("\n");
            }
        }
        if (result.isEmpty()) {
            // 模糊匹配
            for (Map.Entry<String, String> e : KNOWLEDGE.entrySet()) {
                for (String word : e.getKey().split("")) {
                    if (question.contains(word) && word.length() > 1) {
                        result.append(e.getValue()).append("\n");
                        break;
                    }
                }
            }
        }
        return result.isEmpty() ? null : result.toString();
    }

    private boolean containsAny(String q, String key) {
        for (char c : key.toCharArray()) {
            if (c > 127 && q.indexOf(c) >= 0) return true; // 汉字匹配
        }
        return false;
    }

    /** 获取知识库条目列表（前端展示用） */
    public List<String> topics() {
        return new ArrayList<>(KNOWLEDGE.keySet());
    }
}
