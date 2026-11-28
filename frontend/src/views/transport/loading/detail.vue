<template>
  <div class="page-container">
    <div class="page-content trip-detail">
      <!-- Tab 条 -->
      <div class="detail-tabs">
        <div class="detail-tab" :class="{ 'is-on': activeTab === 'load' }" @click="switchTab('load')">
          装车配载
          <span class="tab-badge">{{ loadedSummary.unitCount }} 票</span>
        </div>
        <div class="detail-tab" :class="{ 'is-on': activeTab === 'send' }" @click="switchTab('send')">
          发车
          <span class="tab-badge">{{ editable ? '待发车' : '已发车' }}</span>
        </div>
      </div>

      <!-- Tab1 顶部信息条 -->
      <div v-if="activeTab === 'load'" class="info-card">
        <div class="info-row1">
          <span class="trip-no">{{ trip?.tripNo || '—' }}</span>
          <status-tag :type="tripStatusTagType(trip?.tripStatus)" :text="tripStatusText(trip?.tripStatus)" />
          <span class="category-tag">{{ tripCategoryText(trip?.tripCategory) }}</span>

          <span class="info-kv">
            车次名称
            <el-input
              v-model="basic.tripName"
              size="small"
              maxlength="64"
              class="basic-name"
              :disabled="!editable"
              placeholder="必填"
            />
          </span>

          <span class="info-kv">
            计划发车
            <el-date-picker
              v-model="basic.planDepartTime"
              type="datetime"
              size="small"
              value-format="YYYY-MM-DDTHH:mm:ss"
              format="YYYY-MM-DD HH:mm"
              class="basic-time"
              style="width: 150px"
              :disabled="!editable"
              placeholder="请选择"
            />
          </span>

          <div class="info-actions">
            <el-button size="small" :disabled="!editable || !basicDirty" :loading="basicSaving" @click="saveBasic">
              保存
            </el-button>
            <el-button size="small" @click="destVisible = true">子车次</el-button>
            <el-button size="small" @click="logVisible = true">操作日志</el-button>
            <el-button size="small" @click="printVisible = true">打印装车单</el-button>
          </div>
        </div>

        <div class="info-row2">
          <div class="route-chip">
            <b>{{ trip?.originDeptName || '—' }}</b>
            <span class="route-arrow">→</span>
            <div class="dest-chips">
              <template v-for="dest in dests" :key="dest.id">
                <el-select
                  v-if="destEditId === dest.id"
                  v-model="destEditDeptId"
                  size="small"
                  filterable
                  class="dest-chip__select"
                  :teleported="true"
                  :disabled="destSaving"
                  @change="commitDestEdit(dest)"
                  @visible-change="(open: boolean) => onDestSelectVisible(open)"
                >
                  <el-option
                    v-for="item in deptOptions"
                    :key="item.id"
                    :label="item.name"
                    :value="item.id"
                    :disabled="usedDestDeptIds.has(item.id) && item.id !== dest.destDeptId"
                  />
                </el-select>
                <span
                  v-else
                  class="dest-chip"
                  :class="{ 'is-editable': editable }"
                  :title="dest.destDeptName"
                  @click="startDestEdit(dest)"
                >
                  <span class="dest-chip__name">{{ dest.destDeptName }}</span>
                  <i v-if="editable" class="dest-chip__caret" aria-hidden="true"></i>
                  <button
                    v-if="editable"
                    type="button"
                    class="dest-chip__x"
                    title="移除此目的网点"
                    @click.stop="removeDest(dest)"
                  >×</button>
                </span>
              </template>
              <el-select
                v-if="destAdding"
                v-model="destAddDeptId"
                size="small"
                filterable
                class="dest-chip__select"
                :teleported="true"
                :disabled="destSaving"
                @change="commitAddDest"
                @visible-change="(open: boolean) => onAddSelectVisible(open)"
              >
                <el-option
                  v-for="item in deptOptions"
                  :key="item.id"
                  :label="item.name"
                  :value="item.id"
                  :disabled="usedDestDeptIds.has(item.id)"
                />
              </el-select>
              <button v-else-if="editable" class="dest-add" type="button" @click="startAddDest">
                + 添加目的网点
              </button>
            </div>
          </div>
          <div class="alloc-readonly">
            分摊方式 <b>{{ allocMethodText(trip?.allocMethod) }}</b>
          </div>
          <div class="warn-text" :class="{ 'is-amber': overWeight || overVolume }">{{ tip }}</div>
          <span class="capacity-text">
            载重 {{ trip?.vehicleLoad ? `${formatQuantity(trip.vehicleLoad)} kg` : '未设置' }} · 容积
            {{ trip?.vehicleVolume ? `${formatQuantity(trip.vehicleVolume)} m³` : '未设置' }}
          </span>
        </div>
      </div>

      <!-- Tab1 双栏穿梭 -->
      <div v-if="activeTab === 'load'" class="dual">
        <div class="pane">
          <div class="pane-head">
            <div class="pane-title">
              待配载列表 <span class="pane-count">{{ poolTotal }}</span>
            </div>
            <div class="pane-tools">
              <span class="subtle">均已完成资格校验</span>
              <column-setting-trigger
                :schema="poolSchema"
                :states="poolStates"
                :loading="poolColumnLoading"
                :saving="poolColumnSaving"
                size="small"
                @update:states="poolStates = $event"
                @save="handlePoolColumnSave"
                @reset="handlePoolColumnReset"
              />
              <el-button size="small" @click="loadPool()">刷新</el-button>
            </div>
          </div>

          <div class="pane-table">
            <smart-table
              ref="poolTableRef"
              v-loading="poolLoading"
              :data="poolRows"
              row-key="waybillId"
              height="100%"
              border
              scrollbar-always-on
              show-overflow-tooltip
              persist-key="transport:loading:pool"
              :column-order="poolOrderWithIndex"
              :column-hidden="poolHiddenKeys"
              :empty-text="poolLoading ? ' ' : '本机构暂无符合条件的待配载运单'"
              @sort-change="onPoolSortChange"
              @selection-change="onPoolSelection"
            >
              <el-table-column type="selection" width="44" fixed="left" reserve-selection />
              <el-table-column label="序号" width="56" align="center" fixed="left">
                <template #default="{ $index }">{{ poolIndex($index) }}</template>
              </el-table-column>

              <el-table-column
                v-for="def in poolDataColumns"
                :key="def.key"
                :prop="def.prop"
                :label="def.label"
                :min-width="def.minWidth || 90"
                :align="def.align"
                :fixed="def.key === 'waybillNo' ? 'left' : undefined"
                :sortable="poolSortable(def)"
              >
                <template #header>
                  <div class="col-head">
                    <span class="col-head__label">{{ def.label }}</span>
                    <column-filter
                      v-if="poolCol(def.key).filterType !== 'none'"
                      v-model="poolFilters[def.key]"
                      :type="poolCol(def.key).filterType"
                      :filter-mode="poolCol(def.key).filterMode"
                      :select-multiple="poolCol(def.key).selectMultiple ?? false"
                      :text-mode="poolTextModes[def.key]"
                      :options="poolFilterOptions(def)"
                      @update:mode="(mode: TextMatchMode) => (poolTextModes[def.key] = mode)"
                      @change="onPoolFilterChange"
                    />
                  </div>
                </template>
                <template #default="{ row }">
                  <el-button
                    v-if="def.kind === 'link'"
                    link
                    type="primary"
                    size="small"
                    class="mono"
                    @click="openWaybill(row)"
                  >
                    {{ row.waybillNo }}
                  </el-button>
                  <status-tag
                    v-else-if="def.kind === 'unitType'"
                    :type="unitTypeTag(row.unitType)"
                    :text="poolUnitTypeText(row.unitType)"
                  />
                  <status-tag
                    v-else-if="def.kind === 'status'"
                    :type="statusTagType(row.status)"
                    :text="statusText(row.status)"
                  />
                  <span v-else-if="def.kind === 'money'">{{ moneyText(poolValue(row, def.prop)) }}</span>
                  <span v-else-if="def.kind === 'quantity'">{{ qtyText(poolValue(row, def.prop)) }}</span>
                  <span v-else-if="def.kind === 'datetime'">{{ dtText(poolValue(row, def.prop)) }}</span>
                  <span v-else class="cell-text">{{ poolValue(row, def.prop) ?? '—' }}</span>
                </template>
              </el-table-column>

              <el-table-column label="操作" width="96" fixed="right" align="center">
                <template #default="{ row }">
                  <el-tooltip :disabled="canSplit(row)" :content="splitDisabledReason(row)" placement="top">
                    <span class="btn-wrap">
                      <el-button link type="primary" size="small" :disabled="!canSplit(row)" @click="openSplit(row)">
                        拆单
                      </el-button>
                    </span>
                  </el-tooltip>
                </template>
              </el-table-column>
            </smart-table>
          </div>

          <div class="pane-foot">
            <span>已选 <b>{{ poolSelected.length }}</b> 个单元</span>            <el-pagination
              v-model:current-page="poolPage"
              v-model:page-size="poolPageSize"
              size="small"
              background
              :total="poolTotal"
              :page-sizes="[20, 50, 100]"
              layout="sizes, prev, pager, next"
              @current-change="loadPool()"
              @size-change="onPoolSizeChange"
            />
          </div>
        </div>

        <div class="transfer-col">
          <div class="transfer-group">
            <el-tooltip content="全部右移：当前筛选的全部可配单元" placement="left">
              <el-button class="transfer-btn" :disabled="!canOperate || !poolTotal" @click="moveAllRight">≫</el-button>
            </el-tooltip>
            <el-tooltip content="选中右移：加入勾选的待配载单元" placement="left">
              <el-button class="transfer-btn" :disabled="!canOperate || !poolSelected.length" @click="moveSelectedRight">›</el-button>
            </el-tooltip>
            <el-tooltip content="全选左移：移除本车次全部配载" placement="left">
              <el-button class="transfer-btn" :disabled="!canOperate || !loadedTotal" @click="moveAllLeft">≪</el-button>
            </el-tooltip>
            <el-tooltip content="选中左移：移除勾选的配载单元" placement="left">
              <el-button class="transfer-btn" :disabled="!canOperate || !loadedSelected.length" @click="moveSelectedLeft">‹</el-button>
            </el-tooltip>
          </div>
        </div>

        <div class="pane">
          <div class="pane-head">
            <div class="pane-title">
              已配载列表 <span class="pane-count">{{ loadedTotal }}</span>
            </div>
            <div class="pane-tools">
              <span class="subtle">只读，无操作列；移除只能左移穿梭</span>
              <column-setting-trigger
                :schema="loadedSchema"
                :states="loadedStates"
                :loading="loadedColumnLoading"
                :saving="loadedColumnSaving"
                size="small"
                @update:states="loadedStates = $event"
                @save="handleLoadedColumnSave"
                @reset="handleLoadedColumnReset"
              />
              <el-button size="small" @click="loadLoaded()">刷新</el-button>
            </div>
          </div>

          <div class="pane-table">
            <smart-table
              ref="loadedTableRef"
              v-loading="loadedLoading"
              :data="loadedDisplayRows"
              :row-key="loadedRowKey"
              height="100%"
              border
              scrollbar-always-on
              show-overflow-tooltip
              persist-key="transport:loading:loaded"
              :column-order="loadedOrderWithIndex"
              :column-hidden="loadedHiddenKeys"
              :span-method="loadedSpanMethod"
              :row-class-name="loadedRowClass"
              :empty-text="loadedLoading ? ' ' : '暂无配载运单，请从左侧选中右移'"
              @selection-change="onLoadedSelection"
            >
              <el-table-column type="selection" width="44" :selectable="isLoadedSelectable" reserve-selection />
              <el-table-column label="序号" width="56" align="center">
                <template #default="{ row }">
                  <span v-if="isGroupRow(row)" class="group-label">{{ row.label }}</span>
                  <span v-else>{{ row.__seq }}</span>
                </template>
              </el-table-column>

              <el-table-column
                v-for="def in loadedDataColumns"
                :key="def.key"
                :prop="def.prop"
                :label="def.label"
                :min-width="def.minWidth || 90"
                :align="def.align"
                :sortable="false"
              >
                <template #header>
                  <div class="col-head">
                    <span class="col-head__label">{{ def.label }}</span>
                    <column-filter
                      v-if="loadedCol(def.key).filterType !== 'none'"
                      v-model="loadedFilters[def.key]"
                      :type="loadedCol(def.key).filterType"
                      :filter-mode="loadedCol(def.key).filterMode"
                      :select-multiple="loadedCol(def.key).selectMultiple ?? false"
                      :text-mode="loadedTextModes[def.key]"
                      :options="loadedFilterOptions(def)"
                      @update:mode="(mode: TextMatchMode) => (loadedTextModes[def.key] = mode)"
                      @change="onLoadedFilterChange"
                    />
                  </div>
                </template>
                <template #default="{ row }">
                  <template v-if="!isGroupRow(row)">
                    <el-button
                      v-if="def.kind === 'link'"
                      link
                      type="primary"
                      size="small"
                      class="mono"
                      @click="openWaybill(row)"
                    >
                      {{ row.waybillNo }}
                    </el-button>
                    <status-tag
                      v-else-if="def.kind === 'unitType'"
                      :type="unitTypeTag(row.unitType)"
                      :text="loadedUnitTypeText(row)"
                    />
                    <status-tag
                      v-else-if="def.kind === 'status'"
                      :type="statusTagType(row.status)"
                      :text="statusText(row.status)"
                    />
                    <span v-else-if="def.kind === 'tripDest'">{{ row.subTripNo }} / {{ row.destDeptName }}</span>
                    <span v-else-if="def.kind === 'dict'">{{ dictText(def.filterOptions, loadedValue(row, def.prop)) }}</span>
                    <span v-else-if="def.kind === 'money'">{{ moneyText(loadedValue(row, def.prop)) }}</span>
                    <span v-else-if="def.kind === 'quantity'">{{ qtyText(loadedValue(row, def.prop)) }}</span>
                    <span v-else-if="def.kind === 'datetime'">{{ dtText(loadedValue(row, def.prop)) }}</span>
                    <span v-else class="cell-text">{{ loadedValue(row, def.prop) ?? '—' }}</span>
                  </template>
                </template>
              </el-table-column>
            </smart-table>
          </div>

          <div class="pane-foot">
            <span>已选 <b>{{ loadedSelected.length }}</b> 个单元</span>
            <el-pagination
              v-model:current-page="loadedPage"
              v-model:page-size="loadedPageSize"
              size="small"
              background
              :total="loadedTotal"
              :page-sizes="[20, 50, 100]"
              layout="sizes, prev, pager, next"
              @current-change="loadLoaded()"
              @size-change="onLoadedSizeChange"
            />
          </div>
        </div>
      </div>

      <!-- Tab1 底部合计 -->
      <div v-if="activeTab === 'load'" class="detail-foot">
        <div class="totals">
          <span>合计 <b>{{ loadedSummary.unitCount || 0 }}</b> 个单元</span>
          <span><b>{{ formatQuantity(loadedSummary.quantity) }}</b> 件</span>
          <span><b>{{ formatQuantity(loadedSummary.weight) }}</b> kg</span>
          <span><b>{{ formatQuantity(loadedSummary.volume) }}</b> m³</span>
          <span>运费 <b>{{ moneyText(loadedSummary.totalFee) }}</b></span>
          <span v-if="loadedSelected.length" class="subtle">已选 {{ loadedSelected.length }} 个单元</span>
        </div>
        <div class="acts">
          <el-button type="primary" :disabled="!editable" @click="goNext">下一步：发车</el-button>
          <el-button @click="printVisible = true">打印装车单</el-button>
        </div>
      </div>

      <!-- Tab2 发车 -->
      <div v-if="activeTab === 'send'" class="send-wrap">
        <div class="card">
          <div class="card-head">
            <div class="pane-title">车辆信息 <span class="subtle">发车后只读</span></div>
            <span class="subtle">支持选择 + 快速新增</span>
          </div>
          <div class="form-grid">
            <div class="form-item">
              <label>车牌号<span class="req">*</span></label>
              <div class="input-with-add">
                <el-select
                  v-model="vehicle.plateNo"
                  filterable
                  allow-create
                  default-first-option
                  size="small"
                  class="w-full"
                  :disabled="!editable"
                  placeholder="选择或输入车牌"
                  @change="onPlateChange"
                >
                  <el-option v-for="item in vehicleOptions" :key="item.id" :label="item.name" :value="item.name" />
                </el-select>
                <el-button size="small" :disabled="!editable" @click="openQuickVehicle">新增</el-button>
              </div>
            </div>
            <div class="form-item">
              <label>司机姓名<span class="req">*</span></label>
              <div class="input-with-add">
                <el-select
                  v-model="vehicle.driverName"
                  filterable
                  allow-create
                  default-first-option
                  size="small"
                  class="w-full"
                  :disabled="!editable"
                  placeholder="选择或输入司机"
                  @change="onDriverChange"
                >
                  <el-option v-for="item in driverOptions" :key="item.id" :label="item.name" :value="item.name" />
                </el-select>
                <el-button size="small" :disabled="!editable" @click="openQuickDriver">新增</el-button>
              </div>
            </div>
            <div class="form-item">
              <label>司机电话</label>
              <el-input v-model="vehicle.driverPhone" size="small" :disabled="!editable" placeholder="请输入" />
            </div>
            <div class="form-item">
              <label>车型</label>
              <el-input v-model="vehicle.vehicleType" size="small" :disabled="!editable" placeholder="如：9.6 米厢车" />
            </div>
            <div class="form-item">
              <label>核定载重(kg)</label>
              <el-input-number v-model="vehicle.vehicleLoad" :min="0" :controls="false" size="small" class="w-full" :disabled="!editable" />
            </div>
            <div class="form-item">
              <label>核定容积(m³)</label>
              <el-input-number v-model="vehicle.vehicleVolume" :min="0" :controls="false" size="small" class="w-full" :disabled="!editable" />
            </div>
          </div>
        </div>

        <div class="card mt12">
          <div class="card-head">
            <div class="pane-title">费用信息 <span class="subtle">录入车次成本费用</span></div>
            <div class="fee-head-tools">
              <div class="alloc-select">
                <span>全车分摊方式</span>
                <el-select v-model="allocMethod" size="small" class="alloc-select__input" :disabled="!editable" @change="onFeeChanged">
                  <el-option v-for="item in ALLOC_METHOD_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
              </div>
              <el-button link type="primary" class="fee-preview-entry" @click="feePreviewVisible = true">
                分摊预览
                <span class="fee-preview-entry__count">{{ feePreview?.units?.length || 0 }} 个单元</span>
              </el-button>
            </div>
          </div>

          <div class="fee-main">
            <div class="fee-block">
              <h4>
                费用录入
                <span class="subtle">按「费用项 × 归属」逐格录入：发站行填车次费用，到站行只填到站付运输费</span>
              </h4>
              <el-table
                v-if="feeMatrixTypes.length"
                :data="feeMatrixRows"
                size="small"
                border
                class="fee-matrix"
                empty-text="本车次暂无费用项"
              >
                <el-table-column prop="rowName" label="费用归属" min-width="180" fixed>
                  <template #default="{ row }">
                    <span class="fee-owner">
                      <span class="fee-owner__name">{{ row.rowName }}</span>
                      <el-tag
                        size="small"
                        effect="plain"
                        :type="row.role === '发站' ? 'warning' : 'success'"
                      >{{ row.role }}</el-tag>
                    </span>
                  </template>
                </el-table-column>
                <el-table-column
                  v-for="type in feeMatrixTypes"
                  :key="type.feeCode"
                  :label="type.feeName"
                  :min-width="type.arrival ? 156 : 132"
                  align="right"
                >
                  <template #default="{ row }">
                    <el-input-number
                      v-if="cellEditable(row, type)"
                      :model-value="cellValue(row, type)"
                      :min="0"
                      :controls="false"
                      size="small"
                      :disabled="!editable"
                      @update:model-value="(value: number | undefined) => setCellValue(row, type, value)"
                    />
                    <span v-else class="fee-disabled">—</span>
                  </template>
                </el-table-column>
              </el-table>
              <div v-else class="fee-empty">未配置车次级成本项（系统设置 → 财务配置）</div>
            </div>
          </div>
        </div>

        <el-drawer v-model="feePreviewVisible" title="费用分摊预览" size="560px">
          <div v-loading="feePreviewLoading" class="fee-preview-body">
            <div class="fee-preview-sum">
              <span>运费收入合计 <b>{{ moneyText(feePreview?.summary?.incomeTotal) }}</b></span>
              <span>总返款 <b>{{ moneyText(feePreview?.summary?.rebateTotal) }}</b></span>
              <span>成本合计 <b>{{ moneyText(feePreview?.summary?.costTotal) }}</b></span>
              <span>
                毛利
                <b :class="(feePreview?.summary?.grossProfit || 0) >= 0 ? 'is-profit' : 'is-loss'">
                  {{ moneyText(feePreview?.summary?.grossProfit) }}
                </b>
              </span>
            </div>
            <el-table :data="feePreview?.units || []" size="small" border max-height="420" empty-text="暂无可分摊单元">
              <el-table-column prop="waybillNo" label="运单号" min-width="150" class-name="mono" />
              <el-table-column label="基数" width="96" align="right">
                <template #default="{ row }">{{ qtyText(row.basis) }}</template>
              </el-table-column>
              <el-table-column label="占比" width="110" align="right">
                <template #default="{ row }">{{ formatPercent(row.ratio) }}</template>
              </el-table-column>
              <el-table-column label="分摊金额" width="120" align="right">
                <template #default="{ row }">{{ moneyText(row.allocatedFee) }}</template>
              </el-table-column>
            </el-table>
            <div class="fee-hint">
              分摊基数合计 {{ qtyText(feePreview?.basisTotal) }}；为 0 时阻断保存，请先补充运单重量/体积。
            </div>
          </div>
        </el-drawer>

        <div class="card mt12">
          <div class="card-head">
            <div class="pane-title">车次汇总</div>
            <span class="subtle">只读，实时刷新</span>
          </div>
          <div class="trip-summary">
            <div class="summary-row">
              <span class="summary-label">装载信息</span>
              <span>票数 <b>{{ loadedSummary.unitCount || 0 }}</b></span>
              <span>件数 <b>{{ formatQuantity(loadedSummary.quantity) }}</b></span>
              <span>重量 <b>{{ formatQuantity(loadedSummary.weight) }}</b> kg</span>
              <span>体积 <b>{{ formatQuantity(loadedSummary.volume) }}</b> m³</span>
              <span>重量装载率 <b>{{ formatPercent(weightRate) }}</b></span>
              <span>体积装载率 <b>{{ formatPercent(volumeRate) }}</b></span>
              <span v-if="overWeight" class="over-text">已超载 {{ formatQuantity(overWeightValue) }} kg</span>
              <span v-if="overVolume" class="over-text">已超容 {{ formatQuantity(overVolumeValue) }} m³</span>
            </div>
            <div class="summary-row">
              <span class="summary-label">营收信息</span>
              <span>现付 <b>{{ moneyText(trip?.totalPrepaidFreight) }}</b></span>
              <span>到付 <b>{{ moneyText(trip?.totalCollectFreight) }}</b></span>
              <span>回付 <b>{{ moneyText(trip?.totalReturnFreight) }}</b></span>
              <span>月结 <b>{{ moneyText(trip?.totalMonthlyFreight) }}</b></span>
              <span>收入合计 <b class="is-income">{{ moneyText(incomeBriefTotal) }}</b></span>
              <span>成本合计 <b class="is-cost">{{ moneyText(feePreview?.summary?.costTotal) }}</b></span>
              <span>
                实时毛利
                <b :class="(feePreview?.summary?.grossProfit || 0) >= 0 ? 'is-profit' : 'is-loss'">
                  {{ moneyText(feePreview?.summary?.grossProfit) }}
                </b>
                <i class="summary-note">（此毛利为实时毛利，仅供参考）</i>
              </span>
            </div>
          </div>
        </div>

        <div class="detail-foot">
          <div class="acts">
            <el-button @click="activeTab = 'load'">返回装车配载</el-button>
            <el-button :disabled="!editable" :loading="feeSaving" @click="saveFees">保存费用</el-button>
            <el-button v-if="editable" type="primary" :loading="departSaving" @click="openDepart">发车</el-button>
            <el-button v-else type="danger" plain @click="cancelDepart">取消发车</el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 弹窗/抽屉 -->
    <split-load-dialog
      v-model="splitVisible"
      :trip-id="tripId"
      :row="splitRow"
      @done="onSplitDone"
    />
    <trip-dest-drawer v-model="destVisible" :trip="trip" :dests="dests" @refresh="onDestRefresh" />
    <trip-log-drawer v-model="logVisible" :trip-id="tripId" :trip-no="trip?.tripNo" />
    <trip-print-dialog v-model="printVisible" :trip-id="tripId" />

    <el-dialog v-model="quickVehicleVisible" title="快速新增车辆" width="430px" destroy-on-close>
      <el-form label-width="96px">
        <el-form-item label="车牌号" required>
          <el-input v-model="quickVehicle.plateNo" placeholder="如：粤A·12345" />
        </el-form-item>
        <el-form-item label="车型">
          <el-select
            v-model="quickVehicle.vehicleType"
            filterable
            allow-create
            default-first-option
            clearable
            class="w-full"
            placeholder="选择或输入车型"
          >
            <el-option v-for="item in vehicleTypeOptions" :key="item.name" :label="item.name" :value="item.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="核定载重(kg)">
          <el-input-number v-model="quickVehicle.vehicleLoad" :min="0" :controls="false" class="w-full" />
        </el-form-item>
        <el-form-item label="核定容积(m³)">
          <el-input-number v-model="quickVehicle.vehicleVolume" :min="0" :controls="false" class="w-full" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="quickVehicleVisible = false">取消</el-button>
        <el-button type="primary" :loading="quickSaving" @click="submitQuickVehicle">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="quickDriverVisible" title="快速新增司机" width="430px" destroy-on-close>
      <el-form label-width="96px">
        <el-form-item label="司机姓名" required>
          <el-input v-model="quickDriver.driverName" placeholder="请输入" />
        </el-form-item>
        <el-form-item label="司机电话" required>
          <el-input v-model="quickDriver.driverPhone" placeholder="请输入" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="quickDriverVisible = false">取消</el-button>
        <el-button type="primary" :loading="quickSaving" @click="submitQuickDriver">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="departVisible" title="确认发车" width="430px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="预计到达时间" required>
          <el-date-picker
            v-model="planArriveTime"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            format="YYYY-MM-DD HH:mm"
            class="w-full"
            placeholder="请选择"
          />
        </el-form-item>
      </el-form>
      <div class="dialog-hint">发车后本页配载、车辆与费用全部只读，运单状态推进为「分拨发车」。</div>
      <template #footer>
        <el-button @click="departVisible = false">取消</el-button>
        <el-button type="primary" :loading="departSaving" @click="confirmDepart">确认发车</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import SmartTable from '@/components/base/SmartTable.vue'
