CREATE TABLE `cc_outcall_queue` (

  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',

  `instance_id` varchar(100) NOT NULL COMMENT '实例id',

  `env_id` varchar(8) NOT NULL COMMENT '环境id',

  `queue_code` varchar(100) NOT NULL COMMENT '队列code',

  `caller` varchar(100) DEFAULT NULL COMMENT '主叫',

  `callee` varchar(100) NOT NULL COMMENT '被叫',

  `queue_status` varchar(20) DEFAULT 'waiting' COMMENT '呼叫状态:waiting,running,success,fail,stop',

  `task_code` varchar(100) NOT NULL COMMENT '关联的任务code',

  `group_code` varchar(100) DEFAULT NULL COMMENT '关联的分组code',

  `acid` varchar(100) DEFAULT NULL COMMENT '通话id',

  `call_count` int(11) DEFAULT '0' COMMENT '呼叫次数',

  `call_start_time` timestamp NULL DEFAULT NULL COMMENT '呼叫开始时间',

  `call_end_time` timestamp NULL DEFAULT NULL COMMENT '呼叫结束时间',

  `ext_info` text DEFAULT NULL COMMENT '扩展信息',

  `creator` varchar(32) DEFAULT NULL COMMENT '创建者',

  `gmt_create` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

  `modifier` varchar(32) DEFAULT NULL COMMENT '更新者',

  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

  PRIMARY KEY (`id`),

  UNIQUE KEY `uk_instance_code` (`instance_id`, `queue_code`, `env_id`) BLOCK_SIZE 16384 GLOBAL,

  KEY `idx_instance_task_code` (`instance_id`, `task_code`, `gmt_modified`) BLOCK_SIZE 16384 LOCAL COMMENT '时间任务索引',

  KEY `idx_inst_tcode_eid_mult` (`task_code`, `instance_id`, `env_id`, `gmt_modified`) STORING (`queue_status`) BLOCK_SIZE 16384 LOCAL,

  KEY `idx_test` (`instance_id`, `task_code`, `env_id`, `queue_status`, `gmt_create`) BLOCK_SIZE 16384 LOCAL,

  KEY `idx_callee` (`instance_id`, `task_code`, `env_id`, `callee`, `gmt_create`) BLOCK_SIZE 16384 LOCAL

) ORGANIZATION INDEX AUTO_INCREMENT = 191975527 AUTO_INCREMENT_MODE = 'ORDER' DEFAULT CHARSET = utf8mb4 ROW_FORMAT = DYNAMIC COMPRESSION = 'zstd_1.3.8' REPLICA_NUM = 2 BLOCK_SIZE = 16384 USE_BLOOM_FILTER = FALSE ENABLE_MACRO_BLOCK_BLOOM_FILTER = FALSE TABLET_SIZE = 134217728 PCTFREE = 0 COMMENT = '呼叫名单表' TABLE_MODE = 'EXTREME'

