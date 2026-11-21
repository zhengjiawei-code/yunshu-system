<template>
  <page-drawer v-model="visible" :title="drawerTitle" :size="640">
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="100px"
      label-position="right"
      class="dept-form"
    >
      <el-form-item label="上级机构" prop="parentId">
        <span v-if="isHQ" class="readonly-text">—（内置总部为树根，不可移动）</span>
        <el-tree-select
          v-else
          v-model="form.parentId"
          :data="parentOptions"
          :props="{ label: 'deptName', children: 'children', disabled: 'disabled' }"
          node-key="id"
          check-strictly
          default-expand-all
          :render-after-expand="false"
          placeholder="请选择上级机构"
          class="form-control"
        />
      </el-form-item>

      <el-form-item label="机构类型" prop="deptType">
        <el-select v-model="form.deptType" class="form-control" :disabled="isHQ">
          <el-option
            v-for="item in typeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
            :disabled="item.value === 1 || typeNotAllowed(item.value)"
          />
        </el-select>
        <p v-if="!isHQ && selectedParent" class="form-hint">
          上级为「{{ selectedParent.deptName }}」，仅允许：{{ allowedTypeNames }}
        </p>
      </el-form-item>

      <el-form-item label="机构性质" prop="deptNature">
        <el-select v-model="form.deptNature" class="form-control" :disabled="isHQ">
          <el-option
            v-for="item in natureOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <p v-if="isHQ" class="form-hint">内置总部固定为自营，不可修改</p>
      </el-form-item>

      <el-form-item label="是否末端">
        <el-checkbox v-model="form.isLeaf" :disabled="form.deptType !== 5">末端网点</el-checkbox>
        <p class="form-hint">
          {{ form.deptType === 5
            ? '仅表示该网点是否为末端网点，与其下是否挂有机构无关'
            : '仅机构类型为网点时可设置，其它类型已置灰并清空' }}
        </p>
      </el-form-item>

      <el-form-item label="机构名称" prop="deptName">
        <el-input v-model="form.deptName" maxlength="50" show-word-limit placeholder="2-50 个字" />
      </el-form-item>

      <el-form-item label="机构编码" prop="deptCode">
        <el-input
          v-model="form.deptCode"
          :disabled="isEdit"
          maxlength="20"
          placeholder="2-20 位字母、数字或下划线"
        >
          <template v-if="isEdit" #suffix>
            <el-tooltip content="机构编码创建后不可修改" placement="top">
              <el-icon><Lock /></el-icon>
            </el-tooltip>
          </template>
        </el-input>
        <p v-if="isEdit" class="form-hint">编码用于对接与数据导入，创建后不可修改</p>
      </el-form-item>

      <el-form-item label="负责人" prop="leader">
        <el-input v-model="form.leader" maxlength="20" placeholder="选填" />
      </el-form-item>

      <el-form-item label="联系电话" prop="phone">
        <el-input v-model="form.phone" maxlength="20" placeholder="选填，手机号或座机号" />
      </el-form-item>

      <el-form-item label="所在地区" prop="provinceCode">
        <div class="region-row">
          <el-select
            v-model="form.provinceCode"
            placeholder="省"
            clearable
            filterable
            class="region-select"
            @change="handleProvinceChange"
          >
            <el-option
              v-for="item in provinceOptions"
              :key="item.code"
              :label="item.name"
              :value="item.code"
            />
          </el-select>
          <el-select
            v-model="form.cityCode"
            placeholder="市"
            clearable
            filterable
            class="region-select"
            :disabled="!form.provinceCode"
            @change="handleCityChange"
          >
            <el-option
              v-for="item in cityOptions"
              :key="item.code"
              :label="item.name"
              :value="item.code"
            />
          </el-select>
          <el-select
            v-model="form.districtCode"
            placeholder="区县"
            clearable
            filterable
            class="region-select"
            :disabled="!form.cityCode"
            @change="clearRegionValidate"
          >
            <el-option
              v-for="item in districtOptions"
              :key="item.code"
              :label="item.name"
              :value="item.code"
            />
          </el-select>
        </div>
        <p class="form-hint">
          {{ regionRequired ? '分拨中心、网点必须选择省/市/区县' : '选填；填写时需省/市/区县同时选择' }}
        </p>
      </el-form-item>

      <el-form-item label="详细地址" prop="address">
        <el-input v-model="form.address" maxlength="200" placeholder="选填，街道门牌等详细地址" />
      </el-form-item>

      <el-form-item
        v-if="form.deptType === 5 && form.isLeaf"
        label="适用公司"
        prop="applyCompanyIds"
      >
        <el-select
          v-model="form.applyCompanyIds"
          multiple
          filterable
          collapse-tags
          collapse-tags-tooltip
          class="form-control"
          placeholder="请选择适用分公司"
        >
          <el-option
            v-for="item in companyOptions"
            :key="item.id"
            :label="item.deptName"
            :value="item.id"
          />
        </el-select>
        <p class="form-hint">
          末端网点可被多个分公司共用；分公司管理员默认本公司，总部创建需手动选择
        </p>
      </el-form-item>

      <el-form-item label="排序" prop="sortOrder">
        <el-input-number
          v-model="form.sortOrder"
          :min="0"
          :max="9999"
          :step="10"
          controls-position="right"
        />
      </el-form-item>

      <el-form-item label="状态">
        <el-switch
          v-model="form.status"
          inline-prompt
          active-text="启用"
          inactive-text="禁用"
          :disabled="isHQ"
        />
        <p v-if="isHQ" class="form-hint">内置总部不可禁用</p>
      </el-form-item>

      <el-form-item label="备注" prop="remark">
        <el-input
          v-model="form.remark"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          placeholder="选填"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
    </template>
  </page-drawer>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Lock } from '@element-plus/icons-vue'