import StatusTag from '@/components/base/StatusTag.vue'
import ColumnFilter from '@/components/base/ColumnFilter.vue'
import ColumnSettingTrigger from '@/components/base/ColumnSettingTrigger.vue'
import type { ColumnDef, ColumnOption } from '@/composables/useColumnConfig'
import { useColumnConfig } from '@/composables/useColumnConfig'
import type { TextMatchMode } from '@/utils/textFilter'
import {
  addTripDest,
  cancelDepartTrip,
  deleteTripDest,
  departTrip,
  getTripDetail,
  getTripLoaded,
  getTripOptions,
  getTripPool,
  loadTripWaybills,
  quickCreateDriver,
  quickCreateVehicle,
  removeTripWaybills,
  saveTripFees,
  saveTripVehicle,
  updateTrip,
  updateTripDest,
  previewTripFees
} from '@/api/transport'
import { getFeeTypePage } from '@/api/system/feeType'
import type {
  FeePreview,
  FeeTypeRow,
  LoadedPage,
  LoadedRow,
  PoolRow,
  TripDest,
  TripDetail,
  TripOption,
  TripRecord
} from '@/types/transport'
import {
  ALLOC_METHOD_OPTIONS,
  allocMethodText,
  tripCategoryText,
  tripStatusTagType,
  tripStatusText
} from '@/utils/transportDict'
import {
  formatDateTime,
  formatMoney,
  formatQuantity,
  PAY_METHOD_OPTIONS,
  statusTagType,
  statusText,
  WAYBILL_STATUS_OPTIONS
} from '@/utils/operationDict'
import SplitLoadDialog from './components/SplitLoadDialog.vue'
import TripDestDrawer from './components/TripDestDrawer.vue'
import TripLogDrawer from './components/TripLogDrawer.vue'
import TripPrintDialog from './components/TripPrintDialog.vue'

