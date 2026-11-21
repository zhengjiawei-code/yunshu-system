<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-header__title">路由管理</h2>
        <p class="page-header__desc">
          维护短驳/干线与直达/外转路由；短驳/干线为网点间物理段，按创建公司归属、不做跨公司授权
        </p>
      </div>
    </div>

    <div class="page-content route-page">
      <el-tabs v-model="activeTab" class="route-tabs" @tab-change="handleTabChange">
        <!-- ==================== Tab1 短驳/干线 ==================== -->
        <el-tab-pane label="短驳/干线" name="route">
          <div class="tab-body">
            <div class="action-bar">
              <div class="action-left">
                <el-button type="primary" @click="openRouteCreate">新增路由</el-button>
              </div>
              <div class="action-right">
                <el-button @click="resetRouteFilters">重置</el-button>
                <el-button :icon="Download" @click="handleRouteExport">导出</el-button>
                <el-popover placement="bottom-end" :width="200" trigger="click">
                  <template #reference>
                    <el-button :icon="Setting">列设置</el-button>
                  </template>
                  <div class="column-setting">
                    <div class="column-setting__head">
                      <span>显示列</span>
                      <el-button link type="primary" size="small" @click="resetRouteColumns">恢复默认</el-button>
                    </div>
                    <el-checkbox
                      v-for="item in routeColumnOptions"
                      :key="item.key"
                      v-model="routeColumnState[item.key]"
                      :disabled="item.locked"
                      class="column-setting__item"
                      @change="(value: boolean) => onRouteColumnToggle(item.key, value)"
                    >
                      {{ item.label }}
                    </el-checkbox>
                  </div>
                </el-popover>
                <el-pagination
                  v-model:current-page="routePage"
                  v-model:page-size="routePageSize"
                  class="table-pager"
                  size="small"
                  background
                  :total="routeTotal"
                  :page-sizes="[20, 50, 100]"
                  layout="total, sizes, prev, pager, next"
                  @current-change="loadRoutes"
                  @size-change="handleRouteSizeChange"
                />
              </div>
            </div>

            <div class="table-wrap">
              <smart-table
                ref="routeTableRef"
                v-loading="routeLoading"
                :data="routeRows"
                row-key="id"
                height="100%"
                empty-text="暂无匹配的路由"
                persist-key="system:route"
                show-summary
                :summary-method="routeSummary"
                show-overflow-tooltip
                scrollbar-always-on
                class="route-table"
                @sort-change="handleRouteSort"
              >
                <el-table-column type="index" label="序号" width="64" align="center" :index="routeRowIndex" />

                <el-table-column v-if="routeColumnState.routeCode" prop="routeCode" label="路由编码" min-width="130" sortable="custom">
                  <template #header>
                    <div class="col-head">
                      <span class="col-head__label">路由编码</span>
                      <el-input v-model="routeFilters.routeCode" size="small" placeholder="请输入" clearable @input="onRouteTextFilter" />
                    </div>
                  </template>
                  <template #default="{ row }"><span class="mono">{{ row.routeCode }}</span></template>
                </el-table-column>

                <el-table-column v-if="routeColumnState.routeName" prop="routeName" label="线路名称" min-width="200" sortable="custom">
                  <template #header>
                    <div class="col-head">
                      <span class="col-head__label">线路名称</span>
                      <el-input v-model="routeFilters.routeName" size="small" placeholder="请输入" clearable @input="onRouteTextFilter" />
                    </div>
                  </template>
                </el-table-column>

                <el-table-column v-if="routeColumnState.routeCategory" prop="routeCategory" label="类型" width="96" align="center" sortable="custom">
                  <template #header>
                    <div class="col-head">
                      <span class="col-head__label">类型</span>
                      <el-select v-model="routeFilters.routeCategory" size="small" placeholder="全部" clearable @change="onRouteFilterChange">
                        <el-option label="短驳" :value="1" />
                        <el-option label="干线" :value="2" />
                      </el-select>
                    </div>
                  </template>
                  <template #default="{ row }">
                    <status-tag :type="row.routeCategory === 2 ? 'primary' : 'info'" :text="row.routeCategory === 2 ? '干线' : '短驳'" />
                  </template>
                </el-table-column>

                <el-table-column v-if="routeColumnState.originDeptName" prop="originDeptName" label="始发网点" min-width="170" sortable="custom">
                  <template #header>
                    <div class="col-head">
                      <span class="col-head__label">始发网点</span>
                      <el-tree-select
                        v-model="routeFilters.originDeptId"
                        size="small"
                        :data="scopeTree"
                        :props="{ label: 'deptName', children: 'children' }"
                        node-key="id"
                        check-strictly
                        clearable
                        placeholder="全部"
                        @change="onRouteFilterChange"
                      />
                    </div>
                  </template>
                  <template #default="{ row }">{{ row.originDeptName || '—' }}</template>
                </el-table-column>

                <el-table-column v-if="routeColumnState.destDeptName" prop="destDeptName" label="到达网点" min-width="170" sortable="custom">
                  <template #header>
                    <div class="col-head">
                      <span class="col-head__label">到达网点</span>
                      <el-tree-select
                        v-model="routeFilters.destDeptId"
                        size="small"
                        :data="scopeTree"
                        :props="{ label: 'deptName', children: 'children' }"
                        node-key="id"
                        check-strictly
                        clearable
                        placeholder="全部"
                        @change="onRouteFilterChange"
                      />
                    </div>
                  </template>
                  <template #default="{ row }">{{ row.destDeptName || '—' }}</template>
                </el-table-column>

                <el-table-column v-if="routeColumnState.warehouseArea" prop="warehouseArea" label="库区" min-width="120">
                  <template #default="{ row }">{{ row.warehouseArea || '—' }}</template>
                </el-table-column>

                <el-table-column v-if="routeColumnState.mileage" prop="mileage" label="里程(km)" min-width="110" align="right" sortable="custom">
                  <template #default="{ row }">{{ formatNumber(row.mileage) }}</template>
                </el-table-column>

                <el-table-column v-if="routeColumnState.transitHours" prop="transitHours" label="时效(小时)" min-width="110" align="right" sortable="custom">
                  <template #default="{ row }">{{ formatNumber(row.transitHours) }}</template>
                </el-table-column>

                <el-table-column v-if="routeColumnState.contactName" prop="contactName" label="线路联系人" min-width="130">
                  <template #default="{ row }">{{ row.contactName || '—' }}</template>
                </el-table-column>

                <el-table-column v-if="routeColumnState.contactPhone" prop="contactPhone" label="联系电话" min-width="140">
                  <template #default="{ row }">{{ row.contactPhone || '—' }}</template>
                </el-table-column>

                <el-table-column v-if="routeColumnState.serviceName" prop="serviceName" label="线路客服" min-width="120">
                  <template #default="{ row }">{{ row.serviceName || '—' }}</template>
                </el-table-column>

                <el-table-column v-if="routeColumnState.servicePhone" prop="servicePhone" label="客服电话" min-width="140">
                  <template #default="{ row }">{{ row.servicePhone || '—' }}</template>
                </el-table-column>

                <el-table-column v-if="routeColumnState.ownerCompanyName" prop="ownerCompanyName" label="创建公司" min-width="150" sortable="custom">
                  <template #default="{ row }">{{ row.ownerCompanyName || '—' }}</template>
                </el-table-column>

                <el-table-column v-if="routeColumnState.status" prop="status" label="状态" width="110" align="center" sortable="custom">
                  <template #header>
                    <div class="col-head">
                      <span class="col-head__label">状态</span>
                      <el-select v-model="routeFilters.status" size="small" placeholder="全部" clearable @change="onRouteFilterChange">
                        <el-option label="启用" :value="true" />
                        <el-option label="停用" :value="false" />
                      </el-select>
                    </div>
                  </template>
                  <template #default="{ row }">
                    <status-tag :type="row.status ? 'primary' : 'info'" :text="row.status ? '启用' : '停用'" />
                  </template>
                </el-table-column>

                <el-table-column v-if="routeColumnState.sortOrder" prop="sortOrder" label="排序号" width="100" align="right" sortable="custom" />

                <el-table-column v-if="routeColumnState.updateTime" prop="updateTime" label="更新时间" min-width="170" align="center" sortable="custom" />

                <el-table-column label="操作" width="120" fixed="right" align="right">
                  <template #default="{ row }">
                    <div class="row-actions">
                      <el-button v-if="row.editable" link type="primary" size="small" @click="openRouteEdit(row)">编辑</el-button>
                      <el-button link type="danger" size="small" @click="handleRouteDelete(row)">删除</el-button>
                    </div>
                  </template>
                </el-table-column>
              </smart-table>
            </div>
          </div>
        </el-tab-pane>

        <!-- ==================== Tab2 直达/外转 ==================== -->
        <el-tab-pane label="直达/外转" name="transfer">
          <div class="tab-body">
            <div class="transfer-toolbar">
              <div class="transfer-toolbar__left">
                <span class="transfer-toolbar__label">配置网点</span>
                <el-tree-select
                  v-model="transferApplyDeptId"
                  class="transfer-toolbar__dept"
                  :data="scopeTree"
                  :props="{ label: 'deptName', children: 'children' }"
                  node-key="id"
                  check-strictly
                  clearable
                  placeholder="请选择配置网点"
                  @change="handleApplyDeptChange"
                />
                <span class="transfer-toolbar__hint">路由按网点配置，切换网点后区划库与已配置列表同步刷新</span>
              </div>
              <div class="transfer-toolbar__right">
                <el-button v-if="isHq" @click="openBatchGrant('transfer')">批量授权</el-button>
                <el-button @click="resetTransferFilters">重置</el-button>
                <el-button :icon="Download" @click="handleTransferExport">导出</el-button>
                <el-popover placement="bottom-end" :width="200" trigger="click">
                  <template #reference>
                    <el-button :icon="Setting">列设置</el-button>
                  </template>
                  <div class="column-setting">
                    <div class="column-setting__head">
                      <span>显示列</span>
                      <el-button link type="primary" size="small" @click="resetTransferColumns">恢复默认</el-button>
                    </div>
                    <el-checkbox
                      v-for="item in transferColumnOptions"
                      :key="item.key"
                      v-model="transferColumnState[item.key]"
                      :disabled="item.locked"
                      class="column-setting__item"
                      @change="(value: boolean) => onTransferColumnToggle(item.key, value)"
                    >
                      {{ item.label }}
                    </el-checkbox>
                  </div>
                </el-popover>
                <el-pagination
                  v-model:current-page="transferPage"
                  v-model:page-size="transferPageSize"
                  class="table-pager"
                  size="small"
                  background
                  :total="transferTotal"
                  :page-sizes="[20, 50, 100]"
                  layout="total, sizes, prev, pager, next"
                  @current-change="loadTransfers"
                  @size-change="handleTransferSizeChange"
                />
              </div>
            </div>

            <div class="transfer-columns">
              <section class="transfer-pane">
                <div class="pane-header">
                  <span class="pane-title">区划库</span>
                  <span class="pane-sub">关键词或级别查询，单次最多 50 条</span>
                </div>
                <div class="library-filters">
                  <el-input
                    v-model="regionKeyword"
                    size="small"
                    placeholder="区划名称/编码"
                    clearable
                    class="library-filters__keyword"
                    @keyup.enter="loadRegionLibrary"
                    @clear="loadRegionLibrary"
                  />
                  <el-select
                    v-model="regionLevel"
                    size="small"
                    placeholder="级别"
                    clearable
                    class="library-filters__select"
                    @change="loadRegionLibrary"
                  >
                    <el-option label="市" :value="2" />
                    <el-option label="区县" :value="3" />
                    <el-option label="乡级" :value="4" />
                  </el-select>
                  <el-select
                    v-model="regionConfigured"
                    size="small"
                    placeholder="配置"
                    clearable
                    class="library-filters__select"
                    @change="loadRegionLibrary"
                  >
                    <el-option label="未配置" :value="false" />
                    <el-option label="已配置" :value="true" />
                  </el-select>
                  <el-button size="small" :disabled="!transferApplyDeptId" @click="loadRegionLibrary">查询</el-button>
                </div>
                <div class="pane-table">
                  <smart-table
                    v-loading="regionLoading"
                    :data="regionRows"
                    row-key="code"
                    height="100%"
                    empty-text="请选择配置网点后查询区划"
                    show-overflow-tooltip
                    scrollbar-always-on
                    class="region-table"
                    @selection-change="onRegionSelection"
                  >
                    <el-table-column type="selection" width="40" :selectable="regionSelectable" />
                    <el-table-column label="级别" width="76" align="center">
                      <template #default="{ row }">
                        <status-tag type="info" :text="levelText(row.level)" />
                      </template>
                    </el-table-column>
                    <el-table-column prop="provinceName" label="省" min-width="90" show-overflow-tooltip />
                    <el-table-column prop="cityName" label="市" min-width="90" show-overflow-tooltip />
                    <el-table-column prop="districtName" label="区县" min-width="90" show-overflow-tooltip />
                    <el-table-column label="乡级" min-width="100" show-overflow-tooltip>
                      <template #default="{ row }">{{ row.level === 4 ? row.name : '—' }}</template>
                    </el-table-column>
                    <el-table-column label="配置" width="88" align="center">
                      <template #default="{ row }">
                        <span v-if="row.configured" class="configured-tag">已配置</span>
                        <span v-else class="muted">未配置</span>
                      </template>
                    </el-table-column>
                  </smart-table>
                </div>
              </section>

              <div class="transfer-join">
                <el-button
                  type="primary"
                  :disabled="!transferApplyDeptId || selectedRegionCodes.length === 0"
                  @click="handleJoinRegions"
                >
                  加入
                </el-button>
                <el-button
                  :disabled="!transferApplyDeptId || selectedTransferRows.length === 0"
                  @click="handleRemoveSelected"
                >
                  移除
                </el-button>
              </div>

              <section class="transfer-pane transfer-pane--routes">
                <div class="pane-header">
                  <span class="pane-title">
                    已配置路由{{ transferApplyDeptName ? `（${transferApplyDeptName}）` : '' }}
                  </span>
                  <div class="pane-actions">
                    <el-button size="small" :disabled="!selectedTransferRows.length" @click="handleSwitchType(1)">
                      切直达
                    </el-button>
                    <el-button size="small" :disabled="!selectedTransferRows.length" @click="handleSwitchType(2)">
                      切外转
                    </el-button>
                    <el-button size="small" :disabled="!selectedTransferRows.length" @click="openCarrierBatch">
                      挂承运商
                    </el-button>
                    <el-button size="small" :disabled="!selectedTransferRows.length" @click="handleBatchStatus(true)">
                      启用
                    </el-button>
                    <el-button size="small" :disabled="!selectedTransferRows.length" @click="handleBatchStatus(false)">
                      停用
                    </el-button>
                  </div>
                </div>
                <div class="pane-table">
                  <smart-table
                    ref="transferTableRef"
                    v-loading="transferLoading"
                    :data="transferRows"
                    row-key="id"
                    height="100%"
                    empty-text="当前网点暂未配置路由，请从左侧区划库加入"
                    persist-key="system:route-transfer"
                    show-summary
                    :summary-method="transferSummary"
                    show-overflow-tooltip
                    scrollbar-always-on
                    class="route-table"
                    @sort-change="handleTransferSort"
                    @selection-change="onTransferSelection"
                  >
                    <el-table-column type="selection" width="40" />
                    <el-table-column type="index" label="序号" width="60" align="center" :index="transferRowIndex" />

                    <el-table-column v-if="transferColumnState.routeCode" prop="routeCode" label="路由编码" min-width="140" sortable="custom">
                      <template #header>
                        <div class="col-head">
                          <span class="col-head__label">路由编码</span>
                          <el-input v-model="transferFilters.routeCode" size="small" placeholder="请输入" clearable @input="onTransferTextFilter" />
                        </div>
                      </template>
                      <template #default="{ row }"><span class="mono">{{ row.routeCode }}</span></template>
                    </el-table-column>

                    <el-table-column v-if="transferColumnState.routeName" prop="routeName" label="路由名称" min-width="170" sortable="custom">
                      <template #header>
                        <div class="col-head">
                          <span class="col-head__label">路由名称</span>
                          <el-input v-model="transferFilters.routeName" size="small" placeholder="请输入" clearable @input="onTransferTextFilter" />
                        </div>
                      </template>
                    </el-table-column>

                    <el-table-column v-if="transferColumnState.regionFullName" prop="regionFullName" label="目的地" min-width="180" sortable="custom">
                      <template #header>
                        <div class="col-head">
                          <span class="col-head__label">目的地</span>
                          <el-input v-model="transferFilters.regionCode" size="small" placeholder="编码/名称" clearable @input="onTransferTextFilter" />
                        </div>
                      </template>
                    </el-table-column>

                    <el-table-column v-if="transferColumnState.regionLevel" prop="regionLevel" label="级别" width="96" align="center" sortable="custom">
                      <template #header>
                        <div class="col-head">
                          <span class="col-head__label">级别</span>
                          <el-select v-model="transferFilters.regionLevel" size="small" placeholder="全部" clearable @change="onTransferFilterChange">
                            <el-option label="市" :value="2" />
                            <el-option label="区县" :value="3" />
                            <el-option label="乡级" :value="4" />
                          </el-select>
                        </div>
                      </template>
                      <template #default="{ row }">
                        <status-tag type="info" :text="levelText(row.regionLevel)" />
                      </template>
                    </el-table-column>

                    <el-table-column v-if="transferColumnState.transferType" prop="transferType" label="类型" width="96" align="center" sortable="custom">
                      <template #header>
                        <div class="col-head">
                          <span class="col-head__label">类型</span>
                          <el-select v-model="transferFilters.transferType" size="small" placeholder="全部" clearable @change="onTransferFilterChange">
                            <el-option label="直达" :value="1" />
                            <el-option label="外转" :value="2" />
                          </el-select>
                        </div>
                      </template>
                      <template #default="{ row }">
                        <el-tag
                          v-if="row.editable"
                          class="type-toggle"
                          :type="row.transferType === 1 ? 'primary' : 'warning'"
                          size="small"
                          @click="handleSwitchType(row.transferType === 1 ? 2 : 1, row)"
                        >
                          {{ row.transferType === 1 ? '直达' : '外转' }}
                        </el-tag>
                        <status-tag v-else :type="row.transferType === 1 ? 'primary' : 'warning'" :text="row.transferType === 1 ? '直达' : '外转'" />
                      </template>
                    </el-table-column>

                    <el-table-column v-if="transferColumnState.carrierName" prop="carrierName" label="承运商" min-width="150">
                      <template #default="{ row }">
                        <span v-if="row.carrierName">{{ row.carrierName }}</span>
                        <status-tag v-else-if="row.transferType === 2" type="warning" text="未配置" />
                        <span v-else class="muted">—</span>
                      </template>
                    </el-table-column>

                    <el-table-column v-if="transferColumnState.status" prop="status" label="状态" width="104" align="center" sortable="custom">
                      <template #header>
                        <div class="col-head">
                          <span class="col-head__label">状态</span>
                          <el-select v-model="transferFilters.status" size="small" placeholder="全部" clearable @change="onTransferFilterChange">
                            <el-option label="启用" :value="true" />
                            <el-option label="停用" :value="false" />
                          </el-select>
                        </div>
                      </template>
                      <template #default="{ row }">
                        <status-tag :type="row.status ? 'primary' : 'info'" :text="row.status ? '启用' : '停用'" />
                      </template>
                    </el-table-column>

                    <el-table-column v-if="transferColumnState.sortOrder" prop="sortOrder" label="排序" width="96" align="right" sortable="custom" />

                    <el-table-column v-if="transferColumnState.updateByName" prop="updateByName" label="修改人" min-width="120" sortable="custom">
                      <template #header>
                        <div class="col-head">
                          <span class="col-head__label">修改人</span>
                          <el-input v-model="transferFilters.updateByName" size="small" placeholder="请输入" clearable @input="onTransferTextFilter" />
                        </div>
                      </template>
                      <template #default="{ row }">{{ row.updateByName || '—' }}</template>
                    </el-table-column>

                    <el-table-column v-if="transferColumnState.updateTime" prop="updateTime" label="修改时间" min-width="170" align="center" sortable="custom" />

                    <el-table-column v-if="isHq && transferColumnState.grantScope" prop="grantScope" label="授权范围" width="110" align="center" sortable="custom">
                      <template #header>
                        <div class="col-head">
                          <span class="col-head__label">授权范围</span>
                          <el-select v-model="transferFilters.grantScope" size="small" placeholder="全部" clearable @change="onTransferFilterChange">
                            <el-option label="全部" value="ALL" />
                            <el-option label="部分" value="PARTIAL" />
                          </el-select>
                        </div>
                      </template>
                      <template #default="{ row }">
                        <el-tooltip
                          v-if="row.grantScope === 'ALL'"
                          content="创建时授权全部分公司，新增分公司自动生效"
                          placement="top"
                        >
                          <status-tag type="primary" text="全部" />
                        </el-tooltip>
                        <status-tag v-else type="info" :text="`${row.grantCount ?? 0} 家`" />
                      </template>
                    </el-table-column>

                    <el-table-column v-if="transferColumnState.ownerCompanyName" prop="ownerCompanyName" label="创建公司" min-width="140" sortable="custom">
                      <template #default="{ row }">{{ row.ownerCompanyName || '—' }}</template>
                    </el-table-column>

                    <el-table-column label="操作" :width="isHq ? 166 : 108" fixed="right" align="right">
                      <template #default="{ row }">
                        <div class="row-actions">
                          <el-button v-if="row.editable" link type="primary" size="small" @click="openTransferEdit(row)">编辑</el-button>
                          <el-button v-if="isHq" link type="primary" size="small" @click="openGrant('transfer', row)">授权</el-button>
                          <el-button v-if="row.deletable" link type="danger" size="small" @click="handleTransferDelete(row)">移除</el-button>
                        </div>
                      </template>
                    </el-table-column>
                  </smart-table>
                </div>
              </section>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <route-form-drawer
      v-model="routeDrawer.visible"
      :mode="routeDrawer.mode"
      :record="routeDrawer.record"
      :scope-tree="scopeTree"
      @saved="loadRoutes"
    />

    <transfer-form-drawer
      v-model="transferDrawer.visible"
      :record="transferDrawer.record"
      @saved="afterTransferSaved"
    />

    <el-dialog v-model="carrierBatch.visible" title="挂载承运商" width="420px">
      <p class="dialog-hint">仅对选中的外转路由生效，直达路由会被跳过</p>
      <el-select
        v-model="carrierBatch.carrierId"
        filterable
        clearable
        placeholder="请选择承运商"
        style="width: 100%"
      >
        <el-option
          v-for="item in carrierOptions"
          :key="item.id"
          :label="item.carrierName"
          :value="item.id"
        />
      </el-select>
      <template #footer>
        <el-button @click="carrierBatch.visible = false">取消</el-button>
        <el-button type="primary" :loading="carrierBatch.loading" @click="submitCarrierBatch">确定</el-button>
      </template>
    </el-dialog>

    <route-grant-dialog
      v-model="grantDialog.visible"
      :route-scope="grantDialog.routeScope"
      :route="grantDialog.route"
      :is-hq="isHq"
      @changed="refreshCurrentTab"
    />

    <route-batch-grant-dialog
      v-model="batchDialog.visible"
      :route-scope="batchDialog.routeScope"
      :routes="batchDialog.routes"
      @changed="refreshCurrentTab"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Setting } from '@element-plus/icons-vue'