import type { DeptNature, DeptNode, DeptSavePayload, DeptType } from '@/types/dept'
import type { RegionNode } from '@/types/region'
import { createDept, updateDept } from '@/api/system/dept'
import { getRegionChildren } from '@/api/system/region'
import { useUserStore } from '@/stores/user'
import PageDrawer from '@/components/base/PageDrawer.vue'

const props = defineProps<{
  modelValue: boolean
  mode: 'create' | 'edit'
  parentId: number
  record: DeptNode | null
  tree: DeptNode[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: []
}>()

/** 父子允许矩阵：父类型 → 允许的子类型（与后端 Service 一致） */
const ALLOWED_CHILD: Record<number, DeptType[]> = {
  1: [2, 3, 4],
  2: [2, 3, 4, 5],
  3: [3],
  4: [3, 5],
  5: [3]
}

const TYPE_NAME: Record<number, string> = {
  1: '总部',
  2: '公司',
  3: '部门',
  4: '分拨中心',
  5: '网点'
}

const typeOptions: Array<{ label: string; value: DeptType }> = [
  { label: '总部', value: 1 },
  { label: '公司', value: 2 },
  { label: '部门', value: 3 },
  { label: '分拨中心', value: 4 },
  { label: '网点', value: 5 }
]

const natureOptions: Array<{ label: string; value: DeptNature }> = [
  { label: '自营', value: 1 },
  { label: '加盟', value: 2 },
  { label: '合作', value: 3 }
]

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const formRef = ref<FormInstance>()
const submitting = ref(false)
const userStore = useUserStore()

const form = reactive({
  parentId: 0 as number | null,
  deptType: 3 as DeptType,
  deptNature: 1 as DeptNature,
  isLeaf: false,
  deptName: '',
  deptCode: '',
  leader: '',
  phone: '',
  address: '',
  provinceCode: '',
  cityCode: '',
  districtCode: '',
  applyCompanyIds: [] as number[],
  sortOrder: 10,
  status: true,
  remark: ''
})

const provinceOptions = ref<RegionNode[]>([])
const cityOptions = ref<RegionNode[]>([])
const districtOptions = ref<RegionNode[]>([])

const regionRequired = computed(() => form.deptType === 4 || form.deptType === 5)

const companyOptions = computed(() => {
  const result: DeptNode[] = []
  const walk = (nodes: DeptNode[]) => {
    nodes.forEach((node) => {
      if (node.deptType === 2 && node.status) result.push(node)
      if (node.children) walk(node.children)
    })
  }
  walk(props.tree)
  return result
})

/** 当前登录人所属公司：主机构向上最近的公司；总部返回 undefined */
const defaultCompanyId = computed(() => {
  let current = userStore.user?.deptId ? findDept(props.tree, userStore.user.deptId) : undefined
  let guard = 0
  while (current && guard++ < 100) {
    if (current.deptType === 2) return current.id
    if (current.deptType === 1) return undefined
    current = current.parentId ? findDept(props.tree, current.parentId) : undefined
  }
  return undefined
})

const isEdit = computed(() => props.mode === 'edit')
const isHQ = computed(() => isEdit.value && props.record?.id === 1)
const drawerTitle = computed(() => (isEdit.value ? '编辑机构' : '新增机构'))

const selectedParent = computed(() =>
  form.parentId ? findDept(props.tree, form.parentId) : undefined
)

const allowedTypeNames = computed(() => {
  const parent = selectedParent.value
  if (!parent) return ''
  return (ALLOWED_CHILD[parent.deptType] || []).map((type) => TYPE_NAME[type]).join('、')
})

function typeNotAllowed(type: DeptType): boolean {
  if (isHQ.value) return false
  const parent = selectedParent.value
  if (!parent) return false
  return !(ALLOWED_CHILD[parent.deptType] || []).includes(type)
}

const parentOptions = computed(() =>
  buildParentOptions(props.tree, props.record?.id, props.record?.parentId)
)

/* ---------- 校验 ---------- */
function findDept(nodes: DeptNode[], id: number): DeptNode | undefined {
  for (const node of nodes) {
    if (node.id === id) return node
    const found = node.children ? findDept(node.children, id) : undefined
    if (found) return found
  }
  return undefined
}

function subtreeHeight(node: DeptNode): number {
  if (!node.children?.length) return 0
  return 1 + Math.max(...node.children.map(subtreeHeight))
}

function depthOf(id: number): number {
  let depth = 0
  let current: DeptNode | undefined = findDept(props.tree, id)
  while (current) {
    depth++
    if (!current.parentId) break
    current = findDept(props.tree, current.parentId)
  }
  return depth
}

function siblingsOf(parentId: number | null): DeptNode[] {
  if (!parentId) return props.tree
  return findDept(props.tree, parentId)?.children || []
}

function nameExists(name: string, parentId: number | null): boolean {
  const trimmed = name.trim()
  return siblingsOf(parentId).some(
    (node) => node.deptName === trimmed && node.id !== props.record?.id
  )
}

/* ---------- 区划联动 ---------- */
async function loadProvinces() {
  provinceOptions.value = await getRegionChildren('0')
}

async function loadCities(provinceCode: string) {
  cityOptions.value = provinceCode ? await getRegionChildren(provinceCode) : []
}

async function loadDistricts(cityCode: string) {
  districtOptions.value = cityCode ? await getRegionChildren(cityCode) : []
}

async function initRegionOptions() {
  try {
    await loadProvinces()
    if (form.provinceCode) await loadCities(form.provinceCode)
    if (form.cityCode) await loadDistricts(form.cityCode)
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  }
}

async function handleProvinceChange(value: string) {
  form.cityCode = ''
  form.districtCode = ''
  districtOptions.value = []
  try {
    await loadCities(value)
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  }
  clearRegionValidate()
}

async function handleCityChange(value: string) {
  form.districtCode = ''
  try {
    await loadDistricts(value)
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  }
  clearRegionValidate()
}

function clearRegionValidate() {
  formRef.value?.clearValidate('provinceCode')
}

const rules: FormRules = {
  parentId: [
    {
      validator: (_rule, value, callback) => {
        if (isHQ.value) return callback()
        if (!value) return callback(new Error('请选择上级机构'))
        callback()
      },
      trigger: 'change'
    }
  ],
  deptType: [{ required: true, message: '请选择机构类型', trigger: 'change' }],
  deptNature: [{ required: true, message: '请选择机构性质', trigger: 'change' }],
  deptName: [
    { required: true, message: '请输入机构名称', trigger: 'blur' },
    { min: 2, max: 50, message: '机构名称为 2-50 个字', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value && nameExists(value, form.parentId)) {
          return callback(new Error('同一上级下已存在同名机构'))
        }
        callback()
      },
      trigger: 'blur'
    }
  ],
  deptCode: [
    { required: true, message: '请输入机构编码', trigger: 'blur' },
    {
      pattern: /^[A-Za-z0-9_]{2,20}$/,
      message: '机构编码为 2-20 位字母、数字或下划线',
      trigger: 'blur'
    }
  ],
  leader: [{ max: 20, message: '负责人不能超过 20 个字符', trigger: 'blur' }],
  phone: [
    {
      validator: (_rule, value, callback) => {
        if (!value) return callback()
        if (!/^(1[3-9]\d{9}|0\d{2,3}-?\d{7,8})$/.test(value)) {
          return callback(new Error('请输入正确的手机号或座机号'))
        }
        callback()
      },
      trigger: 'blur'
    }
  ],
  provinceCode: [
    {
      validator: (_rule, _value, callback) => {
        const hasAny = !!(form.provinceCode || form.cityCode || form.districtCode)
        const complete = !!(form.provinceCode && form.cityCode && form.districtCode)
        if (regionRequired.value && !complete) {
          return callback(new Error('请选择省、市、区县'))
        }
        if (!regionRequired.value && hasAny && !complete) {
          return callback(new Error('省、市、区县需同时选择'))
        }
        callback()
      },
      trigger: 'change'
    }
  ],
  address: [{ max: 200, message: '详细地址不能超过 200 个字符', trigger: 'blur' }],
  applyCompanyIds: [
    {
      validator: (_rule, _value, callback) => {
        if (form.deptType === 5 && form.isLeaf && form.applyCompanyIds.length === 0) {
          return callback(new Error('请选择末端网点的适用公司'))
        }
        callback()
      },
      trigger: 'change'
    }
  ],
  sortOrder: [{ required: true, message: '请输入排序号', trigger: 'blur' }],
  remark: [{ max: 500, message: '备注不能超过 500 字', trigger: 'blur' }]
}

