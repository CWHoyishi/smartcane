# 智能手杖系统 优化与扩展方案

> 更新日期：2026-09-16　当前分支：`dev`（`main` 保留初始版本）
> 本文档记录已确认的决策、实测环境、待办事项与验证标准，随迭代同步更新。

## 1. 技术栈（升级后）

| 模块 | 技术 | 备注 |
| --- | --- | --- |
| 后端框架 | Spring Boot 3.4.5 + Java 17 | 原 2.6.13 + Java 8，已升级 |
| 持久层 | MyBatis-Plus 3.5.7 + MySQL 8 | 原 3.5.5（starter 坐标不同） |
| 接口文档 | springdoc-openapi 2.8.5 | 原 knife4j 3.0.3 + springfox，Spring 6 下不可用 |
| 缓存/消息 | Redis（Docker 部署） | 依赖与配置已就绪，业务代码尚未使用 |
| 物联网接入 | OneNET：MQTT 推送 + HTTP 物模型 API 拉取 | 双通道写同一张表 |
| 前端 | Vue 3 + Vite + Element Plus | dev server 端口 3000，`/api` 代理到 8080 |
| 小程序 | 微信原生小程序 | `utils/api.js` 的 BASE_URL 指向后端 8080 |

## 2. 环境信息（实测）

| 项 | 实测结果 |
| --- | --- |
| JDK | 17.0.10（`D:\Java\jdk-17`，`JAVA_HOME` 已指向） |
| Maven | 3.6.3（IDEA 内置）；**活动本地仓库为 `D:\IDEA\plugins\maven\lib\maven3\mvn_repo`**，非默认 `~/.m2` |
| Maven 仓库连通性 | Central 可达（需在沙箱外执行）；`settings.xml` 中 mirror 用的是 aliyun 旧版 nexus 地址 |
| MySQL | `192.168.88.130:3306` 可达 |
| Redis | `192.168.88.130:6379` 可达，密码 `123456` 认证通过；**版本 2.8.19**，不支持 Stream（`XADD`/`XINFO` 报 unknown command），仅基础命令与 List 可用 |
| OneNET MQTT | `896VnUK204.mqtts.acc.cmcconenet.cn:6002` TCP 可达，但应用连接后对端 EOF（待确认） |
| 后端端口 | 8080 |

> 注：本项目的开发沙箱禁用了 AF_UNIX 回环 socket，任何基于 NIO 的 Java 服务都无法在其中启动。
> 这已被最小复现程序（仅调用 `Selector.open()`）证实，与项目代码无关，运行期验证需在 IDE 或本机终端进行。

## 3. 已确认的决策

1. **技术栈**：升级到 Spring Boot 3.4.5 + Java 17（用户原选 3.2.x，因 3.2 开源维护期已结束、且本地仓库已缓存 3.4.5 而改用 3.4.5）。
2. **消息队列**：采用 **Redis Stream**（出于技术栈完备性考虑）。需配套消费组、手动 ACK 与 pending 兜底，否则重启会丢消息。
   - ⚠️ 实测该实例为 **2.8.19**，**不支持 Stream**（Stream 需 5.0+，`XAUTOCLAIM` 需 6.2+）。需先升级容器才能落地本方案；备选是在 2.8 上用 List 可靠队列（`LPUSH`/`BRPOPLPUSH`）自行实现重试与孤儿消息回收。
3. **告警通道**：采用 **短信**，不使用微信订阅消息。当前阶段只实现 `MockSmsSender`（输出到日志），正式实现（阿里云/腾讯云）留接口位置。
   - 因此**不需要**用户体系：设备表已有 `guardian_phone` 字段，短信直接发往该号码，无需 openid、`wx.login`、绑定表。
4. **部署场景**：内网演示。故不引入 HTTPS、完整 JWT 鉴权体系；跨域沿用现有宽松配置。
5. **前端端口**：3000。
6. **在线时长口径**（2026-09-16 定案）：相邻采样间隔 ≤300 秒即累加，与设备离线判定同一门槛，不再要求位移 ≥10 米。字段与接口名仍沿用 `activeMinutes`，不动表结构。

## 4. 性能问题清单（升级前实测，含位置）