import type { CarrierRecord } from '@/types/carrier'
import type { ManageScopeNode } from '@/types/dept'
import type {
  RegionLibraryRecord,
  RouteQuery,
  RouteRecord,
  TransferQuery,
  TransferRecord
} from '@/types/route'
import {
  batchJoinTransfer,
  batchTransferCarrier,
  batchTransferStatus,
  deleteRoute,
  deleteTransfer,
  getRegionLibrary,
  getRoutePage,
  getTransferPage,
  switchTransferType
} from '@/api/system/route'
import { getCarrierPage } from '@/api/system/carrier'
import { getManageScope } from '@/api/system/dept'
import StatusTag from '@/components/base/StatusTag.vue'
import SmartTable from '@/components/base/SmartTable.vue'
import RouteFormDrawer from './components/RouteFormDrawer.vue'
import TransferFormDrawer from './components/TransferFormDrawer.vue'
import RouteGrantDialog from './components/RouteGrantDialog.vue'
import RouteBatchGrantDialog from './components/RouteBatchGrantDialog.vue'

defineOptions({ name: 'SystemRoute' })

const ROUTE_COLUMN_KEY = 'yunshu_route_columns_v1'
const TRANSFER_COLUMN_KEY = 'yunshu_route_transfer_columns_v1'

