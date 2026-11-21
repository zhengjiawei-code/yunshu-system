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
                <el-popover placement="bottom-end" :width="440" trigger="click">
                  <template #reference>
                    <el-button :icon="Setting">列设置</el-button>
                  </template>
                  <column-setting
                    :schema="routeSchema"
                    :states="routeStates"
                    :loading="routeColumnLoading"
                    :saving="routeColumnSaving"
                    @update:states="onRouteStatesUpdate"
                    @save="saveRouteColumns"
                    @reset="resetRouteColumns"
                  />
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
                :column-order="routeColumnOrder"
                :column-hidden="routeHiddenKeys"
                :column-sortable="routeSortableMap"
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
                      <column-filter
                        v-if="routeCol('routeCode').filterType !== 'none'"
                        v-model="routeFilters.routeCode"
                        :type="routeCol('routeCode').filterType"
                        :options="routeCol('routeCode').filterOptions"
                        @change="onRouteTextFilter"
                      />
                    </div>
                  </template>
                  <template #default="{ row }"><span class="mono">{{ row.routeCode }}</span></template>
                </el-table-column>

                <el-table-column v-if="routeColumnState.routeName" prop="routeName" label="线路名称" min-width="200" sortable="custom">
                  <template #header>
                    <div class="col-head">
                      <span class="col-head__label">线路名称</span>
                      <column-filter
                        v-if="routeCol('routeName').filterType !== 'none'"
                        v-model="routeFilters.routeName"
                        :type="routeCol('routeName').filterType"
                        :options="routeCol('routeName').filterOptions"
                        @change="onRouteTextFilter"
                      />
                    </div>
                  </template>
                </el-table-column>

                <el-table-column v-if="routeColumnState.routeCategory" prop="routeCategory" label="类型" width="96" align="center" sortable="custom">
                  <template #header>
                    <div class="col-head">
                      <span class="col-head__label">类型</span>
                      <column-filter
                        v-if="routeCol('routeCategory').filterType !== 'none'"
                        v-model="routeFilters.routeCategory"
                        :type="routeCol('routeCategory').filterType"
                        :options="routeCol('routeCategory').filterOptions"
                        @change="onRouteFilterChange"
                      />
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
                      <column-filter
                        v-if="routeCol('originDeptName').filterType !== 'none'"
                        v-model="routeFilters.originDeptId"
                        :type="routeCol('originDeptName').filterType"
                        :options="deptFilterOptions"
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
                      <column-filter
                        v-if="routeCol('destDeptName').filterType !== 'none'"
                        v-model="routeFilters.destDeptId"
                        :type="routeCol('destDeptName').filterType"
                        :options="deptFilterOptions"
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
                      <column-filter
                        v-if="routeCol('status').filterType !== 'none'"
                        v-model="routeFilters.status"
                        :type="routeCol('status').filterType"
                        :options="routeCol('status').filterOptions"
                        @change="onRouteFilterChange"
                      />
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
                <el-popover placement="bottom-end" :width="440" trigger="click">
                  <template #reference>
                    <el-button :icon="Setting">列设置</el-button>
                  </template>
                  <column-setting
                    :schema="transferSchema"
                    :states="transferStates"
                    :loading="transferColumnLoading"
                    :saving="transferColumnSaving"
                    @update:states="onTransferStatesUpdate"
                    @save="saveTransferColumns"
                    @reset="resetTransferColumns"
                  />
                </el-popover>
              </div>
            </div>

            <div class="transfer-columns">
              <section class="transfer-pane">
                <div class="pane-header">
                  <span class="pane-title">区划库</span>
                  <div class="pane-header__actions">
                    <span class="pane-sub">共 {{ regionTotal }} 条</span>
                    <el-popover placement="bottom-end" :width="440" trigger="click">
                      <template #reference>
                        <el-button size="small" :icon="Setting">列设置</el-button>
                      </template>
                      <column-setting
                        :schema="regionSchema"
                        :states="regionStates"
                        :loading="regionColumnLoading"
                        :saving="regionColumnSaving"
                        @update:states="onRegionStatesUpdate"
                        @save="saveRegionColumns"
                        @reset="resetRegionColumns"
                      />
                    </el-popover>
                  </div>
                </div>
                <div class="pane-table">
                  <smart-table
                    ref="regionTableRef"
                    v-loading="regionLoading"
                    :data="regionRows"
                    row-key="code"
                    height="100%"
                    empty-text="请选择配置网点后查询区划"
                    show-overflow-tooltip
                    scrollbar-always-on
                    :column-order="regionColumnOrder"
                    :column-hidden="regionHiddenKeys"
                    :column-sortable="regionSortableMap"
                    class="region-table"
                    @sort-change="handleRegionSort"
                    @selection-change="onRegionSelection"
                  >
                    <el-table-column type="selection" width="40" :selectable="regionSelectable" />
                    <el-table-column v-if="regionVisible('level')" prop="level" label="级别" width="110" align="center">
                      <template #header>
                        <div class="col-head">
                          <span class="col-head__label">级别</span>
                          <column-filter
                            v-if="regionCol('level').filterType !== 'none'"
                            v-model="regionFilters.level"
                            :type="regionCol('level').filterType"
                            :options="regionCol('level').filterOptions"
                            @change="reloadRegionFromFirstPage"
                          />
                        </div>
                      </template>
                      <template #default="{ row }">
                        <status-tag type="info" :text="levelText(row.level)" />
                      </template>
                    </el-table-column>
                    <el-table-column v-if="regionVisible('provinceName')" prop="provinceName" label="省" min-width="120" show-overflow-tooltip>
                      <template #header>
                        <div class="col-head">
                          <span class="col-head__label">省</span>
                          <column-filter
                            v-if="regionCol('provinceName').filterType !== 'none'"
                            v-model="regionFilters.provinceCode"
                            :type="regionCol('provinceName').filterType"
                            :options="provinceFilterOptions"
                            @change="handleProvinceFilterChange"
                          />
                        </div>
                      </template>
                    </el-table-column>
                    <el-table-column v-if="regionVisible('cityName')" prop="cityName" label="市" min-width="120" show-overflow-tooltip>
                      <template #header>
                        <div class="col-head">
                          <span class="col-head__label">市</span>
                          <column-filter
                            v-if="regionCol('cityName').filterType !== 'none'"
                            v-model="regionFilters.cityCode"
                            :type="regionCol('cityName').filterType"
                            :options="cityFilterOptions"
                            @change="reloadRegionFromFirstPage"
                          />
                        </div>
                      </template>
                    </el-table-column>
                    <el-table-column v-if="regionVisible('districtName')" prop="districtName" label="区县" min-width="120" show-overflow-tooltip>
                      <template #header>
                        <div class="col-head">
                          <span class="col-head__label">区县</span>
                          <column-filter
                            v-if="regionCol('districtName').filterType !== 'none'"
                            v-model="regionFilters.districtKeyword"
                            :type="regionCol('districtName').filterType"
                            :options="regionCol('districtName').filterOptions"
                            @change="onRegionFilterInput"
                          />
                        </div>
                      </template>
                    </el-table-column>
                    <el-table-column v-if="regionVisible('townName')" label="乡级" min-width="130" show-overflow-tooltip>
                      <template #header>
                        <div class="col-head">
                          <span class="col-head__label">乡级</span>
                          <column-filter
                            v-if="regionCol('townName').filterType !== 'none'"
                            v-model="regionFilters.townKeyword"
                            :type="regionCol('townName').filterType"
                            :options="regionCol('townName').filterOptions"
                            @change="onRegionFilterInput"
                          />
                        </div>
                      </template>
                      <template #default="{ row }">{{ row.level === 4 ? row.name : '—' }}</template>
                    </el-table-column>
                    <el-table-column v-if="regionVisible('configured')" label="配置" width="110" align="center">
                      <template #header>
                        <div class="col-head">
                          <span class="col-head__label">配置</span>
                          <column-filter
                            v-if="regionCol('configured').filterType !== 'none'"
                            v-model="regionFilters.configured"
                            :type="regionCol('configured').filterType"
                            :options="regionCol('configured').filterOptions"
                            @change="reloadRegionFromFirstPage"
                          />
                        </div>
                      </template>
                      <template #default="{ row }">
                        <span v-if="row.configured" class="configured-tag">已配置</span>
                        <span v-else class="muted">未配置</span>
                      </template>
                    </el-table-column>
                  </smart-table>
                </div>
                <div class="pane-footer">
                  <el-pagination
                    v-model:current-page="regionPage"
                    v-model:page-size="regionPageSize"
                    class="table-pager"
                    size="small"
                    background
                    :total="regionTotal"
                    :pager-count="5"
                    :page-sizes="[20, 50, 100]"
                    layout="sizes, prev, pager, next"
                    @current-change="loadRegionLibrary"
                    @size-change="handleRegionSizeChange"
                  />
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
                  <div class="pane-title-group">
                    <span class="pane-title">
                      已配置路由{{ transferApplyDeptName ? `（${transferApplyDeptName}）` : '' }}
                    </span>
                    <span class="pane-sub pane-sub--total">共 {{ transferTotal }} 条</span>
                  </div>
                  <div class="pane-actions">
                    <el-dropdown trigger="click" :disabled="!selectedTransferRows.length" @command="handleSwitchTypeCommand">
                      <el-button size="small" :disabled="!selectedTransferRows.length">切换类型</el-button>
                      <template #dropdown>
                        <el-dropdown-menu>
                          <el-dropdown-item :command="1">设为直达</el-dropdown-item>
                          <el-dropdown-item :command="2">设为外转</el-dropdown-item>
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
                    <el-button size="small" :disabled="!selectedTransferRows.length" @click="openCarrierBatch">
                      挂承运商
                    </el-button>
                    <el-dropdown trigger="click" :disabled="!selectedTransferRows.length" @command="handleStatusCommand">
                      <el-button size="small" :disabled="!selectedTransferRows.length">状态</el-button>
                      <template #dropdown>
                        <el-dropdown-menu>
                          <el-dropdown-item :command="true">启用</el-dropdown-item>
                          <el-dropdown-item :command="false">停用</el-dropdown-item>
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
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
                    :column-order="transferColumnOrder"
                    :column-hidden="transferHiddenKeys"
                    :column-sortable="transferSortableMap"
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
                          <column-filter
                            v-if="col('routeCode').filterType !== 'none'"
                            v-model="transferFilters.routeCode"
                            :type="col('routeCode').filterType"
                            :options="col('routeCode').filterOptions"
                            @change="onTransferTextFilter"
                          />
                        </div>
                      </template>
                      <template #default="{ row }"><span class="mono">{{ row.routeCode }}</span></template>
                    </el-table-column>

                    <el-table-column v-if="transferColumnState.routeName" prop="routeName" label="路由名称" min-width="170" sortable="custom">
                      <template #header>
                        <div class="col-head">
                          <span class="col-head__label">路由名称</span>
                          <column-filter
                            v-if="col('routeName').filterType !== 'none'"
                            v-model="transferFilters.routeName"
                            :type="col('routeName').filterType"
                            :options="col('routeName').filterOptions"
                            @change="onTransferTextFilter"
                          />
                        </div>
                      </template>
                    </el-table-column>

                    <el-table-column v-if="transferColumnState.regionFullName" prop="regionFullName" label="目的地" min-width="180" sortable="custom">
                      <template #header>
                        <div class="col-head">
                          <span class="col-head__label">目的地</span>
                          <column-filter
                            v-if="col('regionFullName').filterType !== 'none'"
                            v-model="transferFilters.regionCode"
                            :type="col('regionFullName').filterType"
                            :options="col('regionFullName').filterOptions"
                            placeholder="编码/名称"
                            @change="onTransferTextFilter"
                          />
                        </div>
                      </template>
                    </el-table-column>

                    <el-table-column v-if="transferColumnState.regionLevel" prop="regionLevel" label="级别" width="96" align="center" sortable="custom">
                      <template #header>
                        <div class="col-head">
                          <span class="col-head__label">级别</span>
                          <column-filter
                            v-if="col('regionLevel').filterType !== 'none'"
                            v-model="transferFilters.regionLevel"
                            :type="col('regionLevel').filterType"
                            :options="col('regionLevel').filterOptions"
                            @change="onTransferFilterChange"
                          />
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
                          <column-filter
                            v-if="col('transferType').filterType !== 'none'"
                            v-model="transferFilters.transferType"
                            :type="col('transferType').filterType"
                            :options="col('transferType').filterOptions"
                            @change="onTransferFilterChange"
                          />
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
                      <template #header>
                        <div class="col-head">
                          <span class="col-head__label">承运商</span>
                          <column-filter
                            v-if="col('carrierName').filterType !== 'none'"
                            v-model="transferFilters.carrierId"
                            :type="col('carrierName').filterType"
                            :options="carrierFilterOptions"
                            @change="onTransferFilterChange"
                          />
                        </div>
                      </template>
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
                          <column-filter
                            v-if="col('status').filterType !== 'none'"
                            v-model="transferFilters.status"
                            :type="col('status').filterType"
                            :options="col('status').filterOptions"
                            @change="onTransferFilterChange"
                          />
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
                          <column-filter
                            v-if="col('updateByName').filterType !== 'none'"
                            v-model="transferFilters.updateByName"
                            :type="col('updateByName').filterType"
                            :options="col('updateByName').filterOptions"
                            @change="onTransferTextFilter"
                          />
                        </div>
                      </template>
                      <template #default="{ row }">{{ row.updateByName || '—' }}</template>
                    </el-table-column>

                    <el-table-column v-if="transferColumnState.updateTime" prop="updateTime" label="修改时间" min-width="170" align="center" sortable="custom" />

                    <el-table-column v-if="isHq && transferColumnState.grantScope" prop="grantScope" label="授权范围" width="110" align="center" sortable="custom">
                      <template #header>
                        <div class="col-head">
                          <span class="col-head__label">授权范围</span>
                          <column-filter
                            v-if="col('grantScope').filterType !== 'none'"
                            v-model="transferFilters.grantScope"
                            :type="col('grantScope').filterType"
                            :options="col('grantScope').filterOptions"
                            @change="onTransferFilterChange"
                          />
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
                <div class="pane-footer">
                  <el-pagination
                    v-model:current-page="transferPage"
                    v-model:page-size="transferPageSize"
                    class="table-pager"
                    size="small"
                    background
                    :total="transferTotal"
                    :pager-count="5"
                    :page-sizes="[20, 50, 100]"
                    layout="sizes, prev, pager, next"
                    @current-change="loadTransfers"
                    @size-change="handleTransferSizeChange"
                  />
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
import { computed, onMounted, reactive, ref } from 'vue'
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
import { getRegionChildren } from '@/api/system/region'
import type { ColumnDef, ColumnItemState } from '@/composables/useColumnConfig'
import { useColumnConfig } from '@/composables/useColumnConfig'
import ColumnFilter from '@/components/base/ColumnFilter.vue'
import ColumnSetting from '@/components/base/ColumnSetting.vue'
import StatusTag from '@/components/base/StatusTag.vue'
import SmartTable from '@/components/base/SmartTable.vue'
import RouteFormDrawer from './components/RouteFormDrawer.vue'
import TransferFormDrawer from './components/TransferFormDrawer.vue'
import RouteGrantDialog from './components/RouteGrantDialog.vue'
import RouteBatchGrantDialog from './components/RouteBatchGrantDialog.vue'

