-- MySQL dump 10.13  Distrib 8.0.45, for Linux (x86_64)
--
-- Host: localhost    Database: ida_db
-- ------------------------------------------------------
-- Server version	8.0.45

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Dumping data for table `ida_interview_question`
--

LOCK TABLES `ida_interview_question` WRITE;
/*!40000 ALTER TABLE `ida_interview_question` DISABLE KEYS */;
INSERT INTO `ida_interview_question` VALUES (1,2,1,'关系型数据库和非关系型数据库有什么区别？','关系型数据库主要包括Mysql，表和表之间是有关联的，非关系型数据库主要包括redis，表和表之间一般不关联，且数据可以设有过期时间，可以自动丢弃，查询起来更加快捷，但安全性较低，而关系型数据库增删改查相对慢一点，但数据比较安全，且通过表与表的关联，可以大幅降低一个表的耦合度，实现松散耦合高内聚，性能是比较好的，但同时也会更加占据存储空间，因为表会比较多。而非关系型数据库更像是一个快捷地笔记本，快速地记录简易的内容，且无需长期保留的有实时性的内容，比较轻量级。','回答提到了关系型数据库通过表关联实现数据一致性，非关系型数据库的灵活性（如过期时间、快速查询），但存在多处不准确：\n1）关系型数据库并非“安全性高”，而是通过ACID保证事务一致性；\n2）非关系型数据库并非“安全性低”，而是牺牲一致性换取性能或可用性；\n3）“降低表耦合度”表述矛盾，关系型数据库通过外键增加耦合；\n4）遗漏了重要的CAP理论、一致性模型、扩展性差异等核心区别。整体不够系统，需要补充原理性对比。','关系型数据库（如MySQL）基于关系模型，使用SQL查询，强调ACID（原子性、一致性、隔离性、持久性），通过表结构（主键、外键、索引）维护数据完整性，适合结构化数据、事务型应用，扩展性差（垂直扩展为主）。\n非关系型数据库（如Redis）无固定模式，通常支持水平扩展，类型多样：键值型（Redis）、文档型（MongoDB）、列族型（HBase）、图型（Neo4j）。一般放弃ACID，采用最终一致性或BASE理论，以换取高性能、高可用和弹性扩展。\n主要区别：\n1）存储结构：关系型用表，非关系型用键值、文档等；\n2）扩展方式：关系型垂直扩展，非关系型水平扩展；\n3）事务支持：关系型支持强一致，非关系型通常弱一致；\n4）数据模型：关系型需要预定义模式，非关系型灵活；\n5）适用场景：关系型适合财务系统、ERP，非关系型适合缓存、日志、社交网络等。',NULL,NULL,'2026-06-25 07:11:56.903','2026-06-25 07:11:56.903');
/*!40000 ALTER TABLE `ida_interview_question` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `ida_interview_session`
--

LOCK TABLES `ida_interview_session` WRITE;
/*!40000 ALTER TABLE `ida_interview_session` DISABLE KEYS */;
INSERT INTO `ida_interview_session` VALUES
(1,2066367665870573569,'未命名复盘',NULL,NULL,NULL,NULL,0,'2026-06-15 21:43:04.000','2026-06-15 13:42:21.813','2026-06-15 21:43:04.036'),(2,2066367665870573569,'南京大翼航空面试复盘','南京大翼航空','软件开发实习生','一面','2026-04-13',1,NULL,'2026-06-25 07:00:50.863','2026-06-25 07:11:56.939'),(3,2066367665870573569,'未命名复盘',NULL,NULL,NULL,NULL,0,'2026-06-25 17:13:01.000','2026-06-25 08:33:42.301','2026-06-25 17:13:01.142'),(4,2066367665870573569,'未命名复盘',NULL,NULL,NULL,NULL,0,'2026-06-25 17:13:06.000','2026-06-25 09:12:45.918','2026-06-25 17:13:06.674'),(5,2066367665870573569,'未命名复盘',NULL,NULL,NULL,NULL,0,'2026-06-30 17:48:17.000','2026-06-25 09:13:07.688','2026-06-30 17:48:17.864'),(6,2066367665870573569,'未命名复盘',NULL,NULL,NULL,NULL,0,'2026-06-30 17:48:16.000','2026-06-25 15:42:40.524','2026-06-30 17:48:16.157'),(7,2066367665870573569,'未命名复盘',NULL,NULL,NULL,NULL,0,'2026-06-30 18:10:16.000','2026-06-30 09:48:19.575','2026-06-30 18:10:16.318'),(8,2066367665870573569,'未命名复盘',NULL,NULL,NULL,NULL,0,'2026-06-30 18:10:15.000','2026-06-30 09:50:31.000','2026-06-30 18:10:15.186'),(9,2066367665870573569,'未命名复盘',NULL,NULL,NULL,NULL,0,'2026-06-30 18:10:13.000','2026-06-30 10:09:59.576','2026-06-30 18:10:13.567'),(10,2066367665870573569,'未命名复盘',NULL,NULL,NULL,NULL,0,'2026-06-30 22:03:24.000','2026-06-30 10:10:39.086','2026-06-30 22:03:24.492'),(11,2066367665870573569,'未命名复盘',NULL,NULL,NULL,NULL,0,'2026-06-30 22:05:45.000','2026-06-30 14:03:25.773','2026-06-30 22:05:45.132'),(12,2066367665870573569,'未命名复盘',NULL,NULL,NULL,NULL,0,'2026-06-30 22:05:43.000','2026-06-30 14:03:43.371','2026-06-30 22:05:43.123'),(13,2066367665870573569,'未命名复盘',NULL,NULL,NULL,NULL,0,'2026-06-30 22:06:03.000','2026-06-30 14:05:55.540','2026-06-30 22:06:03.216'),(14,2066367665870573569,'未命名复盘',NULL,NULL,NULL,NULL,0,'2026-06-30 22:09:34.000','2026-06-30 14:06:11.051','2026-06-30 22:09:34.536'),(15,2066367665870573569,'未命名复盘',NULL,NULL,NULL,NULL,0,'2026-06-30 22:09:32.000','2026-06-30 14:06:14.224','2026-06-30 22:09:32.651'),(16,2066367665870573569,'未命名复盘',NULL,NULL,NULL,NULL,0,'2026-06-30 22:09:28.000','2026-06-30 14:07:33.929','2026-06-30 22:09:28.467'),(17,2066367665870573569,'未命名复盘',NULL,NULL,NULL,NULL,0,'2026-07-03 01:16:45.000','2026-07-01 07:50:53.917','2026-07-03 01:16:45.911'),(18,2066367665870573569,'未命名复盘',NULL,NULL,NULL,NULL,0,NULL,'2026-07-03 01:42:35.193','2026-07-03 01:42:35.193'),(19,2066367665870573569,'未命名复盘',NULL,NULL,NULL,NULL,0,NULL,'2026-07-05 04:10:53.696','2026-07-05 04:10:53.696');
/*!40000 ALTER TABLE `ida_interview_session` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `ida_resume`
--

LOCK TABLES `ida_resume` WRITE;
/*!40000 ALTER TABLE `ida_resume` DISABLE KEYS */;
INSERT INTO `ida_resume` VALUES (1,2066367665870573569,'简历new2',0,'黄晋豪',0,NULL,NULL,NULL,'13534832455','2467201477@qq.com','2467201477',NULL,NULL,NULL,NULL,NULL,'共青团员',NULL,NULL,NULL,NULL,NULL,NULL,1,NULL,NULL,NULL,NULL,NULL,1,'0','Java 开发工程师',NULL,NULL,NULL,NULL,NULL,'1. 拥有真实企业外包项目后端主开发经验，熟悉企业级开发流程、协作模式与质量要求，能独立完成接口开发、数据库设计、项目部署及线上问题排查。\n2. 学习能力强，关注云原生、分布式系统等相关技术，愿意主动探索新技术。\n3. 责任心强、做事踏实，可保证稳定实习时间，能快速适应团队开发节奏。',NULL,'[\"https://www.yuque.com/h_cake\"]','{\"lastUpload\": {\"ossBucket\": \"xhsocial-maverick\", \"ossObjectKey\": \"app/resume/2066367665870573569/6daa199418f049078bb619c3f945b96c.pdf\", \"originalFilename\": \"简历new2.pdf\"}}',2,2,NULL,'2026-06-15 14:45:57.307','2026-06-15 14:46:15.852'),(2,2074475915917197313,'我的简历',0,'高江萍',2,'2005-05-04','ID_CARD','500241200505045929','18806832463','gjp3282409246@qq.com','3282409246','18806832463',NULL,'中国','重庆秀山','汉族','CYL','RURAL','','重庆','秀山','江苏',NULL,1,NULL,NULL,'18806832463',NULL,4,1,NULL,'英语教师，机构老师，文职','江苏，浙江，上海，广州','教育业','7-8K','随时',1,'本人为英语专业本科生，能够适应各种工作要求。','善于沟通和解决问题，学习能力强，团队合作能力强',NULL,NULL,2,1,NULL,'2026-07-08 07:57:51.319','2026-07-08 07:57:51.319');
/*!40000 ALTER TABLE `ida_resume` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `ida_resume_award`
--

LOCK TABLES `ida_resume_award` WRITE;
/*!40000 ALTER TABLE `ida_resume_award` DISABLE KEYS */;
INSERT INTO `ida_resume_award` VALUES (3,1,'第16届蓝桥杯软件赛个人赛 省一等奖','省级一等奖',NULL,NULL,NULL,0,'2026-06-15 14:46:15.852','2026-06-15 14:46:15.852'),(4,1,'“精创杯”创新创业模拟赛 国家二等奖','国家级二等奖',NULL,NULL,NULL,1,'2026-06-15 14:46:15.852','2026-06-15 14:46:15.852');
/*!40000 ALTER TABLE `ida_resume_award` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `ida_resume_campus`
--

LOCK TABLES `ida_resume_campus` WRITE;
/*!40000 ALTER TABLE `ida_resume_campus` DISABLE KEYS */;
INSERT INTO `ida_resume_campus` VALUES (2,1,'江苏科技大学2322418076团支部','团支书',NULL,NULL,'负责班级组织建设与活动统筹，锻炼了沟通协调与团队协作能力',0,'2026-06-15 14:46:15.852','2026-06-15 14:46:15.852');
/*!40000 ALTER TABLE `ida_resume_campus` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `ida_resume_certificate`
--

LOCK TABLES `ida_resume_certificate` WRITE;
/*!40000 ALTER TABLE `ida_resume_certificate` DISABLE KEYS */;
INSERT INTO `ida_resume_certificate` VALUES (2,1,'软件设计师','全国计算机软件资格考试办公室',NULL,NULL,0,'2026-06-15 14:46:15.852','2026-06-15 14:46:15.852');
/*!40000 ALTER TABLE `ida_resume_certificate` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `ida_resume_education`
--

LOCK TABLES `ida_resume_education` WRITE;
/*!40000 ALTER TABLE `ida_resume_education` DISABLE KEYS */;
INSERT INTO `ida_resume_education` VALUES (2,1,'江苏科技大学',NULL,'计算机科学与技术','本科','学士',1,'2023-09-01','2027-06-01',NULL,'3.87',NULL,'软件工程、计算机网络、数据库原理、操作系统、数据结构与算法设计等','团支书','人民奖学金（大一、大二）；优秀学生干部','负责班级组织建设与活动统筹，锻炼了沟通协调与团队协作能力',0,'2026-06-15 14:46:15.852','2026-06-15 14:46:15.852'),(3,2,'江苏科技大学','外国语言学院','英语','BACHELOR','学士',1,'2023-09-07','2027-06-30','中国江苏',NULL,NULL,'基础英语，高级语言，英语听力，英语语言学，英语听力学，英语写作，英汉互译，语言文学阅读，英语国家概况，学术论文写作','组织委员','国家励志奖学金',NULL,0,'2026-07-08 07:57:51.319','2026-07-08 07:57:51.319');
/*!40000 ALTER TABLE `ida_resume_education` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `ida_resume_language`
--

LOCK TABLES `ida_resume_language` WRITE;
/*!40000 ALTER TABLE `ida_resume_language` DISABLE KEYS */;
INSERT INTO `ida_resume_language` VALUES (3,1,'英语',NULL,NULL,NULL,NULL,'大学英语四级证书',NULL,0,'2026-06-15 14:46:15.852','2026-06-15 14:46:15.852'),(4,1,'英语',NULL,NULL,NULL,NULL,'大学英语六级证书',NULL,1,'2026-06-15 14:46:15.852','2026-06-15 14:46:15.852'),(5,2,'英语',NULL,NULL,NULL,NULL,'CET-6',NULL,0,'2026-07-08 07:57:51.319','2026-07-08 07:57:51.319'),(6,2,'法语',NULL,NULL,NULL,NULL,'法语四级',NULL,1,'2026-07-08 07:57:51.319','2026-07-08 07:57:51.319');
/*!40000 ALTER TABLE `ida_resume_language` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `ida_resume_project`
--

LOCK TABLES `ida_resume_project` WRITE;
/*!40000 ALTER TABLE `ida_resume_project` DISABLE KEYS */;
INSERT INTO `ida_resume_project` VALUES (3,1,'智能投递助手—AI 驱动简历解析与面试复盘平台','全栈开发 & 架构设计','2026-04-01',NULL,NULL,'SpringBoot、MySQL、MyBatis-Plus、Redis、LangChain4j、阿里云 OSS/短信/语音识别、PDFBox、POI、Knife4j、Docker','XHSocial-Maverick-server','1. 独立完成双进程SpringBoot微服务架构设计，拆分App端（简历/面试）与Plugin端（浏览器插件API），实现模块解耦与独立部署；\n2. 主导简历一键填表核心链路：基于LangChain4j AI Services声明式定义填表助手接口；设计异步任务提交+轮询进度机制，解决大模型长响应导致的网关超时问题；采用确定性匹配+LLM推理两阶段策略，通过中文标签别名表快速覆盖约80%标准字段，剩余复杂字段再交由大模型处理，有效缩减Prompt体积与调用延迟。\n3. 实现简历上传解析闭环：PDF/Word文件上传至阿里云OSS，结合PDFBox/POI提取文本后交由大模型结构化解析，输出教育、工作、项目、技能等多维度简历档案；\n4. 设计面试复盘模块：集成阿里云实时语音识别（NLS）+ Whisper双通道听写，录制面试语音后自动转写为文本，再由DeepSeek进行Q&A结构化总结；\n5. MySQL表结构设计（简历多表、面试场次/问题、用户体系），Redis缓存登录会话与任务状态；使用MyBatis-Plus逻辑删除、Lambda查询等特性保障数据安全与开发效率；\n6. 基于Docker容器化部署，Nginx反向代理双进程服务，配合Knife4j生成API文档，支撑前后端协作联调。','1. 简历智能解析准确率90%+，支持教育/工作/项目/技能等10+维度字段自动提取，替代人工逐项填写的低效流程；\n2. 一键填表功能通过异步化改造，将大模型调用（30s+）从同步阻塞转为异步轮询，前端体验从白屏等待升级为实时进度反馈；\n3. 面试复盘模块打通“语音录制→实时转写→AI总结”全链路，单次面试复盘时间从手动整理1小时缩短至分钟级；\n4. 积累了大模型工程化落地的完整经验：LangChain4j框架集成、Prompt工程、Provider抽象切换、异步编排、结果缓存、确定性规则兜底等生产实践。','面向求职场景的AI效率工具，双进程微服务架构（App服务+浏览器插件服务），基于LangChain4j统一编排多场景LLM调用，提供简历智能解析入库、浏览器插件一键填表、面试语音复盘与AI总结三大能力，大幅提升求职投递效率。',0,'2026-06-15 14:46:15.852','2026-06-15 14:46:15.852'),(4,1,'商业外包项目｜企业定制微信小程序电商平台','后端主开发 & 核心业务负责人','2026-02-01','2026-03-01',NULL,'SpringBoot、MySQL、MyBatis-Plus、Redis、Vue、腾讯云、Git、微信小程序','XiaoH (little-fox-helper) - Gitee.com','1. 独立完成用户登录授权、商品、订单、支付、积分、提现等基础模块开发，遵循RESTful规范，保障接口的可读性、可维护性；\n2. 主导实现团队级差收益、树状会员关系、排队补位机制、贡献值结算等复杂分润逻辑，重点处理并发场景下的结算精准性问题，规避数据不一致风险；\n3. MySQL表结构设计、索引优化；使用Redis缓存登录状态、支付状态、防重复结算；\n4. 提供权限控制、系统配置、数据统计报表接口与后台管理vue界面；\n5. 负责腾讯云轻量级服务器、云数据库环境搭建、docker镜像部署项目；\n6. 独立完成前后端联调，借助Cursor AI工具完成前端调试、Bug修复与接口对接，推动项目快速迭代交付。','1. 基础业务流程与核心收益逻辑全部实现，小程序顺利上线并稳定运行，获得企业客户认可，后续将持续配合优化迭代。\n2. 实现企业级复杂分润系统，支持多级团队管理、级差奖励发放、排队补位返利等。\n3. 完成支付、提现、积分、余额互转等资金相关模块开发，通过缓存优化与并发处理，保障数据一致性与系统稳定性。\n4. 掌握企业级项目从需求分析、架构设计、业务开发到部署上线的全流程，积累了真实商业项目的协作与问题解决经验。','为企业定制的创新商业模式电商小程序，基于「免费消费+持续收益」理念设计，包含用户、商品、订单、支付、积分等基础模块，核心实现树状团队关系、级差收益、排队补位返利、贡献值结算、余额提现等企业专属复杂业务，已交付上线。',1,'2026-06-15 14:46:15.852','2026-06-15 14:46:15.852');
/*!40000 ALTER TABLE `ida_resume_project` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `ida_resume_publication`
--

LOCK TABLES `ida_resume_publication` WRITE;
/*!40000 ALTER TABLE `ida_resume_publication` DISABLE KEYS */;
/*!40000 ALTER TABLE `ida_resume_publication` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `ida_resume_skill`
--

LOCK TABLES `ida_resume_skill` WRITE;
/*!40000 ALTER TABLE `ida_resume_skill` DISABLE KEYS */;
INSERT INTO `ida_resume_skill` VALUES (15,1,'后端','Java','熟练',NULL,0,'2026-06-15 14:46:15.852','2026-06-15 14:46:15.852'),(16,1,'后端','SpringBoot','熟练',NULL,1,'2026-06-15 14:46:15.852','2026-06-15 14:46:15.852'),(17,1,'数据','MySQL','熟练',NULL,2,'2026-06-15 14:46:15.852','2026-06-15 14:46:15.852'),(18,1,'数据','Redis','熟练',NULL,3,'2026-06-15 14:46:15.852','2026-06-15 14:46:15.852'),(19,1,'数据','MyBatis-Plus','熟练',NULL,4,'2026-06-15 14:46:15.852','2026-06-15 14:46:15.852'),(20,1,'Agent开发','LangChain4j','熟悉',NULL,5,'2026-06-15 14:46:15.852','2026-06-15 14:46:15.852'),(21,1,'部署','Git','熟练',NULL,6,'2026-06-15 14:46:15.852','2026-06-15 14:46:15.852'),(22,1,'部署','Docker','熟悉',NULL,7,'2026-06-15 14:46:15.852','2026-06-15 14:46:15.852'),(23,1,'框架','芋道','熟悉',NULL,8,'2026-06-15 14:46:15.852','2026-06-15 14:46:15.852'),(24,1,'框架','RuoYi','熟悉',NULL,9,'2026-06-15 14:46:15.852','2026-06-15 14:46:15.852'),(25,1,'网络','TCP/HTTP','熟悉',NULL,10,'2026-06-15 14:46:15.852','2026-06-15 14:46:15.852'),(26,1,'微服务','Spring Cloud Alibaba','了解',NULL,11,'2026-06-15 14:46:15.852','2026-06-15 14:46:15.852'),(27,1,'前端','Vue','了解',NULL,12,'2026-06-15 14:46:15.852','2026-06-15 14:46:15.852'),(28,1,'前端','微信小程序','了解',NULL,13,'2026-06-15 14:46:15.852','2026-06-15 14:46:15.852'),(29,2,'驾驶','开车','精通',NULL,0,'2026-07-08 07:57:51.319','2026-07-08 07:57:51.319');
/*!40000 ALTER TABLE `ida_resume_skill` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `ida_resume_work`
--

LOCK TABLES `ida_resume_work` WRITE;
/*!40000 ALTER TABLE `ida_resume_work` DISABLE KEYS */;
INSERT INTO `ida_resume_work` VALUES (2,1,'上海灵曼科技有限公司',NULL,'Java 开发工程师',NULL,NULL,'实习','2026-04-01','2026-06-01','基于芋道框架（RuoYi-pro）、SpringBoot、MyBatis-Plus、PostgreSQL、Git、RabbitMQ、ZLM流媒体及Git等技术栈，完成核心系统的后端开发与落地。主要负责：\n1、基于数据权限，实现SaaS系统多租户隔离下的分页列表查询；\n2、使用RabbitMQ延迟消费特性实现定时任务与超时未处理预警；\n3、整合框架BPM工作流，完成申请→审批→回调的全流程闭环；\n4、开发基于角色权限的定向推送模块，实现公告消息按角色隔离发送；\n5、实现业务数据的多维度统计接口（年/月/日聚合）及实时状态计数查询；\n6、对接流媒体服务器与AI模型回调接口，根据结果创建告警记录并入库；\n7、编写自动化部署脚本，配置GitLab CI/CD实现自动化部署；\n8、规范Git分支流程，独立维护版本迭代，并完成核心模块单元测试；\n9、开发问答式检索模块，后台自定义问题和关键字，前台通过关键字模糊匹配检索。',NULL,0,'2026-06-15 14:46:15.852','2026-06-15 14:46:15.852');
/*!40000 ALTER TABLE `ida_resume_work` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `ida_site_fill_rule`
--

LOCK TABLES `ida_site_fill_rule` WRITE;
/*!40000 ALTER TABLE `ida_site_fill_rule` DISABLE KEYS */;
/*!40000 ALTER TABLE `ida_site_fill_rule` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `ida_user`
--

LOCK TABLES `ida_user` WRITE;
/*!40000 ALTER TABLE `ida_user` DISABLE KEYS */;
INSERT INTO `ida_user` VALUES (2066367665870573569,'13534832455',1,'2026-06-15 03:50:17.452','2026-07-24 15:55:30.419'),(2074475915917197313,'18806832463',1,'2026-07-07 12:49:34.905','2026-07-07 12:49:34.905'),(2085210143453188098,'19708892291',1,'2026-08-06 03:43:34.183','2026-08-07 08:27:46.192');
/*!40000 ALTER TABLE `ida_user` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-08-12 14:30:52
