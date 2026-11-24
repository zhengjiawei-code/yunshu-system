-- 外转/直达路由补充时效与里程（选填，NULL=未配置）
ALTER TABLE sys_route_transfer
  ADD COLUMN transit_hours DECIMAL(8,2)  NULL COMMENT '外转时效（小时），NULL=未配置' AFTER alias,
  ADD COLUMN mileage       DECIMAL(10,2) NULL COMMENT '外转里程（公里），NULL=未配置' AFTER transit_hours;