| # | 问题 | 位置 | 影响 |
| --- | --- | --- | --- |
| 1 | `fetchInterval` 单位错配：配置 10000 被 `@Scheduled` 按**毫秒**解析，实际每 10 秒拉一轮，而注释写的是 60 秒 | `application.yml`、`DataSyncScheduler.java` | 云平台调用量与配额放大 6 倍；`OneNetController` 还输出「10000秒」 |
| 2 | `new RestTemplate()` 无连接/读超时、无连接复用 | `OneNetApiService.java` | 平台不响应时调度线程被永久挂住；频繁建连 |
| 3 | 每个 HTTP 请求都重算 HMAC-SHA256 生成 Token | `OneNetApiService.doFetch()` | 无谓的 CPU 开销 |
| 4 | 调度为单线程串行 for 循环 | `DataSyncScheduler.syncDeviceData()` | 设备数增长后单轮耗时线性超周期 |
| 5 | MQTT 回调线程内同步做 DB 查询与写入；每条消息先 `selectOne` 查设备 | `OneNetDataProcessor.ensureDeviceExists()` | 高频上报时阻塞 Paho 回调线程 |
| 6 | 双通道（MQTT + HTTP）写同一张表，无去重、无唯一键 | `OneNetDataProcessor` / `OneNetApiService` | 历史数据重复，统计失真 |
| 7 | `listByDeviceSn` 注释写「最近100条」但代码无 LIMIT | `CrutchSensorDataServiceImpl` | 按 10 秒一条，单设备一天 8640 条会全量返回 |
| 8 | 日志过载：业务包 debug + MyBatis `StdOutImpl` 打印全部 SQL + 每条 MQTT 报文与每次入库都 info 全量打印 | `application.yml`、`OneNetMqttConfig`、`OneNetDataProcessor` | 生产环境 I/O 开销显著 |
| 9 | 前端与小程序均为 10 秒轮询 | `LatestDataView.vue`、`pages/monitor/index.js` | 服务端 QPS ≈ 设备数 × 在线客户端数 × 6/min |
| 10 | 数据清理任务硬删 2 天前数据 | `DataSyncScheduler.cleanExpiredSensorData()` | 长期趋势数据不可回溯 |

## 5. 迭代计划与状态

### 迭代 0：技术栈升级 + Java 17（已完成，提交 `9eb0d7b`）

- `pom.xml`：parent 2.6.13 → 3.4.5；`java.version` 1.8 → 17；`knife4j` → `springdoc-openapi-starter-webmvc-ui`；`mybatis-plus-boot-starter` → `mybatis-plus-spring-boot3-starter`；`mysql-connector-java` → `com.mysql:mysql-connector-j`；移除未使用的 `spring-integration`、`spring-integration-mqtt`、`xstream`。
- `Knife4jConfig.java`（springfox）→ `OpenApiConfig.java`（springdoc）。
- 12 个文件的 swagger v2 注解 → OpenAPI v3 注解（`@Api`→`@Tag`、`@ApiOperation`→`@Operation`、`@ApiModel/@ApiModelProperty`→`@Schema`）。
- `OneNetMqttConfig`：`javax.annotation` → `jakarta.annotation`。
- 删除 `OneNetMessageHandler.java`：spring-integration 时代的遗留类，全项目零引用。
- `application.yml`：移除仅供 springfox 使用的 `spring.mvc.pathmatch.matching-strategy`；Redis 前缀 `spring.redis` → `spring.data.redis` 并指向 `192.168.88.130`；文档入口 `/doc.html` → `/swagger-ui.html`。
- 验证：`mvn clean package` 成功；启动日志确认 Boot 3.4.5 / Spring 6.2.6 / Tomcat 10.1.40 / MyBatis-Plus 3.5.7 装配正常，中文日志无乱码。

### 迭代 1：性能止血（已完成，待运行验证）

- 修复单位错配：属性更名为 `fetchIntervalMs`（`application.yml` 设为 `60000`），`OneNetMqttProperties`、`DataSyncScheduler`、`OneNetController` 同步更新，单位统一为毫秒。→ 验证：日志显示 60 秒一轮。
- 新增 `RestTemplateConfig`：connect 3s / read 10s 超时，复用 Boot 探测出的底层客户端（JDK HttpClient，自带连接池），`OneNetApiService` 改为构造注入。→ 验证：平台不可达时调度线程不阻塞。
- `OneNetApiService` 新增 Token 缓存：有效期 1 小时、提前 5 分钟刷新、双重检查加锁。→ 验证：多设备单轮只签名一次。
- 日志瘦身：业务包日志级别 `debug` → `info`；移除 MyBatis `StdOutImpl`（SQL 不再打屏）；`[解析]`/`[API拉取]` 的明细日志降为 `debug`；MQTT 报文日志降为 `debug` 并截断 500 字符；每次入库的 `[保存-成功]` 降为 `debug`；移除了已无意义的 `org.springframework.integration` 日志级别配置。
- `OneNetMqttConfig`：MQTT 报文解码显式指定 `StandardCharsets.UTF_8`（原先依赖平台默认编码，中文环境下有隐患）。
- 验证：`mvn clean compile` 通过（30 个源文件，release 17）。

