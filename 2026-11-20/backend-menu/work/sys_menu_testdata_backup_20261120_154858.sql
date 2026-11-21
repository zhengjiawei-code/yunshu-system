-- MySQL dump 10.13  Distrib 8.0.46, for macos15 (x86_64)
--
-- Host: 127.0.0.1    Database: yunshu
-- ------------------------------------------------------
-- Server version	8.0.46

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
-- Dumping data for table `sys_menu`
--
-- WHERE:  id in (2404,2405,2406,2407,2408,2409,2410,2411,2412,2413,2414,2415,2416,2417,2418)

/*!40000 ALTER TABLE `sys_menu` DISABLE KEYS */;
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `route_path`, `component`, `redirect`, `perms`, `icon`, `portal_type`, `terminal_type`, `sort_order`, `visible`, `status`, `keep_alive`, `affix`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`) VALUES (2404,0,'验收临时目录',1,'/verify-tmp',NULL,NULL,NULL,NULL,'CLIENT','PC',999,1,1,0,0,NULL,NULL,'2026-11-20 15:01:28',NULL,'2026-11-20 15:03:25',1);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `route_path`, `component`, `redirect`, `perms`, `icon`, `portal_type`, `terminal_type`, `sort_order`, `visible`, `status`, `keep_alive`, `affix`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`) VALUES (2405,2404,'验收临时菜单',2,NULL,NULL,NULL,NULL,NULL,'CLIENT','PC',10,1,1,1,0,NULL,NULL,'2026-11-20 15:01:28',NULL,'2026-11-20 15:03:25',1);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `route_path`, `component`, `redirect`, `perms`, `icon`, `portal_type`, `terminal_type`, `sort_order`, `visible`, `status`, `keep_alive`, `affix`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`) VALUES (2406,0,'验收临时目录',1,'/verify-tmp',NULL,NULL,NULL,NULL,'CLIENT','PC',999,1,1,0,0,NULL,NULL,'2026-11-20 15:03:30',NULL,'2026-11-20 15:04:03',1);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `route_path`, `component`, `redirect`, `perms`, `icon`, `portal_type`, `terminal_type`, `sort_order`, `visible`, `status`, `keep_alive`, `affix`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`) VALUES (2407,2406,'验收临时菜单',2,'/verify-tmp/page','system/menu/index',NULL,NULL,'Setting','CLIENT','PC',10,1,1,1,0,NULL,NULL,'2026-11-20 15:03:33',NULL,'2026-11-20 15:04:00',1);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `route_path`, `component`, `redirect`, `perms`, `icon`, `portal_type`, `terminal_type`, `sort_order`, `visible`, `status`, `keep_alive`, `affix`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`) VALUES (2408,2407,'验收临时按钮',3,NULL,NULL,NULL,'verify:tmp:test',NULL,'CLIENT','PC',10,1,1,0,0,NULL,NULL,'2026-11-20 15:03:36',NULL,'2026-11-20 15:03:57',1);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `route_path`, `component`, `redirect`, `perms`, `icon`, `portal_type`, `terminal_type`, `sort_order`, `visible`, `status`, `keep_alive`, `affix`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`) VALUES (2409,0,'测试目录-接口验证A',1,'/test-api-a',NULL,NULL,NULL,'Folder','CLIENT','PC',20,1,1,0,0,'接口联调临时数据，验证后可删',NULL,'2026-11-20 15:04:42',NULL,'2026-11-20 15:05:13',1);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `route_path`, `component`, `redirect`, `perms`, `icon`, `portal_type`, `terminal_type`, `sort_order`, `visible`, `status`, `keep_alive`, `affix`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`) VALUES (2410,0,'测试目录-接口验证B',1,'/test-api-b',NULL,NULL,NULL,'Folder','ADMIN','PC',10,1,1,0,0,'接口联调临时数据，验证后可删',NULL,'2026-11-20 15:04:42',NULL,'2026-11-20 15:05:13',1);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `route_path`, `component`, `redirect`, `perms`, `icon`, `portal_type`, `terminal_type`, `sort_order`, `visible`, `status`, `keep_alive`, `affix`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`) VALUES (2411,2409,'测试菜单-接口验证改',2,'/test-api-a/page','system/menu/index',NULL,'test:api:list','Menu','CLIENT','PC',20,1,1,0,0,'编辑验证',NULL,'2026-11-20 15:04:47',NULL,'2026-11-20 15:05:13',1);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `route_path`, `component`, `redirect`, `perms`, `icon`, `portal_type`, `terminal_type`, `sort_order`, `visible`, `status`, `keep_alive`, `affix`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`) VALUES (2412,2411,'测试按钮-接口验证',3,NULL,NULL,NULL,'test:api:add',NULL,'CLIENT','PC',10,1,1,0,0,'接口联调临时数据',NULL,'2026-11-20 15:04:51',NULL,'2026-11-20 15:05:13',1);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `route_path`, `component`, `redirect`, `perms`, `icon`, `portal_type`, `terminal_type`, `sort_order`, `visible`, `status`, `keep_alive`, `affix`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`) VALUES (2413,0,'联调UI目录',1,'/ui-test',NULL,NULL,NULL,'Folder','ADMIN','PC',150,1,1,0,0,NULL,NULL,'2026-11-20 15:06:16',NULL,'2026-11-20 15:06:16',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `route_path`, `component`, `redirect`, `perms`, `icon`, `portal_type`, `terminal_type`, `sort_order`, `visible`, `status`, `keep_alive`, `affix`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`) VALUES (2414,2413,'联调UI菜单',2,'/ui-test/page','system/menu/index',NULL,NULL,'Menu','ADMIN','PC',10,1,1,1,0,NULL,NULL,'2026-11-20 15:08:16',NULL,'2026-11-20 15:08:16',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `route_path`, `component`, `redirect`, `perms`, `icon`, `portal_type`, `terminal_type`, `sort_order`, `visible`, `status`, `keep_alive`, `affix`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`) VALUES (2415,2414,'联调UI按钮',3,NULL,NULL,NULL,'ui:test:btn',NULL,'ADMIN','PC',10,1,1,0,0,NULL,NULL,'2026-11-20 15:09:16',NULL,'2026-11-20 15:09:16',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `route_path`, `component`, `redirect`, `perms`, `icon`, `portal_type`, `terminal_type`, `sort_order`, `visible`, `status`, `keep_alive`, `affix`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`) VALUES (2416,0,'测试目录-联调',1,'/test-lj',NULL,NULL,NULL,'Folder','CLIENT','PC',160,1,1,0,0,NULL,NULL,'2026-11-20 15:18:51',NULL,'2026-11-20 15:37:43',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `route_path`, `component`, `redirect`, `perms`, `icon`, `portal_type`, `terminal_type`, `sort_order`, `visible`, `status`, `keep_alive`, `affix`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`) VALUES (2417,2416,'测试菜单-联调改',2,'/test-lj/page','system/menu/index',NULL,NULL,'Menu','CLIENT','PC',10,1,1,1,0,NULL,NULL,'2026-11-20 15:23:42',NULL,'2026-11-20 15:37:43',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `route_path`, `component`, `redirect`, `perms`, `icon`, `portal_type`, `terminal_type`, `sort_order`, `visible`, `status`, `keep_alive`, `affix`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`) VALUES (2418,2417,'测试按钮-联调',3,NULL,NULL,NULL,'test:lj:add',NULL,'CLIENT','PC',30,1,1,0,0,NULL,NULL,'2026-11-20 15:24:18',NULL,'2026-11-20 15:43:29',0);
/*!40000 ALTER TABLE `sys_menu` ENABLE KEYS */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-20 15:48:58