/* ---------- 初始化 ---------- */
function resetForm() {
  const node = props.record
  if (isEdit.value && node) {
    form.parentId = node.parentId
    form.deptType = node.deptType
    // 总部固定自营，历史数据缺省也回填 1
    form.deptNature = node.id === 1 ? 1 : (node.deptNature ?? 1)
    form.isLeaf = node.isLeaf === true
    form.deptName = node.deptName
    form.deptCode = node.deptCode
    form.leader = node.leader || ''
    form.phone = node.phone || ''
    form.address = node.address || ''
    form.provinceCode = node.provinceCode || ''
    form.cityCode = node.cityCode || ''
    form.districtCode = node.districtCode || ''
    form.applyCompanyIds = [...(node.applyCompanyIds || [])]
    form.sortOrder = node.sortOrder
    form.status = node.status
    form.remark = node.remark || ''
  } else {
    const parent = props.parentId ? findDept(props.tree, props.parentId) : undefined
    const siblings = siblingsOf(props.parentId)
    form.parentId = props.parentId || 1
    form.deptType = parent && parent.deptType === 1 ? 2 : 3
    form.deptNature = 1
    form.isLeaf = false
    form.deptName = ''
    form.deptCode = ''
    form.leader = ''
    form.phone = ''
    form.address = ''
    form.provinceCode = ''
    form.cityCode = ''
    form.districtCode = ''
    form.applyCompanyIds = []
    form.sortOrder = siblings.reduce((max, item) => Math.max(max, item.sortOrder || 0), 0) + 10
    form.status = true
    form.remark = ''
  }
  formRef.value?.clearValidate()
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      resetForm()
      void initRegionOptions()
    }
  }
)

