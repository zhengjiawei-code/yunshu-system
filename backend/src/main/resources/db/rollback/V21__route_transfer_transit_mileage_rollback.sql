-- 回滚 V21：移除外转时效与里程列
ALTER TABLE sys_route_transfer
  DROP COLUMN mileage,
  DROP COLUMN transit_hours;