const routeColumnOptions: Array<{ key: string; label: string; locked?: boolean }> = [
  { key: 'routeCode', label: '路由编码', locked: true },
  { key: 'routeName', label: '线路名称' },
  { key: 'routeCategory', label: '类型' },
  { key: 'originDeptName', label: '始发网点' },
  { key: 'destDeptName', label: '到达网点' },
  { key: 'warehouseArea', label: '库区' },
  { key: 'mileage', label: '里程' },
  { key: 'transitHours', label: '时效' },
  { key: 'contactName', label: '线路联系人' },
  { key: 'contactPhone', label: '联系电话' },
  { key: 'serviceName', label: '线路客服' },
  { key: 'servicePhone', label: '客服电话' },
  { key: 'ownerCompanyName', label: '创建公司' },
  { key: 'status', label: '状态' },
  { key: 'sortOrder', label: '排序号' },
  { key: 'updateTime', label: '更新时间' }
]

const transferColumnOptions: Array<{ key: string; label: string; locked?: boolean }> = [
  { key: 'routeCode', label: '路由编码', locked: true },
  { key: 'routeName', label: '路由名称' },
  { key: 'regionFullName', label: '目的地' },
  { key: 'regionLevel', label: '级别' },
  { key: 'transferType', label: '类型' },
  { key: 'carrierName', label: '承运商' },
  { key: 'status', label: '状态' },
  { key: 'sortOrder', label: '排序' },
  { key: 'updateByName', label: '修改人' },
  { key: 'updateTime', label: '修改时间' },
  { key: 'grantScope', label: '授权范围' },
  { key: 'ownerCompanyName', label: '创建公司' }
]