/** 类型切换：非网点时清空“是否末端”与适用公司，避免提交脏值 */
watch(
  () => form.deptType,
  (value) => {
    if (value !== 5) {
      form.isLeaf = false
      form.applyCompanyIds = []
    }
  }
)

/** 勾选末端网点时，分公司管理员默认本公司 */
watch(
  () => form.isLeaf,
  (value) => {
    if (value && form.deptType === 5 && form.applyCompanyIds.length === 0 && defaultCompanyId.value) {
      form.applyCompanyIds = [defaultCompanyId.value]
    }
  }
)

/** 上级变化时：类型落到允许值，新建时重算排序 */
watch(
  () => form.parentId,
  (value, oldValue) => {
    if (!props.modelValue || isEdit.value || value === oldValue) return
    const parent = value ? findDept(props.tree, value) : undefined
    const allowed = parent ? ALLOWED_CHILD[parent.deptType] || [] : []
    if (allowed.length && !allowed.includes(form.deptType)) {
      form.deptType = allowed.includes(3) ? 3 : allowed[0]
    }
    const siblings = siblingsOf(value)
    form.sortOrder = siblings.reduce((max, item) => Math.max(max, item.sortOrder || 0), 0) + 10
    formRef.value?.clearValidate('parentId')
  }
)

