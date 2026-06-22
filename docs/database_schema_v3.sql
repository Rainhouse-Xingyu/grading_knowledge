-- ============================================================================
-- 大连东软信息学院项目双轨制AI评分系统 - 数据库初始化脚本 (MySQL 8.0)
-- 适用场景：学生纯静态提交 -> 教师端全面管控AI触发与下发流转架构
-- ============================================================================

CREATE DATABASE IF NOT EXISTS `neusoft_ai_grading` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `neusoft_ai_grading`;

-- 1. 学生表（严格同步学校统一身份认证CAS下发字段）
DROP TABLE IF EXISTS `t_student`;
CREATE TABLE `t_student` (
  `student_no` varchar(32) NOT NULL COMMENT '学号',
  `name` varchar(50) NOT NULL COMMENT '姓名',
  `gender` varchar(10) DEFAULT NULL COMMENT '性别',
  `dept_code` varchar(32) DEFAULT NULL COMMENT '院(系)/部代码',
  `dept_name` varchar(100) DEFAULT NULL COMMENT '院(系)/部名称',
  `major_code` varchar(32) DEFAULT NULL COMMENT '专业代码',
  `major_name` varchar(100) DEFAULT NULL COMMENT '专业名称',
  `class_code` varchar(32) DEFAULT NULL COMMENT '班级代码',
  `class_name` varchar(100) DEFAULT NULL COMMENT '班级名称',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '首次通过CAS登录录入时间',
  PRIMARY KEY (`student_no`),
  KEY `idx_class_code` (`class_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='学生信息表(统一身份认证CAS缓存)';

-- 2. 教师表（严格同步学校统一身份认证CAS下发字段）
DROP TABLE IF EXISTS `t_teacher`;
CREATE TABLE `t_teacher` (
  `teacher_no` varchar(32) NOT NULL COMMENT '教师号',
  `teacher_name` varchar(50) NOT NULL COMMENT '教师姓名',
  `dept_code` varchar(32) DEFAULT NULL COMMENT '院(系)/部代码',
  `dept_name` varchar(100) DEFAULT NULL COMMENT '院(系)/部名称',
  `title_code` varchar(32) DEFAULT NULL COMMENT '职称代码',
  `title_name` varchar(100) DEFAULT NULL COMMENT '职称名称',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '首次通过CAS登录录入时间',
  PRIMARY KEY (`teacher_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='教师信息表(统一身份认证CAS缓存)';

-- 3. 课程项目主表
DROP TABLE IF EXISTS `t_course_project`;
CREATE TABLE `t_course_project` (
  `course_id` varchar(32) NOT NULL COMMENT '课程项目唯一UUID/代码',
  `course_name` varchar(150) NOT NULL COMMENT '课程项目名称',
  `teacher_no` varchar(32) NOT NULL COMMENT '负责教师工号',
  `semester` varchar(50) DEFAULT NULL COMMENT '开课学期(例如：2025-2026-1)',
  `standard_doc_url` varchar(500) DEFAULT NULL COMMENT '教师上传的评分标准文档在MinIO中的物理路径映射',
  `weight_stage1` decimal(4,2) DEFAULT '0.20' COMMENT '阶段1分数权重(默认0.20)',
  `weight_stage2` decimal(4,2) DEFAULT '0.30' COMMENT '阶段2分数权重(默认0.30)',
  `weight_stage3` decimal(4,2) DEFAULT '0.50' COMMENT '阶段3分数权重(默认0.50)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '课程创建时间',
  PRIMARY KEY (`course_id`),
  KEY `idx_teacher_no` (`teacher_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课程项目主表';

-- 4. 学生选课与期末总结算表
DROP TABLE IF EXISTS `t_student_course`;
CREATE TABLE `t_student_course` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `student_no` varchar(32) NOT NULL COMMENT '学号',
  `course_id` varchar(32) NOT NULL COMMENT '课程项目ID',
  `final_score` decimal(5,2) DEFAULT NULL COMMENT '期末总分(可由三次阶段分数按权重自动算，亦可由教师直接进行覆盖调整)',
  `teacher_final_comment` text COMMENT '教师给出的期末总评语',
  `grade_status` tinyint DEFAULT '0' COMMENT '期末总分状态: 0-未生成, 1-已下发学生可见',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stu_course` (`student_no`,`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='学生选课与期末总分表';

-- 5. 三阶段分段式提交与AI/教师双轨评审表（核心流转业务表）
DROP TABLE IF EXISTS `t_submission_stage`;
CREATE TABLE `t_submission_stage` (
  `submission_id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `student_no` varchar(32) NOT NULL COMMENT '学号',
  `course_id` varchar(32) NOT NULL COMMENT '课程项目ID',
  `stage_num` tinyint NOT NULL COMMENT '分段检测阶段编号: 1-第一次提交, 2-第二次提交, 3-第三次最终提交',
  
  -- 文件物理混淆与安全下载映射
  `code_package_path` varchar(500) NOT NULL COMMENT '学生上传的代码压缩包在MinIO中的映射混淆地址',
  `report_path` varchar(500) NOT NULL COMMENT '学生图文报告在MinIO中的映射混淆地址',
  
  -- 评审运行期策略（均由教师端触发时传入）
  `is_joint_review` tinyint DEFAULT '0' COMMENT '是否与上一次标准进行联合评审: 0-不联合, 1-联合评审(引入上一轮教师最终修改评语作为大模型上下文)',
  `model_used` varchar(50) DEFAULT 'DeepSeek-R1' COMMENT '本次评测实际执行的大模型名称(用于监控云端API与本地Ollama降级切换情况)',
  
  -- AI评审层原始数据 (评分严格，不可直接修改此处，留作对比)
  `ai_score` decimal(5,2) DEFAULT NULL COMMENT '大模型评定的严格初始分数',
  `ai_report_markdown` longtext COMMENT '大模型根据评分标准原生输出的Markdown修改建议报告',
  
  -- 教师控制面数据 (教师拥有最高管理权：在线二次编辑Markdown报告、微调覆盖分数)
  `teacher_score` decimal(5,2) DEFAULT NULL COMMENT '教师审查/调分后的最终阶段分数',
  `final_report_markdown` longtext COMMENT '教师在线二次审查、修改微调后的最终版Markdown评审报告',
  
  -- 核心业务状态机流转控制
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态流转: 0-已提交待评测(学生刚传完文件), 1-AI评测中(教师已触发异步任务), 2-AI评测完成/待发布(教师可见/学生不可见/教师可改分改报告), 3-已下发(学生端解锁可见/允许在线预览与导出PDF)',
  
  `submit_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '学生静态文件提交时间',
  `eval_trigger_time` datetime DEFAULT NULL COMMENT '教师端触发AI评测的启动时间',
  `review_time` datetime DEFAULT NULL COMMENT '教师最终微调批阅完成并执行一键下发的时间',
  PRIMARY KEY (`submission_id`),
  UNIQUE KEY `uk_stu_course_stage` (`student_no`,`course_id`,`stage_num`),
  KEY `idx_course_stage` (`course_id`,`stage_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='三阶段分段提交与AI/教师双轨评审主表';

-- ============================================================================
-- 6. 本地登录用户表（与CAS登录并行的独立认证方式）
-- ============================================================================

DROP TABLE IF EXISTS `t_local_user`;
CREATE TABLE `t_local_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `username` varchar(32) NOT NULL COMMENT '登录用户名（全局唯一）',
  `password_hash` varchar(100) NOT NULL COMMENT 'BCrypt哈希后的密码（60字符，含盐）',
  `role` varchar(16) NOT NULL COMMENT '角色: student/teacher/admin',
  `user_no` varchar(32) DEFAULT NULL COMMENT '关联用户编号（student→学号，teacher→工号，admin→NULL）',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '账户状态: 0-正常, 1-锁定(管理员手动锁定)',
  `login_fail_count` int NOT NULL DEFAULT '0' COMMENT '连续登录失败次数（成功登录后重置为0）',
  `locked_until` datetime DEFAULT NULL COMMENT '账户锁定截止时间（连续失败5次自动锁定30分钟）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '首次注册时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_role` (`role`),
  KEY `idx_user_no` (`user_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='本地登录用户表（与CAS并行的独立认证方式）';

-- 默认管理员账户（密码: Admin@123，BCrypt加密）
INSERT INTO `t_local_user` (`username`, `password_hash`, `role`, `user_no`, `status`)
VALUES ('admin', '$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'admin', NULL, 0);