CREATE TABLE `cc_outcall_queue_group` (

  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',

  `instance_id` varchar(100) NOT NULL COMMENT '实例id',

  `env_id` varchar(8) NOT NULL COMMENT '环境标记：pre/prod',

  `group_code` varchar(100) NOT NULL COMMENT '组code',

  `queue_codes` mediumtext DEFAULT NULL,

  `task_code` varchar(100) NOT NULL COMMENT '任务code',

  `group_status` varchar(20) DEFAULT 'waiting' COMMENT '状态:waiting,proccessing,success,fail,stop',

  `group_start_time` timestamp NULL DEFAULT NULL COMMENT '开始时间',

  `group_end_time` timestamp NULL DEFAULT NULL COMMENT '开始时间',

  `priority` int(11) DEFAULT '0' COMMENT '值越大，优先级越高',

  `group_type` varchar(20) NOT NULL DEFAULT 'normal' COMMENT 'normal常规队列,fixedTime择时队列',

  `ext_info` text DEFAULT NULL COMMENT '扩展信息',

  `creator` varchar(32) NOT NULL COMMENT '创建者',

  `gmt_create` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

  `modifier` varchar(32) DEFAULT NULL COMMENT '更新者',

  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

  PRIMARY KEY (`id`),

  UNIQUE KEY `uk_instance_env_code` (`instance_id`, `env_id`, `group_code`) BLOCK_SIZE 16384 LOCAL COMMENT '唯一索引',

  KEY `idx_instance_gmt_modified` (`instance_id`, `task_code`, `group_status`, `env_id`, `gmt_modified`) BLOCK_SIZE 16384 LOCAL

) ORGANIZATION INDEX AUTO_INCREMENT = 562647 AUTO_INCREMENT_MODE = 'ORDER' DEFAULT CHARSET = utf8mb4 ROW_FORMAT = DYNAMIC COMPRESSION = 'zstd_1.3.8' REPLICA_NUM = 2 BLOCK_SIZE = 16384 USE_BLOOM_FILTER = FALSE ENABLE_MACRO_BLOCK_BLOOM_FILTER = FALSE TABLET_SIZE = 134217728 PCTFREE = 0 COMMENT = '待呼叫名单队列表'

CREATE TABLE `cc_outbound_timing_info` (

  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '唯一id',

  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

  `gmt_create` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

  `phone` varchar(32) NOT NULL COMMENT '手机号',

  `instance_id` varchar(255) DEFAULT NULL COMMENT '实例id',

  `timing` varchar(255) NOT NULL COMMENT '时间段',

  `biz_id` varchar(255) DEFAULT NULL COMMENT '关联的来源业务id,例如拓客就是线索id',

  `source` varchar(255) DEFAULT NULL COMMENT '来源,拓客平台,服催平台等',

  `tag` varchar(255) DEFAULT NULL COMMENT '用户标签等信息。多个标签以逗号分割',

  `ext_info` text DEFAULT NULL COMMENT '扩展参数',

  PRIMARY KEY (`id`),

  UNIQUE KEY `uk_phone` (`phone`) BLOCK_SIZE 16384 LOCAL

) ORGANIZATION INDEX AUTO_INCREMENT = 126 AUTO_INCREMENT_MODE = 'ORDER' DEFAULT CHARSET = utf8mb4 ROW_FORMAT = DYNAMIC COMPRESSION = 'zstd_1.3.8' REPLICA_NUM = 2 BLOCK_SIZE = 16384 USE_BLOOM_FILTER = FALSE ENABLE_MACRO_BLOCK_BLOOM_FILTER = FALSE TABLET_SIZE = 134217728 PCTFREE = 0 COMMENT = '外呼择时信息'

CREATE TABLE `cc_outbound_call_task_rules` (

  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',

  `gmt_create` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

  `instance_id` varchar(255) NOT NULL COMMENT '实例ID',

  `task_rules_code` varchar(255) NOT NULL COMMENT '任务规则编码',

  `task_rules_name` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '规则名称',

  `schedule_start_time` varchar(255) DEFAULT NULL COMMENT '定时任务执行时间区间\n',

  `schedule_end_time` varchar(255) DEFAULT NULL COMMENT '定时任务执行区间\n',

  `task_rules_detail` text DEFAULT NULL COMMENT '任务规则\n',

  `enable_flag` int(8) NOT NULL DEFAULT '0' COMMENT '是否启用：0-启用，1-关闭\n',

  `remarks` varchar(255) DEFAULT NULL,

  `take_effect_time` timestamp NULL DEFAULT NULL COMMENT '生效时间',

  `invalid_time` timestamp NULL DEFAULT NULL COMMENT '失效时间',

  `env_flag` varchar(255) DEFAULT NULL COMMENT '环境标志：pre prod sit',

  PRIMARY KEY (`id`),

  KEY `idx_instance` (`instance_id`, `task_rules_code`) BLOCK_SIZE 16384 LOCAL,

  KEY `idx_take_inval_time` (`instance_id`, `take_effect_time`, `invalid_time`) BLOCK_SIZE 16384 LOCAL,

  KEY `idx_ins` (`instance_id`) BLOCK_SIZE 16384 LOCAL,

  KEY `idx_env` (`env_flag`) BLOCK_SIZE 16384 LOCAL,

  KEY `idx_gmt_mod` (`gmt_modified`) BLOCK_SIZE 16384 LOCAL

) ORGANIZATION INDEX AUTO_INCREMENT = 231059 AUTO_INCREMENT_MODE = 'ORDER' DEFAULT CHARSET = utf8mb4 ROW_FORMAT = DYNAMIC COMPRESSION = 'zstd_1.3.8' REPLICA_NUM = 2 BLOCK_SIZE = 16384 USE_BLOOM_FILTER = FALSE ENABLE_MACRO_BLOCK_BLOOM_FILTER = FALSE TABLET_SIZE = 134217728 PCTFREE = 0 COMMENT = '智能外呼任务规则表'