const activeTab = ref<'route' | 'transfer'>('route')
const isHq = ref(false)
const scopeTree = ref<ManageScopeNode[]>([])

const routeTableRef = ref<InstanceType<typeof SmartTable>>()
const routeRows = ref<RouteRecord[]>([])
const routeTotal = ref(0)
const routePage = ref(1)
const routePageSize = ref(20)
const routeLoading = ref(false)
const routeColumnState = reactive<Record<string, boolean>>(loadColumnState(ROUTE_COLUMN_KEY, routeColumnOptions))
const routeFilters = reactive<{
  routeCode: string
  routeName: string
  routeCategory: 1 | 2 | undefined
  originDeptId: number | undefined
  destDeptId: number | undefined
  status: boolean | undefined
  grantScope: 'ALL' | 'PARTIAL' | undefined
}>({
  routeCode: '',
  routeName: '',
  routeCategory: undefined,
  originDeptId: undefined,
  destDeptId: undefined,
  status: undefined,
  grantScope: undefined
})
const routeSort = reactive<{ sortBy?: string; sortOrder?: 'asc' | 'desc' }>({})

const transferTableRef = ref<InstanceType<typeof SmartTable>>()
const transferRows = ref<TransferRecord[]>([])
const transferTotal = ref(0)
const transferPage = ref(1)
const transferPageSize = ref(20)
const transferLoading = ref(false)
const transferApplyDeptId = ref<number>()
const transferApplyDeptName = ref('')
const selectedTransferRows = ref<TransferRecord[]>([])
const transferColumnState = reactive<Record<string, boolean>>(
  loadColumnState(TRANSFER_COLUMN_KEY, transferColumnOptions)
)
const transferFilters = reactive<{
  routeCode: string
  routeName: string
  regionCode: string
  regionLevel: 2 | 3 | 4 | undefined
  transferType: 1 | 2 | undefined
  status: boolean | undefined
  grantScope: 'ALL' | 'PARTIAL' | undefined
  updateByName: string
}>({
  routeCode: '',
  routeName: '',
  regionCode: '',
  regionLevel: undefined,
  transferType: undefined,
  status: undefined,
  grantScope: undefined,
  updateByName: ''
})
const transferSort = reactive<{ sortBy?: string; sortOrder?: 'asc' | 'desc' }>({})