defineOptions({ name: 'SystemRoute' })

const routeSchema = reactive<ColumnDef[]>([
  { key: 'routeCode', label: '路由编码', prop: 'routeCode', filterType: 'text', locked: true },
  { key: 'routeName', label: '线路名称', prop: 'routeName', filterType: 'text' },
  {
    key: 'routeCategory',
    label: '类型',
    prop: 'routeCategory',
    filterType: 'select',
    filterOptions: [
      { label: '短驳', value: 1 },
      { label: '干线', value: 2 }
    ]
  },
  { key: 'originDeptName', label: '始发网点', prop: 'originDeptName', filterType: 'select', filterOptions: [] },
  { key: 'destDeptName', label: '到达网点', prop: 'destDeptName', filterType: 'select', filterOptions: [] },
  // 以下字段后端未提供对应筛选参数，保持无筛选，避免出现无效控件
  { key: 'warehouseArea', label: '库区', prop: 'warehouseArea', filterType: 'none' },
  { key: 'mileage', label: '里程', prop: 'mileage', filterType: 'none', align: 'right' },
  { key: 'transitHours', label: '时效', prop: 'transitHours', filterType: 'none', align: 'right' },
  { key: 'contactName', label: '线路联系人', prop: 'contactName', filterType: 'none' },
  { key: 'contactPhone', label: '联系电话', prop: 'contactPhone', filterType: 'none' },
  { key: 'serviceName', label: '线路客服', prop: 'serviceName', filterType: 'none' },
  { key: 'servicePhone', label: '客服电话', prop: 'servicePhone', filterType: 'none' },
  { key: 'ownerCompanyName', label: '创建公司', prop: 'ownerCompanyName', filterType: 'none' },
  {
    key: 'status',
    label: '状态',
    prop: 'status',
    filterType: 'select',
    filterOptions: [
      { label: '启用', value: true },
      { label: '停用', value: false }
    ]
  },
  { key: 'sortOrder', label: '排序号', prop: 'sortOrder', filterType: 'none', align: 'right' },
  { key: 'updateTime', label: '更新时间', prop: 'updateTime', filterType: 'none', align: 'center' },
  {
    key: 'operation',
    label: '操作',
    elKey: 'label:操作',
    filterType: 'none',
    sortable: false,
    locked: true
  }
])

