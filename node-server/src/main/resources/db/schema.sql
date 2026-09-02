-- ============================================================
-- Node-RED 中心管理平台 - 数据库初始化脚本
-- 兼容 H2 (MySQL 模式) / MySQL 8.x
-- ============================================================

-- 实例表
CREATE TABLE IF NOT EXISTS t_instance (
    id                    BIGINT          AUTO_INCREMENT PRIMARY KEY,
    instance_id           VARCHAR(64)     NOT NULL,
    name                  VARCHAR(128)    NOT NULL DEFAULT '',
    ip                    VARCHAR(64)     NOT NULL DEFAULT '',
    port                  INT             NOT NULL DEFAULT 1880,
    platform              VARCHAR(32)     NOT NULL DEFAULT '',
    arch                  VARCHAR(32)     NOT NULL DEFAULT '',
    node_version          VARCHAR(32)     NOT NULL DEFAULT '',
    node_red_version      VARCHAR(32)     NOT NULL DEFAULT '',
    start_time            TIMESTAMP       NULL,
    register_time         TIMESTAMP       NOT NULL,
    last_heartbeat_time   TIMESTAMP       NULL,
    -- online=在线, offline=离线, deregistered=已注销
    status                VARCHAR(16)     NOT NULL DEFAULT 'online',
    -- bound=已绑定, unbound=未绑定
    bind_status           VARCHAR(16)     NOT NULL DEFAULT 'unbound',
    bind_time             TIMESTAMP       NULL,
    remark                VARCHAR(255)    NOT NULL DEFAULT '',
    create_time           TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time           TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 索引（H2 MySQL 模式不支持 CREATE INDEX IF NOT EXISTS，靠 continue-on-error=true 兜底）
CREATE UNIQUE INDEX uk_instance_id       ON t_instance(instance_id);
CREATE INDEX        idx_status           ON t_instance(status);
CREATE INDEX        idx_bind_status      ON t_instance(bind_status);
CREATE INDEX        idx_last_heartbeat   ON t_instance(last_heartbeat_time);

-- 预授权凭证表
CREATE TABLE IF NOT EXISTS t_token (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    token           VARCHAR(64)     NOT NULL,
    remark          VARCHAR(128)    NOT NULL DEFAULT '',
    -- 1=启用, 0=吊销（吊销后 agent 调任何接口都返回 401）
    enabled         INT             NOT NULL DEFAULT 1,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_token          ON t_token(token);
CREATE INDEX        idx_token_enabled ON t_token(enabled);

-- 操作日志表（注册/心跳/注销/绑定记录）
CREATE TABLE IF NOT EXISTS t_instance_log (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    instance_id     VARCHAR(64)     NOT NULL,
    -- register=注册, heartbeat=心跳, deregister=注销, bind=绑定, auto_offline=超时离线, auto_deregister=自动注销
    action          VARCHAR(32)     NOT NULL,
    detail          VARCHAR(512)    NOT NULL DEFAULT '',
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_log_instance    ON t_instance_log(instance_id);
CREATE INDEX idx_log_action      ON t_instance_log(action);
CREATE INDEX idx_log_create_time ON t_instance_log(create_time);