CREATE TABLE `cc_outbound_call_task` (

  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',

  `gmt_create` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

  `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

  `task_code` varchar(255) NOT NULL COMMENT '任务编码',

  `task_name` varchar(255) DEFAULT NULL COMMENT '任务名称',

  `instance_id` varchar(255) NOT NULL COMMENT '实例ID',

  `task_rules_code` varchar(255) NOT NULL COMMENT '任务规则code',

  `task_type` varchar(255) NOT NULL COMMENT 'AUTO_CALL\n 预测外呼；OUTBOUND_CALL-预览外呼 IVR_CALL-ivr外呼\n',

  `transfer_code` varchar(255) NOT NULL COMMENT '实际执行的对象（坐席/技能组/IVRCode）\n',

  `task_status` int(8) NOT NULL DEFAULT '0' COMMENT '任务状态 0-启用，1-暂停',

  `outbound_caller` varchar(255) NOT NULL COMMENT '主叫号码\n',

  `task_transfer_type` varchar(255) DEFAULT NULL COMMENT '转接类型',

  `env_flag` varchar(255) DEFAULT NULL COMMENT '环境标志：pre prod sit',

  `ext_info` text DEFAULT NULL COMMENT '扩展参数',

  `acquire_status` varchar(128) DEFAULT NULL COMMENT '收单状态：NOT_NEED（无需收单）、PENDING（待收单）、COMPLETED（已收单完成），为空表述NOT_NEED',

  `version` bigint(20) NOT NULL DEFAULT '0' COMMENT '版本号',

  PRIMARY KEY (`id`),

  KEY `idx_instance_taskType` (`instance_id`, `task_type`) BLOCK_SIZE 16384 LOCAL,

  KEY `idx_instance_rule` (`instance_id`, `task_rules_code`) BLOCK_SIZE 16384 LOCAL,

  KEY `idx_instance_taskCode` (`instance_id`, `task_code`) BLOCK_SIZE 16384 LOCAL,

  KEY `idx_instance` (`instance_id`) BLOCK_SIZE 16384 LOCAL,

  KEY `idx_env` (`env_flag`) BLOCK_SIZE 16384 LOCAL,

  KEY `idx_gmt_mod` (`gmt_modified`) BLOCK_SIZE 16384 LOCAL

) ORGANIZATION INDEX AUTO_INCREMENT = 2310109 AUTO_INCREMENT_MODE = 'ORDER' DEFAULT CHARSET = utf8mb4 ROW_FORMAT = DYNAMIC COMPRESSION = 'zstd_1.3.8' REPLICA_NUM = 2 BLOCK_SIZE = 16384 USE_BLOOM_FILTER = FALSE ENABLE_MACRO_BLOCK_BLOOM_FILTER = FALSE TABLET_SIZE = 134217728 PCTFREE = 0 COMMENT = '智能外呼任务表'