defineOptions({ name: 'TransportLoadingDetail' })

type CellKind = 'text' | 'link' | 'unitType' | 'quantity' | 'money' | 'datetime' | 'status' | 'tripDest' | 'dict'

interface PaneColumn extends ColumnDef {
  kind?: CellKind
}

interface GroupRow {
  __group: true
  __key: string
  __seq?: number
  tripDestId: number
  label: string
  count: number
  quantity: number
  weight: number
  volume: number
  totalFee: number
}

type LoadedDisplayRow = LoadedRow & { __seq?: number } | GroupRow

const route = useRoute()
const router = useRouter()
const tripId = Number(route.query.id)

/* ------------------------------ 基础数据 ------------------------------ */

const detail = ref<TripDetail | null>(null)
const trip = computed(() => detail.value?.trip || null)
const dests = computed(() => detail.value?.dests || [])
const editable = computed(() => {
  const status = trip.value?.tripStatus
  return status === 10 || status === 20
})
const canOperate = computed(() => editable.value)

const activeTab = ref<'load' | 'send'>('load')

const basic = reactive({ tripName: '', planDepartTime: '' })
const basicBaseline = reactive({ tripName: '', planDepartTime: '' })
const basicSaving = ref(false)
const basicDirty = computed(
  () => basic.tripName.trim() !== basicBaseline.tripName.trim() || basic.planDepartTime !== basicBaseline.planDepartTime
)

/* 末端网点：全部平铺展示，点击chip直接改并自动保存，不弹窗 */
const deptOptions = ref<Array<{ id: number; name: string }>>([])
const destEditId = ref<number | null>(null)
const destEditDeptId = ref<number | undefined>(undefined)
const destAdding = ref(false)
const destAddDeptId = ref<number | undefined>(undefined)
const destSaving = ref(false)
const usedDestDeptIds = computed(() => new Set(dests.value.map((item) => item.destDeptId)))

async function loadDeptOptions() {
  if (deptOptions.value.length) return
  try {
    const options = await getTripOptions('dept')
    deptOptions.value = options
      .filter((item) => !!item.name)
      .map((item) => ({ id: item.id, name: item.name as string }))
  } catch {
    // 请求层已提示
  }
}

async function refreshAfterDestChange() {
  await loadTripDetail()
  await loadLoaded()
}

function startDestEdit(dest: TripDest) {
  if (!editable.value || destSaving.value) return
  loadDeptOptions()
  destAdding.value = false
  destEditId.value = dest.id
  destEditDeptId.value = dest.destDeptId
}

function onDestSelectVisible(open: boolean) {
  if (!open) destEditId.value = null
}

async function commitDestEdit(dest: TripDest) {
  if (destEditDeptId.value === undefined || destEditDeptId.value === dest.destDeptId) {
    destEditId.value = null
    return
  }
  destSaving.value = true
  try {
    await updateTripDest(tripId, dest.id, { destDeptId: destEditDeptId.value })
    ElMessage.success('已保存')
    destEditId.value = null
    await refreshAfterDestChange()
  } catch {
    // 请求层已提示
  } finally {
    destSaving.value = false
  }
}

function startAddDest() {
  if (!editable.value) return
  loadDeptOptions()
  destEditId.value = null
  destAdding.value = true
  destAddDeptId.value = undefined
}

function onAddSelectVisible(open: boolean) {
  if (!open) destAdding.value = false
}

async function commitAddDest() {
  if (destAddDeptId.value === undefined) {
    destAdding.value = false
    return
  }
  destSaving.value = true
  try {
    await addTripDest(tripId, { destDeptId: destAddDeptId.value })
    ElMessage.success('目的网点已新增')
    destAdding.value = false
    await refreshAfterDestChange()
  } catch {
    // 请求层已提示
  } finally {
    destSaving.value = false
  }
}

