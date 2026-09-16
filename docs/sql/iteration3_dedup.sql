-- 迭代 3：统一 report_time 口径 + 重复上报去重
-- 目标库：smartcane（MySQL 8）
--
-- 应用侧改动已随本迭代提交：两条采集通道（MQTT 推送 / HTTP 拉取）写入前统一把
-- report_time 截断到秒，并把唯一键冲突当作「重复上报」忽略。
-- 但只有本脚本建立的唯一索引真正存在时，去重才生效 —— 二者必须一起上线。
--
-- 执行顺序不可颠倒：必须先清理历史重复行，否则第 3 步建索引会失败。
-- 建议在业务低峰期执行, 并先做一次备份: mysqldump -h192.168.88.130 -uroot -p smartcane t_crutch_sensor_data > backup.sql

-- ============ 第 0 步：现状确认 ============
SELECT COUNT(*) AS total_rows FROM t_crutch_sensor_data;

SELECT COUNT(*) AS duplicate_groups FROM (
    SELECT device_sn, report_time
    FROM t_crutch_sensor_data
    GROUP BY device_sn, report_time
    HAVING COUNT(*) > 1
) AS g;

-- ============ 第 1 步：查看重复明细（人工确认再删） ============
SELECT device_sn, report_time, COUNT(*) AS cnt
FROM t_crutch_sensor_data
GROUP BY device_sn, report_time
HAVING cnt > 1
ORDER BY cnt DESC, report_time DESC
LIMIT 20;

-- ============ 第 2 步：删除重复行，每组仅保留最小 id ============
-- 注意：该语句会删除数据，执行前请确认第 1 步结果符合预期。
DELETE t1
FROM t_crutch_sensor_data t1
JOIN t_crutch_sensor_data t2
  ON t1.device_sn = t2.device_sn
 AND t1.report_time = t2.report_time
 AND t1.id > t2.id;

-- ============ 第 3 步：建立唯一索引（应用侧去重依赖它） ============
ALTER TABLE t_crutch_sensor_data
    ADD CONSTRAINT uk_device_report UNIQUE (device_sn, report_time);

-- ============ 第 4 步：验证 ============
SHOW INDEX FROM t_crutch_sensor_data WHERE Key_name = 'uk_device_report';

SELECT COUNT(*) AS duplicate_groups_after FROM (
    SELECT device_sn, report_time
    FROM t_crutch_sensor_data
    GROUP BY device_sn, report_time
    HAVING COUNT(*) > 1
) AS g;

-- 补充说明：
-- 1) 唯一键只能挡住「device_sn + report_time 完全相同」的重复。
--    若两个通道取到的是同一条采样但时间戳不同（例如 MQTT 用消息 at、API 用属性 updateTime），
--    仍可能落两行。应用侧已统一为「优先取平台时间戳 + 截断到秒」，若实际运行仍有漏网重复，
--    需要进一步核对两个通道的时间戳来源。
-- 2) 现有索引 idx_dev_report (device_sn, report_time) 与本唯一键列相同，
--    建唯一索引后该普通索引已冗余，可选择删除以节省写入开销：
--    DROP INDEX idx_dev_report ON t_crutch_sensor_data;