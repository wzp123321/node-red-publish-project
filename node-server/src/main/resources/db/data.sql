-- ============================================================
-- 演示数据（首次启动时插入，已存在则不重复插入）
-- H2 MySQL 模式兼容写法
-- ============================================================

INSERT INTO t_token (token, remark, enabled)
SELECT 'demo-token-001', '演示用 - 张家港现场', 1
WHERE NOT EXISTS (SELECT 1 FROM t_token WHERE token = 'demo-token-001');

INSERT INTO t_token (token, remark, enabled)
SELECT 'demo-token-002', '演示用 - 溧阳现场', 1
WHERE NOT EXISTS (SELECT 1 FROM t_token WHERE token = 'demo-token-002');

INSERT INTO t_token (token, remark, enabled)
SELECT 'demo-token-003', '演示用 - 已吊销示例', 0
WHERE NOT EXISTS (SELECT 1 FROM t_token WHERE token = 'demo-token-003');