const regionRows = ref<RegionLibraryRecord[]>([])
const regionLoading = ref(false)
const regionKeyword = ref('')
const regionLevel = ref<2 | 3 | 4 | undefined>()
const regionConfigured = ref<boolean>()
const selectedRegionCodes = ref<string[]>([])
const carrierOptions = ref<CarrierRecord[]>([])
const carrierBatch = reactive<{ visible: boolean; carrierId?: number; loading: boolean }>({
  visible: false,
  carrierId: undefined,
  loading: false
})

const routeDrawer = reactive<{ visible: boolean; mode: 'create' | 'edit'; record: RouteRecord | null }>({
  visible: false,
  mode: 'create',
  record: null
})

const transferDrawer = reactive<{
  visible: boolean
  record: TransferRecord | null
}>({
  visible: false,
  record: null
})

const grantDialog = reactive<{
  visible: boolean
  routeScope: 1 | 2
  route: { id: number; routeCode: string; routeName: string } | null
}>({
  visible: false,
  routeScope: 1,
  route: null
})

const batchDialog = reactive<{
  visible: boolean
  routeScope: 1 | 2
  routes: RouteRecord[]
}>({
  visible: false,
  routeScope: 1,
  routes: []
})

/* ---------------- 通用 ---------------- */

function routeRowIndex(index: number) {
  return (routePage.value - 1) * routePageSize.value + index + 1
}

function transferRowIndex(index: number) {
  return (transferPage.value - 1) * transferPageSize.value + index + 1
}

function formatNumber(value?: number | null) {
  if (value === undefined || value === null) return '—'
  return Number(value).toFixed(2)
}

function loadColumnState(key: string, options: Array<{ key: string; label: string; locked?: boolean }>) {
  const defaults: Record<string, boolean> = {}
  options.forEach((item) => {
    defaults[item.key] = true
  })
  try {
    const raw = localStorage.getItem(key)
    if (!raw) return defaults
    const saved = JSON.parse(raw) as Record<string, boolean>
    options.forEach((item) => {
      if (item.locked) return
      if (typeof saved[item.key] === 'boolean') defaults[item.key] = saved[item.key]
    })
  } catch {
    // 回退默认
  }
  return defaults
}

function persistColumnState(key: string, state: Record<string, boolean>) {
  try {
    localStorage.setItem(key, JSON.stringify(state))
  } catch {
    // 忽略
  }
}

function csvCell(value: string): string {
  return /[",\r\n]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value
}

function exportStamp(): string {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}`
}

function downloadCsv(name: string, header: string[], body: string[][]) {
  const csv = [header, ...body].map((line) => line.map(csvCell).join(',')).join('\r\n')
  const blob = new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${name}_${exportStamp()}.csv`
  link.click()
  URL.revokeObjectURL(url)
}

/* ---------------- Tab1 ---------------- */

function buildRouteQuery(pageNum = routePage.value, size = routePageSize.value): RouteQuery {
  return {
    routeCode: routeFilters.routeCode.trim() || undefined,
    routeName: routeFilters.routeName.trim() || undefined,
    routeCategory: routeFilters.routeCategory,
    originDeptId: routeFilters.originDeptId,
    destDeptId: routeFilters.destDeptId,
    status: routeFilters.status,
    grantScope: routeFilters.grantScope,
    sortBy: routeSort.sortBy,
    sortOrder: routeSort.sortOrder,
    pageNum,
    pageSize: size
  }
}

