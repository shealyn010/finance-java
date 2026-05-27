package com.fininsight.transaction.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("work_order")
public class WorkOrder {
    @TableId
    private String orderId;          // UUID
    private Long userId;
    private String customer;         // 客户名称
    private String serviceType;      // 安装/维修/巡检/保养/定制
    private String serviceDesc;      // 服务描述
    private BigDecimal laborHours;    // 工时
    private BigDecimal laborCost;     // 人工费
    private BigDecimal materialCost;  // 材料费
    private BigDecimal otherCost;     // 其他费用
    private BigDecimal totalRevenue;  // 工单收入
    private BigDecimal totalCost;     // 总成本
    private BigDecimal profit;        // 利润
    private BigDecimal profitRate;    // 利润率(%)
    private String location;         // 服务地点
    private String technician;       // 技术员
    private LocalDateTime orderTime; // 工单日期
    private String status;           // pending/processing/completed/closed
    private String aiCategory;       // AI标签: 高利润/常规/亏损/重点客户
    private Integer aiAnalyzed;      // 0/1
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