const transferSchema = reactive<ColumnDef[]>([
  { key: 'routeCode', label: '路由编码', prop: 'routeCode', filterType: 'text', locked: true },
  { key: 'routeName', label: '路由名称', prop: 'routeName', filterType: 'text' },
  { key: 'regionFullName', label: '目的地', prop: 'regionFullName', filterType: 'text' },
  {
    key: 'regionLevel',
    label: '级别',
    prop: 'regionLevel',
    filterType: 'select',
    filterOptions: [
      { label: '市', value: 2 },
      { label: '区县', value: 3 },
      { label: '乡级', value: 4 }
    ]
  },
  {
    key: 'transferType',
    label: '类型',
    prop: 'transferType',
    filterType: 'select',
    filterOptions: [
      { label: '直达', value: 1 },
      { label: '外转', value: 2 }
    ]
  },
  { key: 'carrierName', label: '承运商', prop: 'carrierName', filterType: 'select', filterOptions: [] },
  {
    key: 'status',
    label: '状态',
    prop: 'status',
    filterType: 'select',
    filterOptions: [
      { label: '启用', value: true },
      { label: '停用', value: false }
    ]
  },
  { key: 'sortOrder', label: '排序', prop: 'sortOrder', filterType: 'none', align: 'right' },
  { key: 'updateByName', label: '修改人', prop: 'updateByName', filterType: 'text' },
  { key: 'updateTime', label: '修改时间', prop: 'updateTime', filterType: 'none', align: 'center' },
  {
    key: 'grantScope',
    label: '授权范围',
    prop: 'grantScope',
    filterType: 'select',
    filterOptions: [
      { label: '全部', value: 'ALL' },
      { label: '部分', value: 'PARTIAL' }
    ]
  },
  { key: 'ownerCompanyName', label: '创建公司', prop: 'ownerCompanyName', filterType: 'none' },
  {
    key: 'operation',
    label: '操作',
    elKey: 'label:操作',
    filterType: 'none',
    sortable: false,
    locked: true
  }
])

