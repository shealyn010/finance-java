-- FinInsight 数据库初始化
CREATE DATABASE IF NOT EXISTS workmind DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE workmind;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `username`    VARCHAR(255) NOT NULL UNIQUE,
    `password`    VARCHAR(255) NOT NULL COMMENT 'bcrypt加密',
    `phone`       VARCHAR(20)  DEFAULT NULL,
    `company`     VARCHAR(100) DEFAULT NULL COMMENT '所属企业',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 上传任务表
CREATE TABLE IF NOT EXISTS `upload_task` (
    `task_id`    VARCHAR(36)  NOT NULL PRIMARY KEY COMMENT '任务UUID',
    `user_id`    BIGINT       NOT NULL,
    `filename`   VARCHAR(255) NOT NULL COMMENT '原始文件名',
    `file_size`  BIGINT       DEFAULT 0 COMMENT '文件大小(字节)',
    `status`     ENUM('pending','processing','done','failed') NOT NULL DEFAULT 'pending',
    `total`      INT          DEFAULT 0 COMMENT '有效记录数',
    `error_msg`  TEXT         DEFAULT NULL,
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='上传任务表';

-- 交易明细表 (微信账单)
CREATE TABLE IF NOT EXISTS `wechat_transaction` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `task_id`          VARCHAR(36)  NOT NULL,
    `user_id`          BIGINT       NOT NULL,
    `trans_time`       DATETIME     NOT NULL COMMENT '交易时间',
    `target`           VARCHAR(100) DEFAULT NULL COMMENT '交易对方',
    `specific_product` VARCHAR(200) DEFAULT NULL COMMENT '商品说明',
    `trans_type`       ENUM('支出','收入') NOT NULL,
    `money`            DECIMAL(10,2) NOT NULL COMMENT '金额(元)',
    `category`         VARCHAR(50)  DEFAULT '未分类' COMMENT 'AI分类结果',
    `ai_classified`    TINYINT(1)   DEFAULT 0 COMMENT '是否已AI分类',
    INDEX `idx_task` (`task_id`),
    INDEX `idx_user_time_type` (`user_id`, `trans_time`, `trans_type`),
    INDEX `idx_user_category` (`user_id`, `category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微信交易明细表';

-- 企业工单表 (新增)
CREATE TABLE IF NOT EXISTS `work_order` (
    `order_id`       VARCHAR(36)  NOT NULL PRIMARY KEY COMMENT '工单编号',
    `user_id`        BIGINT       NOT NULL,
    `customer`       VARCHAR(100) NOT NULL COMMENT '客户名称',
    `service_type`   VARCHAR(50)  NOT NULL COMMENT '服务类型: 安装/维修/巡检/保养/定制',
    `service_desc`   VARCHAR(500) DEFAULT NULL COMMENT '服务描述',
    `labor_hours`    DECIMAL(5,1) DEFAULT 0 COMMENT '工时(h)',
    `labor_cost`     DECIMAL(10,2) DEFAULT 0 COMMENT '人工费',
    `material_cost`  DECIMAL(10,2) DEFAULT 0 COMMENT '材料费',
    `other_cost`     DECIMAL(10,2) DEFAULT 0 COMMENT '其他费用',
    `total_revenue`  DECIMAL(10,2) DEFAULT 0 COMMENT '工单收入',
    `total_cost`     DECIMAL(10,2) DEFAULT 0 COMMENT '总成本',
    `profit`         DECIMAL(10,2) DEFAULT 0 COMMENT '利润',
    `profit_rate`    DECIMAL(5,2)  DEFAULT 0 COMMENT '利润率(%)',
    `location`       VARCHAR(100) DEFAULT NULL COMMENT '服务地点(如: 中山市古镇)',
    `technician`     VARCHAR(50)  DEFAULT NULL COMMENT '技术人员',
    `order_time`     DATETIME     NOT NULL COMMENT '工单日期',
    `status`         ENUM('pending','processing','completed','closed') NOT NULL DEFAULT 'pending',
    `ai_category`    VARCHAR(50)  DEFAULT '未分类' COMMENT 'AI分析标签: 高利润/常规/亏损/重点客户',
    `ai_analyzed`    TINYINT(1)   DEFAULT 0 COMMENT '是否已AI分析',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_user_time` (`user_id`, `order_time`),
    INDEX `idx_user_ai_cat` (`user_id`, `ai_category`),
    INDEX `idx_location` (`location`),
    INDEX `idx_service_type` (`service_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企业工单表';

-- 工单报表缓存表 (预聚合，加速查询)
CREATE TABLE IF NOT EXISTS `work_order_report` (
    `id`           BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `user_id`      BIGINT NOT NULL,
    `period`       VARCHAR(10) NOT NULL COMMENT '周期: day/month/year',
    `period_value` VARCHAR(20) NOT NULL COMMENT '周期值: 2026-05-27/2026-05/2026',
    `total_orders` INT    DEFAULT 0,
    `total_revenue` DECIMAL(12,2) DEFAULT 0,
    `total_cost`   DECIMAL(12,2) DEFAULT 0,
    `total_profit` DECIMAL(12,2) DEFAULT 0,
    `avg_profit_rate` DECIMAL(5,2) DEFAULT 0,
    UNIQUE KEY `uk_user_period` (`user_id`, `period`, `period_value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单报表缓存表';
