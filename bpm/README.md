# Polaris 流程中心

本目录是可独立部署的流程中心模块。流程定义支持审批、人工作业、业务动作、条件/并行/包容网关、定时/消息事件和子流程调用；发布后由 Flowable 生成真实可执行的 BPMN 版本。

- `bpm-service`：Flowable 流程后端，默认端口 `8090`
- `bpm-web`：Vue 管理前端，默认端口 `8091`

后端已按流程定义、表单、流程实例、任务、指标和引擎事件拆分应用服务；Flowable 生命周期通过 listener 统一写入 `bpm_event_log`，支持事件审计、状态投影和重试去重。详细边界见 [`docs/bpm-service-architecture.md`](../docs/bpm-service-architecture.md)。

## 启动后端

```bash
cd bpm/bpm-service
mvn spring-boot:run
```

后端默认连接本机 MySQL：

```text
数据库：polaris_mes
地址：localhost:3306
用户名：root
密码：root123（仅用于本地开发，请勿用于生产）
```

也可以通过环境变量覆盖：

```bash
DB_URL='jdbc:mysql://localhost:3306/polaris_mes?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true' \
DB_USERNAME=root \
DB_PASSWORD=root123 \
mvn spring-boot:run
```

## 启动前端

```bash
cd bpm/bpm-web
pnpm install
pnpm dev
```

浏览器访问 `http://localhost:8091`。开发环境通过 Vite 代理访问 `http://localhost:8090/api`，生产环境由 Nginx 代理 `/api` 到 BPM 后端，可通过 `VITE_BPM_API_BASE` 修改。

## 云端部署

根目录 `docker-compose.yml` 已包含 `bpm-api` 和 `bpm-web`：

```bash
POLARIS_TOKEN_SECRET='请替换为至少 32 位随机字符串' docker compose up -d --build mysql bpm-api bpm-web
```

流程中心入口为 `http://localhost:8091`，后端健康检查为 `http://localhost:8090/actuator/health`。生产环境请将数据库、密码、域名 TLS、备份和统一身份认证配置放入云平台 Secret，不要使用仓库中的默认值。

## 工单集成

发起 `WORK_ORDER` 流程时，后端会读取共享数据库中的 `work_order` 表：

- 发起审批：工单状态更新为 `PENDING_APPROVAL`
- 最终通过：更新为 `PLANNED`
- 最终驳回：更新为 `REJECTED`
- 取消流程：恢复为 `PLANNED`

启动前请确保 `work_order` 表已存在，并且工单主键可以转换为请求中的 `businessId`。

## 验证

```bash
cd bpm/bpm-service
mvn test

cd ../bpm-web
pnpm build
```

本地测试使用 H2；生产或联调启动时请配置可用的 MySQL 连接。根目录的 `service`、`web` 不是本 BPM 模块的启动目录。

## 企业级运维配置

后端暴露 `health`、`info`、`metrics` 和 `prometheus` actuator 端点。生产环境请通过 Secret 覆盖数据库连接、默认操作人和日志/监控配置，并设置 `BPM_SQL_INIT_MODE=never`，将 `schema.sql` 替换为正式版本化迁移流程。