const regionSchema = reactive<ColumnDef[]>([
  {
    key: 'level',
    label: '级别',
    prop: 'level',
    filterType: 'select',
    sortable: true,
    filterOptions: [
      { label: '市', value: 2 },
      { label: '区县', value: 3 },
      { label: '乡级', value: 4 }
    ]
  },
  { key: 'provinceName', label: '省', prop: 'provinceName', filterType: 'select', sortable: false, filterOptions: [] },
  { key: 'cityName', label: '市', prop: 'cityName', filterType: 'select', sortable: false, filterOptions: [] },
  { key: 'districtName', label: '区县', prop: 'districtName', filterType: 'text', sortable: false },
  { key: 'townName', label: '乡级', elKey: 'label:乡级', filterType: 'text', sortable: false },
  {
    key: 'configured',
    label: '配置',
    elKey: 'label:配置',
    filterType: 'select',
    sortable: false,
    filterOptions: [
      { label: '已配置', value: true },
      { label: '未配置', value: false }
    ]
  }
])

const {
  states: routeStates,
  loading: routeColumnLoading,
  saving: routeColumnSaving,
  col: routeCol,
  columnOrder: routeColumnOrder,
  hiddenKeys: routeHiddenKeys,
  sortableMap: routeSortableMap,
  load: loadRouteColumnConfig,
  save: saveRouteColumnConfig,
  reset: resetRouteColumnConfig,
  defaultFilters: routeDefaultFilters
} = useColumnConfig('system:route', routeSchema)