### 迭代 2：并发解耦 + Redis Stream（待做）

**前置阻塞**：Redis 版本过低。密码已配置为 `${REDIS_PASSWORD:123456}`（可用环境变量覆盖），但实例为 **2.8.19**，不支持 Stream，需先升级容器（建议 `redis:7-alpine` 并保留 `requirepass`）。

- `DataSyncScheduler` 串行拉取改为固定大小线程池并发。
- MQTT 回调仅做解析与投递，`XADD` 到 Redis Stream；消费者（有界线程池）负责入库。
- 消费组 + 手动 ACK + `XPENDING`/`XCLAIM` 兜底，保证重启不丢消息。
- `ensureDeviceExists` 增加本地缓存，去掉每条消息一次 `selectOne`。
- → 验证：200 msg/s 压测无堆积；杀掉消费者再启动不丢消息。

### 迭代 3：去重与 `report_time` 统一（已完成，DDL 已执行）

- 新增 `SensorDataWriter`：两条采集通道共用的落库入口，命中唯一键时按「重复上报」忽略（不再当故障报警）。手工上报接口 `report()` 也统一走它。
- `report_time` 口径统一：两条通道都优先取平台时间戳，并统一 `truncatedTo(SECONDS)` 截断到秒。
  - 这是去重能生效的前提：`report_time` 是 `DATETIME(0)`，MySQL 会对小数秒做四舍五入，不截断就可能出现同一采样两个不同值。
- 迁移脚本：`docs/sql/iteration3_dedup.sql`（查重复 → 删重复保留最小 id → 建唯一索引 `uk_device_report` → 验证）。
- ⚠️ 应用侧改动与唯一索引必须一起上线，否则去重不生效（没有索引时不会产生冲突）。
- ✅ 迁移已于 2026-09-16 在库上执行：清理重复行 507 条（958 → 452），唯一索引 `uk_device_report` 已建立，重复组归零。
  - 删除前已逐组校验重复行字段值完全一致，不存在数据丢失。
- → 验证：待运行期回归（双通道并发写入不再产生重复行）。

### 迭代 4：查询收敛（已完成）

- `listByDeviceSn` 补 `LIMIT 100`（原先注释写「最近100条」，代码却没有任何限制）。
- 分页参数加上限保护：`pageNum` 最小 1，`pageSize` 限制在 1~200（默认 10）。
- → 验证：单次响应体小于 100 KB。

### 迭代 5：告警闭环（后端已完成；前端入口、未确认升级留到后续）

**判定规则**（`AlarmEvaluator`）：只判摔倒。

| 条件 | 告警类型 | 级别 |
| --- | --- | --- |
| `fall_status = 1` | `FALL` | 3 紧急 |

- 心率/血氧**不参与判定**（2026-09-16 按需求收敛）：这两个值受佩戴状态影响大，取下拐杖就可能读到 0 或异常值，误报代价高，只在采样表中留档；要放开就在 `evaluate()` 里加分支。
- 判定挂在 `SensorDataWriter` 采样**落库成功之后**：双通道重复上报不会重复判定；判定异常只记 error 日志，不阻断采集主链路。
- 表结构与列注释未改：历史数据里仍有 `HEART_RATE` / `BLOOD_OXYGEN` 记录，接口按 `alarmType` 查询时需要兼容这些旧值。

**存储**：新增 `t_alarm_record`，建表脚本 `docs/sql/iteration5_alarm.sql`（✅ 2026-09-16 已在库上执行）。

- 唯一键 `uk_device_alarm (device_sn, alarm_type, report_time)` 是第二道防线，应用侧命中 `DuplicateKeyException` 直接忽略。
  - 实测：同设备同类型同时间重复插入被拒绝；同采样不同类型（FALL + HEART_RATE）各留一条。
- 索引 `idx_alarm_status_time (status, report_time)` 支撑待处理列表查询。

