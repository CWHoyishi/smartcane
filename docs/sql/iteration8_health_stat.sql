-- 迭代 8：健康统计（日/周报）预聚合表
-- 目标库：smartcane（MySQL 8.0.19+，upsert 用到行别名语法）
--
-- 为什么预聚合：日/周报要算「平均心率、平均血氧、异常次数、在线时长」，
-- 实时聚合等于每次请求都扫一遍 t_crutch_sensor_data（单设备一天约 1440 行，多设备线性放大），
-- 且明细只保留 48 小时，历史报表根本查不到。改成定时把结果写进这张表，接口只读表。
--
-- 心率/血氧存 sum + count 而不是平均数：周报要按样本数加权（SUM(sum)/SUM(count)），
-- 存平均数会丢权重，天数不等时算错。

CREATE TABLE IF NOT EXISTS t_health_daily_stat
(
    id                          bigint unsigned auto_increment comment '主键ID'
        primary key,
    device_sn                   varchar(64)            not null comment '关联拐杖设备序列号',
    stat_date                   date                   not null comment '统计日期（自然日）',
    sample_count                int      default 0     not null comment '当日采样总条数',
    heart_rate_sum              int      default 0     not null comment '有效心率之和（heart_rate > 0）',
    heart_rate_count            int      default 0     not null comment '有效心率样本数，均值 = sum / count',
    blood_oxygen_sum            int      default 0     not null comment '有效血氧之和（blood_oxygen > 0）',
    blood_oxygen_count          int      default 0     not null comment '有效血氧样本数',
    fall_count                  int      default 0     not null comment '摔倒次数（fall_status = 1 的采样数）',
    heart_rate_abnormal_count   int      default 0     not null comment '心率异常次数（<50 或 >120）',
    blood_oxygen_abnormal_count int      default 0     not null comment '血氧异常次数（<90）',
    active_minutes              int      default 0     not null comment '在线时长（分钟）：相邻采样间隔 ≤5 分钟即累加（2026-09-16 由「位移≥10米」改为在线口径）',
    create_time                 datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time                 datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '最近一次重算时间',
    constraint uk_device_date
        unique (device_sn, stat_date),
    constraint fk_stat_dev
        foreign key (device_sn) references t_crutch_device (device_sn)
            on delete cascade
)
    comment '健康统计日报表（定时预聚合，可重复重算）';

-- 不需要额外索引：查询口径固定为「单设备 + 日期区间」，uk_device_date 已覆盖。

-- ============ 验证 ============
SHOW INDEX FROM t_health_daily_stat;

SELECT COUNT(*) AS stat_rows FROM t_health_daily_stat;

-- 补充说明：
-- 1) 重算入口：应用启动后每 10 分钟自动重算「今天 + 昨天」（HealthStatScheduler），
--    也可手动 POST /api/health-stat/rebuild?days=7 回填历史。
-- 2) 没有采样的日期不落行；接口读取时会补空行，避免把「没有数据」记成「0 活动」。
-- 3) 外键沿用 ON DELETE CASCADE：删除设备会连带清空统计，属迭代 7「设备删除保护」已拦截的操作。