async function loadRoutes() {
  routeLoading.value = true
  try {
    const result = await getRoutePage(buildRouteQuery())
    routeRows.value = result.records
    routeTotal.value = result.total
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  } finally {
    routeLoading.value = false
  }
}

let routeTimer: ReturnType<typeof setTimeout> | undefined

function onRouteTextFilter() {
  if (routeTimer) clearTimeout(routeTimer)
  routeTimer = setTimeout(() => {
    routePage.value = 1
    loadRoutes()
  }, 300)
}

function onRouteFilterChange() {
  routePage.value = 1
  loadRoutes()
}

function handleRouteSort({ prop, order }: { prop?: string; order?: 'ascending' | 'descending' | null }) {
  if (!order || !prop) {
    routeSort.sortBy = undefined
    routeSort.sortOrder = undefined
  } else {
    routeSort.sortBy = prop
    routeSort.sortOrder = order === 'ascending' ? 'asc' : 'desc'
  }
  routePage.value = 1
  loadRoutes()
}

function handleRouteSizeChange() {
  routePage.value = 1
  loadRoutes()
}

function resetRouteFilters() {
  routeFilters.routeCode = ''
  routeFilters.routeName = ''
  routeFilters.routeCategory = undefined
  routeFilters.originDeptId = undefined
  routeFilters.destDeptId = undefined
  routeFilters.status = undefined
  routeFilters.grantScope = undefined
  routeSort.sortBy = undefined
  routeSort.sortOrder = undefined
  routeTableRef.value?.getTable()?.clearSort?.()
  routePage.value = 1
  loadRoutes()
}

function routeSummary({ columns }: { columns: Array<{ label: string }> }) {
  return columns.map((_, index) => (index === 0 ? '' : index === 1 ? `合计 ${routeTotal.value} 条路由` : ''))
}

const ROUTE_FILTER_RESET: Record<string, () => void> = {
  routeCode: () => (routeFilters.routeCode = ''),
  routeName: () => (routeFilters.routeName = ''),
  routeCategory: () => (routeFilters.routeCategory = undefined),
  originDeptName: () => (routeFilters.originDeptId = undefined),
  destDeptName: () => (routeFilters.destDeptId = undefined),
  status: () => (routeFilters.status = undefined),
  grantScope: () => (routeFilters.grantScope = undefined)
}

function onRouteColumnToggle(key: string, visible: boolean) {
  persistColumnState(ROUTE_COLUMN_KEY, routeColumnState)
  if (!visible && ROUTE_FILTER_RESET[key]) {
    ROUTE_FILTER_RESET[key]()
    if (routeSort.sortBy === key) {
      routeSort.sortBy = undefined
      routeSort.sortOrder = undefined
      routeTableRef.value?.getTable()?.clearSort?.()
    }
    routePage.value = 1
    loadRoutes()
  }
}

function resetRouteColumns() {
  routeColumnOptions.forEach((item) => {
    routeColumnState[item.key] = true
  })
  persistColumnState(ROUTE_COLUMN_KEY, routeColumnState)
  routeTableRef.value?.resetColumnWidths()
  ElMessage.success('已恢复默认列')
}

async function handleRouteExport() {
  const columns = routeColumnOptions.filter((item) => routeColumnState[item.key])
  try {
    const result = await getRoutePage(buildRouteQuery(1, 10000))
    const header = ['序号', ...columns.map((item) => item.label)]
    const body = result.records.map((row, index) => [
      String(index + 1),
      ...columns.map((item) => {
        const value = row[item.key as keyof RouteRecord]
        if (item.key === 'routeCategory') return row.routeCategory === 2 ? '干线' : '短驳'
        if (item.key === 'status') return row.status ? '启用' : '停用'
        if (item.key === 'grantScope') {
          return row.grantScope === 'ALL' ? '全部' : `${row.grantCount ?? 0} 家`
        }
        return value === undefined || value === null || value === '' ? '—' : String(value)
      })
    ])
    downloadCsv('路由管理_短驳干线', header, body)
    ElMessage.success(`已导出 ${result.total} 条`)
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  }
}

function openRouteCreate() {
  routeDrawer.mode = 'create'
  routeDrawer.record = null
  routeDrawer.visible = true
}

function openRouteEdit(row: RouteRecord) {
  routeDrawer.mode = 'edit'
  routeDrawer.record = row
  routeDrawer.visible = true
}