/* ---------- 提交 ---------- */
async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  const parentId = form.parentId || 0
  const parent = parentId ? findDept(props.tree, parentId) : undefined

  if (!isHQ.value) {
    if (!parent) {
      ElMessage.error('上级机构不存在')
      return
    }
    if (!parent.status) {
      ElMessage.error('上级机构已禁用，不能在其下新增机构')
      return
    }
    if (!(ALLOWED_CHILD[parent.deptType] || []).includes(form.deptType)) {
      ElMessage.error(`${TYPE_NAME[parent.deptType]}下不允许创建${TYPE_NAME[form.deptType]}`)
      return
    }
    const depth = depthOf(parentId) + 1 + (props.record ? subtreeHeight(props.record) : 0)
    if (depth > 5) {
      ElMessage.error('机构层级最多 5 级，当前已超出')
      return
    }
  }

  // 类型改动：新类型必须允许所有现有子节点
  if (isEdit.value && props.record && props.record.deptType !== form.deptType) {
    const conflicts = (props.record.children || []).filter(
      (child) => !(ALLOWED_CHILD[form.deptType] || []).includes(child.deptType)
    )
    if (conflicts.length) {
      ElMessage.error(
        `${TYPE_NAME[form.deptType]}下不允许存在以下子机构：` +
          conflicts.map((child) => `${child.deptName}（${TYPE_NAME[child.deptType]}）`).join('、')
      )
      return
    }
  }

  if (nameExists(form.deptName, parentId)) {
    ElMessage.error(`同一上级下已存在同名机构：${form.deptName.trim()}`)
    return
  }

  // 授权开关不随基础信息提交，由后端保留原值；机构授权走独立抽屉
  const payload: DeptSavePayload = {
    parentId,
    deptType: form.deptType,
    deptNature: form.deptNature,
    isLeaf: form.deptType === 5 && form.isLeaf,
    deptName: form.deptName.trim(),
    deptCode: form.deptCode.trim(),
    leader: form.leader.trim() || undefined,
    phone: form.phone.trim() || undefined,
    address: form.address.trim() || undefined,
    provinceCode: form.provinceCode || undefined,
    cityCode: form.cityCode || undefined,
    districtCode: form.districtCode || undefined,
    applyCompanyIds: form.deptType === 5 && form.isLeaf ? [...form.applyCompanyIds] : [],
    sortOrder: form.sortOrder,
    status: form.status,
    remark: form.remark.trim() || undefined
  }

  submitting.value = true
  try {
    if (isEdit.value && props.record) {
      await updateDept(props.record.id, payload)
      ElMessage.success('保存成功')
    } else {
      await createDept(payload)
      ElMessage.success('新增成功')
    }
    visible.value = false
    emit('saved')
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  } finally {
    submitting.value = false
  }
}

/* ---------- 上级机构选项 ---------- */
interface ParentOption extends DeptNode {
  disabled?: boolean
}

/** 排除自身及后代；禁用机构不可选，但当前上级保留可选，避免编辑时值丢失 */
function buildParentOptions(
  nodes: DeptNode[],
  excludeId?: number,
  keepId?: number
): ParentOption[] {
  const result: ParentOption[] = []
  nodes.forEach((node) => {
    if (node.id === excludeId) return
    const item: ParentOption = {
      ...node,
      children: node.children ? buildParentOptions(node.children, excludeId, keepId) : undefined,
      disabled: !node.status && node.id !== keepId
    }
    result.push(item)
  })
  return result
}
</script>

<style scoped>
.dept-form {
  padding-right: 4px;
}

.form-control {
  width: 100%;
}

.region-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.region-select {
  flex: 1;
  min-width: 0;
}

.readonly-text {
  font-size: var(--font-size-small);
  color: var(--color-text-regular);
}

.form-hint {
  margin: 4px 0 0;
  font-size: var(--font-size-extra-small);
  color: var(--color-text-placeholder);
  line-height: 1.5;
}
</style>
