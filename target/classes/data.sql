-- 插入10个外呼名单数据
-- 访问 H2 控制台：http://localhost:8080/h2-console
 --  使用 JDBC URL: jdbc:h2:mem:testdb
-- 关联到外呼任务 TEST_TASK_001

INSERT INTO cc_outcall_queue (
  instance_id,
  env_id,
  queue_code,
  caller,
  callee,
  queue_status,
  task_code,
  group_code,
  acid,
  call_count,
  call_start_time,
  call_end_time,
  ext_info,
  creator,
  modifier
) VALUES 
-- 名单1
('INSTANCE_001', 'test', 'QUEUE_001', '13800138000', '13912345678', 'WAITING', 'TEST_TASK_001', 'GROUP_001', NULL, 0, NULL, NULL, '{"source": "manual", "priority": 1}', 'admin', 'admin'),

-- 名单2
('INSTANCE_001', 'test', 'QUEUE_002', '13800138000', '13987654321', 'WAITING', 'TEST_TASK_001', 'GROUP_001', NULL, 0, NULL, NULL, '{"source": "manual", "priority": 1}', 'admin', 'admin'),

-- 名单3
('INSTANCE_001', 'test', 'QUEUE_003', '13800138000', '13811111111', 'WAITING', 'TEST_TASK_001', 'GROUP_001', NULL, 0, NULL, NULL, '{"source": "manual", "priority": 1}', 'admin', 'admin'),

-- 名单4
('INSTANCE_001', 'test', 'QUEUE_004', '13800138000', '13822222222', 'WAITING', 'TEST_TASK_001', 'GROUP_001', NULL, 0, NULL, NULL, '{"source": "manual", "priority": 1}', 'admin', 'admin'),

-- 名单5
('INSTANCE_001', 'test', 'QUEUE_005', '13800138000', '13833333333', 'WAITING', 'TEST_TASK_001', 'GROUP_001', NULL, 0, NULL, NULL, '{"source": "manual", "priority": 1}', 'admin', 'admin'),

-- 名单6
('INSTANCE_001', 'test', 'QUEUE_006', '13800138000', '13844444444', 'WAITING', 'TEST_TASK_001', 'GROUP_001', NULL, 0, NULL, NULL, '{"source": "manual", "priority": 1}', 'admin', 'admin'),

-- 名单7
('INSTANCE_001', 'test', 'QUEUE_007', '13800138000', '13855555555', 'WAITING', 'TEST_TASK_001', 'GROUP_001', NULL, 0, NULL, NULL, '{"source": "manual", "priority": 1}', 'admin', 'admin'),

-- 名单8
('INSTANCE_001', 'test', 'QUEUE_008', '13800138000', '13866666666', 'WAITING', 'TEST_TASK_001', 'GROUP_001', NULL, 0, NULL, NULL, '{"source": "manual", "priority": 1}', 'admin', 'admin'),

-- 名单9
('INSTANCE_001', 'test', 'QUEUE_009', '13800138000', '13877777777', 'WAITING', 'TEST_TASK_001', 'GROUP_001', NULL, 0, NULL, NULL, '{"source": "manual", "priority": 1}', 'admin', 'admin'),

-- 名单10
('INSTANCE_001', 'test', 'QUEUE_010', '13800138000', '13888888888', 'WAITING', 'TEST_TASK_001', 'GROUP_001', NULL, 0, NULL, NULL, '{"source": "manual", "priority": 1}', 'admin', 'admin');

-- 创建对应的队列组
INSERT INTO cc_outcall_queue_group (
  instance_id,
  env_id,
  group_code,
  queue_codes,
  task_code,
  group_status,
  priority,
  group_type,
  ext_info,
  creator,
  modifier
) VALUES (
  'INSTANCE_001',
  'test',
  'GROUP_001',
  '["QUEUE_001","QUEUE_002","QUEUE_003","QUEUE_004","QUEUE_005","QUEUE_006","QUEUE_007","QUEUE_008","QUEUE_009","QUEUE_010"]',
  'TEST_TASK_001',
  'waiting',
  1,
  'normal',
  '{"created_by": "admin", "batch_size": 10}',
  'admin',
  'admin'
);

-- 更新任务状态为进行中（可选）
-- UPDATE cc_outbound_call_task SET task_status = 1 WHERE task_code = 'TEST_TASK_001';
