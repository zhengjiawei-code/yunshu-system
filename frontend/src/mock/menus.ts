import type { MenuNode } from '@/types/menu'

/**
 * 骨架阶段的菜单 mock（内部端与客户共用同一套菜单，40 个菜单）
 * 依据：outputs/module-tree.md
 * - 预开单 /operation/pre-create 作为普通菜单存在，通过给客户账号授权控制
 * - /dashboard 为静态 affix 首页，不在此表
 * 后端就绪后由 GET /api/v1/auth/info 返回同结构数据，本文件仅本地预览用。
 */
export const internalMenus: MenuNode[] = [
  {
    id: 1001, parentId: 0, menuName: '系统管理', menuType: 1, routePath: '/system',
    icon: 'Setting', terminalType: 'PC', sortOrder: 10,
    visible: true, status: true,
    children: [
      { id: 1101, parentId: 1001, menuName: '机构管理', menuType: 2, routePath: '/system/org', component: 'system/org/index', icon: 'OfficeBuilding', terminalType: 'PC', sortOrder: 10, visible: true, status: true },
      { id: 1102, parentId: 1001, menuName: '用户管理', menuType: 2, routePath: '/system/user', component: 'system/user/index', icon: 'User', terminalType: 'PC', sortOrder: 20, visible: true, status: true },
      { id: 1103, parentId: 1001, menuName: '角色管理', menuType: 2, routePath: '/system/role', component: 'system/role/index', icon: 'Avatar', terminalType: 'PC', sortOrder: 30, visible: true, status: true },
      { id: 1104, parentId: 1001, menuName: '路由管理', menuType: 2, routePath: '/system/route', component: 'system/route/index', icon: 'Guide', terminalType: 'PC', sortOrder: 40, visible: true, status: true },
      { id: 1105, parentId: 1001, menuName: '菜单管理', menuType: 2, routePath: '/system/menu', component: 'system/menu/index', icon: 'Menu', terminalType: 'PC', sortOrder: 50, visible: true, status: true },
      { id: 1106, parentId: 1001, menuName: '字典管理', menuType: 2, routePath: '/system/dict', component: 'system/dict/index', icon: 'Collection', terminalType: 'PC', sortOrder: 60, visible: true, status: true },
      { id: 1107, parentId: 1001, menuName: '承运商管理', menuType: 2, routePath: '/system/carrier', component: 'system/carrier/index', icon: 'Truck', terminalType: 'PC', sortOrder: 70, visible: true, status: true },
      { id: 1108, parentId: 1001, menuName: '系统配置', menuType: 2, routePath: '/system/config', component: 'system/config/index', icon: 'Tools', terminalType: 'PC', sortOrder: 80, visible: true, status: true },
      { id: 1109, parentId: 1001, menuName: '区划管理', menuType: 2, routePath: '/system/region', component: 'system/region/index', icon: 'MapLocation', terminalType: 'PC', sortOrder: 90, visible: true, status: true }
    ]
  },
  {
    id: 1002, parentId: 0, menuName: '运营管理', menuType: 1, routePath: '/operation',
    icon: 'Tickets', terminalType: 'PC', sortOrder: 20,
    visible: true, status: true,
    children: [
      { id: 1201, parentId: 1002, menuName: '预开单', menuType: 2, routePath: '/operation/pre-create', component: 'operation/preCreate/index', icon: 'EditPen', terminalType: 'PC', sortOrder: 5, visible: true, status: true },
      { id: 1202, parentId: 1002, menuName: '开单揽收', menuType: 2, routePath: '/operation/create', component: 'operation/create/index', icon: 'EditPen', terminalType: 'PC', sortOrder: 10, visible: true, status: true },
      { id: 1203, parentId: 1002, menuName: '运单导入', menuType: 2, routePath: '/operation/import', component: 'operation/import/index', icon: 'Upload', terminalType: 'PC', sortOrder: 20, visible: true, status: true },
      { id: 1204, parentId: 1002, menuName: '运单管理', menuType: 2, routePath: '/operation/waybill', component: 'operation/waybill/index', icon: 'Document', terminalType: 'PC', sortOrder: 30, visible: true, status: true },
      { id: 1205, parentId: 1002, menuName: '删单明细', menuType: 2, routePath: '/operation/deleted', component: 'operation/deleted/index', icon: 'Delete', terminalType: 'PC', sortOrder: 40, visible: true, status: true }
    ]
  },
  {
    id: 1003, parentId: 0, menuName: '运输管理', menuType: 1, routePath: '/transport',
    icon: 'Van', terminalType: 'PC', sortOrder: 30,
    visible: true, status: true,
    children: [
      { id: 1301, parentId: 1003, menuName: '装车配载', menuType: 2, routePath: '/transport/loading', component: 'transport/loading/index', icon: 'Box', terminalType: 'PC,APP', sortOrder: 10, visible: true, status: true }
    ]
  },
  {
    id: 1004, parentId: 0, menuName: '到货管理', menuType: 1, routePath: '/arrival',
    icon: 'Download', terminalType: 'PC', sortOrder: 40,
    visible: true, status: true,
    children: [
      { id: 1401, parentId: 1004, menuName: '到货卸车', menuType: 2, routePath: '/arrival/unload', component: 'arrival/unload/index', icon: 'Download', terminalType: 'PC,APP', sortOrder: 10, visible: true, status: true }
    ]
  },
  {
    id: 1005, parentId: 0, menuName: '提货管理', menuType: 1, routePath: '/pickup',
    icon: 'Box', terminalType: 'PC', sortOrder: 50,
    visible: true, status: true,
    children: [
      { id: 1501, parentId: 1005, menuName: '客户提货', menuType: 2, routePath: '/pickup/customer', component: 'pickup/customer/index', icon: 'TakeawayBox', terminalType: 'PC,APP', sortOrder: 10, visible: true, status: true }
    ]
  },
  {
    id: 1006, parentId: 0, menuName: '送货管理', menuType: 1, routePath: '/delivery',
    icon: 'Location', terminalType: 'PC', sortOrder: 60,
    visible: true, status: true,
    children: [
      { id: 1601, parentId: 1006, menuName: '货物派送', menuType: 2, routePath: '/delivery/dispatch', component: 'delivery/dispatch/index', icon: 'Position', terminalType: 'PC', sortOrder: 10, visible: true, status: true }
    ]
  },
  {
    id: 1007, parentId: 0, menuName: '外转管理', menuType: 1, routePath: '/transfer',
    icon: 'Switch', terminalType: 'PC', sortOrder: 70,
    visible: true, status: true,
    children: [
      { id: 1701, parentId: 1007, menuName: '货物转出', menuType: 2, routePath: '/transfer/out', component: 'transfer/out/index', icon: 'Switch', terminalType: 'PC', sortOrder: 10, visible: true, status: true }
    ]
  },
  {
    id: 1008, parentId: 0, menuName: '签收管理', menuType: 1, routePath: '/sign',
    icon: 'Finished', terminalType: 'PC', sortOrder: 80,
    visible: true, status: true,
    children: [
      { id: 1801, parentId: 1008, menuName: '运单签收', menuType: 2, routePath: '/sign/waybill', component: 'sign/waybill/index', icon: 'Finished', terminalType: 'PC,APP', sortOrder: 10, visible: true, status: true }
    ]
  },
  {
    id: 1009, parentId: 0, menuName: '运力管理', menuType: 1, routePath: '/capacity',
    icon: 'Van', terminalType: 'PC', sortOrder: 90,
    visible: true, status: true,
    children: [
      { id: 1901, parentId: 1009, menuName: '司机管理', menuType: 2, routePath: '/capacity/driver', component: 'capacity/driver/index', icon: 'UserFilled', terminalType: 'PC', sortOrder: 10, visible: true, status: true },
      { id: 1902, parentId: 1009, menuName: '车辆管理', menuType: 2, routePath: '/capacity/vehicle', component: 'capacity/vehicle/index', icon: 'Van', terminalType: 'PC', sortOrder: 20, visible: true, status: true },
      { id: 1903, parentId: 1009, menuName: '车型管理', menuType: 2, routePath: '/capacity/vehicle-type', component: 'capacity/vehicleType/index', icon: 'Postcard', terminalType: 'PC', sortOrder: 30, visible: true, status: true }
    ]
  },
  {
    id: 1010, parentId: 0, menuName: '客户管理', menuType: 1, routePath: '/customer',
    icon: 'User', terminalType: 'PC', sortOrder: 100,
    visible: true, status: true,
    children: [
      { id: 2001, parentId: 1010, menuName: '客户档案', menuType: 2, routePath: '/customer/archive', component: 'customer/archive/index', icon: 'User', terminalType: 'PC', sortOrder: 10, visible: true, status: true }
    ]
  },
  {
    id: 1011, parentId: 0, menuName: '异常管理', menuType: 1, routePath: '/exception',
    icon: 'Warning', terminalType: 'PC', sortOrder: 110,
    visible: true, status: true,
    children: [
      { id: 2101, parentId: 1011, menuName: '异常上报', menuType: 2, routePath: '/exception/report', component: 'exception/report/index', icon: 'Warning', terminalType: 'PC', sortOrder: 10, visible: true, status: true },
      { id: 2102, parentId: 1011, menuName: '异常处理', menuType: 2, routePath: '/exception/handle', component: 'exception/handle/index', icon: 'CircleCheck', terminalType: 'PC', sortOrder: 20, visible: true, status: true }
    ]
  },
  {
    id: 1012, parentId: 0, menuName: '回单管理', menuType: 1, routePath: '/receipt',
    icon: 'Document', terminalType: 'PC', sortOrder: 120,
    visible: true, status: true,
    children: [
      { id: 2201, parentId: 1012, menuName: '回单寄出', menuType: 2, routePath: '/receipt/send', component: 'receipt/send/index', icon: 'Promotion', terminalType: 'PC', sortOrder: 10, visible: true, status: true },
      { id: 2202, parentId: 1012, menuName: '回单接收', menuType: 2, routePath: '/receipt/receive', component: 'receipt/receive/index', icon: 'Download', terminalType: 'PC', sortOrder: 20, visible: true, status: true },
      { id: 2203, parentId: 1012, menuName: '回单发放', menuType: 2, routePath: '/receipt/issue', component: 'receipt/issue/index', icon: 'Upload', terminalType: 'PC', sortOrder: 30, visible: true, status: true }
    ]
  },
  {
    id: 1013, parentId: 0, menuName: '财务管理', menuType: 1, routePath: '/finance',
    icon: 'Money', terminalType: 'PC', sortOrder: 130,
    visible: true, status: true,
    children: [
      { id: 2301, parentId: 1013, menuName: '核销管理', menuType: 2, routePath: '/finance/verification', component: 'finance/verification/index', icon: 'CircleCheck', terminalType: 'PC', sortOrder: 10, visible: true, status: true },
      { id: 2302, parentId: 1013, menuName: '网点对账', menuType: 2, routePath: '/finance/branch-recon', component: 'finance/branchRecon/index', icon: 'OfficeBuilding', terminalType: 'PC', sortOrder: 20, visible: true, status: true },
      { id: 2303, parentId: 1013, menuName: '承运商对账', menuType: 2, routePath: '/finance/carrier-recon', component: 'finance/carrierRecon/index', icon: 'Truck', terminalType: 'PC', sortOrder: 30, visible: true, status: true },
      { id: 2304, parentId: 1013, menuName: '费用录入', menuType: 2, routePath: '/finance/fee-entry', component: 'finance/feeEntry/index', icon: 'EditPen', terminalType: 'PC', sortOrder: 40, visible: true, status: true },
      { id: 2305, parentId: 1013, menuName: '账单中心', menuType: 2, routePath: '/finance/bill-center', component: 'finance/billCenter/index', icon: 'Files', terminalType: 'PC', sortOrder: 50, visible: true, status: true },
      { id: 2306, parentId: 1013, menuName: '收支方式', menuType: 2, routePath: '/finance/pay-method', component: 'finance/payMethod/index', icon: 'Wallet', terminalType: 'PC', sortOrder: 60, visible: true, status: true },
      { id: 2307, parentId: 1013, menuName: '科目管理', menuType: 2, routePath: '/finance/fee-subject', component: 'finance/feeSubject/index', icon: 'Collection', terminalType: 'PC', sortOrder: 70, visible: true, status: true },
      { id: 2308, parentId: 1013, menuName: '账户管理', menuType: 2, routePath: '/finance/account', component: 'finance/account/index', icon: 'CreditCard', terminalType: 'PC', sortOrder: 80, visible: true, status: true }
    ]
  },
  {
    id: 1014, parentId: 0, menuName: '审核管理', menuType: 1, routePath: '/audit',
    icon: 'DocumentChecked', terminalType: 'PC', sortOrder: 140,
    visible: true, status: true,
    children: [
      { id: 2401, parentId: 1014, menuName: '删单审批', menuType: 2, routePath: '/audit/delete', component: 'audit/delete/index', icon: 'Delete', terminalType: 'PC', sortOrder: 10, visible: true, status: true },
      { id: 2402, parentId: 1014, menuName: '改单审批', menuType: 2, routePath: '/audit/modify', component: 'audit/modify/index', icon: 'Edit', terminalType: 'PC', sortOrder: 20, visible: true, status: true },
      { id: 2403, parentId: 1014, menuName: '取消签收审批', menuType: 2, routePath: '/audit/cancel-sign', component: 'audit/cancelSign/index', icon: 'CircleClose', terminalType: 'PC', sortOrder: 30, visible: true, status: true }
    ]
  }
]