**接口**（`AlarmController`，前缀 `/api/alarm`）：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/page` | 分页查询告警（可选 deviceSn / alarmType / status），分页保护同迭代 4 |
| GET | `/pending` | 待处理告警，最近 100 条 |
| GET | `/list/{deviceSn}` | 单设备告警，最近 100 条 |
| PUT | `/ack` | 处置告警，`status` 只接受 1(已确认) / 2(误报) |

- 处置是一次性动作：已处置记录不允许再次改判，避免现场重复点击覆盖结论（如需允许改判，去掉 `AlarmServiceImpl.ack` 中的状态校验即可）。
- 未做（原计划中）：未确认自动升级（依赖迭代 6 的通知通道）、前端/小程序处置入口（属前端迭代）。
- → 验证：在 IDE 启动后灌入模拟数据，确认能生成告警并完成状态流转。

### 迭代 6：短信通道（Mock 已完成；正式厂商待账号）

- `SmsSender` 接口 + `MockSmsSender`（只打日志，不产生真实外呼）。
  - 接正式厂商（阿里云/腾讯云）时新增一个实现类即可，但两个实现会同时被扫描到，需用 `@ConditionalOnProperty` 二选一（接口注释已写明）。
- `SmsNotifier`：告警落库成功后触发，接收方取 `t_crutch_device.guardian_phone`；短信内容含老人姓名、设备号、采样时间与判定值。
  - 限流：同设备同类型告警 5 分钟冷却窗口（内存态，重启重置），窗口内只发第一条 —— 摔倒状态持续时每条新采样都会产生新告警，没有冷却会连打几十条短信。
  - 重试：单条最多 3 次、间隔 1 秒；全部失败只记 error 日志，不影响告警落库与采集主链路。
  - 设备不存在 / 未绑定监护人电话 / 冷却期内：不发，只记日志。
- ⚠️ 当前是**同步发送**：Mock 不耗时；接入真实厂商（HTTP 数百毫秒，叠加重试）后必须改由迭代 2 的消息队列异步消费，不能继续占用采集线程。
- 环境确认：演示设备 `862323084243065`（张三）已绑定监护人 `13682910008`，Mock 日志可直接验证。
- 未做（原计划项）：未确认自动升级。
- → 验证：`POST /api/sensor/report` 灌一条 `fallStatus=1`，日志出现 `[短信-模拟] 发送至 13682910008 ...`。

### 迭代 7：收尾（已完成）

**设备删除保护**

- 默认拒绝删除：先统计该设备的采样与告警条数，存在历史数据时返回 `409` + 明确文案（例如「存在 464 条传感器数据、11 条告警记录，删除会一并清除」），只有显式传 `force=true` 才真正执行。
- 前端 `DeviceView` 收到 409 后弹二次确认（红色「一并删除」），确认后才带 `force` 重删；取消则什么都不做。
- 没有采用逻辑删除：`t_crutch_device.device_sn` 是唯一键，逻辑删除后同一序列号无法重新录入，反而更难收场。

**离线判定**

- 设备表没有 `last_seen` 字段，MQTT 掉线也没有回调，所以在线状态改为读时派生：`MAX(report_time)` 距今超过 300 秒即判离线（拉取间隔 60 秒，留 5 倍余量防抖）。
- 新增 `CrutchDeviceMapper.selectLastSeen()`：一条 `GROUP BY device_sn` 查全量设备，避免 N+1；实测执行计划 `Using index for group-by`，走 `uk_device_report`。
- `CrutchDeviceVO` 增加 `lastReportTime`；`deviceStatus` 语义变为派生值（设备表字段不再对外生效，`update` 仍会写库但不影响展示）。
- 前端设备列表新增「最后上报」列，并移除表单里的「在线状态」开关（该值已不可人工设置）。

**Actuator**

- 引入 `spring-boot-starter-actuator`，只暴露 `health,info,metrics`：`/actuator/health`、`/actuator/metrics` 供压测取基线。

**无 DDL 变更**：本迭代不改表结构。

- → 验证：`GET /api/device/list` 的 `deviceStatus`/`lastReportTime` 与实际上报一致；删除有数据的设备被拒，带 `force=true` 才成功。

### 迭代 8：健康统计（日/周报，已完成）

**预聚合表** `t_health_daily_stat`（脚本 `docs/sql/iteration8_health_stat.sql`，✅ 2026-09-16 已在库上执行）

- 唯一键 `uk_device_date (device_sn, stat_date)`；查询口径固定是「单设备 + 日期区间」，不需要额外索引。
- 心率/血氧存 `sum + count` 而不是平均值：周报要按样本数加权（`SUM(sum)/SUM(count)`），存均值会丢权重，天数不等时算错。
- 写入走 `ON DUPLICATE KEY UPDATE`（MySQL 8.0.19+ 行别名语法），重算幂等；实测重复写入是覆盖更新而不是报错。

**聚合口径**（`HealthStatAggregator`，纯计算、不碰数据库）

- 平均值只统计有效读数：心率/血氧为 `0` 视为未佩戴，既不计入均值也不计为异常。
- 异常次数：心率 `<50` 或 `>120`、血氧 `<90`、摔倒（`fall_status = 1`）。**只是统计，与告警无关**（告警目前只判摔倒）。
- 在线时长：相邻采样间隔 ≤300 秒即把整段间隔计入，与设备离线判定（`CrutchDeviceServiceImpl.OFFLINE_THRESHOLD_SECONDS`）用同一门槛。原先按球面位移 ≥10 米近似「走动」，2026-09-16 按需求改为在线口径：老人静坐不动、原地不走也算在线。
- 均值/异常口径实测：13 条构造采样 → 心率均值 62.8、心率异常 4、血氧异常 4、摔倒 1，与手算一致。
- 在线时长口径实测（改口径后重跑）：按时上报但原地不动 3 条（间隔 1 分钟）→ 2 分钟；间隔 6 分钟 → 0 分钟（超窗不计）；carry-in 跨小时边界 → 1 分钟；连续 20 条 → 19 分钟。

**调度**（`HealthStatScheduler`）

- 每 10 分钟重算「今天 + 昨天」，应用启动后立即执行一次。只在凌晨跑一次的话，前端一整天都看不到当天数据。
- 没有采样的日期不落行，接口读取时补空行，保证趋势图横轴连续。
- 采样明细只保留 7 天（迭代 9 起），报表靠聚合表长期留存，不能依赖明细回查；日统计的数据源在迭代 9 改为小时表。

**接口** `/api/health-stat`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/daily/{deviceSn}?days=7` | 日统计，上限 90 天，缺数据日期补空行 |
| GET | `/weekly/{deviceSn}?weeks=4` | 周报，自然周（周一起），上限 12 周 |
| POST | `/rebuild?days=7` | 手动重算，用于回填历史或演示时立即出数 |

