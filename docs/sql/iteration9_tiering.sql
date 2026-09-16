-- 迭代 9：数据分层（原始明细 7 天 + 小时聚合表长期保留）
-- 目标库：smartcane（MySQL 8.0.19+）
--
-- 分层结构：
--   t_crutch_sensor_data   原始明细，保留 7 天（DataSyncScheduler 每天凌晨清理）
--   t_health_hourly_stat   小时统计，长期保留（本脚本新建）—— 报表的持久底座
--   t_health_daily_stat    日统计，由小时统计求和（迭代 8 建表）
--   周报                    由日统计汇总，不落表
--
-- 为什么改成 7 天：原来保留 48 小时，明细一删就再也算不出当天以外的报表。
-- 现在明细只负责「最近 7 天的明细查询 + 给小时表供货」，历史报表全部走聚合表。

CREATE TABLE IF NOT EXISTS t_health_hourly_stat
(
    id                          bigint unsigned auto_increment comment '主键ID'
        primary key,
    device_sn                   varchar(64)                        not null comment '关联拐杖设备序列号',
    stat_hour                   datetime                           not null comment '统计小时（整点）',
    sample_count                int      default 0                 not null comment '该小时采样条数',
    heart_rate_sum              int      default 0                 not null comment '有效心率之和（heart_rate > 0）',
    heart_rate_count            int      default 0                 not null comment '有效心率样本数，均值 = sum / count',
    blood_oxygen_sum            int      default 0                 not null comment '有效血氧之和（blood_oxygen > 0）',
    blood_oxygen_count          int      default 0                 not null comment '有效血氧样本数',
    fall_count                  int      default 0                 not null comment '摔倒次数（fall_status = 1 的采样数）',
    heart_rate_abnormal_count   int      default 0                 not null comment '心率异常次数（<50 或 >120）',
    blood_oxygen_abnormal_count int      default 0                 not null comment '血氧异常次数（<90）',
    active_minutes              int      default 0                 not null comment '在线时长（分钟）：相邻采样间隔 ≤5 分钟累加，跨小时用 carry-in 补边界（2026-09-16 由位移口径改为在线口径）',
    create_time                 datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time                 datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '最近一次重算时间',
    constraint uk_device_hour
        unique (device_sn, stat_hour),
    constraint fk_hourly_dev
        foreign key (device_sn) references t_crutch_device (device_sn)
            on delete cascade
)
    comment '健康统计小时表（预聚合，长期保留，日统计的数据源）';

-- 不需要额外索引：查询口径固定是「单设备 + 时间区间」，uk_device_hour 已覆盖。

-- ============ 验证 ============
SHOW INDEX FROM t_health_hourly_stat;

SELECT COUNT(*) AS hourly_rows FROM t_health_hourly_stat;

-- 补充说明：
-- 1) 小时表不设清理任务：它是历史报表的唯一数据源，清掉就等于报表断档。
--    真到了量大的时候，可以按「保留 1 年 + 归档到日表」再处理，演示阶段不做。
-- 2) 小时表重算窗口是最近 48 小时（HealthStatScheduler），与明细 7 天保留期之间有 5 天安全边际。
-- 3) 外键沿用 ON DELETE CASCADE：删除设备会连带清空统计，属迭代 7「设备删除保护」已拦截的操作。