const routeColumnState = computed(
  () =>
    Object.fromEntries(routeStates.value.map((item) => [item.key, item.visible])) as Record<
      string,
      boolean
    >
)

const {
  states: transferStates,
  loading: transferColumnLoading,
  saving: transferColumnSaving,
  col,
  columnOrder: transferColumnOrder,
  hiddenKeys: transferHiddenKeys,
  sortableMap: transferSortableMap,
  load: loadTransferColumnConfig,
  save: saveTransferColumnConfig,
  reset: resetTransferColumnConfig,
  defaultFilters: transferDefaultFilters
} = useColumnConfig('system:route-transfer', transferSchema)

const transferColumnState = computed(
  () =>
    Object.fromEntries(transferStates.value.map((item) => [item.key, item.visible])) as Record<
      string,
      boolean
    >
)

const {
  states: regionStates,
  loading: regionColumnLoading,
  saving: regionColumnSaving,
  col: regionCol,
  isVisible: regionVisible,
  columnOrder: regionColumnOrder,
  hiddenKeys: regionHiddenKeys,
  sortableMap: regionSortableMap,
  load: loadRegionColumnConfig,
  save: saveRegionColumnConfig,
  reset: resetRegionColumnConfig
} = useColumnConfig('system:route-library', regionSchema)

const activeTab = ref<'route' | 'transfer'>('route')
const isHq = ref(false)
const scopeTree = ref<ManageScopeNode[]>([])

const routeTableRef = ref<InstanceType<typeof SmartTable>>()
const routeRows = ref<RouteRecord[]>([])
const routeTotal = ref(0)
const routePage = ref(1)
const routePageSize = ref(20)
const routeLoading = ref(false)
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
const transferFilters = reactive<{
  routeCode: string
  routeName: string
  regionCode: string
  regionLevel: 2 | 3 | 4 | undefined
  transferType: 1 | 2 | undefined
  carrierId: number | undefined
  status: boolean | undefined
  grantScope: 'ALL' | 'PARTIAL' | undefined
  updateByName: string
}>({
  routeCode: '',
  routeName: '',
  regionCode: '',
  regionLevel: undefined,
  transferType: undefined,
  carrierId: undefined,
  status: undefined,
  grantScope: undefined,
  updateByName: ''
})
const transferSort = reactive<{ sortBy?: string; sortOrder?: 'asc' | 'desc' }>({})

