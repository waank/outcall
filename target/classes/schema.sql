-- H2 数据库初始化脚本
-- 为 H2 内存数据库创建表结构

-- 1. 外呼任务表
CREATE TABLE cc_outbound_call_task (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  gmt_create TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  gmt_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  task_code VARCHAR(100) NOT NULL,
  task_name VARCHAR(200) NOT NULL,
  instance_id VARCHAR(100) NOT NULL,
  task_rules_code VARCHAR(100),
  task_type VARCHAR(50),
  transfer_code VARCHAR(100),
  task_status INT DEFAULT 0,
  outbound_caller VARCHAR(100),
  task_transfer_type VARCHAR(50),
  env_flag VARCHAR(20),
  ext_info TEXT,
  acquire_status VARCHAR(20),
  version INT DEFAULT 0
);

-- 2. 外呼任务规则表
CREATE TABLE cc_outbound_call_task_rules (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  gmt_create TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  gmt_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  instance_id VARCHAR(100) NOT NULL,
  task_rules_code VARCHAR(100) NOT NULL,
  task_rules_name VARCHAR(200),
  schedule_start_time VARCHAR(20),
  schedule_end_time VARCHAR(20),
  task_rules_detail TEXT,
  enable_flag INT DEFAULT 1,
  remarks VARCHAR(500),
  take_effect_time TIMESTAMP,
  invalid_time TIMESTAMP,
  env_flag VARCHAR(20)
);

-- 3. 外呼择时信息表
CREATE TABLE cc_outbound_timing_info (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  gmt_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  gmt_create TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  phone VARCHAR(20) NOT NULL,
  instance_id VARCHAR(100),
  timing VARCHAR(50),
  biz_id VARCHAR(100),
  source VARCHAR(50),
  tag VARCHAR(200),
  ext_info TEXT
);

-- 4. 外呼队列表
CREATE TABLE cc_outcall_queue (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  instance_id VARCHAR(100) NOT NULL,
  env_id VARCHAR(8) NOT NULL,
  queue_code VARCHAR(100) NOT NULL,
  caller VARCHAR(100),
  callee VARCHAR(100) NOT NULL,
  queue_status VARCHAR(20) DEFAULT 'waiting',
  task_code VARCHAR(100) NOT NULL,
  group_code VARCHAR(100),
  acid VARCHAR(100),
  call_count INT DEFAULT 0,
  call_start_time TIMESTAMP,
  call_end_time TIMESTAMP,
  ext_info TEXT,
  creator VARCHAR(32),
  modifier VARCHAR(32),
  gmt_create TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  gmt_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 5. 外呼队列组表
CREATE TABLE cc_outcall_queue_group (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  instance_id VARCHAR(100) NOT NULL,
  env_id VARCHAR(8) NOT NULL,
  group_code VARCHAR(100) NOT NULL,
  queue_codes TEXT,
  task_code VARCHAR(100) NOT NULL,
  group_status VARCHAR(20) DEFAULT 'waiting',
  group_start_time TIMESTAMP,
  group_end_time TIMESTAMP,
  priority INT DEFAULT 0,
  group_type VARCHAR(50),
  ext_info TEXT,
  creator VARCHAR(32),
  modifier VARCHAR(32),
  gmt_create TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  gmt_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_task_status ON cc_outbound_call_task(task_status);
CREATE INDEX idx_task_code ON cc_outbound_call_task(task_code);
CREATE INDEX idx_queue_status ON cc_outcall_queue(queue_status);
CREATE INDEX idx_queue_task_code ON cc_outcall_queue(task_code);
CREATE INDEX idx_group_status ON cc_outcall_queue_group(group_status);
CREATE INDEX idx_group_task_code ON cc_outcall_queue_group(task_code);

-- 插入一些测试数据
INSERT INTO cc_outbound_call_task (task_code, task_name, instance_id, task_type, task_status, outbound_caller, env_flag, task_rules_code) 
VALUES ('TEST_TASK_001', '测试外呼任务', 'INSTANCE_001', 'PREDICTIVE', 0, '13800138000', 'test', 'RULE_001');

INSERT INTO cc_outbound_call_task_rules (instance_id, task_rules_code, task_rules_name, schedule_start_time, schedule_end_time, env_flag, task_rules_detail, take_effect_time, invalid_time)
VALUES ('INSTANCE_001', 'RULE_001', '全天24小时规则', '00:00', '23:59', 'test', '[{"startTime":0,"endTime":2359}]', '2020-01-01 00:00:00', '2030-01-01 00:00:00');