**前端**

- 新增「健康统计」页 `HealthStatView.vue`：日报/周报切换、4 个概览指标、4 张趋势图（平均心率、平均血氧、异常次数、在线时长）、明细表格。
- 图表用自写的 SVG 组件 `TrendChart.vue`（折线/柱状两种），不引入 ECharts：内网演示不值得为几张图引入 1 MB 依赖，需要更丰富的交互时再替换。

### 迭代 9：数据分层（明细 7 天 + 小时聚合表长期保留，已完成）

**分层结构**

| 层 | 表 | 保留期 | 说明 |
| --- | --- | --- | --- |
| 原始明细 | `t_crutch_sensor_data` | 7 天 | 明细查询 + 给小时表供货 |
| 小时统计 | `t_health_hourly_stat` | 长期 | 报表的持久底座，不设清理任务 |
| 日统计 | `t_health_daily_stat` | 长期 | 由小时统计求和 |
| 周报 | 不落表 | — | 读取时按自然周汇总日统计 |

- 建表脚本 `docs/sql/iteration9_tiering.sql`（✅ 2026-09-16 已执行）：唯一键 `uk_device_hour (device_sn, stat_hour)`，字段与日表一致（同样存 sum + count）。
- 清理任务 `DataSyncScheduler.cleanExpiredSensorData`：保留期 48 小时 → 7 天（常量 `RAW_DATA_RETENTION_DAYS`），仍是每天凌晨 3 点执行。
- 删除前保护：先调用 `healthStatService.rebuild(RAW_DATA_RETENTION_DAYS + 1)` 重算聚合（多算 1 天是为了覆盖「截止点落在整点之间」的那 1 小时），再删明细；聚合抛异常时直接跳过本次删除，不冒丢历史的风险。

**聚合链路**（`HealthStatScheduler` 每 10 分钟调用一次）

1. 明细 → 小时表：重算最近 48 小时（含当前小时）。
2. 小时表 → 日表：重算最近 2 天，读的是小时表而不是明细。
3. 两步顺序不可颠倒：日统计依赖小时表已刷新。

