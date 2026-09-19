-- 迭代 10：登录鉴权 + 监护人数据隔离
-- 目标库：smartcane（MySQL 8.0）
--
-- 为什么自建账号表：内网演示环境没有统一认证中心，前端（Vue）和小程序都用账号密码登录，
-- 小程序不接微信 code2session（需要 AppSecret 与备案域名，内网演示不具备）。
--
-- 密码只存 PBKDF2WithHmacSHA256 哈希（格式 pbkdf2$迭代次数$盐$哈希），不存明文、不存 MD5：
-- PBKDF2 是 JDK 自带能力，不用为了 BCrypt 额外引入 Spring Security。
--
-- t_user_device 是数据隔离的依据：GUARDIAN 角色的查询会被强制收窄到绑定过的 device_sn，
-- ADMIN 不加限制。绑定关系随设备删除级联清理。

CREATE TABLE IF NOT EXISTS t_user
(
    id            bigint unsigned auto_increment comment '主键ID'
        primary key,
    username      varchar(32)  not null comment '登录名',
    password_hash varchar(200) not null comment 'PBKDF2 密码哈希，格式 pbkdf2$迭代次数$盐$哈希',
    role          varchar(16)  not null default 'GUARDIAN' comment '角色：ADMIN 管理员 / GUARDIAN 监护人',
    real_name     varchar(32)  default '' null comment '真实姓名',
    phone         varchar(11)  default '' null comment '手机号',
    status        tinyint      not null default 1 comment '状态：1启用 0禁用',
    create_time   datetime     not null default CURRENT_TIMESTAMP comment '创建时间',
    update_time   datetime     not null default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_username
        unique (username)
)
    comment '系统用户表（登录账号）';

CREATE TABLE IF NOT EXISTS t_user_device
(
    id          bigint unsigned auto_increment comment '主键ID'
        primary key,
    user_id     bigint unsigned not null comment '用户ID',
    device_sn   varchar(64)     not null comment '绑定的设备序列号',
    create_time datetime        not null default CURRENT_TIMESTAMP comment '绑定时间',
    constraint uk_user_device
        unique (user_id, device_sn),
    constraint fk_ud_user
        foreign key (user_id) references t_user (id)
            on delete cascade,
    constraint fk_ud_dev
        foreign key (device_sn) references t_crutch_device (device_sn)
            on delete cascade
)
    comment '监护人-设备绑定关系表（数据隔离依据）';

-- ============ 种子账号（内网演示用，正式部署必须改口令）============
-- admin/admin123、guardian/guardian123
INSERT INTO t_user (username, password_hash, role, real_name, phone, status)
VALUES ('admin',
        'pbkdf2$120000$NLa6uhZkCrgf7+exHjVdHg==$uSH43tEjvykSY78jNI9IgLGNWrqPvH4JRFnZD5pKzoM=',
        'ADMIN', '系统管理员', '', 1),
       ('guardian',
        'pbkdf2$120000$flASjV646FraAiAKpHlVmw==$JmElHjVd2WPQov/v8OUCFUPplbkiz/d2YWl4kMSmRiw=',
        'GUARDIAN', '张三家属', '13682910008', 1) AS new
ON DUPLICATE KEY UPDATE password_hash = new.password_hash,
                        role          = new.role,
                        real_name     = new.real_name,
                        status        = new.status;

-- 给演示监护人绑定演示设备。设备不存在时不插（便于在空库上重复执行）
INSERT IGNORE INTO t_user_device (user_id, device_sn)
SELECT u.id, d.device_sn
FROM t_user u
         JOIN t_crutch_device d ON d.device_sn = '862323084243065'
WHERE u.username = 'guardian';

-- ============ 验证 ============
SHOW INDEX FROM t_user;

SHOW INDEX FROM t_user_device;

SELECT username, role, status, real_name FROM t_user;

SELECT u.username, b.device_sn, d.elder_name, d.guardian_phone
FROM t_user_device b
         JOIN t_user u ON u.id = b.user_id
         LEFT JOIN t_crutch_device d ON d.device_sn = b.device_sn;

-- 补充说明：
-- 1) 会话不落库：token 存在 Redis（key=smartcane:auth:token:{token}，TTL 2 小时，操作即续期），
--    登出与账号停用立即生效。
-- 2) 权限判定在每个请求回查一次 t_user，多一次主键查询换「停用立即生效」。
-- 3) 删除用户会级联删除绑定关系，但不删除设备数据。