const regionRows = ref<RegionLibraryRecord[]>([])
const regionTableRef = ref<InstanceType<typeof SmartTable>>()
const regionTotal = ref(0)
const regionPage = ref(1)
const regionPageSize = ref(20)
const regionLoading = ref(false)
const regionSort = reactive<{ sortBy?: string; sortOrder?: 'asc' | 'desc' }>({})
const regionFilters = reactive<{
  level: 2 | 3 | 4 | undefined
  provinceCode: string | undefined
  cityCode: string | undefined
  districtKeyword: string
  townKeyword: string
  configured: boolean | undefined
}>({
  level: undefined,
  provinceCode: undefined,
  cityCode: undefined,
  districtKeyword: '',
  townKeyword: '',
  configured: undefined
})
const provinceFilterOptions = ref<Array<{ label: string; value: string }>>([])
const cityFilterOptions = ref<Array<{ label: string; value: string }>>([])
const selectedRegionCodes = ref<string[]>([])
const carrierOptions = ref<CarrierRecord[]>([])
const deptFilterOptions = computed(() => flattenDeptOptions(scopeTree.value))
const carrierFilterOptions = computed(() =>
  carrierOptions.value.map((item) => ({ label: item.carrierName, value: item.id }))
)

async function loadCarrierFilterOptions() {
  if (carrierOptions.value.length > 0) return
  try {
    const result = await getCarrierPage({ pageNum: 1, pageSize: 200, status: true })
    carrierOptions.value = result.records
    const column = transferSchema.find((item) => item.key === 'carrierName')
    if (column) column.filterOptions = carrierFilterOptions.value
  } catch {
    // 承运商选项失败不阻断路由列表
  }
}
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