- 小时表窗口取 48 小时而不是「当前小时」：重启后要能补出停机期间的小时；距明细 7 天保留期还有 5 天安全边际。
- 跨小时的在线时长用 carry-in 补：聚合某小时时额外取「上一小时最后一条采样」，只用它的时间补上跨小时边界的那一段，不重复计入指标。实测对照：带 carry-in = 1 分钟，不带 = 0。
- 小时级在线时长按分钟取整（不足 1 分钟不计），多层嵌套最多带来 ≤1 分钟/小时的取整损失。

**接口**：`POST /api/health-stat/rebuild?days=N` 现在先刷小时表再刷日表（上限 30 天），适合停机后一次性回填。

**小程序**：新增「健康统计」tab（`wechat-miniprogram/pages/health/`）：日报/周报切换、4 个概览指标、4 张趋势图（平均心率、平均血氧、异常次数、在线时长）与明细列表。

- 同样不引第三方图表库：折线用「旋转的细 view」拼线段，柱状用归一化高度，纯 WXSS 实现；横轴最多显示 5 个标签，缺数据的周期断开折线而不是连成假趋势。



## 6. 数据库现状与约束（依据现有建表 SQL）

- `t_crutch_sensor_data` 已有索引：`idx_dev_report (device_sn, report_time)`、`idx_fall_status (fall_status)`。
  → 原计划中的「补索引」项作废；`idx_fall_status` 选择性低，可选优化为 `(fall_status, report_time)`，演示阶段不急。
- 外键：`t_crutch_sensor_data.device_sn` → `t_crutch_device.device_sn`，**ON DELETE CASCADE**。
- 唯一键 `uk_device_report (device_sn, report_time)` 已由迭代 3 建立；`idx_dev_report` 列相同，已冗余（可选删除）。
- 新增 `t_alarm_record`（迭代 5）：唯一键 `uk_device_alarm (device_sn, alarm_type, report_time)`、索引 `idx_alarm_status_time (status, report_time)`，外键同样 `ON DELETE CASCADE`。
- 新增 `t_health_daily_stat`（迭代 8）：唯一键 `uk_device_date (device_sn, stat_date)`，无额外索引（查询口径是「单设备 + 日期区间」）。
- 新增 `t_health_hourly_stat`（迭代 9）：唯一键 `uk_device_hour (device_sn, stat_hour)`，长期保留、无清理任务。

## 7. 风险与待办

| 项 | 状态 | 说明 |
| --- | --- | --- |
| Redis 版本 | **阻塞中** | 实例为 2.8.19，不支持 Stream，迭代 2 需先升级容器 |
| OneNET MQTT EOF | **待确认** | TCP 可达但 broker 未回 CONNACK，需在正常网络下复现判断是网络干扰还是凭据/产品配置问题 |
| 运行期回归 | 部分完成 | 沙箱无法启动 NIO 服务；迭代 5 的告警链路已在运行环境产生真实记录（`t_alarm_record` 内已有 FALL / HEART_RATE 记录），接口与前端仍建议人工过一遍 |
| 小时表回填 | 待执行 | 迭代 9 需重启后端才生效（启动即跑 `rebuild(2)`）；要补更早历史则调 `POST /api/health-stat/rebuild?days=7`。2026-09-16 实测库内 `t_health_hourly_stat` 仍为 0 行、日表仅今天 1 行 |
| 唯一索引未建 | ✅ 已执行 | `docs/sql/iteration3_dedup.sql` 已于 2026-09-16 在库上执行，重复行 958 → 452 |
| 告警判定未验证 | 待执行 | 迭代 5 的判定需在 IDE 启动后灌入模拟数据，确认告警生成与状态流转 |
| 短信签名与模板 | 待申请 | 未就绪前仅 MockSmsSender |
| 在线时长口径 | ✅ 已定案 | 2026-09-16 由「间隔 ≤5 分钟且位移 ≥10 米」改为在线口径：间隔 ≤300 秒即累加。只改 `HealthStatAggregator.onlineSecondsBetween`，表结构与字段名未动；库里 `active_minutes` 列注释仍是旧文案（纯注释，无功能影响） |
| 敏感信息 | 已知风险 | `application.yml` 中 MySQL 密码与 OneNET accessKey 为明文，且已进入 git 历史 |
| fastjson 1.2.83 | 待处理 | 存在已知反序列化风险，计划在迭代 3 之后替换为 Jackson 或 fastjson2 |