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
| Redis | `192.168.88.130:6379` 可达，但返回 `NOAUTH Authentication required`（配置中缺密码） |
| OneNET MQTT | `896VnUK204.mqtts.acc.cmcconenet.cn:6002` TCP 可达，但应用连接后对端 EOF（待确认） |
| 后端端口 | 8080 |

> 注：本项目的开发沙箱禁用了 AF_UNIX 回环 socket，任何基于 NIO 的 Java 服务都无法在其中启动。
> 这已被最小复现程序（仅调用 `Selector.open()`）证实，与项目代码无关，运行期验证需在 IDE 或本机终端进行。

## 3. 已确认的决策

1. **技术栈**：升级到 Spring Boot 3.4.5 + Java 17（用户原选 3.2.x，因 3.2 开源维护期已结束、且本地仓库已缓存 3.4.5 而改用 3.4.5）。
2. **消息队列**：采用 **Redis Stream**（出于技术栈完备性考虑）。需配套消费组、手动 ACK 与 pending 兜底，否则重启会丢消息。
3. **告警通道**：采用 **短信**，不使用微信订阅消息。当前阶段只实现 `MockSmsSender`（输出到日志），正式实现（阿里云/腾讯云）留接口位置。
   - 因此**不需要**用户体系：设备表已有 `guardian_phone` 字段，短信直接发往该号码，无需 openid、`wx.login`、绑定表。
4. **部署场景**：内网演示。故不引入 HTTPS、完整 JWT 鉴权体系；跨域沿用现有宽松配置。
5. **前端端口**：3000。

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

**前置阻塞**：Redis 需要密码，`application.yml` 目前无 `password` 字段（实例返回 `NOAUTH`）。建议以环境变量方式提供，避免明文入库。

- `DataSyncScheduler` 串行拉取改为固定大小线程池并发。
- MQTT 回调仅做解析与投递，`XADD` 到 Redis Stream；消费者（有界线程池）负责入库。
- 消费组 + 手动 ACK + `XPENDING`/`XCLAIM` 兜底，保证重启不丢消息。
- `ensureDeviceExists` 增加本地缓存，去掉每条消息一次 `selectOne`。
- → 验证：200 msg/s 压测无堆积；杀掉消费者再启动不丢消息。

### 迭代 3：去重与 `report_time` 统一（待做）

- 统一 MQTT 与 HTTP 两条通道的 `report_time` 取值口径。
- 迁移步骤：先清理历史重复行，再加唯一索引 `uk_device_report(device_sn, report_time)`，写入改为 `INSERT IGNORE`。
- → 验证：双通道并发写入后行数不再增长。

### 迭代 4：查询收敛（待做）

- `listByDeviceSn` 补 `LIMIT`（与注释一致）；分页 `pageSize` 加上限校验。
- → 验证：单次响应体小于 100 KB。

### 迭代 5：告警闭环（待做）

- 新增 `t_alarm_record`（device_sn、alarm_type、level、status 待处理/已确认/误报、handle_by、handle_time、快照数据）。
- 跌倒、心率越界、血氧越界判定；未确认自动升级；小程序提供处置入口。
- → 验证：灌入模拟数据能生成告警并完成状态流转。

### 迭代 6：短信通道（待做）

- `SmsSender` 接口 + `MockSmsSender`（日志输出）+ 正式实现（阿里云/腾讯云，待账号与签名模板就绪）。
- 发送限流与失败重试；接收方取 `t_crutch_device.guardian_phone`。
- → 验证：跌倒后目标号码收到短信（或 Mock 日志可见）。

### 迭代 7：收尾（待做）

- 设备删除保护：外键 `fk_crutch_dev` 为 `ON DELETE CASCADE`，当前 `deleteById` 会连带清空该设备全部历史数据，需增加确认或改为逻辑删除。
- 离线判定：设备表无 `last_seen` 字段，用 `MAX(report_time)` 派生，避免改表。
- 接入 Actuator 指标，为后续压测提供基线。

## 6. 数据库现状与约束（依据现有建表 SQL）

- `t_crutch_sensor_data` 已有索引：`idx_dev_report (device_sn, report_time)`、`idx_fall_status (fall_status)`。
  → 原计划中的「补索引」项作废；`idx_fall_status` 选择性低，可选优化为 `(fall_status, report_time)`，演示阶段不急。
- 外键：`t_crutch_sensor_data.device_sn` → `t_crutch_device.device_sn`，**ON DELETE CASCADE**。
- 无唯一键，去重需先清理历史重复数据再加索引。

## 7. 风险与待办

| 项 | 状态 | 说明 |
| --- | --- | --- |
| Redis 密码 | **待提供** | 迭代 2 的阻塞项 |
| OneNET MQTT EOF | **待确认** | TCP 可达但 broker 未回 CONNACK，需在正常网络下复现判断是网络干扰还是凭据/产品配置问题 |
| 运行期回归 | **待执行** | 沙箱无法启动 NIO 服务，需在 IDE 启动并验证接口与文档页 |
| 短信签名与模板 | 待申请 | 未就绪前仅 MockSmsSender |
| 敏感信息 | 已知风险 | `application.yml` 中 MySQL 密码与 OneNET accessKey 为明文，且已进入 git 历史 |
| fastjson 1.2.83 | 待处理 | 存在已知反序列化风险，计划在迭代 3 之后替换为 Jackson 或 fastjson2 |