function flattenDeptOptions(nodes: ManageScopeNode[]): Array<{ label: string; value: number }> {
  const result: Array<{ label: string; value: number }> = []
  const walk = (list: ManageScopeNode[]) => {
    list.forEach((node) => {
      result.push({ label: node.deptName, value: node.id })
      if (node.children?.length) walk(node.children)
    })
  }
  walk(nodes)
  return result
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
    routeCode: routeColumnState.value.routeCode
      ? routeFilters.routeCode.trim() || undefined
      : undefined,
    routeName: routeColumnState.value.routeName
      ? routeFilters.routeName.trim() || undefined
      : undefined,
    routeCategory: routeColumnState.value.routeCategory ? routeFilters.routeCategory : undefined,
    originDeptId: routeColumnState.value.originDeptName ? routeFilters.originDeptId : undefined,
    destDeptId: routeColumnState.value.destDeptName ? routeFilters.destDeptId : undefined,
    status: routeColumnState.value.status ? routeFilters.status : undefined,
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

/* ---------- Tab1 统一列设置 ---------- */
function onRouteStatesUpdate(next: ColumnItemState[]) {
  routeStates.value = next
}

async function saveRouteColumns() {
  await saveRouteColumnConfig()
}

async function resetRouteColumns() {
  await resetRouteColumnConfig()
  routeTableRef.value?.resetColumnWidths()
  routePage.value = 1
  loadRoutes()
}

function applyRouteDefaultFilters() {
  const defaults = routeDefaultFilters()
  if (defaults.routeCode != null) routeFilters.routeCode = String(defaults.routeCode)
  if (defaults.routeName != null) routeFilters.routeName = String(defaults.routeName)
  if (defaults.routeCategory === 1 || defaults.routeCategory === 2) {
    routeFilters.routeCategory = defaults.routeCategory
  }
  if (defaults.originDeptName != null) routeFilters.originDeptId = Number(defaults.originDeptName)
  if (defaults.destDeptName != null) routeFilters.destDeptId = Number(defaults.destDeptName)
  if (typeof defaults.status === 'boolean') routeFilters.status = defaults.status
}

async function handleRouteExport() {
  const columns = routeSchema.filter(
    (item) => item.key !== 'operation' && routeColumnState.value[item.key]
  )
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
    routeCode: transferColumnState.value.routeCode
      ? transferFilters.routeCode.trim() || undefined
      : undefined,
    routeName: transferColumnState.value.routeName
      ? transferFilters.routeName.trim() || undefined
      : undefined,
    transferType: transferColumnState.value.transferType ? transferFilters.transferType : undefined,
    regionLevel: transferColumnState.value.regionLevel ? transferFilters.regionLevel : undefined,
    applyDeptId: transferApplyDeptId.value,
    regionCode: transferColumnState.value.regionFullName
      ? transferFilters.regionCode.trim() || undefined
      : undefined,
    carrierId: transferColumnState.value.carrierName ? transferFilters.carrierId : undefined,
    status: transferColumnState.value.status ? transferFilters.status : undefined,
    grantScope: transferColumnState.value.grantScope ? transferFilters.grantScope : undefined,
    updateByName: transferColumnState.value.updateByName
      ? transferFilters.updateByName.trim() || undefined
      : undefined,
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
  regionPage.value = 1
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

function hasRegionLibraryFilter(): boolean {
  return !!(
    regionFilters.level ||
    regionFilters.provinceCode ||
    regionFilters.cityCode ||
    regionFilters.districtKeyword.trim() ||
    regionFilters.townKeyword.trim() ||
    regionFilters.configured !== undefined
  )
}

async function loadRegionLibrary() {
  if (!transferApplyDeptId.value) {
    regionRows.value = []
    regionTotal.value = 0
    return
  }
  if (!hasRegionLibraryFilter()) {
    regionRows.value = []
    regionTotal.value = 0
    return
  }
  regionLoading.value = true
  try {
    const result = await getRegionLibrary({
      applyDeptId: transferApplyDeptId.value,
      level: regionFilters.level,
      configured: regionFilters.configured,
      provinceCode: regionFilters.provinceCode,
      cityCode: regionFilters.cityCode,
      districtKeyword: regionFilters.districtKeyword.trim() || undefined,
      townKeyword: regionFilters.townKeyword.trim() || undefined,
      sortBy: regionSort.sortBy,
      sortOrder: regionSort.sortOrder,
      pageNum: regionPage.value,
      pageSize: regionPageSize.value
    })
    regionRows.value = result.records
    regionTotal.value = result.total
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  } finally {
    regionLoading.value = false
  }
}

/** 筛选/排序/网点变化统一回到第 1 页 */
function reloadRegionFromFirstPage() {
  regionPage.value = 1
  void loadRegionLibrary()
}

function handleRegionSizeChange() {
  regionPage.value = 1
  void loadRegionLibrary()
}

function handleRegionSort({ prop, order }: { prop: string; order: 'ascending' | 'descending' | null }) {
  regionSort.sortBy = order ? prop : undefined
  regionSort.sortOrder = order === 'ascending' ? 'asc' : order === 'descending' ? 'desc' : undefined
  reloadRegionFromFirstPage()
}

let regionFilterTimer: number | undefined

function onRegionFilterInput() {
  window.clearTimeout(regionFilterTimer)
  regionFilterTimer = window.setTimeout(() => {
    reloadRegionFromFirstPage()
  }, 300)
}

async function loadProvinceFilterOptions() {
  try {
    const provinces = await getRegionChildren('0')
    provinceFilterOptions.value = provinces.map((item) => ({ label: item.name, value: item.code }))
    const provinceColumn = regionSchema.find((item) => item.key === 'provinceName')
    if (provinceColumn) provinceColumn.filterOptions = provinceFilterOptions.value
  } catch {
    // 省级选项失败不影响主流程
  }
}

async function handleProvinceFilterChange() {
  regionFilters.cityCode = undefined
  cityFilterOptions.value = []
  if (regionFilters.provinceCode) {
    try {
      const cities = await getRegionChildren(regionFilters.provinceCode)
      cityFilterOptions.value = cities.map((item) => ({ label: item.name, value: item.code }))
    } catch {
      // 市级选项失败不阻断查询
    }
  }
  const cityColumn = regionSchema.find((item) => item.key === 'cityName')
  if (cityColumn) cityColumn.filterOptions = cityFilterOptions.value
  reloadRegionFromFirstPage()
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

function handleSwitchTypeCommand(command: number) {
  void handleSwitchType(command as 1 | 2)
}

function handleStatusCommand(command: boolean) {
  void handleBatchStatus(command)
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

  regionFilters.level = undefined
  regionFilters.provinceCode = undefined
  regionFilters.cityCode = undefined
  regionFilters.districtKeyword = ''
  regionFilters.townKeyword = ''
  regionFilters.configured = undefined
  regionSort.sortBy = undefined
  regionSort.sortOrder = undefined
  regionTableRef.value?.getTable()?.clearSort?.()

  transferPage.value = 1
  regionPage.value = 1
  void loadTransfers()
  void loadRegionLibrary()
}

/* ---------- Tab2 统一列设置 ---------- */
function onTransferStatesUpdate(next: ColumnItemState[]) {
  transferStates.value = next
}

async function saveTransferColumns() {
  await saveTransferColumnConfig()
}

async function resetTransferColumns() {
  await resetTransferColumnConfig()
  transferTableRef.value?.resetColumnWidths()
  transferPage.value = 1
  loadTransfers()
}

function applyTransferDefaultFilters() {
  const defaults = transferDefaultFilters()
  if (defaults.routeCode != null) transferFilters.routeCode = String(defaults.routeCode)
  if (defaults.routeName != null) transferFilters.routeName = String(defaults.routeName)
  if (defaults.regionFullName != null) transferFilters.regionCode = String(defaults.regionFullName)
  if (defaults.regionLevel === 2 || defaults.regionLevel === 3 || defaults.regionLevel === 4) {
    transferFilters.regionLevel = defaults.regionLevel
  }
  if (defaults.transferType === 1 || defaults.transferType === 2) {
    transferFilters.transferType = defaults.transferType
  }
  if (defaults.carrierName != null) transferFilters.carrierId = Number(defaults.carrierName)
  if (typeof defaults.status === 'boolean') transferFilters.status = defaults.status
  if (defaults.grantScope === 'ALL' || defaults.grantScope === 'PARTIAL') {
    transferFilters.grantScope = defaults.grantScope
  }
  if (defaults.updateByName != null) transferFilters.updateByName = String(defaults.updateByName)
}

function onRegionStatesUpdate(next: ColumnItemState[]) {
  regionStates.value = next
}

async function saveRegionColumns() {
  await saveRegionColumnConfig()
}

async function resetRegionColumns() {
  await resetRegionColumnConfig()
}

async function handleTransferExport() {
  const columns = transferSchema.filter(
    (item) => item.key !== 'operation' && transferColumnState.value[item.key]
  )
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
  await Promise.all([
    loadScope(),
    loadRouteColumnConfig(),
    loadTransferColumnConfig(),
    loadRegionColumnConfig(),
    loadProvinceFilterOptions(),
    loadCarrierFilterOptions()
  ])
  applyRouteDefaultFilters()
  const originColumn = routeSchema.find((item) => item.key === 'originDeptName')
  if (originColumn) originColumn.filterOptions = deptFilterOptions.value
  const destColumn = routeSchema.find((item) => item.key === 'destDeptName')
  if (destColumn) destColumn.filterOptions = deptFilterOptions.value
  const carrierColumn = transferSchema.find((item) => item.key === 'carrierName')
  if (carrierColumn) carrierColumn.filterOptions = carrierFilterOptions.value
  applyTransferDefaultFilters()
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
  line-height: 18px;
}

/* 两栏筛选控件统一 24px 高，避免左 select / 右 input 高度差导致表头行高不一致 */
.pane-table :deep(.col-head .el-input),
.pane-table :deep(.col-head .el-select),
.pane-table :deep(.col-head .el-date-editor),
.pane-table :deep(.col-head .number-range) {
  height: 24px;
  line-height: 24px;
}

.pane-table :deep(.col-head .el-input__wrapper),
.pane-table :deep(.col-head .el-select__wrapper) {
  min-height: 24px;
  height: 24px;
}

.pane-table :deep(.el-table__header th .cell) {
  overflow: visible;
  line-height: 1.4;
}

/* 空态文案统一固定两行高度并居中，窄屏换行时两栏垂直位置仍一致 */
.pane-table :deep(.el-table__empty-text) {
  max-width: 80%;
  min-height: 44px;
  line-height: 22px;
  white-space: normal;
  text-align: center;
  display: flex;
  align-items: center;
  justify-content: center;
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
  /* 两栏等宽：flex-basis 0 + flex-grow 1，可用宽度扣掉中间操作列与间距后 1:1 均分 */
  flex: 1 1 0;
  min-width: 0;
  /* flex 子项默认 min-height:auto 会让表格撑开容器、导致内容被裁切且不出现内部滚动条 */
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  padding: 10px;
  border: 1px solid var(--color-border-light);
  border-radius: 8px;
  background: var(--color-white);
}

.pane-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding-bottom: 8px;
  height: 40px;
  min-height: 40px;
  box-sizing: border-box;
  overflow: hidden;
}

.pane-header__actions {
  display: flex;
  align-items: center;
  gap: 8px;
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

.pane-title-group {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 0 1 auto;
}

.pane-title-group .pane-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pane-sub--total {
  flex-shrink: 0;
}

.pane-actions {
  display: flex;
  gap: 4px;
  flex-wrap: nowrap;
  justify-content: flex-end;
  overflow-x: auto;
  scrollbar-width: none;
}

.pane-actions::-webkit-scrollbar {
  display: none;
}

.pane-table {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

/* 两栏底部同规格分页区：固定高度，保证分页与滚动条底部 y 坐标一致 */
.pane-footer {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  min-height: 32px;
  padding-top: 8px;
}

.pane-footer .table-pager {
  margin: 0;
}

/* 双栏滚动条：横向常显 10px，纵向独立滚动；两栏共用同一尺寸，出现/消失不改变列宽 */
.pane-table :deep(.el-scrollbar__bar.is-horizontal) {
  height: 10px;
}

.pane-table :deep(.el-scrollbar__bar.is-vertical) {
  width: 10px;
}

.pane-table :deep(.el-scrollbar__bar) {
  opacity: 1;
}

.pane-table :deep(.el-scrollbar__thumb) {
  background-color: var(--color-text-placeholder);
  opacity: 0.55;
  border-radius: 5px;
}

.pane-table :deep(.el-scrollbar__thumb:hover) {
  opacity: 0.85;
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