async function removeDest(dest: TripDest) {
  if (!editable.value || destSaving.value) return
  try {
    await ElMessageBox.confirm(`确认移除目的网点 ${dest.destDeptName}？`, '移除目的网点', {
      type: 'warning',
      confirmButtonText: '移除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  destSaving.value = true
  try {
    await deleteTripDest(tripId, dest.id)
    ElMessage.success('目的网点已移除')
    await refreshAfterDestChange()
  } catch {
    // 请求层已提示
  } finally {
    destSaving.value = false
  }
}

const overWeight = computed(() => {
  const capacity = Number(trip.value?.vehicleLoad || 0)
  return capacity > 0 && Number(trip.value?.totalWeight || 0) > capacity
})
const overVolume = computed(() => {
  const capacity = Number(trip.value?.vehicleVolume || 0)
  return capacity > 0 && Number(trip.value?.totalVolume || 0) > capacity
})
const overWeightValue = computed(() => Number(trip.value?.totalWeight || 0) - Number(trip.value?.vehicleLoad || 0))
const overVolumeValue = computed(() => Number(trip.value?.totalVolume || 0) - Number(trip.value?.vehicleVolume || 0))

const tip = computed(() => {
  if (!trip.value) return ''
  if (!editable.value) return '已发车，配载与车辆费用只读；运单改单/作废请到运单管理操作'
  if (overWeight.value || overVolume.value) {
    const parts: string[] = []
    if (overWeight.value) parts.push(`超载 ${formatQuantity(overWeightValue.value)} kg`)
    if (overVolume.value) parts.push(`超容 ${formatQuantity(overVolumeValue.value)} m³`)
    return `已${parts.join(' / ')}（不影响继续配载）`
  }
  if (basicDirty.value) return '修改后点击上方保存才能生效'
  return '网点变更需保存并校验'
})

/* ------------------------------ 双栏列定义 ------------------------------ */

const UNIT_TYPE_OPTIONS: ColumnOption[] = [
  { label: '整票', value: 1 },
  { label: '母单余额', value: 2 }
]
const POOL_STATUS_OPTIONS: ColumnOption[] = [
  { label: '已揽收', value: 20 },
  { label: '分拨卸车', value: 60 }
]
const LOADED_UNIT_TYPE_OPTIONS: ColumnOption[] = [
  { label: '整票', value: 1 },
  { label: '子单', value: 3 }
]
const SETTLE_STATUS_OPTIONS: ColumnOption[] = [
  { label: '未结算', value: 0 },
  { label: '已结算', value: 1 },
  { label: '部分结算', value: 2 },
  { label: '无需结算', value: 3 }
]

const poolSchema = reactive<PaneColumn[]>([
  { key: 'waybillNo', label: '运单号', prop: 'waybillNo', minWidth: 160, kind: 'link', filterType: 'text', filterMode: 'multi', locked: true },
  { key: 'unitType', label: '运单类型', prop: 'unitType', minWidth: 96, kind: 'unitType', filterType: 'select', filterOptions: UNIT_TYPE_OPTIONS },
  { key: 'customerName', label: '客户名称', prop: 'customerName', minWidth: 150, kind: 'text', filterType: 'text', filterMode: 'multi' },
  { key: 'destName', label: '目的站', prop: 'destName', minWidth: 120, kind: 'text', filterType: 'text' },
  { key: 'nextDeptName', label: '到达网点', prop: 'nextDeptName', minWidth: 140, kind: 'text', filterType: 'select' },
  { key: 'receiverName', label: '收货人', prop: 'receiverName', minWidth: 100, kind: 'text', filterType: 'text' },
  { key: 'remainingQuantity', label: '剩余件数', prop: 'remainingQuantity', minWidth: 90, align: 'right', kind: 'quantity', filterType: 'numberrange', sortable: false },
  { key: 'remainingWeight', label: '剩余重量(kg)', prop: 'remainingWeight', minWidth: 108, align: 'right', kind: 'quantity', filterType: 'numberrange', sortable: false },
  { key: 'remainingVolume', label: '剩余体积(m³)', prop: 'remainingVolume', minWidth: 108, align: 'right', kind: 'quantity', filterType: 'numberrange', sortable: false },
  { key: 'remainFee', label: '运费余额', prop: 'remainFee', minWidth: 104, align: 'right', kind: 'money', filterType: 'none' },
  { key: 'totalFee', label: '运费合计', prop: 'totalFee', minWidth: 104, align: 'right', kind: 'money', filterType: 'none', defaultHidden: true },
  { key: 'inventoryTime', label: '库存落入时间', prop: 'inventoryTime', minWidth: 156, kind: 'datetime', filterType: 'daterange' },
  { key: 'status', label: '状态', prop: 'status', minWidth: 90, kind: 'status', filterType: 'select', filterOptions: POOL_STATUS_OPTIONS },
  { key: 'operation', label: '操作', elKey: 'label:操作', minWidth: 96, filterType: 'none', sortable: false, locked: true },
  { key: 'originalQuantity', label: '原件数', prop: 'originalQuantity', minWidth: 90, align: 'right', kind: 'quantity', filterType: 'none', defaultHidden: true },
  { key: 'originalWeight', label: '原重量(kg)', prop: 'originalWeight', minWidth: 108, align: 'right', kind: 'quantity', filterType: 'none', defaultHidden: true },
  { key: 'originalVolume', label: '原体积(m³)', prop: 'originalVolume', minWidth: 108, align: 'right', kind: 'quantity', filterType: 'none', defaultHidden: true },
  { key: 'createTime', label: '下单时间', prop: 'createTime', minWidth: 156, kind: 'datetime', filterType: 'daterange', defaultHidden: true }
])

const loadedSchema = reactive<PaneColumn[]>([
  { key: 'waybillNo', label: '运单号', prop: 'waybillNo', minWidth: 170, kind: 'link', filterType: 'text', filterMode: 'multi', locked: true },
  { key: 'status', label: '运单状态', prop: 'status', minWidth: 100, kind: 'status', filterType: 'select', selectMultiple: true, filterOptions: WAYBILL_STATUS_OPTIONS },
  { key: 'payMethod', label: '支付方式', prop: 'payMethod', minWidth: 100, kind: 'dict', filterType: 'select', selectMultiple: true, filterOptions: PAY_METHOD_OPTIONS },
  { key: 'settleStatus', label: '结算状态', prop: 'settleStatus', minWidth: 100, kind: 'dict', filterType: 'select', filterOptions: SETTLE_STATUS_OPTIONS },
  { key: 'unitType', label: '运单类型', prop: 'unitType', minWidth: 92, kind: 'unitType', filterType: 'select', filterOptions: LOADED_UNIT_TYPE_OPTIONS },
  { key: 'destDeptName', label: '运达网点', prop: 'destDeptName', minWidth: 150, kind: 'text', filterType: 'select' },
  { key: 'senderName', label: '发货人', prop: 'senderName', minWidth: 100, kind: 'text', filterType: 'text', filterMode: 'multi' },
  { key: 'receiverName', label: '收货人', prop: 'receiverName', minWidth: 100, kind: 'text', filterType: 'text', filterMode: 'multi' },
  { key: 'quantity', label: '装车件数', prop: 'quantity', minWidth: 96, align: 'right', kind: 'quantity', filterType: 'numberrange', sortable: false },
  { key: 'weight', label: '装车重量(kg)', prop: 'weight', minWidth: 116, align: 'right', kind: 'quantity', filterType: 'numberrange', sortable: false },
  { key: 'volume', label: '装车体积(m³)', prop: 'volume', minWidth: 116, align: 'right', kind: 'quantity', filterType: 'numberrange', sortable: false },
  { key: 'loadFee', label: '装车运费', prop: 'loadFee', minWidth: 104, align: 'right', kind: 'money', filterType: 'numberrange', sortable: false },
  { key: 'loadRebate', label: '装车返款', prop: 'loadRebate', minWidth: 104, align: 'right', kind: 'money', filterType: 'numberrange', sortable: false },
  { key: 'totalFee', label: '运费合计', prop: 'totalFee', minWidth: 104, align: 'right', kind: 'money', filterType: 'numberrange', sortable: false, defaultHidden: true },
  { key: 'rebateTotal', label: '返款合计', prop: 'rebateTotal', minWidth: 104, align: 'right', kind: 'money', filterType: 'numberrange', sortable: false, defaultHidden: true },
  { key: 'loadTime', label: '配载时间', prop: 'loadTime', minWidth: 156, kind: 'datetime', filterType: 'daterange' }
])

const {
  states: poolStates,
  loading: poolColumnLoading,
  saving: poolColumnSaving,
  isVisible: isPoolVisible,
  columnOrder: poolColumnOrder,
  hiddenKeys: poolHiddenKeys,
  col: poolCol,
  load: loadPoolColumnConfig,
  save: savePoolColumnConfig,
  reset: resetPoolColumnConfig,
  filterModesParam: poolFilterModesParam,
  defaultFiltersRaw: poolDefaultFiltersRaw
} = useColumnConfig('transport.loading.pool', poolSchema)

const {
  states: loadedStates,
  loading: loadedColumnLoading,
  saving: loadedColumnSaving,
  isVisible: isLoadedVisible,
  columnOrder: loadedColumnOrder,
  hiddenKeys: loadedHiddenKeys,
  col: loadedCol,
  load: loadLoadedColumnConfig,
  save: saveLoadedColumnConfig,
  reset: resetLoadedColumnConfig,
  filterModesParam: loadedFilterModesParam,
  defaultFiltersRaw: loadedDefaultFiltersRaw
} = useColumnConfig('transport.loading.loaded', loadedSchema, {
  // v3 → v4 一次性迁移：存量配置仅隐藏这两列，其他顺序/显隐/列宽不动
  migrationHiddenKeys: ['totalFee', 'rebateTotal']
})

const poolDataColumns = computed(() => poolSchema.filter((item) => item.key !== 'operation' && isPoolVisible(item.key)))
const loadedDataColumns = computed(() => loadedSchema.filter((item) => isLoadedVisible(item.key)))
const poolOrderWithIndex = computed(() => ['label:序号', ...poolColumnOrder.value])
const loadedOrderWithIndex = computed(() => ['label:序号', ...loadedColumnOrder.value])

/* ------------------------------ 双栏数据与筛选 ------------------------------ */

const poolLoading = ref(false)
const poolRows = ref<PoolRow[]>([])
const poolTotal = ref(0)
const poolPage = ref(1)
const poolPageSize = ref(20)
const poolSelected = ref<PoolRow[]>([])
const poolSort = reactive<{ prop: string; order: 'asc' | 'desc' | '' }>({ prop: '', order: '' })
const poolFilters = reactive<Record<string, unknown>>({})
const poolTextModes = reactive<Record<string, TextMatchMode>>({})

const loadedLoading = ref(false)
const loadedRows = ref<LoadedRow[]>([])
const loadedTotal = ref(0)
const loadedPage = ref(1)
const loadedPageSize = ref(50)
const loadedSummary = ref({
  unitCount: 0,
  quantity: 0,
  weight: 0,
  volume: 0,
  totalFee: 0,
  rebateTotal: 0,
  grossProfit: 0
})
const loadedSelected = ref<LoadedRow[]>([])
const loadedFilters = reactive<Record<string, unknown>>({})
const loadedTextModes = reactive<Record<string, TextMatchMode>>({})

const poolTableRef = ref<InstanceType<typeof SmartTable>>()
const loadedTableRef = ref<InstanceType<typeof SmartTable>>()

const destDeptOptions = computed<ColumnOption[]>(() =>
  dests.value.map((item) => ({ label: item.destDeptName, value: item.id }))
)

const destDeptFilterOptions = computed<ColumnOption[]>(() =>
  dests.value.map((item) => ({ label: item.destDeptName, value: item.destDeptId }))
)

function initFilterState(schema: PaneColumn[], filters: Record<string, unknown>) {
  schema.forEach((item) => {
    if (item.key === 'operation') return
    if (item.filterType === 'numberrange' || item.filterType === 'daterange') {
      filters[item.key] = []
    } else if (item.filterType === 'select' && item.selectMultiple) {
      filters[item.key] = []
    } else {
      filters[item.key] = undefined
    }
  })
}

function applyDefaultFilters(
  schema: PaneColumn[],
  raw: Record<string, unknown>,
  filters: Record<string, unknown>,
  textModes: Record<string, TextMatchMode>
) {
  Object.entries(raw).forEach(([key, value]) => {
    const def = schema.find((item) => item.key === key)
    if (!def) return
    if (def.filterType === 'text' && def.filterMode === 'multi' && value && typeof value === 'object') {
      const payload = value as { mode?: TextMatchMode; value?: string }
      filters[key] = payload.value ?? ''
      if (payload.mode) textModes[key] = payload.mode
      return
    }
    filters[key] = value
  })
}

function poolFilterOptions(def: PaneColumn): ColumnOption[] | undefined {
  if (def.key === 'nextDeptName') return destDeptOptions.value
  return def.filterOptions
}

function loadedFilterOptions(def: PaneColumn): ColumnOption[] | undefined {
  if (def.key === 'destDeptName') return destDeptFilterOptions.value
  return def.filterOptions
}

function poolSortable(def: PaneColumn): boolean | 'custom' {
  return ['waybillNo', 'remainingQuantity', 'remainingWeight', 'remainingVolume', 'createTime'].includes(def.key)
    ? 'custom'
    : false
}

function applyRange(params: Record<string, unknown>, prefix: string, value: unknown) {
  if (!Array.isArray(value)) return
  const [min, max] = value as Array<number | string | undefined>
  if (min !== undefined && min !== null && min !== '') params[`${prefix}Min`] = min
  if (max !== undefined && max !== null && max !== '') params[`${prefix}Max`] = max
}

function buildPoolParams(): Record<string, unknown> {
  const params: Record<string, unknown> = {
    page: poolPage.value,
    pageSize: poolPageSize.value
  }
  const text = (key: string) => {
    const value = poolFilters[key]
    if (typeof value === 'string' && value.trim()) params[key] = value.trim()
  }
  text('waybillNo')
  text('customerName')
  text('receiverName')
  const destStation = poolFilters.destName
  if (typeof destStation === 'string' && destStation.trim()) params.destStation = destStation.trim()

  const unitType = poolFilters.unitType
  if (unitType !== undefined && unitType !== null && unitType !== '') params.unitType = unitType
  if (poolFilters.nextDeptName) params.nextDeptId = poolFilters.nextDeptName
  const status = poolFilters.status
  if (Array.isArray(status) && status.length) params.status = status.join(',')
  else if (typeof status === 'string' && status) params.status = status

  applyRange(params, 'quantity', poolFilters.remainingQuantity)
  applyRange(params, 'weight', poolFilters.remainingWeight)
  applyRange(params, 'volume', poolFilters.remainingVolume)
  applyRange(params, 'inventory', poolFilters.inventoryTime)
  applyRange(params, 'create', poolFilters.createTime)

  const modes = poolFilterModesParam(
    { waybillNo: 'waybillNo', customerName: 'customerName', receiverName: 'receiverName', destName: 'destStation' },
    poolTextModes
  )
  if (Object.keys(modes).length) params.filterModes = JSON.stringify(modes)

  const sortFieldMap: Record<string, string> = {
    waybillNo: 'waybillNo',
    remainingQuantity: 'quantity',
    remainingWeight: 'weight',
    remainingVolume: 'volume',
    createTime: 'createTime'
  }
  if (poolSort.prop && poolSort.order) {
    params.sortBy = sortFieldMap[poolSort.prop] || poolSort.prop
    params.sortOrder = poolSort.order
  }
  return params
}

function buildLoadedParams(): Record<string, unknown> {
  const params: Record<string, unknown> = {
    page: loadedPage.value,
    pageSize: loadedPageSize.value
  }
  const text = (key: string) => {
    const value = loadedFilters[key]
    if (typeof value === 'string' && value.trim()) params[key] = value.trim()
  }
  text('waybillNo')
  text('senderName')
  text('receiverName')
  if (loadedFilters.destDeptName) params.destDeptId = loadedFilters.destDeptName
  if (loadedFilters.unitType) params.unitType = loadedFilters.unitType
  if (loadedFilters.settleStatus !== undefined && loadedFilters.settleStatus !== null && loadedFilters.settleStatus !== '') {
    params.settleStatus = loadedFilters.settleStatus
  }
  const status = loadedFilters.status
  if (Array.isArray(status) && status.length) params.status = status.join(',')
  else if (typeof status === 'string' && status) params.status = status
  const payMethod = loadedFilters.payMethod
  if (Array.isArray(payMethod) && payMethod.length) params.payMethod = payMethod.join(',')
  else if (typeof payMethod === 'string' && payMethod) params.payMethod = payMethod

  applyRange(params, 'quantity', loadedFilters.quantity)
  applyRange(params, 'weight', loadedFilters.weight)
  applyRange(params, 'volume', loadedFilters.volume)
  applyRange(params, 'loadFee', loadedFilters.loadFee)
  applyRange(params, 'loadRebate', loadedFilters.loadRebate)
  applyRange(params, 'totalFee', loadedFilters.totalFee)
  applyRange(params, 'rebateTotal', loadedFilters.rebateTotal)
  applyRange(params, 'load', loadedFilters.loadTime)

  const modes = loadedFilterModesParam(
    { waybillNo: 'waybillNo', senderName: 'senderName', receiverName: 'receiverName' },
    loadedTextModes
  )
  if (Object.keys(modes).length) params.filterModes = JSON.stringify(modes)
  return params
}

async function loadTripDetail() {
  try {
    const result = await getTripDetail(tripId)
    detail.value = result
    if (!basicDirty.value) {
      basic.tripName = result.trip.tripName || ''
      basic.planDepartTime = result.trip.planDepartTime || ''
      basicBaseline.tripName = basic.tripName
      basicBaseline.planDepartTime = basic.planDepartTime
    }
    if (!vehicleTouched.value) syncVehicleFromTrip(result.trip)
    if (!feeInitialized.value) {
      allocMethod.value = result.trip.allocMethod || 3
      initFeeAmounts(result.trip)
    }
    if (activeTab.value === 'send') scheduleFeePreview()
  } catch {
    // 请求层已按响应体 code 提示
  }
}

async function loadPool() {
  if (!editable.value) {
    poolRows.value = []
    poolTotal.value = 0
    poolSelected.value = []
    return
  }
  poolLoading.value = true
  try {
    const result = await getTripPool(tripId, buildPoolParams())
    poolRows.value = result.records || []
    poolTotal.value = result.total || 0
    poolSelected.value = []
    poolTableRef.value?.getTable()?.clearSelection?.()
  } catch {
    poolRows.value = []
    poolTotal.value = 0
  } finally {
    poolLoading.value = false
  }
}

async function loadLoaded() {
  loadedLoading.value = true
  try {
    const result: LoadedPage = await getTripLoaded(tripId, buildLoadedParams())
    loadedRows.value = result.records || []
    loadedTotal.value = result.total || 0
    loadedSummary.value = result.summary || {
      unitCount: 0,
      quantity: 0,
      weight: 0,
      volume: 0,
      totalFee: 0,
      rebateTotal: 0,
      grossProfit: 0
    }
    loadedSelected.value = []
    loadedTableRef.value?.getTable()?.clearSelection?.()
  } catch {
    loadedRows.value = []
    loadedTotal.value = 0
  } finally {
    loadedLoading.value = false
  }
}

function onPoolFilterChange() {
  poolPage.value = 1
  loadPool()
}

function onLoadedFilterChange() {
  loadedPage.value = 1
  loadLoaded()
}

function onPoolSizeChange() {
  poolPage.value = 1
  loadPool()
}

function onLoadedSizeChange() {
  loadedPage.value = 1
  loadLoaded()
}

function onPoolSortChange({ prop, order }: { prop?: string; order?: 'ascending' | 'descending' | null }) {
  poolSort.prop = prop || ''
  poolSort.order = order === 'ascending' ? 'asc' : order === 'descending' ? 'desc' : ''
  poolPage.value = 1
  loadPool()
}

function poolIndex(index: number): number {
  return (poolPage.value - 1) * poolPageSize.value + index + 1
}

function onPoolSelection(rows: PoolRow[]) {
  poolSelected.value = rows
}

function onLoadedSelection(rows: LoadedRow[]) {
  loadedSelected.value = rows.filter((row) => !isGroupRow(row as unknown as LoadedDisplayRow))
}

/* ------------------------------ 已配载分组 ------------------------------ */

const loadedDisplayRows = computed<LoadedDisplayRow[]>(() => {
  const groups = new Map<number, LoadedRow[]>()
  loadedRows.value.forEach((row) => {
    const key = row.tripDestId ?? 0
    const list = groups.get(key) || []
    list.push(row)
    groups.set(key, list)
  })

  const result: LoadedDisplayRow[] = []
  let seq = (loadedPage.value - 1) * loadedPageSize.value + 1
  const pushGroup = (tripDestId: number, rows: LoadedRow[], fallbackLabel: string) => {
    const dest = dests.value.find((item) => item.id === tripDestId)
    const quantity = rows.reduce((sum, row) => sum + Number(row.quantity || 0), 0)
    const weight = rows.reduce((sum, row) => sum + Number(row.weight || 0), 0)
    const volume = rows.reduce((sum, row) => sum + Number(row.volume || 0), 0)
    const totalFee = rows.reduce((sum, row) => sum + Number(row.totalFee || 0), 0)
    const subTripNo = rows[0]?.subTripNo || ''
    const label = `${subTripNo || fallbackLabel} → ${dest?.destDeptName || rows[0]?.destDeptName || '—'} · ${rows.length} 个单元 · ${formatQuantity(quantity)} 件 · ${formatQuantity(weight)} kg · ${formatQuantity(volume)} m³ · 运费 ${formatMoney(totalFee)}`
    result.push({
      __group: true,
      __key: `group-${tripDestId || 'unknown'}`,
      tripDestId,
      label,
      count: rows.length,
      quantity,
      weight,
      volume,
      totalFee
    })
    rows.forEach((row) => {
      result.push({ ...row, __seq: seq++ })
    })
  }

  dests.value.forEach((dest) => {
    const rows = groups.get(dest.id)
    if (rows && rows.length) {
      pushGroup(dest.id, rows, dest.subTripNo)
      groups.delete(dest.id)
    }
  })
  groups.forEach((rows, tripDestId) => {
    if (rows.length) pushGroup(tripDestId, rows, '未匹配子车次')
  })
  return result
})

function isGroupRow(row: LoadedDisplayRow | LoadedRow): row is GroupRow {
  return (row as GroupRow).__group === true
}

function loadedRowKey(row: LoadedDisplayRow): string | number {
  return isGroupRow(row) ? row.__key : `unit-${row.tripWaybillId}`
}

function isLoadedSelectable(row: LoadedDisplayRow): boolean {
  return !isGroupRow(row)
}

function loadedRowClass(ctx: { row?: LoadedDisplayRow } | LoadedDisplayRow): string {
  const row = (ctx as { row?: LoadedDisplayRow }).row || (ctx as LoadedDisplayRow)
  return isGroupRow(row) ? 'loaded-group-row' : ''
}

function loadedSpanMethod({ row, columnIndex }: { row: LoadedDisplayRow; columnIndex: number }) {
  if (!isGroupRow(row)) return undefined
  if (columnIndex === 1) return { rowspan: 1, colspan: loadedDataColumns.value.length }
  if (columnIndex > 1) return { rowspan: 0, colspan: 0 }
  return undefined
}

/* ------------------------------ 展示工具 ------------------------------ */

function poolValue(row: PoolRow, prop?: string): unknown {
  return prop ? (row as unknown as Record<string, unknown>)[prop] : undefined
}

function loadedValue(row: LoadedRow, prop?: string): unknown {
  return prop ? (row as unknown as Record<string, unknown>)[prop] : undefined
}

function poolUnitTypeText(value?: number): string {
  return value === 2 ? '母单余额' : value === 3 ? '子单' : '整票'
}

function loadedUnitTypeText(row: LoadedRow): string {
  return row.isChild === 1 ? '子单' : '整票'
}

function unitTypeTag(value?: number): 'primary' | 'info' | 'warning' {
  if (value === 2) return 'warning'
  if (value === 3) return 'primary'
  return 'info'
}

function moneyText(value: unknown): string {
  return value === undefined || value === null || value === '' ? '—' : formatMoney(value as number)
}

function dictText(options: ColumnOption[] | undefined, value: unknown): string {
  if (value === undefined || value === null || value === '') return '—'
  return options?.find((item) => item.value === value)?.label ?? String(value)
}

function qtyText(value: unknown): string {
  return value === undefined || value === null || value === '' ? '—' : formatQuantity(value as number)
}

function dtText(value: unknown): string {
  return formatDateTime(value as string | undefined)
}

function formatPercent(value: unknown): string {
  if (value === undefined || value === null || value === '') return '—'
  return `${(Number(value) * 100).toFixed(2)}%`
}

/* ------------------------------ 穿梭 ------------------------------ */

function canSplit(row: PoolRow): boolean {
  return canOperate.value && row.unitType !== 3 && Number(row.remainingQuantity || 0) > 1
}

function splitDisabledReason(row: PoolRow): string {
  if (!editable.value) return '车次已发车，不可拆单'
  if (row.unitType === 3) return '子单不可再拆'
  if (Number(row.remainingQuantity || 0) <= 1) return '件数为 1，不可拆单'
  return ''
}

function confirmOverLimit(rows: PoolRow[]): Promise<boolean> {
  const capacityW = Number(trip.value?.vehicleLoad || 0)
  const capacityV = Number(trip.value?.vehicleVolume || 0)
  if (!capacityW && !capacityV) return Promise.resolve(true)
  const addWeight = rows.reduce((sum, row) => sum + Number(row.remainingWeight || 0), 0)
  const addVolume = rows.reduce((sum, row) => sum + Number(row.remainingVolume || 0), 0)
  const nextWeight = Number(trip.value?.totalWeight || 0) + addWeight
  const nextVolume = Number(trip.value?.totalVolume || 0) + addVolume
  const parts: string[] = []
  if (capacityW > 0 && nextWeight > capacityW) parts.push(`超载 ${formatQuantity(nextWeight - capacityW)} kg`)
  if (capacityV > 0 && nextVolume > capacityV) parts.push(`超容 ${formatQuantity(nextVolume - capacityV)} m³`)
  if (!parts.length) return Promise.resolve(true)
  return ElMessageBox.confirm(`本次加入后${parts.join('，')}。超载/超容不影响继续配载，是否继续？`, '装载超限提醒', {
    type: 'warning',
    confirmButtonText: '继续配载',
    cancelButtonText: '取消'
  }).then(
    () => true,
    () => false
  )
}

async function doLoad(ids: number[]) {
  if (!ids.length) {
    ElMessage.warning('没有可配载的单元')
    return
  }
  try {
    const result = await loadTripWaybills(tripId, ids)
    if (result.successCount > 0) {
      ElMessage.success(`已配载 ${result.successCount} 个单元`)
    }
    if (result.failures?.length) {
      const detailText = result.failures.slice(0, 3).map((item) => `${item.waybillNo || item.waybillId}：${item.reason}`).join('；')
      ElMessage.error(`${result.failures.length} 个单元配载失败：${detailText}${result.failures.length > 3 ? ' 等' : ''}`)
    }
    if (result.warnings?.length) {
      ElMessage.warning(result.warnings.join('；'))
    }
    await refreshAfterChange()
  } catch {
    // 请求层按响应体 code 提示
  }
}

async function moveSelectedRight() {
  if (!canOperate.value) return
  const ids = poolSelected.value.map((row) => row.waybillId)
  if (!ids.length) return
  if (!(await confirmOverLimit(poolSelected.value))) return
  await doLoad(ids)
}

async function fetchAllPoolIds(): Promise<number[]> {
  const ids: number[] = []
  const size = 200
  let page = 1
  let total = 0
  let guard = 0
  do {
    const params = { ...buildPoolParams(), page, pageSize: size }
    const result = await getTripPool(tripId, params)
    ;(result.records || []).forEach((row) => {
      // 母单余额同样可直接右移：后端按剩余件数生成子单并与本车同母单子单合并
      ids.push(row.waybillId)
    })
    total = result.total || 0
    page += 1
    guard += 1
  } while ((page - 1) * size < total && guard < 30)
  return ids
}

async function moveAllRight() {
  if (!canOperate.value) return
  let ids: number[] = []
  try {
    ids = await fetchAllPoolIds()
  } catch {
    return
  }
  if (!ids.length) {
    ElMessage.warning('当前筛选结果没有可配载的单元')
    return
  }
  try {
    await ElMessageBox.confirm(`将把当前筛选的 ${ids.length} 个可配单元全部配载，是否继续？`, '全部右移', {
      type: 'warning',
      confirmButtonText: '继续配载',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await doLoad(ids)
}

async function doRemove(ids: number[]) {
  if (!ids.length) return
  try {
    const result = await removeTripWaybills(tripId, ids)
    if (result.successCount > 0) {
      ElMessage.success(`已移除 ${result.successCount} 个单元，运单回到待配载池`)
    }
    if (result.failures?.length) {
      const detailText = result.failures.slice(0, 3).map((item) => `${item.waybillNo || item.waybillId}：${item.reason}`).join('；')
      ElMessage.error(`${result.failures.length} 个单元移除失败：${detailText}`)
    }
    await refreshAfterChange()
  } catch {
    // 请求层按响应体 code 提示
  }
}

async function moveSelectedLeft() {
  if (!canOperate.value) return
  const ids = loadedSelected.value.map((row) => row.waybillId)
  if (!ids.length) return
  await doRemove(ids)
}

async function fetchAllLoadedIds(): Promise<number[]> {
  const ids: number[] = []
  const size = 200
  let page = 1
  let total = 0
  let guard = 0
  do {
    const params = { ...buildLoadedParams(), page, pageSize: size }
    const result = await getTripLoaded(tripId, params)
    ;(result.records || []).forEach((row) => ids.push(row.waybillId))
    total = result.total || 0
    page += 1
    guard += 1
  } while ((page - 1) * size < total && guard < 30)
  return ids
}

async function moveAllLeft() {
  if (!canOperate.value) return
  let ids: number[] = []
  try {
    ids = await fetchAllLoadedIds()
  } catch {
    return
  }
  if (!ids.length) return
  try {
    await ElMessageBox.confirm(`将把本车次已配载的 ${ids.length} 个单元全部移除，是否继续？`, '全选左移', {
      type: 'warning',
      confirmButtonText: '全部移除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await doRemove(ids)
}

async function refreshAfterChange() {
  await Promise.all([loadTripDetail(), loadPool(), loadLoaded()])
}

/* ------------------------------ 拆单 ------------------------------ */

const splitVisible = ref(false)
const splitRow = ref<PoolRow | null>(null)

function openSplit(row: PoolRow) {
  if (!canSplit(row)) {
    ElMessage.warning(splitDisabledReason(row) || '该单元不可拆单')
    return
  }
  splitRow.value = row
  splitVisible.value = true
}

async function onSplitDone() {
  await refreshAfterChange()
}

/* ------------------------------ 打开运单详情 ------------------------------ */

/** 单号点击统一跳运单详情页，不在此页另写只读弹窗 */
async function openWaybill(row: PoolRow | LoadedRow) {
  if (!(await saveBasicIfDirty())) return
  router.push(`/operation/waybill/${row.waybillId}`)
}

/* ------------------------------ 顶部保存/切换 ------------------------------ */

async function saveBasic(): Promise<boolean> {
  if (!editable.value) return true
  if (!basic.tripName.trim()) {
    ElMessage.warning('车次名称不能为空')
    return false
  }
  basicSaving.value = true
  try {
    await updateTrip(tripId, {
      tripName: basic.tripName.trim(),
      planDepartTime: basic.planDepartTime || undefined
    })
    basicBaseline.tripName = basic.tripName.trim()
    basicBaseline.planDepartTime = basic.planDepartTime
    ElMessage.success('车次信息已保存')
    await loadTripDetail()
    return true
  } catch {
    return false
  } finally {
    basicSaving.value = false
  }
}

async function switchTab(tab: 'load' | 'send') {
  if (tab === activeTab.value) return
  if (tab === 'send') {
    const ok = await saveBasicIfDirty()
    if (!ok) return
    await ensureSendData()
  }
  activeTab.value = tab
}

async function saveBasicIfDirty(): Promise<boolean> {
  if (!basicDirty.value) return true
  return saveBasic()
}

async function goNext() {
  if (!editable.value) return
  const ok = await saveBasicIfDirty()
  if (!ok) return
  await ensureSendData()
  activeTab.value = 'send'
  ElMessage.success('已进入「发车」Tab：请录入车辆信息与费用信息后发车')
}

/* ------------------------------ 子车次/日志/打印 ------------------------------ */

const destVisible = ref(false)
const logVisible = ref(false)
const printVisible = ref(false)

async function onDestRefresh() {
  await refreshAfterChange()
}

/* ------------------------------ Tab2 车辆 ------------------------------ */

const vehicleOptions = ref<TripOption[]>([])
const driverOptions = ref<TripOption[]>([])
const vehicleTypeOptions = ref<TripOption[]>([])
const vehicleTouched = ref(false)
const vehicle = reactive<{
  vehicleId?: number | null
  plateNo: string
  vehicleType: string
  vehicleLoad?: number
  vehicleVolume?: number
  driverId?: number | null
  driverName: string
  driverPhone: string
}>({
  vehicleId: null,
  plateNo: '',
  vehicleType: '',
  vehicleLoad: undefined,
  vehicleVolume: undefined,
  driverId: null,
  driverName: '',
  driverPhone: ''
})

const quickVehicleVisible = ref(false)
const quickDriverVisible = ref(false)
const quickSaving = ref(false)
const quickVehicle = reactive<{
  plateNo: string
  vehicleType: string
  vehicleLoad?: number
  vehicleVolume?: number
}>({ plateNo: '', vehicleType: '', vehicleLoad: undefined, vehicleVolume: undefined })
const quickDriver = reactive({ driverName: '', driverPhone: '' })

function syncVehicleFromTrip(value: TripRecord) {
  vehicle.vehicleId = value.vehicleId ?? null
  vehicle.plateNo = value.plateNo || ''
  vehicle.vehicleType = value.vehicleType || ''
  vehicle.vehicleLoad = value.vehicleLoad ?? undefined
  vehicle.vehicleVolume = value.vehicleVolume ?? undefined
  vehicle.driverId = value.driverId ?? null
  vehicle.driverName = value.driverName || ''
  vehicle.driverPhone = value.driverPhone || ''
  vehicleTouched.value = false
}

function onPlateChange(name: string) {
  vehicleTouched.value = true
  const option = vehicleOptions.value.find((item) => item.name === name)
  if (option) {
    vehicle.vehicleId = option.id
    vehicle.vehicleType = option.type || vehicle.vehicleType
    vehicle.vehicleLoad = option.load ?? vehicle.vehicleLoad
    vehicle.vehicleVolume = option.volume ?? vehicle.vehicleVolume
  } else {
    vehicle.vehicleId = null
  }
}

function onDriverChange(name: string) {
  vehicleTouched.value = true
  const option = driverOptions.value.find((item) => item.name === name)
  if (option) {
    vehicle.driverId = option.id
    vehicle.driverPhone = option.phone || vehicle.driverPhone
  } else {
    vehicle.driverId = null
  }
}

async function loadVehicleOptions() {
  if (vehicleOptions.value.length) return
  try {
    vehicleOptions.value = await getTripOptions('vehicle')
  } catch {
    // 请求层已提示
  }
}

async function loadDriverOptions() {
  if (driverOptions.value.length) return
  try {
    driverOptions.value = await getTripOptions('driver')
  } catch {
    // 请求层已提示
  }
}

/** 车型下拉：已有车辆车型 + 常用车型预设，缺省也能直接输入新车型 */
const VEHICLE_TYPE_PRESETS = ['4.2 米厢车', '6.8 米厢车', '9.6 米厢车', '13 米高栏', '17.5 米平板']

async function loadVehicleTypeOptions() {
  if (vehicleTypeOptions.value.length) return
  let names: string[] = []
  try {
    names = (await getTripOptions('vehicleType')).map((item) => item.name).filter(Boolean)
  } catch {
    // 请求层已提示
  }
  vehicleTypeOptions.value = Array.from(new Set([...names, ...VEHICLE_TYPE_PRESETS]))
    .map((name, index) => ({ id: index + 1, name, type: 'vehicleType' }))
}

function openQuickVehicle() {
  quickVehicle.plateNo = ''
  quickVehicle.vehicleType = ''
  quickVehicle.vehicleLoad = undefined
  quickVehicle.vehicleVolume = undefined
  void loadVehicleTypeOptions()
  quickVehicleVisible.value = true
}

async function submitQuickVehicle() {
  if (!quickVehicle.plateNo.trim()) {
    ElMessage.warning('车牌号不能为空')
    return
  }
  quickSaving.value = true
  try {
    const id = await quickCreateVehicle({
      plateNo: quickVehicle.plateNo.trim(),
      vehicleType: quickVehicle.vehicleType.trim() || undefined,
      vehicleLoad: quickVehicle.vehicleLoad,
      vehicleVolume: quickVehicle.vehicleVolume
    })
    vehicleOptions.value = []
    await loadVehicleOptions()
    const option = vehicleOptions.value.find((item) => item.id === id)
    vehicle.vehicleId = id
    vehicle.plateNo = option?.name || quickVehicle.plateNo.trim()
    vehicle.vehicleType = option?.type || quickVehicle.vehicleType
    vehicle.vehicleLoad = option?.load ?? quickVehicle.vehicleLoad
    vehicle.vehicleVolume = option?.volume ?? quickVehicle.vehicleVolume
    vehicleTouched.value = true
    ElMessage.success('车辆已新增并选中')
    quickVehicleVisible.value = false
  } catch {
    // 请求层已提示
  } finally {
    quickSaving.value = false
  }
}

function openQuickDriver() {
  quickDriver.driverName = ''
  quickDriver.driverPhone = ''
  quickDriverVisible.value = true
}

async function submitQuickDriver() {
  if (!quickDriver.driverName.trim() || !quickDriver.driverPhone.trim()) {
    ElMessage.warning('司机姓名与电话必填')
    return
  }
  quickSaving.value = true
  try {
    const id = await quickCreateDriver({
      driverName: quickDriver.driverName.trim(),
      driverPhone: quickDriver.driverPhone.trim()
    })
    driverOptions.value = []
    await loadDriverOptions()
    const option = driverOptions.value.find((item) => item.id === id)
    vehicle.driverId = id
    vehicle.driverName = option?.name || quickDriver.driverName.trim()
    vehicle.driverPhone = option?.phone || quickDriver.driverPhone.trim()
    vehicleTouched.value = true
    ElMessage.success('司机已新增并选中')
    quickDriverVisible.value = false
  } catch {
    // 请求层已提示
  } finally {
    quickSaving.value = false
  }
}

/* ------------------------------ Tab2 费用 ------------------------------ */

const COST_FIELD_BY_SOURCE: Record<string, string> = {
  'biz_trip.prepaid_transport_fee': 'prepaidTransportFee',
  'biz_trip.return_transport_fee': 'returnTransportFee',
  'biz_trip.arrival_transport_fee': 'arrivalTransportFee',
  'biz_trip.prepaid_oil_card_fee': 'prepaidOilCardFee',
  'biz_trip.whole_weighing_fee': 'wholeWeighingFee',
  'biz_trip.whole_insurance_fee': 'wholeInsuranceFee',
  'biz_trip.bulk_load_fee': 'bulkLoadFee',
  'biz_trip.bulk_unload_fee': 'bulkUnloadFee',
  'biz_trip.bulk_other_fee': 'bulkOtherFee',
  'biz_trip.total_pickup_cost': 'totalPickupCost',
  'biz_trip.total_delivery_fee': 'totalDeliveryFee',
  'biz_trip.total_transfer_fee': 'totalTransferFee'
}

const costFeeTypes = ref<FeeTypeRow[]>([])
const feeAmounts = reactive<Record<string, number>>({})
const allocMethod = ref(3)
const feePreview = ref<FeePreview | null>(null)
const feePreviewLoading = ref(false)
const feePreviewVisible = ref(false)
const feeSaving = ref(false)
const feeInitialized = ref(false)
/** 到站付运输费：按目的网点录入的金额 */
const arrivalTransportType = ref<FeeTypeRow | null>(null)
const destFeeAmounts = reactive<Record<number, number>>({})
let feeTypesLoaded = false
let destFeeInitialized = false
let feePreviewTimer: number | undefined
let feePreviewToken = 0

const destFeeRows = computed(() =>
  dests.value.map((dest) => {
    const saved = feePreview.value?.destTransportFees?.find((item) => item.tripDestId === dest.id)
    return {
      tripDestId: dest.id,
      destDeptName: dest.destDeptName,
      feeCode: arrivalTransportType.value?.feeCode || saved?.feeCode || ''
    }
  })
)

type FeeMatrixType = FeeTypeRow & { arrival: boolean }
type FeeMatrixRow =
  | { key: string; kind: 'trip'; rowName: string; role: '发站' }
  | { key: string; kind: 'dest'; rowName: string; role: '到站'; tripDestId: number }

/** 费用矩阵：列=费用项，行=费用归属（发站行 / 各到站网点行） */
const feeMatrixTypes = computed<FeeMatrixType[]>(() => {
  const list: FeeMatrixType[] = costFeeTypes.value.map((type) => ({ ...type, arrival: false }))
  const arrival = arrivalTransportType.value
  if (!arrival) {
    return list
  }
  // 到站付运输费紧跟回付运输费
  const returnIndex = list.findIndex((type) => type.sourceField === 'biz_trip.return_transport_fee')
  list.splice(returnIndex >= 0 ? returnIndex + 1 : Math.min(2, list.length), 0, {
    ...arrival,
    arrival: true
  })
  return list
})

const feeMatrixRows = computed<FeeMatrixRow[]>(() => [
  { key: 'trip', kind: 'trip', rowName: trip.value?.originDeptName || '始发站', role: '发站' },
  ...dests.value.map((dest) => ({
    key: `dest-${dest.id}`,
    kind: 'dest' as const,
    rowName: dest.destDeptName,
    role: '到站' as const,
    tripDestId: dest.id
  }))
])

/** 车次行只录车次费用；网点行只录到站付运输费 */
function cellEditable(row: FeeMatrixRow, type: FeeMatrixType): boolean {
  return row.kind === 'trip' ? !type.arrival : type.arrival && !!type.feeCode
}

function cellValue(row: FeeMatrixRow, type: FeeMatrixType): number {
  if (row.kind === 'trip') {
    return Number(feeAmounts[type.feeCode] || 0)
  }
  return Number(destFeeAmounts[row.tripDestId] || 0)
}

function setCellValue(row: FeeMatrixRow, type: FeeMatrixType, value: number | undefined) {
  const amount = Number(value || 0)
  if (row.kind === 'trip') {
    feeAmounts[type.feeCode] = amount
  } else {
    destFeeAmounts[row.tripDestId] = amount
  }
  onFeeChanged()
}

const weightRate = computed(() => {
  const capacity = Number(trip.value?.vehicleLoad || 0)
  return capacity > 0 ? Number(trip.value?.totalWeight || 0) / capacity : 0
})
const volumeRate = computed(() => {
  const capacity = Number(trip.value?.vehicleVolume || 0)
  return capacity > 0 ? Number(trip.value?.totalVolume || 0) / capacity : 0
})

/** 收入合计 = 现付 + 到付 + 回付 + 月结（与上方四项口径一致） */
const incomeBriefTotal = computed(
  () =>
    Number(trip.value?.totalPrepaidFreight || 0) +
    Number(trip.value?.totalCollectFreight || 0) +
    Number(trip.value?.totalReturnFreight || 0) +
    Number(trip.value?.totalMonthlyFreight || 0)
)

function initFeeAmounts(value: TripRecord) {
  if (!costFeeTypes.value.length) {
    feeInitialized.value = true
    return
  }
  costFeeTypes.value.forEach((type) => {
    const field = type.sourceField ? COST_FIELD_BY_SOURCE[type.sourceField] : undefined
    const raw = field ? (value as unknown as Record<string, unknown>)[field] : 0
    feeAmounts[type.feeCode] = Number(raw || 0)
  })
  feeInitialized.value = true
}

async function ensureSendData() {
  await Promise.all([loadVehicleOptions(), loadDriverOptions()])
  if (!feeTypesLoaded) {
    try {
      const page = await getFeeTypePage({ feeCategory: 2, enabled: true, page: 1, pageSize: 200 })
      const enabledTypes = (page.rows || []).filter((row) => row.enabled !== false && row.feeCategory === 2)
      // 到站付运输费为子车次级：按目的网点单独录入，不进车次级成本列表
      arrivalTransportType.value =
        enabledTypes.find((row) => row.sourceField === 'biz_trip_dest.arrival_transport_fee') || null
      costFeeTypes.value = enabledTypes.filter((row) => (row.sourceField || '').startsWith('biz_trip.'))
      feeTypesLoaded = true
      if (trip.value) {
        allocMethod.value = trip.value.allocMethod || 3
        initFeeAmounts(trip.value)
      }
    } catch {
      // 请求层已提示
    }
  }
  scheduleFeePreview()
}

function initDestFeeAmounts(preview: FeePreview | null) {
  if (destFeeInitialized) return
  const rows = preview?.destTransportFees || []
  if (!rows.length) return
  rows.forEach((item) => {
    destFeeAmounts[item.tripDestId] = Number(item.amount || 0)
  })
  destFeeInitialized = true
}

function feePayload() {
  const arrival = arrivalTransportType.value
  // 首次预览先不带网点金额，由服务端回填已保存值，避免用空值覆盖历史录入
  const destItems = arrival && destFeeInitialized
    ? destFeeRows.value.map((row) => ({
        tripDestId: row.tripDestId,
        feeCode: arrival.feeCode,
        amount: Number(destFeeAmounts[row.tripDestId] || 0)
      }))
    : []
  return {
    allocMethod: allocMethod.value,
    items: costFeeTypes.value.map((type) => ({
      feeCode: type.feeCode,
      amount: Number(feeAmounts[type.feeCode] || 0)
    })),
    destItems
  }
}

function onFeeChanged() {
  scheduleFeePreview()
}

function scheduleFeePreview() {
  if (feePreviewTimer) window.clearTimeout(feePreviewTimer)
  feePreviewTimer = window.setTimeout(() => requestFeePreview(), 320)
}

async function requestFeePreview() {
  if (!tripId) return
  const token = ++feePreviewToken
  feePreviewLoading.value = true
  try {
    const result = await previewTripFees(tripId, feePayload())
    if (token !== feePreviewToken) return
    feePreview.value = result
    initDestFeeAmounts(result)
  } catch {
    if (token !== feePreviewToken) return
    feePreview.value = null
  } finally {
    if (token === feePreviewToken) feePreviewLoading.value = false
  }
}

async function saveFees(): Promise<boolean> {
  if (!editable.value) return false
  if (!costFeeTypes.value.length) {
    ElMessage.warning('未加载到车次级成本项，无法保存费用')
    return false
  }
  feeSaving.value = true
  try {
    const result = await saveTripFees(tripId, feePayload())
    feePreview.value = result
    initDestFeeAmounts(result)
    ElMessage.success('费用已保存，分摊与毛利已重算')
    await loadTripDetail()
    return true
  } catch {
    return false
  } finally {
    feeSaving.value = false
  }
}

/* ------------------------------ Tab2 发车 ------------------------------ */

const departVisible = ref(false)
const departSaving = ref(false)
const planArriveTime = ref('')

function defaultPlanArrive(): string {
  const date = new Date(Date.now() + 6 * 3600 * 1000)
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:00`
}

function openDepart() {
  if (!editable.value) return
  if (!loadedSummary.value.unitCount) {
    ElMessage.warning('至少需要 1 个配载单元才能发车')
    return
  }
  if (!vehicle.plateNo.trim()) {
    ElMessage.warning('请填写车牌号')
    return
  }
  if (!vehicle.driverName.trim()) {
    ElMessage.warning('请填写司机姓名')
    return
  }
  planArriveTime.value = defaultPlanArrive()
  departVisible.value = true
}

async function confirmDepart() {
  if (!planArriveTime.value) {
    ElMessage.warning('请选择预计到达时间')
    return
  }
  departSaving.value = true
  try {
    await saveTripVehicle(tripId, {
      vehicleId: vehicle.vehicleId ?? null,
      plateNo: vehicle.plateNo.trim(),
      vehicleType: vehicle.vehicleType.trim() || undefined,
      vehicleLoad: vehicle.vehicleLoad ?? null,
      vehicleVolume: vehicle.vehicleVolume ?? null,
      driverId: vehicle.driverId ?? null,
      driverName: vehicle.driverName.trim(),
      driverPhone: vehicle.driverPhone.trim() || undefined
    })
    if (costFeeTypes.value.length) {
      const feeOk = await saveFees()
      if (!feeOk) return
    }
    await departTrip(tripId, planArriveTime.value)
    ElMessage.success('已发车')
    departVisible.value = false
    vehicleTouched.value = false
    await refreshAfterChange()
    activeTab.value = 'send'
  } catch {
    // 请求层按响应体 code 提示
  } finally {
    departSaving.value = false
  }
}

async function cancelDepart() {
  let reason = ''
  try {
    const result = await ElMessageBox.prompt('请输入取消发车原因', '取消发车', {
      confirmButtonText: '确认取消发车',
      cancelButtonText: '返回',
      inputPlaceholder: '必填',
      inputValidator: (value: string) => (value && value.trim() ? true : '请填写原因')
    })
    reason = result.value.trim()
  } catch {
    return
  }
  try {
    await cancelDepartTrip(tripId, reason)
    ElMessage.success('已取消发车')
    await refreshAfterChange()
  } catch {
    // 请求层已提示
  }
}

/* ------------------------------ 列设置 ------------------------------ */

async function handlePoolColumnSave() {
  await savePoolColumnConfig()
  poolPage.value = 1
  await loadPool()
}

async function handlePoolColumnReset() {
  await resetPoolColumnConfig()
  poolTableRef.value?.resetColumnWidths()
  poolPage.value = 1
  await loadPool()
}

async function handleLoadedColumnSave() {
  await saveLoadedColumnConfig()
  loadedPage.value = 1
  await loadLoaded()
}

async function handleLoadedColumnReset() {
  await resetLoadedColumnConfig()
  loadedTableRef.value?.resetColumnWidths()
  loadedPage.value = 1
  await loadLoaded()
}

/* ------------------------------ 初始化 ------------------------------ */

onMounted(async () => {
  if (!tripId || Number.isNaN(tripId)) {
    ElMessage.error('缺少车次参数')
    return
  }
  await Promise.all([loadTripDetail(), loadPoolColumnConfig(), loadLoadedColumnConfig()])
  initFilterState(poolSchema, poolFilters)
  initFilterState(loadedSchema, loadedFilters)
  applyDefaultFilters(poolSchema, poolDefaultFiltersRaw(), poolFilters, poolTextModes)
  applyDefaultFilters(loadedSchema, loadedDefaultFiltersRaw(), loadedFilters, loadedTextModes)
  await Promise.all([loadPool(), loadLoaded()])
})

watch(
  () => route.query.id,
  (value, oldValue) => {
    if (!value || value === oldValue) return
    window.location.reload()
  }
)
</script>

<style scoped>
.trip-detail {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
}

/* Tab */
.detail-tabs {
  display: flex;
  gap: 4px;
  padding-bottom: 0;
  border-bottom: 1px solid var(--color-border-light);
}

.detail-tab {
  position: relative;
  padding: 8px 18px;
  border: 1px solid transparent;
  border-bottom: none;
  border-radius: var(--radius-sm) var(--radius-sm) 0 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-body);
  cursor: pointer;
  user-select: none;
}

.detail-tab.is-on {
  background: var(--color-bg-container);
  border-color: var(--color-border-light);
  color: var(--color-primary);
  font-weight: 600;
}

.tab-badge {
  display: inline-block;
  margin-left: 6px;
  padding: 0 6px;
  border-radius: 8px;
  background: var(--color-bg-subtle);
  color: var(--color-text-secondary);
  font-size: 11px;
  font-weight: 400;
  line-height: 18px;
}

/* 信息条 */
.info-card {
  padding: 10px 14px;
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-sm);
  background: var(--color-bg-container);
}

.info-row1 {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 10px;
  padding-bottom: 8px;
  border-bottom: 1px dashed var(--color-border-light);
}

.trip-no {
  font-family: var(--font-family-number);
  font-size: 15px;
  font-weight: 600;
}

.category-tag {
  padding: 0 6px;
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-xs);
  color: var(--color-text-secondary);
  font-size: var(--font-size-extra-small);
  line-height: 18px;
}

.info-kv {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-extra-small);
  white-space: nowrap;
}

.basic-name {
  width: 130px;
}

.basic-time {
  width: 150px;
}

.info-actions {
  display: flex;
  gap: 0;
  margin-left: auto;
}

.info-row2 {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 18px;
  padding-top: 8px;
  font-size: var(--font-size-extra-small);
}

.route-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.route-link {
  padding: 0;
  border: none;
  background: none;
  color: var(--color-primary);
  cursor: pointer;
  font-size: inherit;
}

.route-link:hover {
  text-decoration: underline;
}

.dest-chips {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.dest-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 180px;
  height: 28px;
  padding: 0 8px 0 10px;
  /* 实线边框 + 白底 + 下拉箭头：与 el-input/el-select 同视觉，一眼可编辑 */
  border: 1px solid var(--color-border-base);
  border-radius: var(--radius-sm);
  background: var(--color-bg-container);
  color: var(--color-text-primary);
  font-size: var(--font-size-extra-small);
  line-height: 26px;
}

.dest-chip.is-editable {
  cursor: pointer;
}

.dest-chip.is-editable:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.dest-chip__caret {
  flex: none;
  width: 0;
  height: 0;
  margin-left: 2px;
  border-left: 4px solid transparent;
  border-right: 4px solid transparent;
  border-top: 5px solid var(--color-text-placeholder);
}

.dest-chip.is-editable:hover .dest-chip__caret {
  border-top-color: var(--color-primary);
}

.dest-chip__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dest-chip__x {
  padding: 0 0 0 2px;
  border: none;
  background: none;
  color: var(--color-text-placeholder);
  cursor: pointer;
  font-size: 13px;
  line-height: 1;
}

.dest-chip__x:hover {
  color: var(--color-danger);
}

.dest-chip__select {
  width: 150px;
}

.dest-add {
  height: 28px;
  padding: 0 10px;
  border: 1px dashed var(--color-border-base);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
  font-size: var(--font-size-extra-small);
  line-height: 26px;
}

.dest-add:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.route-arrow {
  color: var(--color-text-placeholder);
}

.alloc-readonly b {
  color: var(--color-text-primary);
}

.subtle {
  color: var(--color-text-placeholder);
  font-size: var(--font-size-extra-small);
}

.warn-text {
  flex: 1;
  min-width: 140px;
  color: var(--color-text-secondary);
}

.warn-text.is-amber {
  color: var(--color-warning);
}

.capacity-text {
  color: var(--color-text-placeholder);
}

/* 双栏 */
.dual {
  display: flex;
  gap: 0;
  align-items: stretch;
  flex: 1;
  min-height: 0;
}

.pane {
  display: flex;
  flex-direction: column;
  width: calc((100% - 56px) / 2);
  min-width: 0;
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-sm);
  background: var(--color-bg-container);
}

.pane-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  padding: 8px 10px;
  border-bottom: 1px solid var(--color-border-light);
}

.pane-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--font-size-body);
  font-weight: 600;
}

.pane-count {
  display: inline-block;
  min-width: 20px;
  padding: 0 6px;
  border-radius: 8px;
  background: var(--color-bg-subtle);
  color: var(--color-text-secondary);
  font-size: 11px;
  font-weight: 400;
  line-height: 18px;
  text-align: center;
}

.pane-tools {
  display: flex;
  align-items: center;
  gap: 6px;
}

.pane-table {
  flex: 1;
  min-height: 300px;
}

.pane-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 10px;
  border-top: 1px solid var(--color-border-light);
  color: var(--color-text-secondary);
  font-size: var(--font-size-extra-small);
}

.transfer-col {
  position: relative;
  width: 56px;
  flex: none;
  padding: 0;
}

.transfer-group {
  position: absolute;
  top: 50%;
  left: 50%;
  display: flex;
  flex-direction: column;
  gap: 10px;
  transform: translate(-50%, -50%);
}

.transfer-btn {
  width: 40px;
  height: 30px;
  margin: 0;
  padding: 0;
  font-size: 16px;
  line-height: 1;
}

.col-head {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.col-head__label {
  white-space: nowrap;
  font-size: 12px;
}

.cell-text {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mono {
  font-family: var(--font-family-number);
}

.group-label {
  display: block;
  width: 100%;
  white-space: nowrap;
  text-align: left;
  font-weight: 600;
  color: var(--color-text-primary);
}

:deep(.loaded-group-row > td) {
  background: var(--color-bg-subtle) !important;
}

:deep(.loaded-group-row .el-checkbox) {
  visibility: hidden;
}

.btn-wrap {
  display: inline-flex;
}

/* 底部 */
.detail-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
  padding: 8px 12px;
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-sm);
  background: var(--color-bg-container);
}

.totals {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 16px;
  font-size: var(--font-size-extra-small);
}

.totals b {
  color: var(--color-text-primary);
}

.acts {
  display: flex;
  gap: 8px;
  margin-left: auto;
}

/* Tab2 */
.send-wrap {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.card {
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-sm);
  background: var(--color-bg-container);
}

.card-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 14px;
  border-bottom: 1px solid var(--color-border-light);
}

.mt12 {
  margin-top: 12px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px 16px;
  padding: 12px 14px;
}

.form-item {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.form-item label {
  flex: none;
  width: 92px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-extra-small);
  text-align: right;
  white-space: nowrap;
}

.req {
  color: var(--color-danger);
  margin-left: 2px;
}

.input-with-add {
  display: flex;
  gap: 6px;
  flex: 1;
  min-width: 0;
}

.w-full {
  width: 100%;
}

.alloc-select {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--font-size-extra-small);
}

.alloc-select__input {
  width: 140px;
}

.fee-head-tools {
  display: flex;
  align-items: center;
  gap: 16px;
}

.fee-preview-entry {
  font-size: var(--font-size-extra-small);
}

.fee-preview-entry__count {
  margin-left: 4px;
  color: var(--color-text-placeholder);
}

.fee-main {
  padding: 12px 14px;
}

.fee-block h4 {
  margin: 0 0 8px;
  font-size: var(--font-size-extra-small);
  font-weight: 600;
}

.fee-matrix :deep(.el-table__cell) {
  padding: 5px 0;
}

.fee-owner {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.fee-owner__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fee-matrix :deep(.el-input-number) {
  width: 118px;
}

.fee-disabled {
  color: var(--color-text-placeholder);
  font-size: var(--font-size-extra-small);
}

.trip-summary {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 14px;
}

.summary-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 18px;
  color: var(--color-text-placeholder);
  font-size: var(--font-size-extra-small);
}

.summary-row b {
  margin-left: 4px;
  font-family: var(--font-family-number);
  font-weight: 600;
  color: var(--color-text-secondary);
}

.summary-label {
  min-width: 64px;
  color: var(--color-text-secondary);
  font-weight: 600;
}

.summary-note {
  margin-left: 6px;
  color: var(--color-text-placeholder);
  font-size: var(--font-size-extra-small);
  font-style: normal;
}

.summary-row .is-income {
  color: var(--color-primary);
}

.summary-row .is-cost {
  color: var(--color-warning);
}

.summary-row .is-profit {
  color: var(--color-success);
}

.fee-preview-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.fee-preview-sum {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 18px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-extra-small);
}

.fee-preview-sum b {
  font-family: var(--font-family-number);
  color: var(--color-text-primary);
}

.fee-preview-sum .is-profit {
  color: var(--color-success);
}

.fee-hint,
.fee-empty {
  margin-top: 8px;
  color: var(--color-text-placeholder);
  font-size: var(--font-size-extra-small);
  line-height: 1.6;
}

.is-profit {
  color: var(--color-success);
}

.is-loss {
  color: var(--color-danger);
}

.over-text {
  color: var(--color-warning);
}

.dialog-hint {
  padding: 8px 10px;
  border-radius: var(--radius-xs);
  background: var(--color-bg-subtle);
  color: var(--color-text-secondary);
  font-size: var(--font-size-extra-small);
  line-height: 1.7;
}

@media (max-width: 1180px) {
  .form-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