async function handleRouteDelete(row: RouteRecord) {
  try {
    await ElMessageBox.confirm(`确定取消路由「${row.routeName}」吗？`, '取消路由', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await deleteRoute(row.id)
    ElMessage.success('已取消路由')
    await loadRoutes()
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  }
}

/* ---------------- Tab2 ---------------- */

function buildTransferQuery(pageNum = transferPage.value, size = transferPageSize.value): TransferQuery {
  return {
    routeCode: transferFilters.routeCode.trim() || undefined,
    routeName: transferFilters.routeName.trim() || undefined,
    transferType: transferFilters.transferType,
    regionLevel: transferFilters.regionLevel,
    applyDeptId: transferApplyDeptId.value,
    regionCode: transferFilters.regionCode.trim() || undefined,
    status: transferFilters.status,
    grantScope: transferFilters.grantScope,
    updateByName: transferFilters.updateByName.trim() || undefined,
    sortBy: transferSort.sortBy,
    sortOrder: transferSort.sortOrder,
    pageNum,
    pageSize: size
  }
}

async function loadTransfers() {
  if (!transferApplyDeptId.value) {
    transferRows.value = []
    transferTotal.value = 0
    return
  }
  transferLoading.value = true
  try {
    const result = await getTransferPage(buildTransferQuery())
    transferRows.value = result.records
    transferTotal.value = result.total
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  } finally {
    transferLoading.value = false
  }
}

/* ---------------- Tab2 区划库与双栏 ---------------- */

function levelText(level?: number | null) {
  if (level === 2) return '市'
  if (level === 3) return '区县'
  if (level === 4) return '乡级'
  return '—'
}

function findDeptName(nodes: ManageScopeNode[], id: number): string {
  for (const node of nodes) {
    if (node.id === id) return node.deptName
    if (node.children?.length) {
      const found = findDeptName(node.children, id)
      if (found) return found
    }
  }
  return ''
}

async function handleApplyDeptChange() {
  transferApplyDeptName.value = transferApplyDeptId.value
    ? findDeptName(scopeTree.value, transferApplyDeptId.value)
    : ''
  selectedTransferRows.value = []
  selectedRegionCodes.value = []
  regionRows.value = []
  transferPage.value = 1
  await Promise.all([loadTransfers(), loadRegionLibrary()])
}

function regionSelectable(row: RegionLibraryRecord) {
  return !row.configured
}

function onRegionSelection(rows: RegionLibraryRecord[]) {
  selectedRegionCodes.value = rows.map((row) => row.code)
}

function onTransferSelection(rows: TransferRecord[]) {
  selectedTransferRows.value = rows
}

async function loadRegionLibrary() {
  if (!transferApplyDeptId.value) {
    regionRows.value = []
    return
  }
  if (!regionKeyword.value.trim() && !regionLevel.value) {
    regionRows.value = []
    return
  }
  regionLoading.value = true
  try {
    regionRows.value = await getRegionLibrary({
      applyDeptId: transferApplyDeptId.value,
      keyword: regionKeyword.value.trim() || undefined,
      level: regionLevel.value,
      configured: regionConfigured.value
    })
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  } finally {
    regionLoading.value = false
  }
}

async function handleJoinRegions() {
  if (!transferApplyDeptId.value || selectedRegionCodes.value.length === 0) return
  try {
    const result = await batchJoinTransfer({
      applyDeptId: transferApplyDeptId.value,
      regionCodes: selectedRegionCodes.value
    })
    ElMessage.success(result.message || '加入完成')
    selectedRegionCodes.value = []
    await Promise.all([loadTransfers(), loadRegionLibrary()])
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  }
}

async function handleRemoveSelected() {
  if (selectedTransferRows.value.length === 0) return
  try {
    await ElMessageBox.confirm(
      `确定移除选中的 ${selectedTransferRows.value.length} 条路由吗？被其他公司使用的路由不能移除。`,
      '移除路由',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  let failed = 0
  for (const row of selectedTransferRows.value) {
    try {
      await deleteTransfer(row.id)
    } catch {
      failed++
    }
  }
  if (failed) {
    ElMessage.warning(`${failed} 条路由因被其他公司使用未能移除`)
  } else {
    ElMessage.success('已移除所选路由')
  }
  selectedTransferRows.value = []
  await Promise.all([loadTransfers(), loadRegionLibrary()])
}

async function handleSwitchType(target: 1 | 2, row?: TransferRecord) {
  const ids = row ? [row.id] : selectedTransferRows.value.map((item) => item.id)
  if (ids.length === 0) return
  try {
    const result = await switchTransferType({ ids, transferType: target })
    ElMessage.success(result.message || '切换完成')
    await loadTransfers()
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  }
}

async function handleBatchStatus(status: boolean) {
  const ids = selectedTransferRows.value.map((item) => item.id)
  if (ids.length === 0) return
  try {
    const result = await batchTransferStatus({ ids, status })
    ElMessage.success(result.message || '操作完成')
    await loadTransfers()
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  }
}

async function openCarrierBatch() {
  if (selectedTransferRows.value.length === 0) return
  carrierBatch.carrierId = undefined
  carrierBatch.visible = true
  if (carrierOptions.value.length === 0) {
    try {
      const result = await getCarrierPage({ pageNum: 1, pageSize: 200, status: true })
      carrierOptions.value = result.records
    } catch (error) {
      const message = (error as { message?: string })?.message
      if (message) ElMessage.error(message)
    }
  }
}

async function submitCarrierBatch() {
  if (!carrierBatch.carrierId) {
    ElMessage.warning('请选择承运商')
    return
  }
  carrierBatch.loading = true
  try {
    const result = await batchTransferCarrier({
      ids: selectedTransferRows.value.map((item) => item.id),
      carrierId: carrierBatch.carrierId
    })
    ElMessage.success(result.message || '挂载完成')
    carrierBatch.visible = false
    await loadTransfers()
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  } finally {
    carrierBatch.loading = false
  }
}

let transferTimer: ReturnType<typeof setTimeout> | undefined

function onTransferTextFilter() {
  if (transferTimer) clearTimeout(transferTimer)
  transferTimer = setTimeout(() => {
    transferPage.value = 1
    loadTransfers()
  }, 300)
}

function onTransferFilterChange() {
  transferPage.value = 1
  loadTransfers()
}

function handleTransferSort({ prop, order }: { prop?: string; order?: 'ascending' | 'descending' | null }) {
  if (!order || !prop) {
    transferSort.sortBy = undefined
    transferSort.sortOrder = undefined
  } else {
    transferSort.sortBy = prop
    transferSort.sortOrder = order === 'ascending' ? 'asc' : 'desc'
  }
  transferPage.value = 1
  loadTransfers()
}

function handleTransferSizeChange() {
  transferPage.value = 1
  loadTransfers()
}

function resetTransferFilters() {
  transferFilters.routeCode = ''
  transferFilters.routeName = ''
  transferFilters.transferType = undefined
  transferFilters.regionLevel = undefined
  transferFilters.regionCode = ''
  transferFilters.status = undefined
  transferFilters.grantScope = undefined
  transferFilters.updateByName = ''
  transferSort.sortBy = undefined
  transferSort.sortOrder = undefined
  transferTableRef.value?.getTable()?.clearSort?.()
  transferPage.value = 1
  loadTransfers()
}

function transferSummary({ columns }: { columns: Array<{ label: string }> }) {
  return columns.map((_, index) => (index === 0 ? '' : index === 1 ? '' : index === 2 ? `合计 ${transferTotal.value} 条路由` : ''))
}

const TRANSFER_FILTER_RESET: Record<string, () => void> = {
  routeCode: () => (transferFilters.routeCode = ''),
  routeName: () => (transferFilters.routeName = ''),
  transferType: () => (transferFilters.transferType = undefined),
  regionLevel: () => (transferFilters.regionLevel = undefined),
  regionFullName: () => (transferFilters.regionCode = ''),
  status: () => (transferFilters.status = undefined),
  grantScope: () => (transferFilters.grantScope = undefined),
  updateByName: () => (transferFilters.updateByName = '')
}

function onTransferColumnToggle(key: string, visible: boolean) {
  persistColumnState(TRANSFER_COLUMN_KEY, transferColumnState)
  if (!visible && TRANSFER_FILTER_RESET[key]) {
    TRANSFER_FILTER_RESET[key]()
    if (transferSort.sortBy === key) {
      transferSort.sortBy = undefined
      transferSort.sortOrder = undefined
      transferTableRef.value?.getTable()?.clearSort?.()
    }
    transferPage.value = 1
    loadTransfers()
  }
}

function resetTransferColumns() {
  transferColumnOptions.forEach((item) => {
    transferColumnState[item.key] = true
  })
  persistColumnState(TRANSFER_COLUMN_KEY, transferColumnState)
  transferTableRef.value?.resetColumnWidths()
  ElMessage.success('已恢复默认列')
}

async function handleTransferExport() {
  const columns = transferColumnOptions.filter((item) => transferColumnState[item.key])
  try {
    const result = await getTransferPage(buildTransferQuery(1, 10000))
    const header = ['序号', ...columns.map((item) => item.label)]
    const body = result.records.map((row, index) => [
      String(index + 1),
      ...columns.map((item) => {
        if (item.key === 'transferType') return row.transferType === 1 ? '直达' : '外转'
        if (item.key === 'status') return row.status ? '启用' : '停用'
        if (item.key === 'regionLevel') return levelText(row.regionLevel)
        if (item.key === 'carrierName') return row.carrierName || (row.transferType === 2 ? '未配置' : '—')
        if (item.key === 'grantScope') {
          return row.grantScope === 'ALL' ? '全部' : `${row.grantCount ?? 0} 家`
        }
        const value = row[item.key as keyof TransferRecord]
        return value === undefined || value === null || value === '' ? '—' : String(value)
      })
    ])
    downloadCsv('路由管理_直达外转', header, body)
    ElMessage.success(`已导出 ${result.total} 条`)
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  }
}

function openTransferEdit(row: TransferRecord) {
  transferDrawer.record = row
  transferDrawer.visible = true
}

async function afterTransferSaved() {
  await Promise.all([loadTransfers(), loadRegionLibrary()])
}

async function handleTransferDelete(row: TransferRecord) {
  try {
    await ElMessageBox.confirm(`确定移除路由「${row.routeName}」吗？`, '移除路由', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await deleteTransfer(row.id)
    ElMessage.success('已移除路由')
    await Promise.all([loadTransfers(), loadRegionLibrary()])
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  }
}

/* ---------------- 授权 ---------------- */

function openGrant(tab: 'route' | 'transfer', row: RouteRecord | TransferRecord) {
  grantDialog.routeScope = tab === 'route' ? 1 : 2
  grantDialog.route = { id: row.id, routeCode: row.routeCode, routeName: row.routeName }
  grantDialog.visible = true
}

async function openBatchGrant(tab: 'route' | 'transfer') {
  batchDialog.routeScope = tab === 'route' ? 1 : 2
  try {
    if (tab === 'route') {
      const result = await getRoutePage(buildRouteQuery(1, 10000))
      batchDialog.routes = result.records
    } else {
      const result = await getTransferPage(buildTransferQuery(1, 10000))
      batchDialog.routes = result.records.map((item) => ({ ...item } as unknown as RouteRecord))
    }
    batchDialog.visible = true
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  }
}

function refreshCurrentTab() {
  if (activeTab.value === 'route') {
    loadRoutes()
  } else {
    loadTransfers()
  }
}

function handleTabChange() {
  if (activeTab.value === 'route' && routeRows.value.length === 0) {
    loadRoutes()
  }
}

async function loadScope() {
  try {
    const scope = await getManageScope([4, 5])
    isHq.value = scope.scopeType === 'HQ'
    scopeTree.value = [...scope.tree, ...scope.sharedLeaves]
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  }
}

onMounted(async () => {
  await loadScope()
  await loadRoutes()
})
</script>

<style scoped>
.route-page {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.route-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.route-tabs :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
}

.route-tabs :deep(.el-tab-pane) {
  height: 100%;
}

.tab-body {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding-top: 4px;
}

.table-wrap {
  flex: 1;
  min-height: 0;
}

.col-head {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: stretch;
}

.col-head__label {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-weight: var(--font-weight-medium);
}

.route-table :deep(.el-table__header th .cell) {
  overflow: visible;
  line-height: 1.4;
}

.mono {
  font-family: var(--font-family-number);
  font-size: var(--font-size-extra-small);
}

.row-actions {
  display: flex;
  justify-content: flex-end;
  flex-wrap: nowrap;
  gap: 2px;
  align-items: center;
}

.row-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.disabled-action {
  font-size: var(--font-size-extra-small);
  color: var(--color-text-placeholder);
  cursor: not-allowed;
  padding: 0 8px;
}

.column-setting {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.column-setting__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 6px;
  margin-bottom: 4px;
  font-size: var(--font-size-extra-small);
  color: var(--color-text-secondary);
  border-bottom: 1px solid var(--color-border-light);
}

.column-setting__item {
  height: 26px;
  margin-right: 0;
}

.table-pager {
  margin-left: 4px;
}

/* ---------------- Tab2 双栏 ---------------- */

.transfer-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 10px;
}

.transfer-toolbar__left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.transfer-toolbar__label {
  font-size: var(--font-size-small);
  color: var(--color-text-secondary);
  white-space: nowrap;
}

.transfer-toolbar__dept {
  width: 220px;
}

.transfer-toolbar__hint {
  font-size: var(--font-size-extra-small);
  color: var(--color-text-placeholder);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.transfer-toolbar__right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.transfer-columns {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 12px;
}

.transfer-pane {
  flex: 1 1 46%;
  min-width: 0;
  display: flex;
  flex-direction: column;
  padding: 10px;
  border: 1px solid var(--color-border-light);
  border-radius: 8px;
  background: var(--color-white);
}

.transfer-pane--routes {
  flex: 1 1 56%;
}

.pane-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding-bottom: 8px;
}

.pane-title {
  font-weight: var(--font-weight-medium);
  font-size: var(--font-size-small);
  color: var(--color-text-primary);
  white-space: nowrap;
}

.pane-sub {
  font-size: var(--font-size-extra-small);
  color: var(--color-text-placeholder);
  white-space: nowrap;
}

.pane-actions {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.library-filters {
  display: flex;
  gap: 8px;
  padding-bottom: 8px;
}

.library-filters__keyword {
  flex: 1;
}

.library-filters__select {
  width: 92px;
}

.pane-table {
  flex: 1;
  min-height: 0;
}

.transfer-join {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 10px;
  flex-shrink: 0;
}

.transfer-join .el-button {
  margin-left: 0;
}

.type-toggle {
  cursor: pointer;
}

.configured-tag {
  display: inline-block;
  padding: 0 6px;
  border-radius: 4px;
  font-size: var(--font-size-extra-small);
  color: var(--color-text-secondary);
  background: var(--color-bg-subtle);
}

.muted {
  color: var(--color-text-placeholder);
}

.dialog-hint {
  margin: 0 0 12px;
  font-size: var(--font-size-extra-small);
  color: var(--color-text-secondary);
}
</style>
