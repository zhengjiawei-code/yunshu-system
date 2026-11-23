/* ============================================================
 * 云枢运输管理系统 · 运营管理静态原型
 * 仅用于设计评审：无接口调用、无真实数据写库
 * ============================================================ */

const $ = (s, root = document) => root.querySelector(s);
const $$ = (s, root = document) => Array.from(root.querySelectorAll(s));

const fmtMoney = (v) =>
  Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const fmtInt = (v) => Number(v || 0).toLocaleString('zh-CN');
const fmt3 = (v) =>
  Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 3, maximumFractionDigits: 3 });
const round2 = (v) => Math.round((Number(v) + Number.EPSILON) * 100) / 100;
const escapeHtml = (s) =>
  String(s).replace(/[&<>"']/g, (m) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[m]));
/* 链路展示文本：去掉末尾冗余的「（直）」「（外转）」，类型统一由徽标表达 */
const chainLabel = (s) => String(s || '').replace(/[（(]\s*(直|外转)\s*[)）]\s*$/, '').trim();
const pad6 = (n) => String(n).padStart(6, '0');

/* ---------- Toast ---------- */
let toastTimer = null;
function toast(msg, type = '') {
  const el = $('#toast');
  el.textContent = msg;
  el.className = 'toast ' + type;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.add('hidden'), 2600);
}

/* ---------- Page / tabs ---------- */
function showPage(page) {
  const toastEl = $('#toast');
  if (toastEl) toastEl.classList.add('hidden');
  $$('.page').forEach((p) => p.classList.add('hidden'));
  const target = $('#page-' + page);
  if (target) target.classList.remove('hidden');
  $$('.nav-item[data-page]').forEach((n) => n.classList.toggle('active', n.dataset.page === page));
  $$('.tag-tab').forEach((t) => t.classList.toggle('active', t.dataset.page === page));
  if (page === 'waybill') renderTable();
}
$$('.nav-item[data-page], .tag-tab[data-page]').forEach((el) => {
  el.addEventListener('click', () => showPage(el.dataset.page));
});

let currentTab = 'self';
let preMode = false;
let selectedPreOrder = null;
const drafts = { self: null, pre: null };

function isFormDirty() {
  if (preMode) return true;
  return ['#senderName', '#senderPhone', '#receiverName', '#destInput']
    .some((sel) => $(sel) && String($(sel).value || '').trim());
}
function switchTab(tab, force = false) {
  if (tab === currentTab) return;
  if (!force) {
    const dirty = (currentTab === 'pre' && preMode) || (currentTab === 'self' && isFormDirty());
    if (dirty && !confirm('当前页有未保存内容，切换后将保留草稿，是否继续？')) return;
  }
  drafts[currentTab] = captureForm();
  currentTab = tab;
  $$('.tab').forEach((t) => t.classList.toggle('active', t.dataset.tab === tab));
  if (tab === 'self') {
    $('#preOrderCard').classList.add('hidden');
    $('#preEmpty').classList.add('hidden');
    $('#formArea').classList.remove('hidden');
    restoreForm(drafts.self, 'self');
  } else {
    $('#preOrderCard').classList.remove('hidden');
    if (drafts.pre) {
      restoreForm(drafts.pre, 'pre');
      $('#formArea').classList.remove('hidden');
      $('#preEmpty').classList.add('hidden');
    } else {
      preMode = false;
      selectedPreOrder = null;
      $('#formArea').classList.add('hidden');
      $('#preEmpty').classList.remove('hidden');
    }
    renderPreTable();
  }
}
$$('.tab').forEach((t) => t.addEventListener('click', () => switchTab(t.dataset.tab)));

/* ============================================================
 * 开单表单
 * ============================================================ */
const REGION = {
  山东省: { 济南市: ['历下区', '槐荫区', '天桥区'] },
  广东省: { 广州市: ['白云区', '天河区', '番禺区'], 深圳市: ['南山区', '宝安区'] },
  贵州省: { 贵阳市: ['白云区', '南明区'] },
  江苏省: { 苏州市: ['姑苏区', '吴中区'] },
  浙江省: { 杭州市: ['余杭区', '西湖区'] },
  河南省: { 郑州市: ['金水区'] },
  四川省: { 成都市: ['武侯区'] },
  湖北省: { 武汉市: ['江汉区'] },
  陕西省: { 西安市: ['雁塔区'] },
  西藏自治区: { 拉萨市: ['城关区'] }
};

/* 目的地候选：一个区县可对应多条路线；同名区县（广州白云区 / 贵阳白云区）各自独立 */
const DESTINATIONS = [
  { province: '广东省', city: '广州市', district: '白云区', code: '440111', hub: '广州白云网点', routes: [
    { chain: '济南程驰 → 广州 → 白云区（直）', endType: 1, nodes: 3, hours: 15.0, mileage: 1180 },
    { chain: '济南程驰 → 济南分拨中心 → 广州 → 白云区（直）', endType: 1, nodes: 4, hours: 18.5, mileage: 1210 },
    { chain: '济南程驰 → 济南分拨中心 → 广州 → 白云区（外转）', endType: 2, nodes: 4, hours: 26.0, mileage: 1260 }
  ] },
  { province: '贵州省', city: '贵阳市', district: '白云区', code: '520113', hub: '贵阳白云网点', routes: [
    { chain: '济南程驰 → 济南分拨中心 → 贵阳 → 白云区（直）', endType: 1, nodes: 4, hours: 32.0, mileage: 1900 },
    { chain: '济南程驰 → 济南分拨中心 → 贵阳 → 白云区（外转）', endType: 2, nodes: 4, hours: 38.0, mileage: 1950 },
    { chain: '济南程驰 → 济南分拨中心 → 长沙 → 贵阳 → 白云区（外转）', endType: 2, nodes: 5, hours: 41.0, mileage: 2010 }
  ] },
  { province: '山东省', city: '济南市', district: '历下区', code: '370102', hub: '济南历下网点', routes: [
    { chain: '济南程驰 → 历下区（直）', endType: 1, nodes: 2, hours: 2.0, mileage: 18 }
  ] },
  { province: '山东省', city: '济南市', district: '槐荫区', code: '370104', hub: '济南槐荫网点', routes: [
    { chain: '济南程驰 → 槐荫区（直）', endType: 1, nodes: 2, hours: 1.5, mileage: 12 }
  ] },
  { province: '山东省', city: '济南市', district: '天桥区', code: '370105', hub: '济南天桥网点', routes: [
    { chain: '济南程驰 → 天桥区（直）', endType: 1, nodes: 2, hours: 2.5, mileage: 22 }
  ] },
  { province: '广东省', city: '广州市', district: '天河区', code: '440106', hub: '广州天河网点', routes: [
    { chain: '济南程驰 → 济南分拨中心 → 广州 → 天河区（直）', endType: 1, nodes: 4, hours: 19.0, mileage: 1220 }
  ] },
  { province: '广东省', city: '广州市', district: '番禺区', code: '440113', hub: '广州番禺网点', routes: [
    { chain: '济南程驰 → 济南分拨中心 → 广州 → 番禺区（外转）', endType: 2, nodes: 4, hours: 28.0, mileage: 1290 }
  ] },
  { province: '广东省', city: '深圳市', district: '南山区', code: '440305', hub: '深圳南山网点', routes: [
    { chain: '济南程驰 → 济南分拨中心 → 广州 → 深圳 → 南山区（直）', endType: 1, nodes: 5, hours: 22.0, mileage: 1450 }
  ] },
  { province: '广东省', city: '深圳市', district: '宝安区', code: '440306', hub: '深圳宝安网点', routes: [
    { chain: '济南程驰 → 济南分拨中心 → 广州 → 深圳 → 宝安区（外转）', endType: 2, nodes: 5, hours: 30.0, mileage: 1490 }
  ] },
  { province: '贵州省', city: '贵阳市', district: '南明区', code: '520102', hub: '贵阳南明网点', routes: [
    { chain: '济南程驰 → 济南分拨中心 → 贵阳 → 南明区（直）', endType: 1, nodes: 4, hours: 31.0, mileage: 1880 }
  ] },
  { province: '江苏省', city: '苏州市', district: '姑苏区', code: '320508', hub: '苏州姑苏网点', routes: [
    { chain: '济南程驰 → 济南分拨中心 → 苏州 → 姑苏区（外转）', endType: 2, nodes: 4, hours: 24.0, mileage: 860 }
  ] },
  { province: '江苏省', city: '苏州市', district: '吴中区', code: '320506', hub: '苏州吴中网点', routes: [
    { chain: '济南程驰 → 济南分拨中心 → 苏州 → 吴中区（外转）', endType: 2, nodes: 4, hours: 25.0, mileage: 880 }
  ] },
  { province: '浙江省', city: '杭州市', district: '余杭区', code: '330110', hub: '杭州余杭网点', routes: [
    { chain: '济南程驰 → 济南分拨中心 → 杭州 → 余杭区（直）', endType: 1, nodes: 4, hours: 20.0, mileage: 980 }
  ] },
  { province: '浙江省', city: '杭州市', district: '西湖区', code: '330106', hub: '杭州西湖网点', routes: [
    { chain: '济南程驰 → 济南分拨中心 → 杭州 → 西湖区（直）', endType: 1, nodes: 4, hours: 21.0, mileage: 1000 }
  ] },
  { province: '河南省', city: '郑州市', district: '金水区', code: '410105', hub: '郑州金水网点', routes: [
    { chain: '济南程驰 → 济南分拨中心 → 郑州 → 金水区（直）', endType: 1, nodes: 4, hours: 12.0, mileage: 430 }
  ] },
  { province: '四川省', city: '成都市', district: '武侯区', code: '510107', hub: '成都武侯网点', routes: [] },
  { province: '湖北省', city: '武汉市', district: '江汉区', code: '420103', hub: '武汉江汉网点', routes: [
    { chain: '济南程驰 → 济南分拨中心 → 武汉 → 江汉区（直）', endType: 1, nodes: 4, hours: 17.0, mileage: 760 }
  ] },
  { province: '陕西省', city: '西安市', district: '雁塔区', code: '610113', hub: '西安雁塔网点', routes: [
    { chain: '济南程驰 → 济南分拨中心 → 西安 → 雁塔区（外转）', endType: 2, nodes: 4, hours: 22.0, mileage: 920 }
  ] },
  { province: '西藏自治区', city: '拉萨市', district: '城关区', code: '540102', hub: '拉萨城关网点', routes: [
    { chain: '济南程驰 → 济南分拨中心 → 拉萨 → 城关区（外转）', endType: 2, nodes: 4, hours: 48.0, mileage: 2100 }
  ] }
];

const fees = {
  freightFee: 1200, deliveryFee: 80, pickupFee: 0, otherFee: 0, receiptFee: 20,
  makeBillFee: 10, insuranceFee: 30, cashBack: 0, oweBack: 0
};
const FEE_KEYS = ['freightFee', 'deliveryFee', 'pickupFee', 'otherFee', 'receiptFee', 'makeBillFee', 'insuranceFee'];
let payMethod = 0;
let pay = { prepaidFee: 0, collectFee: 0, monthlyFee: 0, returnFee: 0 };
let routes = [];
let selectedRoute = 0;
let selectedDest = null;
let currentHubs = { originHubName: '济南程驰', destHubName: '' };
let destCandidates = [];
let destTimer = null;
let waybillSeq = 126;

function nowText() {
  const d = new Date();
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}
function calcTotal() {
  return round2(FEE_KEYS.reduce((s, k) => s + Number(fees[k] || 0), 0));
}
function totalPay() {
  return round2(Number(pay.prepaidFee || 0) + Number(pay.collectFee || 0) + Number(pay.monthlyFee || 0) + Number(pay.returnFee || 0));
}
function badge(type) {
  return type === 1
    ? '<span class="badge badge-direct">直达</span>'
    : '<span class="badge badge-transfer">外转</span>';
}

/* ---------- 目的地单输入 + 路由候选 ---------- */
function searchDest(keyword) {
  const q = String(keyword || '').trim();
  if (!q) return [];
  const list = [];
  DESTINATIONS.forEach((dest) => {
    if (!(dest.province + dest.city + dest.district).includes(q)) return;
    dest.routes.forEach((route, idx) => list.push({ dest, route, idx }));
  });
  return list.sort((a, b) =>
    a.route.nodes - b.route.nodes ||
    a.route.endType - b.route.endType ||
    a.route.hours - b.route.hours ||
    a.route.mileage - b.route.mileage
  );
}
function renderDestMenu(keyword) {
  const menu = $('#destMenu');
  const q = String(keyword || '').trim();
  if (!q) { menu.classList.add('hidden'); menu.innerHTML = ''; return; }
  const cands = searchDest(q);
  destCandidates = cands;
  if (!cands.length) {
    const dests = DESTINATIONS.filter((d) => (d.province + d.city + d.district).includes(q));
    menu.innerHTML = dests.length
      ? `<div class="dest-empty"><strong>该目的地暂无可用路由</strong><br>${dests.map((d) => escapeHtml(d.province + d.city + d.district)).join('、')} 未配置从济南程驰出发的可用链路，保存将被阻断。请联系路由管理维护短驳/干线，或申请外转授权。</div>`
      : `<div class="dest-empty">未找到匹配“${escapeHtml(q)}”的目的地，请检查区县名称，或联系总部维护区划与路由。</div>`;
    menu.classList.remove('hidden');
    return;
  }
  menu.innerHTML = cands.slice(0, 12).map((c, i) => `
    <div class="dest-opt" data-idx="${i}">
      <div class="dest-opt__chain">
        <span class="dest-opt__chain-text">${escapeHtml(chainLabel(c.route.chain))}</span>
        ${i === 0 ? '<span class="badge badge-rec">推荐</span>' : ''}
      </div>
      <div class="dest-opt__meta2">
        <span class="dest-opt__dest">${escapeHtml(c.dest.province)} ${escapeHtml(c.dest.city)} ${escapeHtml(c.dest.district)}</span>
        <span class="dest-opt__hub">目的网点：${escapeHtml(c.dest.hub)}</span>
        ${badge(c.route.endType)}
        <span>${c.route.nodes} 个节点</span>
        <span>${c.route.hours} 小时</span>
        <span>${fmtInt(c.route.mileage)} 公里</span>
      </div>
    </div>`).join('');
  menu.classList.remove('hidden');
  $$('.dest-opt', menu).forEach((el) => {
    el.addEventListener('mousedown', (e) => {
      e.preventDefault();
      selectDest(destCandidates[Number(el.dataset.idx)]);
    });
  });
}
function selectDest(cand) {
  if (!cand) return;
  selectedDest = {
    province: cand.dest.province, city: cand.dest.city, district: cand.dest.district,
    code: cand.dest.code, hub: cand.dest.hub
  };
  routes = cand.dest.routes.map((r) => ({ ...r }));
  selectedRoute = cand.idx;
  $('#destInput').value = cand.dest.city + cand.dest.district;
  $('#destInput').classList.remove('is-empty');
  $('#destMenu').classList.add('hidden');
  renderRoutes();
  refreshRequiredStates();
}
function clearDestination() {
  selectedDest = null;
  routes = [];
  selectedRoute = 0;
  destCandidates = [];
  $('#destInput').value = '';
  $('#destInput').classList.remove('is-empty');
  $('#destMenu').classList.add('hidden');
  renderRoutes();
  refreshRequiredStates();
}
function applyHubs() {
  const r = routes[selectedRoute] || routes[0];
  const origin = r ? String(r.chain).split('→')[0].trim() : '济南程驰';
  currentHubs = {
    originHubName: origin || '济南程驰',
    destHubName: selectedDest ? selectedDest.hub : ''
  };
  const o = $('#originHub');
  const d = $('#destHub');
  if (o) o.textContent = currentHubs.originHubName || '—';
  if (d) d.textContent = currentHubs.destHubName || '—';
}
function renderRoutes() {
  const box = $('#routeBox');
  applyHubs();
  if (!selectedDest) {
    box.innerHTML = '<div class="route-line route-line--empty"><span class="route-line__hint">输入目的地并选择候选后，自动带出线路、省/市/区县与目的网点</span></div>';
    return;
  }
  if (!routes.length) {
    box.innerHTML = '<div class="route-line route-line--empty"><span class="route-line__hint" style="color:#8f4444">当前目的地无可用路由，保存已被阻断</span></div>';
    return;
  }
  const r = routes[selectedRoute] || routes[0];
  box.innerHTML = `
    <div class="route-line">
      <span class="route-line__chain" title="${escapeHtml(chainLabel(r.chain))}">${escapeHtml(chainLabel(r.chain))}</span>
      ${badge(r.endType)}
      <span class="route-line__meta"><span>${r.nodes} 节点</span><span>${r.hours}h</span><span>${fmtInt(r.mileage)}km</span></span>
      ${routes.length > 1 ? `<button type="button" class="route-switch" id="routeSwitch">切换(${routes.length})</button>` : ''}
    </div>
    ${routes.length > 1 ? `<div class="route-pop hidden" id="routePop">${routes.map((item, i) => `
      <label class="route-option ${i === selectedRoute ? 'active' : ''}" data-route="${i}">
        <input type="radio" name="routeSel" ${i === selectedRoute ? 'checked' : ''}>
        <span class="route-option__body">
          <span class="route-option__chain">${escapeHtml(chainLabel(item.chain))} ${badge(item.endType)}</span>
          <span class="route-meta"><span>${item.nodes} 个节点</span><span>${item.hours} 小时</span><span>${fmtInt(item.mileage)} 公里</span></span>
        </span>
      </label>`).join('')}</div>` : ''}`;
  const sw = $('#routeSwitch');
  if (sw) {
    sw.addEventListener('click', (e) => {
      e.stopPropagation();
      $('#routePop').classList.toggle('hidden');
    });
  }
  $$('.route-option', box).forEach((opt) => {
    opt.addEventListener('click', (e) => {
      e.preventDefault();
      selectedRoute = Number(opt.dataset.route);
      renderRoutes();
    });
  });
}
function initDest() {
  const input = $('#destInput');
  input.addEventListener('input', () => {
    const kw = input.value.trim();
    clearTimeout(destTimer);
    if (!kw) {
      selectedDest = null;
      routes = [];
      selectedRoute = 0;
      $('#destMenu').classList.add('hidden');
      renderRoutes();
      return;
    }
    if (selectedDest && kw !== selectedDest.city + selectedDest.district) {
      selectedDest = null;
      routes = [];
      selectedRoute = 0;
      renderRoutes();
    }
    destTimer = setTimeout(() => renderDestMenu(kw), 250);
  });
  input.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      clearTimeout(destTimer);
      renderDestMenu(input.value);
    }
    if (e.key === 'Escape') $('#destMenu').classList.add('hidden');
  });
  $('#destClearBtn').addEventListener('click', clearDestination);
  document.addEventListener('click', (e) => {
    if (!e.target.closest('.dest-field')) $('#destMenu').classList.add('hidden');
  });
}

/* ---------- 客户匹配（发货人带出客户名称，只读；未命中留空、不拦截） ---------- */
/* 简易客户表以发货人名称为索引；同名不同电话视为不同客户 */
let customers = [
  { id: 1, code: 'C0001', name: '王海', phone: '13800138001', company: '广州华远物流有限公司' },
  { id: 2, code: 'C0002', name: '刘洋', phone: '13700137002', company: '深圳市顺达供应链有限公司' },
  { id: 3, code: 'C0003', name: '王强', phone: '13800001111', company: '济南本地商贸' },
  { id: 4, code: 'C0004', name: '王强', phone: '13900002222', company: '山东鑫源建材' },
  { id: 5, code: 'C0005', name: '陈美玲', phone: '13500003333', company: '杭州云仓科技' }
];
let matchedCustomer = null;
let customerStatus = 'idle';
let customerOptions = [];
let customerTimer = null;

function matchCustomer(name, phone) {
  const n = String(name || '').trim();
  const p = String(phone || '').trim();
  if (!n) return { status: 'idle', list: [] };
  const byName = customers.filter((c) => c.name === n);
  if (p) {
    const exact = byName.find((c) => c.phone === p);
    if (exact) return { status: 'hit', list: [exact] };
  }
  if (!byName.length) return { status: 'miss', list: [] };
  if (byName.length === 1) return { status: 'hit', list: byName };
  return { status: 'ambiguous', list: byName };
}
function renderCustomerMatch() {
  const box = $('#customerMatch');
  if (!box) return;
  if (matchedCustomer) {
    box.className = 'customer-match is-hit';
    box.innerHTML = `<span class="customer-match__name">${escapeHtml(matchedCustomer.name)}</span>
      <span class="customer-match__meta">${escapeHtml(matchedCustomer.phone || '')}${matchedCustomer.code ? ' · ' + escapeHtml(matchedCustomer.code) : ''}</span>
      <span class="customer-match__tag">已匹配</span>`;
  } else {
    box.className = 'customer-match';
    box.innerHTML = '<span class="customer-match__empty">—</span>';
  }
  const pick = $('#customerPick');
  if (customerStatus === 'ambiguous' && !matchedCustomer && customerOptions.length) {
    pick.classList.remove('hidden');
    pick.innerHTML = `<div class="customer-pick__title">同名客户 ${customerOptions.length} 个，请按电话选择</div>` +
      customerOptions.map((c) => `<div class="customer-pick__opt" data-cust="${c.id}">${escapeHtml(c.name)}<small>${escapeHtml(c.phone)}${c.company ? ' · ' + escapeHtml(c.company) : ''}</small></div>`).join('');
  } else {
    pick.classList.add('hidden');
    pick.innerHTML = '';
  }
}
function refreshCustomerMatch() {
  const res = matchCustomer($('#senderName').value, $('#senderPhone').value);
  customerStatus = res.status;
  customerOptions = res.list;
  matchedCustomer = res.status === 'hit' ? res.list[0] : null;
  renderCustomerMatch();
}
function initCustomerMatch() {
  const debounced = () => {
    clearTimeout(customerTimer);
    customerTimer = setTimeout(refreshCustomerMatch, 250);
  };
  $('#senderName').addEventListener('input', debounced);
  $('#senderPhone').addEventListener('input', debounced);
  $('#senderName').addEventListener('blur', refreshCustomerMatch);
  $('#senderPhone').addEventListener('blur', refreshCustomerMatch);
  $('#customerPick').addEventListener('mousedown', (e) => {
    e.preventDefault();
    const opt = e.target.closest('[data-cust]');
    if (!opt) return;
    matchedCustomer = customers.find((c) => c.id === Number(opt.dataset.cust)) || null;
    customerStatus = matchedCustomer ? 'hit' : 'idle';
    customerOptions = matchedCustomer ? [matchedCustomer] : [];
    renderCustomerMatch();
  });
  document.addEventListener('click', (e) => {
    if (!e.target.closest('.customer-match') && !e.target.closest('#customerPick')) {
      $('#customerPick').classList.add('hidden');
    }
  });
  renderCustomerMatch();
}

/* ---------- 支付方式 ---------- */
const PAY_LABEL = { 1: '现付运费', 2: '到付运费', 3: '月结运费', 4: '回付运费' };
const PAY_FIELDS = [
  [1, 'prepaidFee', '现付运费'], [2, 'collectFee', '到付运费'],
  [3, 'monthlyFee', '月结运费'], [4, 'returnFee', '回付运费']
];
function applyPay() {
  const total = calcTotal();
  if (payMethod === 1) pay = { prepaidFee: total, collectFee: 0, monthlyFee: 0, returnFee: 0 };
  else if (payMethod === 2) pay = { prepaidFee: 0, collectFee: total, monthlyFee: 0, returnFee: 0 };
  else if (payMethod === 3) pay = { prepaidFee: 0, collectFee: 0, monthlyFee: total, returnFee: 0 };
  else if (payMethod === 4) pay = { prepaidFee: 0, collectFee: 0, monthlyFee: 0, returnFee: total };
}
function renderPayFields() {
  const box = $('#payFields');
  if (!box) return;
  if (!payMethod) {
    box.innerHTML = '<div class="pay-empty">请选择支付方式</div>';
    return;
  }
  if (payMethod !== 5) {
    box.innerHTML = `<div class="field field--row"><label>${PAY_LABEL[payMethod]}</label><input class="readonly pay-single" readonly value="${fmtMoney(calcTotal())}"></div>`;
    return;
  }
  box.innerHTML = `<div class="grid grid-2">${PAY_FIELDS.map(([, key, label]) => `
    <div class="field field--row"><label>${label}</label>
      <input class="pay" data-pay="${key}" type="number" min="0" step="0.01" value="${Number(pay[key] || 0).toFixed(2)}">
    </div>`).join('')}</div>`;
}
function syncPayView() {
  if (payMethod !== 5) {
    const el = $('#payFields .pay-single');
    if (el) el.value = fmtMoney(calcTotal());
  }
}
function renderDiff() {
  const bar = $('#diffBar');
  if (!bar) return;
  if (payMethod !== 5) { bar.classList.add('hidden'); return; }
  bar.classList.remove('hidden');
  const total = calcTotal();
  const sum = totalPay();
  const diff = round2(total - sum);
  if (Math.abs(diff) < 0.005) {
    bar.className = 'diff-bar ok';
    bar.innerHTML = `四项合计 ¥${fmtMoney(sum)}，与运费合计一致`;
  } else {
    bar.className = 'diff-bar bad';
    bar.innerHTML = `四项合计 ¥${fmtMoney(sum)}，运费合计 ¥${fmtMoney(total)}，差额 ¥${fmtMoney(Math.abs(diff))} ${diff > 0 ? '未分配' : '超出'}；保存将被拦截`;
  }
}
function recalc() {
  const total = calcTotal();
  $('#totalText').textContent = '¥' + fmtMoney(total);
  const rebate = round2(Number(fees.cashBack || 0) + Number(fees.oweBack || 0));
  const valid = round2(total - rebate);
  $('#rebateText').textContent = '¥' + fmtMoney(rebate);
  $('#validText').textContent = '¥' + fmtMoney(valid) + (valid < 0 ? '（负值）' : '');
  $('#validText').style.color = valid < 0 ? '#d92d20' : '';
  const mirror = $('#codMirror');
  if (mirror) mirror.value = fmtMoney($('#codAmount') ? $('#codAmount').value || 0 : 0);
  if (payMethod >= 1 && payMethod <= 4) applyPay();
  syncPayView();
  renderDiff();
}
function setPayMethod(method, silent = false) {
  if (payMethod === 5 && method !== 5 && !silent) {
    if (!confirm('切换后将清空已录入的多笔付金额，是否继续？')) return;
  }
  payMethod = method;
  $$('#payMethods button').forEach((b) => b.classList.toggle('active', Number(b.dataset.paymethod) === method));
  const block = $('#payBlock');
  if (block) block.classList.remove('has-error');
  if (method === 5) {
    const total = calcTotal();
    pay = { prepaidFee: total, collectFee: 0, monthlyFee: 0, returnFee: 0 };
  }
  recalc();
  renderPayFields();
  refreshRequiredStates();
}
function initFeesAndPay() {
  $$('.fee').forEach((input) => {
    input.addEventListener('input', () => {
      fees[input.dataset.fee] = Number(input.value || 0);
      recalc();
    });
  });
  $('#payFields').addEventListener('input', (e) => {
    const el = e.target.closest('.pay');
    if (!el) return;
    pay[el.dataset.pay] = Number(el.value || 0);
    renderDiff();
  });
  $$('#payMethods button').forEach((b) => {
    b.addEventListener('click', () => setPayMethod(Number(b.dataset.paymethod)));
  });
  $('#codAmount').addEventListener('input', recalc);
  recalc();
  renderPayFields();
}

/* ---------- 货物明细 ---------- */
let goodsRows = [emptyGoodsRow()];
function emptyGoodsRow() {
  return { name: '', type: '', pack: '', count: 0, volume: 0, weight: 0, remark: '' };
}
function goodsTotals() {
  return goodsRows.reduce((acc, r) => ({
    count: acc.count + Number(r.count || 0),
    weight: round2(acc.weight + Number(r.weight || 0)),
    volume: round2(acc.volume + Number(r.volume || 0))
  }), { count: 0, weight: 0, volume: 0 });
}
function updateGoodsSum() {
  const t = goodsTotals();
  $('#sumCount').textContent = fmtInt(t.count);
  $('#sumWeight').textContent = fmt3(t.weight);
  $('#sumVolume').textContent = fmt3(t.volume);
}
function renderGoods() {
  const body = $('#goodsBody');
  body.innerHTML = goodsRows.map((r, i) => `
    <tr data-idx="${i}">
      <td class="num">${i + 1}</td>
      <td><input data-g="name" value="${escapeHtml(r.name || '')}" placeholder="必填"></td>
      <td><input data-g="type" value="${escapeHtml(r.type || '')}" placeholder="选填"></td>
      <td><input data-g="pack" value="${escapeHtml(r.pack || '')}" placeholder="选填"></td>
      <td><input data-g="count" type="number" min="0" value="${Number(r.count || 0)}"></td>
      <td><input data-g="volume" type="number" min="0" step="0.001" value="${Number(r.volume || 0)}"></td>
      <td><input data-g="weight" type="number" min="0" step="0.001" value="${Number(r.weight || 0)}"></td>
      <td><input data-g="remark" value="${escapeHtml(r.remark || '')}" placeholder="选填"></td>
      <td><button type="button" class="del-row" data-del="${i}">删除</button></td>
    </tr>`).join('');
  updateGoodsSum();
  refreshRequiredStates();
}
function initGoods() {
  $('#goodsBody').addEventListener('input', (e) => {
    const tr = e.target.closest('tr');
    const key = e.target.dataset.g;
    if (!tr || !key) return;
    const i = Number(tr.dataset.idx);
    const numeric = ['count', 'volume', 'weight'].includes(key);
    goodsRows[i][key] = numeric ? Number(e.target.value || 0) : e.target.value;
    updateGoodsSum();
    e.target.classList.remove('is-empty');
    refreshRequiredStates();
  });
  $('#goodsBody').addEventListener('click', (e) => {
    const btn = e.target.closest('[data-del]');
    if (!btn) return;
    if (goodsRows.length <= 1) { toast('至少保留一行货物明细', 'error'); return; }
    goodsRows.splice(Number(btn.dataset.del), 1);
    renderGoods();
  });
  $('#addGoodsBtn').addEventListener('click', () => {
    goodsRows.push(emptyGoodsRow());
    renderGoods();
  });
  renderGoods();
}

/* ---------- 收货方省/市/区县 + 地址识别 ---------- */
function initReceiverRegion() {
  const p = $('#rcvProvince');
  p.innerHTML = '<option value="">省</option>' + Object.keys(REGION).map((x) => `<option>${x}</option>`).join('');
  p.addEventListener('change', () => {
    const cities = REGION[p.value] || {};
    $('#rcvCity').innerHTML = '<option value="">市</option>' + Object.keys(cities).map((c) => `<option>${c}</option>`).join('');
    $('#rcvDistrict').innerHTML = '<option value="">区/县</option>';
  });
  $('#rcvCity').addEventListener('change', () => {
    const districts = (REGION[p.value] || {})[$('#rcvCity').value] || [];
    $('#rcvDistrict').innerHTML = '<option value="">区/县</option>' + districts.map((d) => `<option>${d}</option>`).join('');
  });
  $('#addrParseBtn').addEventListener('click', () => {
    const addr = $('#receiverAddress').value;
    if (!addr.trim()) { toast('请先填写收货地址', 'error'); return; }
    for (const [prov, cities] of Object.entries(REGION)) {
      if (!addr.includes(prov)) continue;
      p.value = prov;
      p.dispatchEvent(new Event('change'));
      for (const [city, districts] of Object.entries(cities)) {
        if (!addr.includes(city)) continue;
        $('#rcvCity').value = city;
        $('#rcvCity').dispatchEvent(new Event('change'));
        const hit = districts.find((d) => addr.includes(d));
        if (hit) $('#rcvDistrict').value = hit;
        toast(`已识别：${prov} ${city}${hit ? ' ' + hit : ''}`, 'success');
        return;
      }
    }
    toast('未识别到省市区县，请手动选择', 'error');
  });
}

/* ---------- 预开单揽收 ---------- */
const PRE_STATUS = { 10: '待接单', 20: '已接单', 30: '已揽收', 90: '已撤单' };
const preOrders = [
  { no: 'YJ202611230001', customer: '广州华远物流有限公司', customerPhone: '020-88886666', sender: '王海', senderPhone: '13800138001', senderAddress: '济南市历下区经十路 88 号', receiver: '陈晨', receiverPhone: '13900139001', receiverAddress: '白云区机场路 12 号', province: '广东省', city: '广州市', district: '白云区', goods: '服装', qty: 36, weight: 420.5, volume: 3.2, expectPickup: '2026-11-23 14:00:00', submitted: '2026-11-23 09:20:11', status: 10, acceptBy: '', waybillNo: '' },
  { no: 'YJ202611230002', customer: '深圳市顺达供应链有限公司', customerPhone: '0755-86668888', sender: '刘洋', senderPhone: '13700137002', senderAddress: '济南市槐荫区经一路 36 号', receiver: '周敏', receiverPhone: '13600136002', receiverAddress: '宝安区福永街道 8 号', province: '广东省', city: '深圳市', district: '宝安区', goods: '电子配件', qty: 120, weight: 860, volume: 6.4, expectPickup: '2026-11-23 16:00:00', submitted: '2026-11-23 09:42:35', status: 10, acceptBy: '', waybillNo: '' },
  { no: 'YJ202611230003', customer: '佛山嘉信陶瓷有限公司', customerPhone: '0757-82223333', sender: '赵磊', senderPhone: '13500135003', senderAddress: '济南市天桥区北园大街 20 号', receiver: '吴静', receiverPhone: '13400134003', receiverAddress: '天河区体育西路 5 号', province: '广东省', city: '广州市', district: '天河区', goods: '陶瓷样品', qty: 8, weight: 320, volume: 1.2, expectPickup: '2026-11-23 15:30:00', submitted: '2026-11-23 10:05:52', status: 10, acceptBy: '', waybillNo: '' },
  { no: 'YJ202611230004', customer: '东莞恒通电子有限公司', customerPhone: '0769-81112222', sender: '孙浩', senderPhone: '13300133004', senderAddress: '济南市历城区花园路 9 号', receiver: '郑洁', receiverPhone: '13200132004', receiverAddress: '姑苏区干将东路 100 号', province: '江苏省', city: '苏州市', district: '姑苏区', goods: '主板', qty: 60, weight: 240, volume: 2.1, expectPickup: '2026-11-24 09:00:00', submitted: '2026-11-23 10:22:18', status: 20, acceptBy: '张三', waybillNo: '' },
  { no: 'YJ202611230005', customer: '杭州云仓科技有限公司', customerPhone: '0571-88889999', sender: '钱峰', senderPhone: '13100131005', senderAddress: '济南市市中区纬二路 18 号', receiver: '孙悦', receiverPhone: '13000130005', receiverAddress: '余杭区文一西路 969 号', province: '浙江省', city: '杭州市', district: '余杭区', goods: '百货', qty: 210, weight: 1500, volume: 12.5, expectPickup: '2026-11-24 10:00:00', submitted: '2026-11-23 10:40:06', status: 20, acceptBy: '李四', waybillNo: '' },
  { no: 'YJ202611230006', customer: '郑州中原商贸有限公司', customerPhone: '0371-66667777', sender: '周涛', senderPhone: '13900139006', senderAddress: '济南市天桥区无影山路 6 号', receiver: '李娜', receiverPhone: '13800138006', receiverAddress: '金水区花园路 26 号', province: '河南省', city: '郑州市', district: '金水区', goods: '食品', qty: 90, weight: 600, volume: 4.8, expectPickup: '2026-11-23 18:00:00', submitted: '2026-11-23 08:50:40', status: 30, acceptBy: '张三', waybillNo: 'YD20261123000098' },
  { no: 'YJ202611220011', customer: '成都蜀通物流有限公司', customerPhone: '028-85556666', sender: '吴强', senderPhone: '13700137007', senderAddress: '济南市历下区解放路 22 号', receiver: '何静', receiverPhone: '13600136007', receiverAddress: '武侯区人民南路四段 1 号', province: '四川省', city: '成都市', district: '武侯区', goods: '家具', qty: 15, weight: 950, volume: 8.6, expectPickup: '2026-11-24 14:00:00', submitted: '2026-11-22 16:10:22', status: 90, acceptBy: '', waybillNo: '' },
  { no: 'YJ202611220012', customer: '西安丝路供应链有限公司', customerPhone: '029-86669999', sender: '马超', senderPhone: '13300133009', senderAddress: '济南市历城区洪家楼 7 号', receiver: '白露', receiverPhone: '13200132009', receiverAddress: '雁塔区科技路 50 号', province: '陕西省', city: '西安市', district: '雁塔区', goods: '服装', qty: 48, weight: 380, volume: 2.8, expectPickup: '2026-11-24 11:30:00', submitted: '2026-11-22 17:05:33', status: 30, acceptBy: '王五', waybillNo: 'YD20261122000087' }
];

const PRE_COLUMNS = [
  { key: 'no', label: '预开单号', width: 170, type: 'text', filter: 'text', def: true },
  { key: 'status', label: '状态', width: 136, type: 'prestatus', filter: 'select', options: PRE_STATUS, def: true },
  { key: 'customer', label: '客户名称', width: 180, type: 'text', filter: 'text', def: true },
  { key: 'destName', label: '目的地', width: 180, type: 'text', filter: 'text', def: true },
  { key: 'qty', label: '件数', width: 176, type: 'int', filter: 'number', sum: true, def: true },
  { key: 'weight', label: '重量', width: 176, type: 'decimal3', filter: 'number', sum: true, def: true },
  { key: 'volume', label: '体积', width: 176, type: 'decimal3', filter: 'number', sum: true, def: true },
  { key: 'expectPickup', label: '期望提货时间', width: 200, type: 'datetime', filter: 'date', def: true },
  { key: 'submitted', label: '提交时间', width: 200, type: 'datetime', filter: 'date', def: true },
  { key: 'customerPhone', label: '客户电话', width: 150, type: 'text', filter: 'text', def: false },
  { key: 'sender', label: '发货人', width: 136, type: 'text', filter: 'text', def: false },
  { key: 'receiver', label: '收货人', width: 136, type: 'text', filter: 'text', def: false }
];

const preVisibleKeys = new Set(PRE_COLUMNS.filter((c) => c.def).map((c) => c.key));
let preFilters = {};
let preSortKey = 'submitted';
let preSortOrder = 'desc';
let prePage = 1;
let prePageSize = 10;

function preStatusTag(status) {
  const map = {
    10: ['待接单', 'status-warning'], 20: ['已接单', 'status-info'],
    30: ['已揽收', 'status-success'], 90: ['已撤单', 'status-danger']
  };
  const [text, cls] = map[status] || ['未知', 'status-info'];
  return `<span class="status-tag ${cls}"><i></i>${text}</span>`;
}
function updatePreCount() {
  const n = preOrders.filter((p) => p.status === 10).length;
  $('#preCount').textContent = n;
  $('#preCount').classList.toggle('hidden', n === 0);
}
function preRowActions(p) {
  if (p.status === 10) return `<button data-pre-accept="${p.no}">接单</button>`;
  if (p.status === 20) {
    if (p.acceptBy === '张三') return `<button data-pre-continue="${p.no}">继续处理</button><button class="danger" data-pre-return="${p.no}">退回</button>`;
    return `<button data-pre-taken="${p.no}" title="已被 ${escapeHtml(p.acceptBy)} 接单，重复接单将被拒绝" style="color:#98a2b3;background:none;border:0;padding:0;cursor:pointer">${escapeHtml(p.acceptBy)} 处理中</button>`;
  }
  if (p.status === 30) return `<button data-pre-view="${p.no}">查看运单</button>`;
  return '<span class="muted">--</span>';
}
function renderPrePager(total, pages) {
  const pager = $('#prePager');
  const btns = [];
  for (let i = 1; i <= pages; i++) btns.push(`<button class="pager-btn ${i === prePage ? 'active' : ''}" data-pre-page="${i}">${i}</button>`);
  pager.innerHTML = `
    <span>共 ${total} 条</span>
    <select id="prePageSizeSel">
      <option value="10" ${prePageSize === 10 ? 'selected' : ''}>10 条/页</option>
      <option value="20" ${prePageSize === 20 ? 'selected' : ''}>20 条/页</option>
    </select>
    <button class="pager-btn" data-pre-page="${Math.max(1, prePage - 1)}" ${prePage === 1 ? 'disabled' : ''}>‹</button>
    ${btns.join('')}
    <button class="pager-btn" data-pre-page="${Math.min(pages, prePage + 1)}" ${prePage === pages ? 'disabled' : ''}>›</button>`;
  $$('#prePager [data-pre-page]').forEach((b) => b.addEventListener('click', () => {
    if (b.disabled) return;
    prePage = Number(b.dataset.prePage);
    renderPreBody();
  }));
  const sel = $('#prePageSizeSel');
  if (sel) sel.addEventListener('change', (e) => { prePageSize = Number(e.target.value); prePage = 1; renderPreBody(); });
}
function renderPreHeader() {
  const cols = PRE_COLUMNS.filter((c) => preVisibleKeys.has(c.key));
  const totalWidth = 60 + cols.reduce((s, c) => s + effWidth(c), 0) + 150;
  const colgroup = `<colgroup><col style="width:60px">${cols.map((c) => `<col style="width:${effWidth(c)}px">`).join('')}<col style="width:150px"></colgroup>`;
  const head = `<thead><tr>
    <th class="struct-col"><div class="th-inner"><div class="th-label">序号</div></div></th>
    ${cols.map((c) => {
      const sorted = preSortKey === c.key;
      const caret = sorted ? (preSortOrder === 'asc' ? '↑' : '↓') : '↕';
      return `<th><div class="th-inner">
        <div class="th-label ${sorted ? 'sorted' : ''}" data-pre-sort="${c.key}">${c.label}<span class="caret">${caret}</span></div>
        <div class="th-filter">${filterHtml(c, preFilters, 'pre-filter')}</div>
      </div></th>`;
    }).join('')}
    <th class="struct-col"><div class="th-inner"><div class="th-label">操作</div></div></th>
  </tr></thead>`;
  const table = $('#preTable');
  table.style.width = totalWidth + 'px';
  table.innerHTML = colgroup + head + '<tbody id="preBody"></tbody><tfoot id="preFoot"></tfoot>';
  $$('[data-pre-sort]').forEach((el) => {
    el.addEventListener('click', () => {
      const key = el.dataset.preSort;
      if (preSortKey === key) preSortOrder = preSortOrder === 'asc' ? 'desc' : 'asc';
      else { preSortKey = key; preSortOrder = 'asc'; }
      renderPreHeader();
      renderPreBody();
    });
  });
  $$('#preTable [data-pre-filter]').forEach((el) => {
    const handler = () => {
      const key = el.dataset.preFilter;
      if (el.dataset.preBound) {
        const cur = preFilters[key] && typeof preFilters[key] === 'object' ? preFilters[key] : { min: '', max: '' };
        preFilters[key] = { ...cur, [el.dataset.preBound]: el.value };
      } else {
        preFilters[key] = el.value;
      }
      prePage = 1;
      renderPreBody();
    };
    el.addEventListener('input', handler);
    el.addEventListener('change', handler);
  });
}
function renderPreBody() {
  const normalized = preOrders.map((r) => ({ ...r, destName: r.destName || (r.province || '') + (r.city || '') + (r.district || '') }));
  const rows = sortRowsFor(
    normalized.filter((r) => matchFiltersFor(r, preFilters, preVisibleKeys, PRE_COLUMNS)),
    preSortKey, preSortOrder, PRE_COLUMNS
  );
  const total = rows.length;
  const pages = Math.max(1, Math.ceil(total / prePageSize));
  if (prePage > pages) prePage = pages;
  const pageRows = rows.slice((prePage - 1) * prePageSize, prePage * prePageSize);
  const cols = PRE_COLUMNS.filter((c) => preVisibleKeys.has(c.key));
  $('#preBody').innerHTML = pageRows.map((row, idx) => `
    <tr>
      <td class="center">${(prePage - 1) * prePageSize + idx + 1}</td>
      ${cols.map((c) => {
        const cls = c.type === 'money' ? 'money' : ['int', 'decimal3'].includes(c.type) ? 'num' : '';
        return `<td class="${cls}" title="${escapeHtml(String(row[c.key] ?? ''))}">${cellHtml(c, row)}</td>`;
      }).join('')}
      <td class="center"><div class="row-actions">${preRowActions(row)}</div></td>
    </tr>`).join('') || `<tr><td colspan="${cols.length + 2}" style="text-align:center;color:#98a2b3;height:120px">暂无预开单</td></tr>`;

  const sumCells = cols.map((c, i) => {
    if (i === 0) return '';
    if (!c.sum) {
      const cls = c.type === 'money' ? 'money' : ['int', 'decimal3'].includes(c.type) ? 'num' : '';
      return `<td class="${cls}">--</td>`;
    }
    const val = rows.reduce((s, r) => s + Number(r[c.key] || 0), 0);
    const text = c.type === 'decimal3' ? fmt3(val) : fmtInt(val);
    return `<td class="num">${text}</td>`;
  }).join('');
  $('#preFoot').innerHTML = `<tr><td colspan="2">合计（共 ${total} 条）</td>${sumCells}<td class="center">--</td></tr>`;
  renderPrePager(total, pages);
}
function renderPreTable() {
  renderPreHeader();
  renderPreBody();
}
function updatePrePagerOnly() {}
function renderPreColumnPanel() {
  $('#preColumnList').innerHTML = PRE_COLUMNS.map((c) => `
    <label class="col-check"><input type="checkbox" data-pre-col="${c.key}" ${preVisibleKeys.has(c.key) ? 'checked' : ''}>${c.label}</label>
  `).join('');
  $('#preColumnCount').textContent = `已显示 ${preVisibleKeys.size} / ${PRE_COLUMNS.length}`;
  $$('#preColumnList [data-pre-col]').forEach((cb) => {
    cb.addEventListener('change', () => {
      if (cb.checked) preVisibleKeys.add(cb.dataset.preCol);
      else preVisibleKeys.delete(cb.dataset.preCol);
      $('#preColumnCount').textContent = `已显示 ${preVisibleKeys.size} / ${PRE_COLUMNS.length}`;
      renderPreTable();
    });
  });
}
function resetPreColumns() {
  preVisibleKeys.clear();
  PRE_COLUMNS.filter((c) => c.def).forEach((c) => preVisibleKeys.add(c.key));
  renderPreColumnPanel();
  renderPreTable();
  toast('预开单列已恢复默认');
}
function loadPreOrder(p) {
  preMode = true;
  selectedPreOrder = p;
  fillPreOrderForm(p);
  $('#formArea').classList.remove('hidden');
  $('#preEmpty').classList.add('hidden');
  $('#sourceBanner').scrollIntoView({ behavior: 'smooth', block: 'center' });
}
function confirmSwitchPreOrder(p) {
  if (preMode && selectedPreOrder && selectedPreOrder.no !== p.no) {
    return confirm('当前预开单尚未确认揽收，接单新的预开单将覆盖当前草稿，是否继续？');
  }
  return true;
}
function handlePreAccept(no) {
  const p = preOrders.find((x) => x.no === no);
  if (!p) return;
  if (p.status === 20) {
    if (p.acceptBy !== '张三') { toast(`该预开单已被 ${p.acceptBy} 接单，不能重复接单`, 'error'); return; }
    if (!confirmSwitchPreOrder(p)) return;
    loadPreOrder(p);
    toast('该预开单由你接单，已打开继续处理');
    return;
  }
  if (p.status !== 10) {
    if (p.status === 30) toast(`该预开单已揽收，运单号 ${p.waybillNo}，请到运单管理查看`);
    else toast('该预开单已撤单，不能接单', 'error');
    return;
  }
  if (!confirmSwitchPreOrder(p)) return;
  p.status = 20;
  p.acceptBy = '张三';
  renderPreTable();
  updatePreCount();
  loadPreOrder(p);
  toast('接单成功，已锁定该预开单', 'success');
}
function returnPreByNo(no) {
  const p = preOrders.find((x) => x.no === no);
  if (!p || p.status !== 20) { toast('仅已接单状态可退回', 'error'); return; }
  if (!confirm(`确认退回预开单 ${p.no}？退回后状态恢复为待接单。`)) return;
  p.status = 10;
  p.acceptBy = '';
  if (selectedPreOrder && selectedPreOrder.no === no) {
    preMode = false;
    selectedPreOrder = null;
    drafts.pre = null;
    resetForm(true);
    $('#formArea').classList.add('hidden');
    $('#preEmpty').classList.remove('hidden');
  }
  renderPreTable();
  updatePreCount();
  toast('已退回预开单，状态恢复为待接单');
}
function returnPreOrder() {
  if (!preMode || !selectedPreOrder) return;
  returnPreByNo(selectedPreOrder.no);
}
function clearSource() {
  if (preMode && selectedPreOrder) { returnPreOrder(); return; }
  selectedPreOrder = null;
  preMode = false;
  $('#sourceBanner').classList.add('hidden');
  setFormMode('self');
}

/* ---------- 表单模式 / 草稿 ---------- */
const FORM_SELECTORS = [
  '#relatedNo', '#salesName', '#bizType', '#destInput',
  '#senderName', '#senderPhone', '#senderCompany', '#senderAddress',
  '#receiverName', '#receiverPhone', '#receiverCompany', '#receiverAddress',
  '#rcvProvince', '#rcvCity', '#rcvDistrict',
  '#declaredValue',
  '#codAmount', '#payeeName', '#bankName', '#bankCardNo',
  '#receiptCount', '#remark', '#needReceipt', '#waitNotice'
];

function currentDelivery() {
  const el = document.querySelector('input[name="deliveryMethod"]:checked');
  return el ? el.value : '';
}
function setDelivery(value) {
  $$('input[name="deliveryMethod"]').forEach((r) => { r.checked = String(r.value) === String(value || ''); });
}
function setReceiverRegion(p, c, d) {
  $('#rcvProvince').value = p || '';
  $('#rcvProvince').dispatchEvent(new Event('change'));
  $('#rcvCity').value = c || '';
  $('#rcvCity').dispatchEvent(new Event('change'));
  $('#rcvDistrict').value = d || '';
}

/* ---------- 必填项浅红底状态（v1.5） ---------- */
const REQ_CONTROLS = [
  '#bizType', '#destInput',
  '#senderName', '#senderPhone', '#senderAddress',
  '#receiverName', '#receiverPhone', '#receiverAddress'
];
function refreshRequiredStates() {
  REQ_CONTROLS.forEach((sel) => {
    const el = $(sel);
    if (!el) return;
    let filled = String(el.value || '').trim().length > 0;
    if (sel === '#destInput') filled = !!selectedDest;
    const field = el.closest('.field');
    if (field) field.classList.toggle('is-unfilled', !filled);
  });
  $$('#goodsBody tr').forEach((tr) => {
    ['name', 'count', 'volume', 'weight'].forEach((key) => {
      const el = tr.querySelector(`[data-g="${key}"]`);
      if (!el) return;
      const filled = key === 'name' ? !!String(el.value || '').trim() : Number(el.value || 0) > 0;
      el.classList.toggle('req-unfilled', !filled);
    });
  });
  const payBlock = $('#payBlock');
  if (payBlock) payBlock.classList.toggle('is-unfilled', !payMethod);
  const dvBlock = $('#deliveryBlock');
  if (dvBlock) dvBlock.classList.toggle('is-unfilled', !currentDelivery());
}

function setFormMode(mode) {
  const isPre = mode === 'pre';
  $('#saveBtn').textContent = isPre ? '确认揽收' : '保存';
  $('#saveNextBtn').classList.toggle('hidden', isPre);
  $('#returnBtn').classList.toggle('hidden', !isPre);
  $('#resetBtn').textContent = isPre ? '恢复申报信息' : '重置';
}

function renderSourceBanner() {
  const p = selectedPreOrder;
  if (!p) { $('#sourceBanner').classList.add('hidden'); return; }
  $('#sourceBanner').classList.remove('hidden');
  $('#sourceText').textContent = `${p.no} ｜ ${p.customer} ｜ 提交 ${p.submitted} ｜ 期望提货 ${p.expectPickup}`;
}

function captureForm() {
  const values = {};
  FORM_SELECTORS.forEach((sel) => { const el = $(sel); if (el) values[sel] = el.value; });
  return {
    values,
    fees: { ...fees },
    pay: { ...pay },
    payMethod,
    routes: routes.map((r) => ({ ...r })),
    selectedRoute,
    dest: selectedDest ? { ...selectedDest } : null,
    goods: goodsRows.map((r) => ({ ...r })),
    delivery: currentDelivery(),
    customer: matchedCustomer ? { ...matchedCustomer } : null,
    preNo: selectedPreOrder ? selectedPreOrder.no : null,
    preMode
  };
}

function restoreForm(snap, tab) {
  if (!snap) return;
  const v = snap.values || {};
  $('#rcvProvince').value = v['#rcvProvince'] || '';
  $('#rcvProvince').dispatchEvent(new Event('change'));
  $('#rcvCity').value = v['#rcvCity'] || '';
  $('#rcvCity').dispatchEvent(new Event('change'));
  $('#rcvDistrict').value = v['#rcvDistrict'] || '';
  FORM_SELECTORS.forEach((sel) => {
    if (['#rcvProvince', '#rcvCity', '#rcvDistrict'].includes(sel)) return;
    const el = $(sel);
    if (el && v[sel] !== undefined) el.value = v[sel];
  });
  goodsRows = (snap.goods || [emptyGoodsRow()]).map((r) => ({ ...r }));
  renderGoods();
  matchedCustomer = snap.customer ? { ...snap.customer } : null;
  customerStatus = matchedCustomer ? 'hit' : 'idle';
  customerOptions = matchedCustomer ? [matchedCustomer] : [];
  renderCustomerMatch();
  setDelivery(snap.delivery);
  selectedDest = snap.dest ? { ...snap.dest } : null;
  routes = (snap.routes || []).map((r) => ({ ...r }));
  selectedRoute = snap.selectedRoute || 0;
  renderRoutes();
  Object.assign(fees, snap.fees || {});
  $$('.fee').forEach((i) => { i.value = Number(fees[i.dataset.fee] || 0); });
  Object.assign(pay, snap.pay || {});
  payMethod = Number.isInteger(snap.payMethod) ? snap.payMethod : 0;
  $$('#payMethods button').forEach((b) => b.classList.toggle('active', Number(b.dataset.paymethod) === payMethod));
  renderPayFields();
  selectedPreOrder = snap.preNo ? preOrders.find((x) => x.no === snap.preNo) || null : null;
  preMode = !!(tab === 'pre' && snap.preMode && selectedPreOrder);
  if (preMode) {
    renderSourceBanner();
    setFormMode('pre');
  } else {
    $('#sourceBanner').classList.add('hidden');
    setFormMode('self');
  }
  $('#payBlock').classList.remove('has-error');
  $('#deliveryBlock').classList.remove('has-error');
  recalc();
  refreshRequiredStates();
}

function fillPreOrderForm(p) {
  $('#payBlock').classList.remove('has-error');
  $('#deliveryBlock').classList.remove('has-error');
  $('#senderName').value = p.sender;
  $('#senderPhone').value = p.senderPhone;
  $('#senderCompany').value = p.customer;
  $('#senderAddress').value = p.senderAddress;
  refreshCustomerMatch();
  $('#receiverName').value = p.receiver;
  $('#receiverPhone').value = p.receiverPhone;
  $('#receiverCompany').value = '';
  $('#receiverAddress').value = p.receiverAddress;
  setReceiverRegion(p.province, p.city, p.district);
  goodsRows = [{
    name: p.goods, type: '', pack: '', count: Number(p.qty || 0),
    volume: Number(p.volume || 0), weight: Number(p.weight || 0), remark: ''
  }];
  renderGoods();
  setDelivery('');
  const dest = DESTINATIONS.find((d) => d.province === p.province && d.city === p.city && d.district === p.district);
  if (dest) {
    selectDest({ dest, route: dest.routes[0] || null, idx: 0 });
  } else {
    $('#destInput').value = p.district || '';
    selectedDest = null;
    routes = [];
    selectedRoute = 0;
    renderRoutes();
  }
  Object.keys(fees).forEach((k) => { fees[k] = 0; });
  $$('.fee').forEach((i) => { i.value = '0'; });
  $('#declaredValue').value = '0';
  payMethod = 0;
  pay = { prepaidFee: 0, collectFee: 0, monthlyFee: 0, returnFee: 0 };
  $$('#payMethods button').forEach((b) => b.classList.remove('active'));
  $('#codAmount').value = '0';
  renderPayFields();
  recalc();
  $('#formError').classList.add('hidden');
  $('#formSuccess').classList.add('hidden');
  $('#contextWaybill').textContent = '确认揽收后生成';
  $('#contextWaybill').classList.add('muted');
  $$('.is-empty').forEach((el) => el.classList.remove('is-empty'));
  renderSourceBanner();
  setFormMode('pre');
  refreshRequiredStates();
}

/* ---------- 校验 / 保存 / 重置 ---------- */
const REQUIRED = [
  ['#bizType', '业务类型'], ['#destInput', '目的地'],
  ['#senderName', '发货人'], ['#senderPhone', '发货电话'], ['#senderAddress', '发货地址'],
  ['#receiverName', '收货人'], ['#receiverPhone', '收货电话'], ['#receiverAddress', '收货地址']
];
function validateForm() {
  const errors = [];
  $$('.is-empty').forEach((el) => el.classList.remove('is-empty'));
  $$('#goodsBody tr.goods-invalid').forEach((el) => el.classList.remove('goods-invalid'));
  const payBlock = $('#payBlock');
  const dvBlock = $('#deliveryBlock');
  if (payBlock) payBlock.classList.remove('has-error');
  if (dvBlock) dvBlock.classList.remove('has-error');
  REQUIRED.forEach(([sel, label]) => {
    const el = $(sel);
    if (!String(el.value || '').trim()) {
      el.classList.add('is-empty');
      errors.push(label);
    }
  });
  let goodsError = false;
  goodsRows.forEach((r, i) => {
    const name = String(r.name || '').trim();
    const touched = name || Number(r.count) > 0 || Number(r.volume) > 0 || Number(r.weight) > 0;
    if (!touched) return;
    const missing = [];
    if (!name) missing.push('货物名称');
    if (!(Number(r.count) > 0)) missing.push('件数');
    if (!(Number(r.volume) > 0)) missing.push('体积');
    if (!(Number(r.weight) > 0)) missing.push('重量');
    if (!missing.length) return;
    goodsError = true;
    const tr = $(`#goodsBody tr[data-idx="${i}"]`);
    if (tr) {
      tr.classList.add('goods-invalid');
      if (!(Number(r.volume) > 0)) {
        const vol = tr.querySelector('[data-g="volume"]');
        if (vol) vol.classList.add('is-empty');
      }
    }
    errors.push(`第 ${i + 1} 行货物明细：${missing.join('、')}未填写`);
  });
  if (!goodsError && !goodsRows.some((r) => String(r.name || '').trim())) {
    const first = $('#goodsBody tr');
    if (first) first.classList.add('goods-invalid');
    errors.push('货物明细（至少填写一行：货物名称、件数、体积、重量）');
  }
  if (!payMethod) {
    if (payBlock) payBlock.classList.add('has-error');
    errors.unshift('支付方式');
  }
  if (!currentDelivery()) {
    if (dvBlock) dvBlock.classList.add('has-error');
    errors.unshift('送货方式');
  }
  if (String($('#destInput').value || '').trim() && !selectedDest) errors.push('目的地（请从候选列表中选择唯一目的地）');
  if (!routes.length) errors.push('路由（当前目的地无可用路由）');
  if (payMethod === 5) {
    const diff = round2(calcTotal() - totalPay());
    if (Math.abs(diff) >= 0.005) errors.push(`多笔付四项合计与运费合计差额 ¥${fmtMoney(Math.abs(diff))}`);
  }
  const cod = Number($('#codAmount').value || 0);
  if (cod > 0) {
    [['#payeeName', '收款人'], ['#bankName', '银行名称'], ['#bankCardNo', '银行卡号']].forEach(([sel, label]) => {
      if (!$(sel).value.trim()) { $(sel).classList.add('is-empty'); errors.push(label + '（代收货款>0）'); }
    });
  }
  if ($('#needReceipt').value === '1' && Number($('#receiptCount').value || 0) < 1) {
    $('#receiptCount').classList.add('is-empty');
    errors.push('回单数量');
  }
  const box = $('#formError');
  if (errors.length) {
    box.classList.remove('hidden');
    box.innerHTML = `<b>请完善以下 ${errors.length} 项：</b><ul>${errors.slice(0, 8).map((e) => `<li>${e}</li>`).join('')}${errors.length > 8 ? '<li>…</li>' : ''}</ul>`;
    return false;
  }
  box.classList.add('hidden');
  return true;
}
function doSave(continueNext = false) {
  if (!validateForm()) {
    toast('校验未通过，请查看表单顶部提示', 'error');
    $('#formError').scrollIntoView({ behavior: 'smooth', block: 'center' });
    return;
  }
  const no = 'YD20261123' + pad6(waybillSeq++);
  const payText = $('#payMethods button.active').textContent;
  const rebate = round2(Number(fees.cashBack || 0) + Number(fees.oweBack || 0));
  const valid = round2(calcTotal() - rebate);
  const feeLine = `<li>返款合计 ¥${fmtMoney(rebate)}，有效运费 ¥${fmtMoney(valid)}${valid < 0 ? '（负值，按待确认口径放行）' : ''}</li>`;
  const success = $('#formSuccess');

  if (preMode && selectedPreOrder) {
    const pre = selectedPreOrder;
    pre.status = 30;
    pre.waybillNo = no;
    pre.acceptBy = pre.acceptBy || '张三';
    $('#contextWaybill').textContent = no;
    $('#contextWaybill').classList.remove('muted');
    success.classList.remove('hidden');
    success.innerHTML = `<b>运单 ${no} 已生成</b><ul><li>预开单 ${pre.no} 状态更新为已揽收（30）</li><li>支付方式：${payText}，运费合计 ¥${fmtMoney(calcTotal())}</li>${feeLine}<li>该预开单已从待接单列表移出</li></ul>`;
    toast('预开单已揽收，运单 ' + no + ' 已生成', 'success');
    drafts.pre = null;
    renderPreTable();
    updatePreCount();
    setTimeout(() => {
      resetForm();
      $('#formArea').classList.add('hidden');
      $('#preEmpty').classList.remove('hidden');
      toast('已回到预开单列表，可继续接单');
    }, 1200);
    return;
  }

  $('#contextWaybill').textContent = no;
  $('#contextWaybill').classList.remove('muted');
  success.classList.remove('hidden');
  success.innerHTML = `<b>运单 ${no} 已生成</b><ul><li>状态：已揽收（20）</li><li>支付方式：${payText}，运费合计 ¥${fmtMoney(calcTotal())}</li>${feeLine}<li>来源：自行开单</li></ul>`;
  toast('运单 ' + no + ' 已生成', 'success');
  if (continueNext) {
    setTimeout(() => { resetForm(); toast('已重置，可继续开单', 'success'); }, 900);
  }
}
function resetForm() {
  ['#relatedNo', '#salesName', '#destInput',
    '#senderName', '#senderPhone', '#senderCompany', '#senderAddress',
    '#receiverName', '#receiverPhone', '#receiverCompany', '#receiverAddress', '#rcvDistrict',
    '#codAmount', '#payeeName', '#bankName', '#bankCardNo',
    '#receiptCount', '#remark'].forEach((sel) => { const el = $(sel); if (el) el.value = ''; });
  matchedCustomer = null;
  customerStatus = 'idle';
  customerOptions = [];
  renderCustomerMatch();
  $('#bizType').value = '2';
  $('#waybillNoPreview').value = '保存后自动生成';
  $('#rcvProvince').value = '';
  $('#rcvProvince').dispatchEvent(new Event('change'));
  goodsRows = [emptyGoodsRow()];
  renderGoods();
  setDelivery('');
  $('#needReceipt').value = '0';
  $('#receiptCount').value = '0';
  $('#waitNotice').value = '0';
  clearDestination();
  Object.keys(fees).forEach((k) => { fees[k] = 0; });
  $$('.fee').forEach((i) => { i.value = '0'; });
  $('#declaredValue').value = '0';
  $('#codAmount').value = '0';
  payMethod = 0;
  pay = { prepaidFee: 0, collectFee: 0, monthlyFee: 0, returnFee: 0 };
  $$('#payMethods button').forEach((b) => b.classList.remove('active'));
  renderPayFields();
  selectedRoute = 0;
  routes = [];
  renderRoutes();
  $('#sourceBanner').classList.add('hidden');
  $('#formError').classList.add('hidden');
  $('#formSuccess').classList.add('hidden');
  $('#contextWaybill').textContent = '保存后生成';
  $('#contextWaybill').classList.add('muted');
  setFormMode('self');
  selectedPreOrder = null;
  preMode = false;
  recalc();
  $('#payBlock').classList.remove('has-error');
  $('#deliveryBlock').classList.remove('has-error');
  refreshRequiredStates();
}

/* ============================================================
 * 运单管理：列定义（49 项）
 * ============================================================ */
const BIZ_TYPE = { 1: '整车', 2: '零担' };
const PAY_METHOD = { 1: '现付', 2: '到付', 3: '月结', 4: '回付', 5: '多笔付' };
const DELIVERY_METHOD = { 1: '送货', 2: '自提', 3: '大车直送' };
const YES_NO = { 1: '是', 0: '否' };
const PROVINCES = Object.keys(REGION);

const HUB_NAMES = Array.from(new Set(['济南程驰', ...DESTINATIONS.map((d) => d.hub)]));
const COLUMNS = [
  { group: '单据', key: 'waybillNo', label: '运单号', width: 220, type: 'link', filter: 'text', def: true },
  { group: '单据', key: 'isChild', label: '单据类型', width: 100, type: 'enum', options: { 0: '母单', 1: '子单' }, filter: 'select', def: false },
  { group: '单据', key: 'childSeq', label: '子单序号', width: 96, type: 'int', filter: 'number', def: false },
  { group: '单据', key: 'relatedNo', label: '关联单号', width: 160, type: 'text', filter: 'text', def: true },
  { group: '目的地', key: 'destName', label: '目的地', width: 180, type: 'text', filter: 'text', def: true },
  { group: '目的地', key: 'destProvince', label: '目的地省', width: 140, type: 'text', filter: 'select', options: PROVINCES, filterOptions: PROVINCES, def: false },
  { group: '目的地', key: 'destCity', label: '目的市', width: 140, type: 'text', filter: 'text', def: false },
  { group: '目的地', key: 'destDistrict', label: '目的地区县', width: 140, type: 'text', filter: 'text', def: false },
  { group: '网点', key: 'originHubName', label: '始发网点', width: 150, type: 'text', filter: 'select', filterOptions: HUB_NAMES, def: false },
  { group: '网点', key: 'destHubName', label: '运达网点', width: 150, type: 'text', filter: 'select', filterOptions: HUB_NAMES, def: true },
  { group: '基础', key: 'salesName', label: '业务员', width: 136, type: 'text', filter: 'text', def: false },
  { group: '基础', key: 'bizType', label: '业务类型', width: 140, type: 'enum', options: BIZ_TYPE, filter: 'select', def: true },
  { group: '基础', key: 'routeName', label: '线路', width: 280, type: 'text', filter: 'text', def: true },
  { group: '发货方', key: 'senderName', label: '发货人', width: 136, type: 'text', filter: 'text', def: true },
  { group: '发货方', key: 'senderPhone', label: '发货电话', width: 150, type: 'text', filter: 'text', def: false },
  { group: '发货方', key: 'senderAddress', label: '发货地址', width: 220, type: 'text', filter: 'text', def: false },
  { group: '发货方', key: 'customerName', label: '客户名称', width: 180, type: 'text', filter: 'text', def: true },
  { group: '发货方', key: 'customerPhone', label: '客户电话', width: 150, type: 'text', filter: 'text', def: false },
  { group: '收货方', key: 'receiverName', label: '收货人', width: 136, type: 'text', filter: 'text', def: true },
  { group: '收货方', key: 'receiverPhone', label: '收货电话', width: 150, type: 'text', filter: 'text', def: false },
  { group: '收货方', key: 'receiverAddress', label: '收货地址', width: 220, type: 'text', filter: 'text', def: false },
  { group: '货物', key: 'goodsName', label: '货物名称', width: 160, type: 'text', filter: 'text', def: false },
  { group: '货物', key: 'goodsType', label: '货物类型', width: 136, type: 'text', filter: 'text', def: false },
  { group: '货物', key: 'packageType', label: '包装类型', width: 136, type: 'text', filter: 'text', def: false },
  { group: '货物', key: 'quantity', label: '件数', width: 176, type: 'int', filter: 'number', sum: true, def: true },
  { group: '货物', key: 'weight', label: '重量', width: 176, type: 'decimal3', filter: 'number', sum: true, def: true },
  { group: '货物', key: 'volume', label: '体积', width: 176, type: 'decimal3', filter: 'number', sum: true, def: false },
  { group: '货物', key: 'goodsRemark', label: '货物备注', width: 200, type: 'text', filter: 'text', def: false },
  { group: '支付', key: 'payMethod', label: '支付方式', width: 140, type: 'enum', options: PAY_METHOD, filter: 'select', def: true },
  { group: '费用', key: 'freightFee', label: '运费', width: 176, type: 'money', filter: 'number', sum: true, def: true },
  { group: '费用', key: 'deliveryFee', label: '送货费', width: 176, type: 'money', filter: 'number', sum: true, def: false },
  { group: '费用', key: 'pickupFee', label: '提货费', width: 176, type: 'money', filter: 'number', sum: true, def: false },
  { group: '费用', key: 'otherFee', label: '其他费', width: 176, type: 'money', filter: 'number', sum: true, def: false },
  { group: '费用', key: 'receiptFee', label: '回单费', width: 176, type: 'money', filter: 'number', sum: true, def: false },
  { group: '费用', key: 'makeBillFee', label: '制单费', width: 176, type: 'money', filter: 'number', sum: true, def: false },
  { group: '费用', key: 'insuranceFee', label: '保费', width: 176, type: 'money', filter: 'number', sum: true, def: false },
  { group: '费用', key: 'declaredValue', label: '声明价值', width: 176, type: 'money', filter: 'number', sum: false, def: true },
  { group: '费用', key: 'cashBack', label: '现返', width: 176, type: 'money', filter: 'number', sum: true, def: false },
  { group: '费用', key: 'oweBack', label: '欠返', width: 176, type: 'money', filter: 'number', sum: true, def: false },
  { group: '费用', key: 'rebateTotal', label: '返款合计', width: 176, type: 'money', filter: 'number', sum: true, def: false },
  { group: '费用', key: 'validFreight', label: '有效运费', width: 176, type: 'money', filter: 'number', sum: true, def: true },
  { group: '支付', key: 'prepaidFee', label: '现付运费', width: 176, type: 'money', filter: 'number', sum: true, def: false },
  { group: '支付', key: 'collectFee', label: '到付运费', width: 176, type: 'money', filter: 'number', sum: true, def: false },
  { group: '支付', key: 'monthlyFee', label: '月结运费', width: 176, type: 'money', filter: 'number', sum: true, def: false },
  { group: '支付', key: 'returnFee', label: '回付运费', width: 176, type: 'money', filter: 'number', sum: true, def: false },
  { group: '其他', key: 'deliveryMethod', label: '送货方式', width: 150, type: 'enum', options: DELIVERY_METHOD, filter: 'select', def: false },
  { group: '代收', key: 'codAmount', label: '代收货款', width: 176, type: 'money', filter: 'number', sum: true, def: true },
  { group: '代收', key: 'payeeName', label: '收款人', width: 136, type: 'text', filter: 'text', def: false },
  { group: '代收', key: 'bankName', label: '银行名称', width: 160, type: 'text', filter: 'text', def: false },
  { group: '代收', key: 'bankCardNo', label: '银行卡号', width: 180, type: 'masked', filter: 'text', def: false },
  { group: '其他', key: 'needReceipt', label: '是否回单', width: 136, type: 'bool', options: YES_NO, filter: 'select', def: false },
  { group: '其他', key: 'receiptCount', label: '回单数量', width: 176, type: 'int', filter: 'number', sum: true, def: false },
  { group: '其他', key: 'waitNotice', label: '是否等通知', width: 136, type: 'bool', options: YES_NO, filter: 'select', def: false },
  { group: '其他', key: 'remark', label: '备注', width: 220, type: 'text', filter: 'text', def: false },
  { group: '审计', key: 'createByName', label: '开单员', width: 136, type: 'text', filter: 'text', def: true },
  { group: '审计', key: 'createTime', label: '开单时间', width: 200, type: 'datetime', filter: 'date', def: true }
];

const BASE_ROW = {
  id: 0, waybillNo: '', relatedNo: '', parentId: null, parentNo: '', isChild: 0, childSeq: null,
  destName: '', destProvince: '', destCity: '', destDistrict: '',
  originHubName: '济南程驰', destHubName: '',
  salesName: '', bizType: 2, routeName: '', senderName: '', senderPhone: '', senderAddress: '',
  customerName: '', customerPhone: '', receiverName: '', receiverPhone: '', receiverAddress: '',
  goodsName: '', goodsType: '', packageType: '', quantity: 0, weight: 0, volume: 0, goodsRemark: '',
  payMethod: 1, freightFee: 0, deliveryFee: 0, pickupFee: 0, otherFee: 0, receiptFee: 0, makeBillFee: 0,
  insuranceFee: 0, declaredValue: 0, cashBack: 0, oweBack: 0, prepaidFee: 0, collectFee: 0, monthlyFee: 0, returnFee: 0,
  deliveryMethod: 1, codAmount: 0, payeeName: '', bankName: '', bankCardNo: '', needReceipt: 0,
  receiptCount: 0, waitNotice: 0, remark: '', createByName: '', createTime: '', status: 20,
  deptName: '济南程驰'
};

function normalizeRow(row) {
  const total = round2(FEE_KEYS.reduce((s, k) => s + Number(row[k] || 0), 0));
  row.totalFee = total;
  row.rebateTotal = round2(Number(row.cashBack || 0) + Number(row.oweBack || 0));
  row.validFreight = round2(total - row.rebateTotal);
  if (row.payMethod === 1) { row.prepaidFee = total; row.collectFee = 0; row.monthlyFee = 0; row.returnFee = 0; }
  else if (row.payMethod === 2) { row.prepaidFee = 0; row.collectFee = total; row.monthlyFee = 0; row.returnFee = 0; }
  else if (row.payMethod === 3) { row.prepaidFee = 0; row.collectFee = 0; row.monthlyFee = total; row.returnFee = 0; }
  else if (row.payMethod === 4) { row.prepaidFee = 0; row.collectFee = 0; row.monthlyFee = 0; row.returnFee = total; }
  else if (row.payMethod === 5) {
    let sum = round2(row.prepaidFee + row.collectFee + row.monthlyFee + row.returnFee);
    if (sum === 0 && total > 0) { row.prepaidFee = round2(total / 2); row.collectFee = round2(total - row.prepaidFee); }
    else if (Math.abs(sum - total) >= 0.005) {
      const diff = round2(total - sum);
      row.returnFee = round2(row.returnFee + diff);
    }
  }
  return row;
}

const ROUTE_OF = {
  白云区: '济南程驰 → 广州 → 白云区（直）',
  天河区: '济南程驰 → 济南分拨中心 → 广州 → 天河区（直）',
  番禺区: '济南程驰 → 济南分拨中心 → 广州 → 番禺区（外转）',
  宝安区: '济南程驰 → 济南分拨中心 → 广州 → 深圳 → 宝安区（外转）',
  姑苏区: '济南程驰 → 济南分拨中心 → 苏州 → 姑苏区（外转）',
  余杭区: '济南程驰 → 济南分拨中心 → 杭州 → 余杭区（直）',
  金水区: '济南程驰 → 济南分拨中心 → 郑州 → 金水区（直）',
  武侯区: '济南程驰 → 济南分拨中心 → 成都 → 武侯区（外转）',
  江汉区: '济南程驰 → 济南分拨中心 → 武汉 → 江汉区（直）',
  雁塔区: '济南程驰 → 济南分拨中心 → 西安 → 雁塔区（外转）',
  历下区: '济南程驰 → 历下区（直）',
  槐荫区: '济南程驰 → 槐荫区（直）'
};

const TEMPLATES = [
  { customerName: '广州华远物流有限公司', customerPhone: '020-88886666', destProvince: '广东省', destCity: '广州市', destDistrict: '白云区', senderName: '王海', senderPhone: '13800138001', senderAddress: '济南市历下区经十路 88 号', receiverName: '陈晨', receiverPhone: '13900139001', receiverAddress: '白云区机场路 12 号', goodsName: '服装', goodsType: '普货', packageType: '纸箱', quantity: 36, weight: 420.5, volume: 3.2, goodsRemark: '防潮', payMethod: 1, freightFee: 1200, deliveryFee: 80, receiptFee: 20, makeBillFee: 10, insuranceFee: 30, codAmount: 5000, payeeName: '陈晨', bankName: '中国建设银行广州白云支行', bankCardNo: '6227001234567890001', deliveryMethod: 1, needReceipt: 1, receiptCount: 1, remark: '客户要求上午送达', salesName: '李强' },
  { customerName: '深圳市顺达供应链有限公司', customerPhone: '0755-86668888', destProvince: '广东省', destCity: '深圳市', destDistrict: '宝安区', senderName: '刘洋', senderPhone: '13700137002', senderAddress: '济南市槐荫区经一路 36 号', receiverName: '周敏', receiverPhone: '13600136002', receiverAddress: '宝安区福永街道 8 号', goodsName: '电子配件', goodsType: '精密仪器', packageType: '木架', quantity: 120, weight: 860, volume: 6.4, payMethod: 2, freightFee: 2100, deliveryFee: 120, otherFee: 50, receiptFee: 20, makeBillFee: 10, insuranceFee: 60, deliveryMethod: 1, waitNotice: 1, salesName: '王敏' },
  { customerName: '佛山嘉信陶瓷有限公司', customerPhone: '0757-82223333', destProvince: '广东省', destCity: '广州市', destDistrict: '天河区', senderName: '赵磊', senderPhone: '13500135003', senderAddress: '济南市天桥区北园大街 20 号', receiverName: '吴静', receiverPhone: '13400134003', receiverAddress: '天河区体育西路 5 号', goodsName: '陶瓷样品', goodsType: '易碎品', packageType: '木箱', quantity: 8, weight: 320, volume: 1.2, goodsRemark: '易碎，轻拿轻放', payMethod: 3, freightFee: 680, pickupFee: 50, receiptFee: 20, makeBillFee: 10, deliveryMethod: 2, salesName: '赵磊' },
  { customerName: '东莞恒通电子有限公司', customerPhone: '0769-81112222', destProvince: '江苏省', destCity: '苏州市', destDistrict: '姑苏区', senderName: '孙浩', senderPhone: '13300133004', senderAddress: '济南市历城区花园路 9 号', receiverName: '郑洁', receiverPhone: '13200132004', receiverAddress: '姑苏区干将东路 100 号', goodsName: '主板', goodsType: '电子产品', packageType: '纸箱', quantity: 60, weight: 240, volume: 2.1, payMethod: 4, freightFee: 980, deliveryFee: 60, receiptFee: 20, makeBillFee: 10, insuranceFee: 20, deliveryMethod: 1, needReceipt: 1, receiptCount: 2, salesName: '李强' },
  { customerName: '杭州云仓科技有限公司', customerPhone: '0571-88889999', destProvince: '浙江省', destCity: '杭州市', destDistrict: '余杭区', senderName: '钱峰', senderPhone: '13100131005', senderAddress: '济南市市中区纬二路 18 号', receiverName: '孙悦', receiverPhone: '13000130005', receiverAddress: '余杭区文一西路 969 号', goodsName: '百货', goodsType: '普货', packageType: '编织袋', quantity: 210, weight: 1500, volume: 12.5, payMethod: 5, prepaidFee: 1500, collectFee: 1000, monthlyFee: 1230, returnFee: 0, freightFee: 3200, deliveryFee: 200, pickupFee: 100, otherFee: 80, receiptFee: 40, makeBillFee: 10, insuranceFee: 100, deliveryMethod: 1, salesName: '王敏' },
  { customerName: '郑州中原商贸有限公司', customerPhone: '0371-66667777', destProvince: '河南省', destCity: '郑州市', destDistrict: '金水区', senderName: '周涛', senderPhone: '13900139006', senderAddress: '济南市天桥区无影山路 6 号', receiverName: '李娜', receiverPhone: '13800138006', receiverAddress: '金水区花园路 26 号', goodsName: '食品', goodsType: '食品', packageType: '纸箱', quantity: 90, weight: 600, volume: 4.8, goodsRemark: '注意保质期', payMethod: 1, freightFee: 1500, deliveryFee: 100, receiptFee: 20, makeBillFee: 10, insuranceFee: 40, deliveryMethod: 1, salesName: '赵磊' },
  { customerName: '成都蜀通物流有限公司', customerPhone: '028-85556666', destProvince: '四川省', destCity: '成都市', destDistrict: '武侯区', senderName: '吴强', senderPhone: '13700137007', senderAddress: '济南市历下区解放路 22 号', receiverName: '何静', receiverPhone: '13600136007', receiverAddress: '武侯区人民南路四段 1 号', goodsName: '家具', goodsType: '大件', packageType: '木架', quantity: 15, weight: 950, volume: 8.6, payMethod: 2, freightFee: 2600, deliveryFee: 180, pickupFee: 120, otherFee: 100, receiptFee: 20, makeBillFee: 10, insuranceFee: 80, deliveryMethod: 3, salesName: '李强', bizType: 1 },
  { customerName: '武汉长江货运有限公司', customerPhone: '027-87778888', destProvince: '湖北省', destCity: '武汉市', destDistrict: '江汉区', senderName: '郑凯', senderPhone: '13500135008', senderAddress: '济南市槐荫区张庄路 33 号', receiverName: '罗敏', receiverPhone: '13400134008', receiverAddress: '江汉区解放大道 688 号', goodsName: '钢材', goodsType: '重货', packageType: '捆扎', quantity: 5, weight: 5000, volume: 6.0, payMethod: 3, freightFee: 4500, pickupFee: 200, otherFee: 150, receiptFee: 20, makeBillFee: 10, insuranceFee: 150, deliveryMethod: 2, salesName: '王敏', bizType: 1 },
  { customerName: '西安丝路供应链有限公司', customerPhone: '029-86669999', destProvince: '陕西省', destCity: '西安市', destDistrict: '雁塔区', senderName: '马超', senderPhone: '13300133009', senderAddress: '济南市历城区洪家楼 7 号', receiverName: '白露', receiverPhone: '13200132009', receiverAddress: '雁塔区科技路 50 号', goodsName: '服装', goodsType: '普货', packageType: '纸箱', quantity: 48, weight: 380, volume: 2.8, payMethod: 5, prepaidFee: 600, collectFee: 0, monthlyFee: 640, returnFee: 0, freightFee: 1100, deliveryFee: 80, receiptFee: 20, makeBillFee: 10, insuranceFee: 30, deliveryMethod: 1, salesName: '赵磊' },
  { customerName: '广州华远物流有限公司', customerPhone: '020-88886666', destProvince: '广东省', destCity: '广州市', destDistrict: '番禺区', senderName: '王海', senderPhone: '13800138001', senderAddress: '济南市历下区经十路 88 号', receiverName: '陈晨', receiverPhone: '13900139001', receiverAddress: '番禺区市桥街 18 号', goodsName: '家电', goodsType: '大件', packageType: '纸箱', quantity: 24, weight: 760, volume: 5.2, payMethod: 4, freightFee: 1800, deliveryFee: 120, receiptFee: 20, makeBillFee: 10, insuranceFee: 50, deliveryMethod: 1, needReceipt: 1, receiptCount: 1, salesName: '李强' },
  { customerName: '济南泉城商贸有限公司', customerPhone: '0531-88886666', destProvince: '山东省', destCity: '济南市', destDistrict: '历下区', senderName: '张伟', senderPhone: '13700137011', senderAddress: '济南市历下区泉城路 1 号', receiverName: '刘敏', receiverPhone: '13600136011', receiverAddress: '历下区经十路 100 号', goodsName: '文件', goodsType: '文件', packageType: '信封', quantity: 3, weight: 12, volume: 0.1, payMethod: 1, freightFee: 60, deliveryFee: 20, receiptFee: 20, makeBillFee: 10, deliveryMethod: 2, salesName: '王敏', status: 10 },
  { customerName: '深圳市顺达供应链有限公司', customerPhone: '0755-86668888', destProvince: '广东省', destCity: '广州市', destDistrict: '白云区', senderName: '刘洋', senderPhone: '13700137002', senderAddress: '济南市槐荫区经一路 36 号', receiverName: '周敏', receiverPhone: '13600136002', receiverAddress: '白云区机场路 20 号', goodsName: '日用品', goodsType: '普货', packageType: '纸箱', quantity: 150, weight: 980, volume: 7.2, payMethod: 2, freightFee: 2300, deliveryFee: 150, receiptFee: 20, makeBillFee: 10, insuranceFee: 60, deliveryMethod: 1, salesName: '李强', status: 90 }
];

function buildRows() {
  const rows = [];
  let seq = 101;
  const dates = ['2026-11-23 09:12:33', '2026-11-23 09:26:18', '2026-11-23 09:41:05', '2026-11-23 10:02:44', '2026-11-23 10:18:27', '2026-11-23 10:35:51', '2026-11-22 14:08:16', '2026-11-22 15:22:40', '2026-11-22 16:05:09', '2026-11-22 16:48:32', '2026-11-22 17:12:55', '2026-11-22 17:40:21'];
  TEMPLATES.forEach((t, i) => {
    const row = normalizeRow({ ...BASE_ROW, ...t });
    row.id = i + 1;
    row.waybillNo = 'YD20261123' + pad6(seq++);
    row.relatedNo = i % 3 === 0 ? 'PO-20261123-' + pad6(i + 1) : i % 3 === 1 ? 'KH-' + (8800 + i) : '';
    row.destName = row.destProvince + row.destCity + row.destDistrict;
    row.originHubName = '济南程驰';
    row.destHubName = (DESTINATIONS.find((d) => d.district === row.destDistrict) || {}).hub || '—';
    row.routeName = ROUTE_OF[row.destDistrict] || `济南程驰 → 济南分拨中心 → ${row.destDistrict}（直）`;
    row.createByName = ['张三', '李四', '王五'][i % 3];
    row.createTime = dates[i];
    rows.push(row);
  });
  // 第二批：换单号、日期前移一天，便于演示分页
  TEMPLATES.forEach((t, i) => {
    const row = normalizeRow({ ...BASE_ROW, ...t });
    row.id = 100 + i;
    row.waybillNo = 'YD20261122' + pad6(seq++);
    row.relatedNo = i % 2 === 0 ? 'PO-20261122-' + pad6(i + 1) : '';
    row.destName = row.destProvince + row.destCity + row.destDistrict;
    row.originHubName = '济南程驰';
    row.destHubName = (DESTINATIONS.find((d) => d.district === row.destDistrict) || {}).hub || '—';
    row.routeName = ROUTE_OF[row.destDistrict] || `济南程驰 → 济南分拨中心 → ${row.destDistrict}（直）`;
    row.createByName = ['张三', '李四', '王五'][i % 3];
    row.createTime = '2026-11-22 ' + String(8 + i).padStart(2, '0') + ':15:20';
    row.status = 20;
    rows.push(row);
  });
  return rows;
}
const ROWS = buildRows();

/* v1.9 子母单演示：一张母单拆两张子单，母单计费、子单只装卸与节点 */
function pushSplitDemo(rows) {
  const base = TEMPLATES[0];
  const mk = (id, no, parentNo, seq, count, weight, volume, status, vehicle, route, parentId = null) => {
    const child = !!parentNo;
    const r = normalizeRow({
      ...BASE_ROW, ...base, quantity: count, weight, volume, status,
      freightFee: child ? 0 : 1200, deliveryFee: child ? 0 : 80,
      receiptFee: child ? 0 : 20, makeBillFee: child ? 0 : 10,
      insuranceFee: child ? 0 : 30, cashBack: 0, oweBack: 0,
      codAmount: child ? 0 : base.codAmount,
      declaredValue: child ? 0 : base.declaredValue,
      payeeName: child ? '' : base.payeeName,
      bankName: child ? '' : base.bankName,
      bankCardNo: child ? '' : base.bankCardNo
    });
    r.id = id; r.waybillNo = no;
    r.parentId = child ? parentId : null; r.parentNo = parentNo || '';
    r.isChild = child ? 1 : 0; r.childSeq = seq;
    r.destName = r.destProvince + r.destCity + r.destDistrict;
    r.originHubName = '济南程驰';
    r.destHubName = (DESTINATIONS.find((d) => d.district === r.destDistrict) || {}).hub || '广州白云网点';
    r.routeName = route;
    r.createByName = '张三';
    r.createTime = child ? (seq === 1 ? '2026-11-23 11:05:40' : '2026-11-23 11:05:20') : '2026-11-23 11:06:10';
    r.vehicleNo = vehicle;
    return r;
  };
  const mother = mk(201, 'YD20261123000126', '', null, 10, 500, 3.6, 30, '—', '济南程驰 → 广州 → 白云区（直）');
  const c1 = mk(202, 'YD20261123000126-01', 'YD20261123000126', 1, 6, 300, 2.0, 30, '鲁A·88231', '济南程驰 → 广州 → 白云区（直）', 201);
  const c2 = mk(203, 'YD20261123000126-02', 'YD20261123000126', 2, 4, 200, 1.6, 40, '鲁A·77126', '济南程驰 → 济南分拨中心 → 广州 → 白云区（外转）', 201);
  const mother2 = mk(204, 'YD20261123000127', '', null, 7, 350, 2.4, 30, '—', '济南程驰 → 广州 → 白云区（直）');
  const c3 = mk(205, 'YD20261123000127-01', 'YD20261123000127', 1, 7, 350, 2.4, 40, '鲁A·66218', '济南程驰 → 济南分拨中心 → 广州 → 白云区（直）', 204);
  rows.unshift(c3, mother2, c2, c1, mother);
}
pushSplitDemo(ROWS);

const visibleKeys = new Set(COLUMNS.filter((c) => c.def).map((c) => c.key));
let filters = {};
let sortKey = 'createTime';
let sortOrder = 'desc';
let page = 1;
let pageSize = 20;

function effWidth(c) {
  if (!c.filter) return c.width;
  if (c.filter === 'number') return Math.max(c.width, 176);
  if (c.filter === 'date') return Math.max(c.width, 200);
  return Math.max(c.width, 136);
}
function statusTag(status) {
  const map = {
    10: ['待揽收', 'status-warning'], 20: ['已揽收', 'status-success'],
    25: ['已拆单', 'status-info'],
    30: ['已装车', 'status-info'], 40: ['运输中', 'status-info'],
    50: ['已到达', 'status-info'], 55: ['部分到达', 'status-warning'],
    60: ['已签收', 'status-success'], 70: ['异常', 'status-danger'], 90: ['已作废', 'status-danger']
  };
  const [text, cls] = map[status] || ['未知', 'status-info'];
  return `<span class="status-tag ${cls}"><i></i>${text}</span>`;
}
function maskCard(v) {
  if (!v) return '-';
  const s = String(v);
  return '**** **** **** ' + s.slice(-4);
}
function cellHtml(c, row) {
  const v = row[c.key];
  if (c.type === 'link') {
    if (c.key === 'waybillNo') {
      const hasKids = ROWS.some((r) => r.isChild && r.parentNo === row.waybillNo);
      const badge = hasKids ? '<span class="role-badge role-parent role-badge--after">母</span>' : '';
      return `<span class="link" ${hasKids ? `data-children="${row.id}"` : `data-open="${row.id}"`}>${escapeHtml(v)}</span>${badge}`;
    }
    return `<span class="link" data-open="${row.id}">${escapeHtml(v)}</span>`;
  }
  if (c.type === 'money') return `<span>${fmtMoney(v)}</span>`;
  if (c.type === 'int') return `<span>${fmtInt(v)}</span>`;
  if (c.type === 'decimal3') return `<span>${fmt3(v)}</span>`;
  if (c.type === 'enum') return escapeHtml(c.options[v] ?? '-');
  if (c.type === 'prestatus') return preStatusTag(Number(v));
  if (c.type === 'bool') return Number(v) === 1 ? '是' : '否';
  if (c.type === 'masked') return maskCard(v);
  if (v === null || v === undefined || v === '') return '<span class="muted">-</span>';
  if (c.key === 'routeName') return `${badge(/外转/.test(String(v)) ? 2 : 1)} ${escapeHtml(chainLabel(v))}`;
  return escapeHtml(String(v));
}
function filterHtml(c, state, attr = 'filter') {
  if (!c.filter) return '';
  const f = (state || {})[c.key];
  const bind = `data-${attr}="${c.key}"`;
  if (c.filter === 'text') {
    return `<input ${bind} placeholder="全部" value="${escapeHtml(f || '')}">`;
  }
  if (c.filter === 'select') {
    const opts = c.filterOptions || (c.options ? Object.entries(c.options).map(([k, v]) => ({ k, v })) : []);
    const isObj = !c.filterOptions && c.options;
    return `<select ${bind}><option value="">全部</option>${opts.map((o) => {
      const val = isObj ? o.k : o;
      const label = isObj ? o.v : o;
      return `<option value="${escapeHtml(String(val))}" ${String(f) === String(val) ? 'selected' : ''}>${escapeHtml(String(label))}</option>`;
    }).join('')}</select>`;
  }
  if (c.filter === 'number') {
    const r = f && typeof f === 'object' ? f : { min: '', max: '' };
    return `<div class="range">
      <input type="number" ${bind} data-bound="min" placeholder="最小" value="${escapeHtml(r.min || '')}">
      <input type="number" ${bind} data-bound="max" placeholder="最大" value="${escapeHtml(r.max || '')}">
    </div>`;
  }
  if (c.filter === 'date') {
    return `<input class="date-input" ${bind} placeholder="2026-11-01~2026-11-23" value="${escapeHtml(f || '')}">`;
  }
  return '';
}
function sortRowsFor(rows, key, order, columns) {
  const col = (columns || COLUMNS).find((c) => c.key === key);
  if (!col) return rows;
  const numeric = ['int', 'money', 'decimal3', 'enum', 'bool', 'prestatus'].includes(col.type);
  const dir = order === 'asc' ? 1 : -1;
  return rows.slice().sort((a, b) => {
    let av = a[key];
    let bv = b[key];
    if (numeric) {
      av = Number(av || 0); bv = Number(bv || 0);
      return (av - bv) * dir;
    }
    return String(av || '').localeCompare(String(bv || ''), 'zh-CN') * dir;
  });
}
function matchFiltersFor(row, state, visibleSet, columns) {
  for (const key of Object.keys(state || {})) {
    if (visibleSet && !visibleSet.has(key)) continue;
    const f = state[key];
    const c = (columns || COLUMNS).find((x) => x.key === key);
    if (!c) continue;
    if (c.filter === 'number') {
      const r = typeof f === 'object' ? f : {};
      const val = Number(row[key] || 0);
      if (r.min !== '' && r.min !== undefined && val < Number(r.min)) return false;
      if (r.max !== '' && r.max !== undefined && val > Number(r.max)) return false;
      continue;
    }
    if (f === '' || f === undefined || f === null) continue;
    if (c.filter === 'select') {
      if (String(row[key]) !== String(f)) return false;
      continue;
    }
    if (c.filter === 'date') {
      const day = String(row[key] || '').slice(0, 10);
      const parts = String(f).split('~').map((s) => s.trim()).filter(Boolean);
      if (parts.length === 1 && day !== parts[0]) return false;
      if (parts.length === 2 && (day < parts[0] || day > parts[1])) return false;
      continue;
    }
    if (!String(row[key] || '').toLowerCase().includes(String(f).trim().toLowerCase())) return false;
  }
  return true;
}
function filteredRows() {
  return sortRowsFor(
    ROWS.filter((r) => !r.isChild).filter((r) => matchFiltersFor(r, filters, visibleKeys, COLUMNS)),
    sortKey, sortOrder, COLUMNS
  );
}
function visibleColumns() {
  return COLUMNS.filter((c) => visibleKeys.has(c.key));
}
function renderHeader() {
  const cols = visibleColumns();
  const totalWidth = 60 + cols.reduce((s, c) => s + effWidth(c), 0) + 130;
  const colgroup = `<colgroup><col style="width:60px">${cols.map((c) => `<col style="width:${effWidth(c)}px">`).join('')}<col style="width:130px"></colgroup>`;
  const head = `<thead><tr>
    <th class="struct-col"><div class="th-inner"><div class="th-label">序号</div></div></th>
    ${cols.map((c) => {
      const sorted = sortKey === c.key;
      const caret = sorted ? (sortOrder === 'asc' ? '↑' : '↓') : '↕';
      const label = c.sortable === false
        ? `<div class="th-label">${c.label}</div>`
        : `<div class="th-label ${sorted ? 'sorted' : ''}" data-sort="${c.key}">${c.label}<span class="caret">${caret}</span></div>`;
      return `<th><div class="th-inner">
        ${label}
        <div class="th-filter">${filterHtml(c, filters)}</div>
      </div></th>`;
    }).join('')}
    <th class="struct-col"><div class="th-inner"><div class="th-label">操作</div></div></th>
  </tr></thead>`;
  $('#waybillTable').style.width = totalWidth + 'px';
  $('#waybillTable').innerHTML = colgroup + head + '<tbody id="waybillBody"></tbody><tfoot id="waybillFoot"></tfoot>';
  $$('.th-label[data-sort]').forEach((el) => {
    el.addEventListener('click', () => {
      const key = el.dataset.sort;
      if (sortKey === key) sortOrder = sortOrder === 'asc' ? 'desc' : 'asc';
      else { sortKey = key; sortOrder = 'asc'; }
      renderHeader();
      renderBody();
    });
  });
  $$('[data-filter]').forEach((el) => {
    const handler = () => {
      const key = el.dataset.filter;
      if (el.dataset.bound) {
        const cur = filters[key] && typeof filters[key] === 'object' ? filters[key] : { min: '', max: '' };
        filters[key] = { ...cur, [el.dataset.bound]: el.value };
      } else {
        filters[key] = el.value;
      }
      page = 1;
      renderBody();
    };
    el.addEventListener('input', handler);
    el.addEventListener('change', handler);
  });
}
function renderBody() {
  const rows = filteredRows();
  const total = rows.length;
  const pages = Math.max(1, Math.ceil(total / pageSize));
  if (page > pages) page = pages;
  const pageRows = rows.slice((page - 1) * pageSize, page * pageSize);
  const cols = visibleColumns();
  const tbody = $('#waybillBody');
  tbody.innerHTML = pageRows.map((row, idx) => `
    <tr>
      <td class="center">${(page - 1) * pageSize + idx + 1}</td>
      ${cols.map((c) => {
        const cls = c.type === 'money' ? 'money' : ['int', 'decimal3'].includes(c.type) ? 'num' : '';
        const titleVal = c.type === 'masked' ? maskCard(row[c.key]) : c.key === 'routeName' ? chainLabel(row[c.key]) : String(row[c.key] ?? '');
        return `<td class="${cls}" title="${escapeHtml(titleVal)}">${cellHtml(c, row)}</td>`;
      }).join('')}
      <td class="center"><div class="row-actions">
        <button data-open="${row.id}">详情</button>
        ${row.status !== 90 ? `<button class="danger" data-void="${row.id}">作废</button>` : ''}
      </div></td>
    </tr>`).join('') || `<tr><td colspan="${cols.length + 2}" style="text-align:center;color:#98a2b3;height:120px">暂无数据</td></tr>`;

  const foot = $('#waybillFoot');
  const sumCells = cols.map((c, i) => {
    if (i === 0) return '';
    if (!c.sum) {
      const cls = c.type === 'money' ? 'money' : ['int', 'decimal3'].includes(c.type) ? 'num' : '';
      return `<td class="${cls}">--</td>`;
    }
    const val = rows.reduce((s, r) => s + Number(r[c.key] || 0), 0);
    const text = c.type === 'money' ? fmtMoney(val) : c.type === 'decimal3' ? fmt3(val) : fmtInt(val);
    return `<td class="${c.type === 'money' ? 'money' : 'num'}">${text}</td>`;
  }).join('');
  foot.innerHTML = `<tr>
    <td colspan="2">合计（共 ${total} 条）</td>
    ${sumCells}
    <td class="center">--</td>
  </tr>`;
  renderPager(total, pages);
}
function renderPager(total, pages) {
  const pager = $('#pager');
  const btns = [];
  const maxShow = 5;
  let start = Math.max(1, page - 2);
  let end = Math.min(pages, start + maxShow - 1);
  start = Math.max(1, end - maxShow + 1);
  for (let i = start; i <= end; i++) {
    btns.push(`<button class="pager-btn ${i === page ? 'active' : ''}" data-page="${i}">${i}</button>`);
  }
  pager.innerHTML = `
    <span>共 ${total} 条</span>
    <select id="pageSizeSel">
      <option value="20" ${pageSize === 20 ? 'selected' : ''}>20 条/页</option>
      <option value="50" ${pageSize === 50 ? 'selected' : ''}>50 条/页</option>
      <option value="100" ${pageSize === 100 ? 'selected' : ''}>100 条/页</option>
    </select>
    <button class="pager-btn" data-page="${Math.max(1, page - 1)}" ${page === 1 ? 'disabled' : ''}>‹</button>
    ${btns.join('')}
    <button class="pager-btn" data-page="${Math.min(pages, page + 1)}" ${page === pages ? 'disabled' : ''}>›</button>`;
  $$('#pager [data-page]').forEach((b) => b.addEventListener('click', () => {
    if (b.disabled) return;
    page = Number(b.dataset.page);
    renderBody();
  }));
  $('#pageSizeSel').addEventListener('change', (e) => {
    pageSize = Number(e.target.value);
    page = 1;
    renderBody();
  });
}
function renderTable() {
  renderHeader();
  renderBody();
}
/* 列表只显示母单：点「子单 n」直接用详情抽屉展示该母单的子单清单 */
function openChildren(id) {
  const row = ROWS.find((r) => r.id === Number(id));
  if (!row) return;
  const kids = ROWS
    .filter((r) => r.isChild && r.parentNo === row.waybillNo)
    .sort((a, b) => Number(a.childSeq || 0) - Number(b.childSeq || 0));
  if (!kids.length) return;
  const toastEl = $('#toast');
  if (toastEl) toastEl.classList.add('hidden');
  const sum = {
    count: kids.reduce((s, r) => s + Number(r.quantity || 0), 0),
    weight: round2(kids.reduce((s, r) => s + Number(r.weight || 0), 0)),
    volume: round2(kids.reduce((s, r) => s + Number(r.volume || 0), 0))
  };
  const same = Number(row.quantity) === sum.count
    && Math.abs(Number(row.weight) - sum.weight) < 0.001
    && Math.abs(Number(row.volume) - sum.volume) < 0.001;
  $('#drawerNo').textContent = row.waybillNo;
  $('#drawerVoid').classList.add('hidden');
  $('#drawerBody').innerHTML = `
    <div class="detail-card">
      <div class="detail-head">
        <span class="role-badge role-parent">母</span>
        <span class="no">${escapeHtml(row.waybillNo)}</span>
        ${statusTag(row.status)}
        <span class="muted" style="font-size:12px">子单清单（${kids.length} 张 · 费用与结算挂母单）</span>
        <span class="link" data-open="${row.id}" style="margin-left:auto">查看母单详情</span>
      </div>
      <div class="mini-table-wrap">
        <table class="mini-table">
          <thead><tr><th>子单号</th><th class="num">件数</th><th class="num">重量(kg)</th><th class="num">体积(m³)</th><th>始发网点</th><th>运达网点</th><th>状态</th></tr></thead>
          <tbody>${kids.map((k) => `<tr>
            <td><span class="link" data-open="${k.id}">${escapeHtml(k.waybillNo)}</span></td>
            <td class="num">${fmtInt(k.quantity)}</td>
            <td class="num">${fmt3(k.weight)}</td>
            <td class="num">${fmt3(k.volume)}</td>
            <td>${escapeHtml(k.originHubName || '-')}</td>
            <td>${escapeHtml(k.destHubName || '-')}</td>
            <td>${statusTag(k.status)}</td>
          </tr>`).join('')}</tbody>
        </table>
      </div>
      <div class="split-sum">子单合计：<b>${fmtInt(sum.count)}</b> 件 · <b>${fmt3(sum.weight)}</b> kg · <b>${fmt3(sum.volume)}</b> m³ ｜ 母单：<b>${fmtInt(row.quantity)}</b> 件 · <b>${fmt3(row.weight)}</b> kg · <b>${fmt3(row.volume)}</b> m³<span class="${same ? 'split-ok' : 'split-bad'}">${same ? '一致' : '不一致'}</span></div>
    </div>`;
  $('#drawerMask').classList.remove('hidden');
}
function openRow(id) {
  const row = ROWS.find((r) => r.id === Number(id));
  if (!row) return;
  const toastEl = $('#toast');
  if (toastEl) toastEl.classList.add('hidden');
  const nodes = row.routeName.split('→').map((s) => chainLabel(s.trim()));
  const routeNodes = nodes.map((n, i) => {
    const isEnd = i === nodes.length - 1;
    const typeText = isEnd ? (row.routeName.includes('外转') ? '外转' : '直达') : i === 0 ? '短驳' : '干线';
    return `<div class="tl-node ${isEnd ? 'end' : ''}">
      <div class="tl-title">${escapeHtml(n)} ${isEnd ? badge(row.routeName.includes('外转') ? 2 : 1) : ''}</div>
      <div class="tl-meta">${isEnd ? '' : `<span>${typeText}</span>`}<span>${isEnd ? '' : '库区：A-03'}</span><span>时效：${(3 + i * 2).toFixed(1)} 小时</span><span>里程：${(80 + i * 320).toLocaleString('zh-CN')} 公里</span></div>
    </div>`;
  }).join('');
  const item = (label, val, cls = '') => `<div class="detail-item ${cls}"><label>${label}</label><div>${val === '' || val === null || val === undefined ? '<span class="muted">-</span>' : escapeHtml(String(val))}</div></div>`;
  const kids = ROWS.filter((r) => r.parentNo === row.waybillNo)
    .sort((a, b) => Number(a.childSeq || 0) - Number(b.childSeq || 0));
  const kidSum = {
    count: kids.reduce((s, r) => s + Number(r.quantity || 0), 0),
    weight: round2(kids.reduce((s, r) => s + Number(r.weight || 0), 0)),
    volume: round2(kids.reduce((s, r) => s + Number(r.volume || 0), 0))
  };
  const sameSplit = Number(row.quantity) === kidSum.count
    && Math.abs(Number(row.weight) - kidSum.weight) < 0.001
    && Math.abs(Number(row.volume) - kidSum.volume) < 0.001;
  const motherOf = row.isChild ? ROWS.find((r) => r.waybillNo === row.parentNo) : null;
  const splitCard = row.isChild
    ? `<div class="detail-card">
        <h4>子母单</h4>
        <div class="detail-grid">
          <div class="detail-item"><label>母单号</label><div>${motherOf ? `<span class="link" data-open="${motherOf.id}">${escapeHtml(row.parentNo)}</span>` : escapeHtml(row.parentNo)}</div></div>
          ${item('子单序号', String(row.childSeq || 0).padStart(2, '0'))}
          ${item('计费归属', '挂母单（客户结算）')}
          ${item('本单职责', '装卸车与节点流转')}
        </div>
      </div>`
    : (kids.length
      ? `<div class="detail-card">
          <h4>子单清单 <span class="muted" style="font-weight:400;font-size:12px">（${kids.length} 张 · 费用与结算挂母单）</span></h4>
          <div class="mini-table-wrap">
            <table class="mini-table">
              <thead><tr><th>子单号</th><th class="num">件数</th><th class="num">重量(kg)</th><th class="num">体积(m³)</th><th>车辆</th><th>状态</th></tr></thead>
              <tbody>${kids.map((k) => `<tr>
                <td><span class="link" data-open="${k.id}">${k.waybillNo}</span></td>
                <td class="num">${fmtInt(k.quantity)}</td>
                <td class="num">${fmt3(k.weight)}</td>
                <td class="num">${fmt3(k.volume)}</td>
                <td>${escapeHtml(k.vehicleNo || '-')}</td>
                <td>${statusTag(k.status)}</td>
              </tr>`).join('')}</tbody>
            </table>
          </div>
          <div class="split-sum">母单合计：<b>${fmtInt(row.quantity)}</b> 件 · <b>${fmt3(row.weight)}</b> kg · <b>${fmt3(row.volume)}</b> m³ ｜ 子单合计：<b>${fmtInt(kidSum.count)}</b> 件 · <b>${fmt3(kidSum.weight)}</b> kg · <b>${fmt3(kidSum.volume)}</b> m³<span class="${sameSplit ? 'split-ok' : 'split-bad'}">${sameSplit ? '一致' : '不一致'}</span></div>
        </div>`
      : '');
  $('#drawerNo').textContent = row.waybillNo;
  $('#drawerBody').innerHTML = `
    <div class="detail-card">
      <div class="detail-head">
        ${row.isChild ? '<span class="role-badge role-child">子单</span>' : '<span class="role-badge role-parent">母单</span>'}
        <span class="no">${row.waybillNo}</span>
        ${statusTag(row.status)}
        <span class="badge badge-direct">${BIZ_TYPE[row.bizType]}</span>
        <span class="muted" style="font-size:12px">开单网点：${row.deptName} ｜ 开单员：${row.createByName} ｜ 开单时间：${row.createTime}</span>
      </div>
      <div class="detail-grid">
        ${item('关联单号', row.relatedNo)}
        ${item('目的地', row.destName)}
        ${item('线路', chainLabel(row.routeName))}
        ${item('始发网点', row.originHubName)}
        ${item('运达网点', row.destHubName)}
        ${item('业务员', row.salesName)}
      </div>
    </div>
    ${splitCard}
    <div class="detail-card">
      <h4>发货方</h4>
      <div class="detail-grid">
        ${item('客户名称', row.customerName)}
        ${item('客户电话', row.customerPhone)}
        ${item('发货人', row.senderName)}
        ${item('发货电话', row.senderPhone)}
        <div class="detail-item span-2"><label>发货地址</label><div>${escapeHtml(row.senderAddress)}</div></div>
      </div>
    </div>
    <div class="detail-card">
      <h4>收货方</h4>
      <div class="detail-grid">
        ${item('收货人', row.receiverName)}
        ${item('收货电话', row.receiverPhone)}
        <div class="detail-item span-2"><label>收货地址</label><div>${escapeHtml(row.receiverAddress)}</div></div>
      </div>
    </div>
    <div class="detail-card">
      <h4>货物信息</h4>
      <div class="detail-grid">
        ${item('货物名称', row.goodsName)}
        ${item('货物类型', row.goodsType)}
        ${item('包装类型', row.packageType)}
        ${item('件数', fmtInt(row.quantity))}
        ${item('体积（m³）', fmt3(row.volume))}
        ${item('重量（kg）', fmt3(row.weight))}
        ${item('货物备注', row.goodsRemark)}
        ${item('送货方式', DELIVERY_METHOD[row.deliveryMethod])}
      </div>
    </div>
    <div class="detail-card">
      <h4>路由链路</h4>
      <div class="timeline">${routeNodes}</div>
    </div>
    ${row.isChild ? `<div class="detail-card">
      <h4>费用与结算</h4>
      <div class="split-sum">子单不重复计费：运费、返款、代收与支付均挂在母单 ${row.parentNo}，本单仅承担装卸车与节点流转。</div>
    </div>` : `<div class="detail-card">
      <h4>费用与支付</h4>
      <table class="fee-table">
        <thead><tr><th>费用项</th><th class="num">金额（元）</th><th>费用项</th><th class="num">金额（元）</th></tr></thead>
        <tbody>
          <tr><td>运费</td><td class="num">${fmtMoney(row.freightFee)}</td><td>回单费</td><td class="num">${fmtMoney(row.receiptFee)}</td></tr>
          <tr><td>送货费</td><td class="num">${fmtMoney(row.deliveryFee)}</td><td>制单费</td><td class="num">${fmtMoney(row.makeBillFee)}</td></tr>
          <tr><td>提货费</td><td class="num">${fmtMoney(row.pickupFee)}</td><td>保费</td><td class="num">${fmtMoney(row.insuranceFee)}</td></tr>
          <tr><td>其他费</td><td class="num">${fmtMoney(row.otherFee)}</td><td>声明价值</td><td class="num">${fmtMoney(row.declaredValue)}</td></tr>
          <tr><td>现返 / 欠返</td><td class="num">${fmtMoney(row.cashBack)} / ${fmtMoney(row.oweBack)}</td><td></td><td></td></tr>
          <tr class="total"><td>运费合计</td><td class="num">${fmtMoney(row.totalFee)}</td><td>支付方式</td><td class="num">${PAY_METHOD[row.payMethod]}</td></tr>
          <tr><td>返款合计</td><td class="num">${fmtMoney(row.rebateTotal)}</td><td>有效运费</td><td class="num" style="${row.validFreight < 0 ? 'color:#d92d20;font-weight:600' : 'font-weight:600'}">${fmtMoney(row.validFreight)}${row.validFreight < 0 ? '（负值待确认）' : ''}</td></tr>
          <tr><td>现付运费</td><td class="num">${fmtMoney(row.prepaidFee)}</td><td>到付运费</td><td class="num">${fmtMoney(row.collectFee)}</td></tr>
          <tr><td>月结运费</td><td class="num">${fmtMoney(row.monthlyFee)}</td><td>回付运费</td><td class="num">${fmtMoney(row.returnFee)}</td></tr>
          <tr><td>代收货款</td><td class="num">${fmtMoney(row.codAmount)}</td><td>收款人</td><td class="num">${escapeHtml(row.payeeName || '-')}</td></tr>
        </tbody>
      </table>
    </div>`}
    <div class="detail-card">
      <h4>其他</h4>
      <div class="detail-grid">
        ${item('是否回单', row.needReceipt ? '是' : '否')}
        ${item('回单数量', fmtInt(row.receiptCount))}
        ${item('是否等通知', row.waitNotice ? '是' : '否')}
        ${item('银行卡号', maskCard(row.bankCardNo))}
        ${item('银行名称', row.bankName)}
        ${item('备注', row.remark)}
      </div>
    </div>`;
  $('#drawerMask').classList.remove('hidden');
  $('#drawerVoid').classList.remove('hidden');
}
function closeDrawer() { $('#drawerMask').classList.add('hidden'); }
/* 导出：只导出当前筛选后的母单，随可见列；不包含子单行 */
function exportCell(c, row) {
  const v = row[c.key];
  if (c.type === 'enum') return c.options ? (c.options[v] ?? '') : String(v ?? '');
  if (c.type === 'bool') return Number(v) === 1 ? '是' : '否';
  if (c.type === 'masked') return maskCard(v);
  if (c.key === 'routeName') return chainLabel(v);
  if (c.type === 'money') return Number(v || 0).toFixed(2);
  if (c.type === 'decimal3') return Number(v || 0).toFixed(3);
  if (c.type === 'int') return String(Number(v || 0));
  return String(v ?? '');
}
function exportCsv() {
  const rows = filteredRows();
  const cols = visibleColumns();
  const lines = [
    ['序号', ...cols.map((c) => c.label)],
    ...rows.map((row, i) => [i + 1, ...cols.map((c) => exportCell(c, row))])
  ];
  const csv = '\ufeff' + lines.map((line) => line.map((v) => `"${String(v).replace(/"/g, '""')}"`).join(',')).join('\r\n');
  window.__lastExport = { rows: rows.length, dataLines: lines.length - 1, csv };
  try {
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `运单列表_${new Date().toISOString().slice(0, 10)}.csv`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(a.href), 1000);
  } catch (err) { /* 原型环境忽略下载异常 */ }
  toast(`已导出 ${rows.length} 条母单（不含子单）`, 'success');
}
function voidRow(id) {
  const row = ROWS.find((r) => r.id === Number(id));
  if (!row) return;
  if (row.status === 90) { toast('该运单已作废', 'error'); return; }
  if (!confirm(`确认作废运单 ${row.waybillNo}？作废后不可恢复。`)) return;
  row.status = 90;
  closeDrawer();
  renderBody();
  toast('运单已作废：' + row.waybillNo, 'success');
}

/* ---------- 列设置 ---------- */
function renderColumnPanel() {
  const groups = [];
  COLUMNS.forEach((c) => {
    let g = groups.find((x) => x.name === c.group);
    if (!g) { g = { name: c.group, cols: [] }; groups.push(g); }
    g.cols.push(c);
  });
  $('#columnList').innerHTML = groups.map((g) => `
    <div class="col-group-title">${g.name}</div>
    ${g.cols.map((c) => `<label class="col-check"><input type="checkbox" data-col="${c.key}" ${visibleKeys.has(c.key) ? 'checked' : ''}>${c.label}</label>`).join('')}
  `).join('');
  $('#columnCount').textContent = `已显示 ${visibleKeys.size} / ${COLUMNS.length}`;
  $$('#columnList [data-col]').forEach((cb) => {
    cb.addEventListener('change', () => {
      if (cb.checked) visibleKeys.add(cb.dataset.col);
      else visibleKeys.delete(cb.dataset.col);
      $('#columnCount').textContent = `已显示 ${visibleKeys.size} / ${COLUMNS.length}`;
      renderTable();
    });
  });
}
function resetColumns() {
  visibleKeys.clear();
  COLUMNS.filter((c) => c.def).forEach((c) => visibleKeys.add(c.key));
  renderColumnPanel();
  renderTable();
  toast(`已恢复默认列（${visibleKeys.size} 列）`, 'success');
}

/* ---------- 事件绑定 ---------- */
function togglePanel(panel, btn) {
  const r = btn.getBoundingClientRect();
  panel.style.top = (r.bottom + 6) + 'px';
  panel.style.right = Math.max(12, window.innerWidth - r.right) + 'px';
  panel.style.maxHeight = Math.max(260, window.innerHeight - r.bottom - 24) + 'px';
  panel.classList.toggle('hidden');
}
function bindEvents() {
  $('#saveBtn').addEventListener('click', () => doSave(false));
  $('#saveNextBtn').addEventListener('click', () => doSave(true));
  $('#resetBtn').addEventListener('click', () => {
    if (preMode && selectedPreOrder) {
      if (!confirm('将恢复为该预开单的申报信息，已补录的内容会被清空，是否继续？')) return;
      fillPreOrderForm(selectedPreOrder);
      toast('已恢复为预开单申报信息');
      return;
    }
    resetForm();
    toast('已重置');
  });
  $('#clearSource').addEventListener('click', clearSource);
  $('#returnBtn').addEventListener('click', returnPreOrder);

  $('#resetFilterBtn').addEventListener('click', () => {
    filters = {}; sortKey = 'createTime'; sortOrder = 'desc'; page = 1;
    renderTable();
    toast('筛选已清空');
  });
  $('#refreshBtn').addEventListener('click', () => { renderTable(); toast('已刷新'); });
  $('#exportBtn').addEventListener('click', exportCsv);
  $('#columnBtn').addEventListener('click', (e) => {
    e.stopPropagation();
    togglePanel($('#columnPanel'), e.currentTarget);
  });
  $('#columnResetBtn').addEventListener('click', resetColumns);

  /* 预开单列表工具条 */
  $('#preResetFilter').addEventListener('click', () => {
    preFilters = {}; preSortKey = 'submitted'; preSortOrder = 'desc'; prePage = 1;
    renderPreTable();
    toast('预开单筛选已清空');
  });
  $('#preExport').addEventListener('click', () => toast('原型演示：按当前可见列 + 筛选条件导出预开单 xlsx'));
  $('#preColumnBtn').addEventListener('click', (e) => {
    e.stopPropagation();
    togglePanel($('#preColumnPanel'), e.currentTarget);
  });
  $('#preColumnResetBtn').addEventListener('click', resetPreColumns);

  window.addEventListener('resize', () => {
    $('#columnPanel').classList.add('hidden');
    $('#preColumnPanel').classList.add('hidden');
  });
  document.addEventListener('click', (e) => {
    if (!e.target.closest('.column-setting')) {
      $('#columnPanel').classList.add('hidden');
      $('#preColumnPanel').classList.add('hidden');
    }
  });

  $('#preTable').addEventListener('click', (e) => {
    const accept = e.target.closest('[data-pre-accept]');
    const cont = e.target.closest('[data-pre-continue]');
    const back = e.target.closest('[data-pre-return]');
    const view = e.target.closest('[data-pre-view]');
    const taken = e.target.closest('[data-pre-taken]');
    if (accept) handlePreAccept(accept.dataset.preAccept);
    if (cont) handlePreAccept(cont.dataset.preContinue);
    if (back) returnPreByNo(back.dataset.preReturn);
    if (taken) {
      const p = preOrders.find((x) => x.no === taken.dataset.preTaken);
      if (p) toast(`该预开单已被 ${p.acceptBy} 接单，重复接单请求将被拒绝（409）`, 'error');
    }
    if (view) {
      const p = preOrders.find((x) => x.no === view.dataset.preView);
      if (p) toast(`预开单 ${p.no} 已生成运单 ${p.waybillNo}，请在运单管理查看`);
    }
  });

  $('#waybillTable').addEventListener('click', (e) => {
    const open = e.target.closest('[data-open]');
    const kids = e.target.closest('[data-children]');
    const voidBtn = e.target.closest('[data-void]');
    if (kids) openChildren(kids.dataset.children);
    else if (open) openRow(open.dataset.open);
    if (voidBtn) voidRow(voidBtn.dataset.void);
  });
  $('#drawerClose').addEventListener('click', closeDrawer);
  $('#drawerClose2').addEventListener('click', closeDrawer);
  $('#drawerBody').addEventListener('click', (e) => {
    const open = e.target.closest('[data-open]');
    if (open) openRow(open.dataset.open);
  });
  $('#drawerMask').addEventListener('click', (e) => { if (e.target.id === 'drawerMask') closeDrawer(); });
  $('#drawerVoid').addEventListener('click', () => {
    const no = $('#drawerNo').textContent;
    const row = ROWS.find((r) => r.waybillNo === no);
    if (row) voidRow(row.id);
  });
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') { closeDrawer(); }
  });

  document.addEventListener('input', (e) => {
    if ((e.target.classList.contains('req') || e.target.classList.contains('is-empty')) && String(e.target.value).trim()) {
      e.target.classList.remove('is-empty');
    }
    refreshRequiredStates();
  });
  document.addEventListener('change', (e) => {
    if ((e.target.classList.contains('req') || e.target.classList.contains('is-empty')) && String(e.target.value).trim()) {
      e.target.classList.remove('is-empty');
    }
    if (e.target.name === 'deliveryMethod') {
      const block = $('#deliveryBlock');
      if (block) block.classList.remove('has-error');
    }
    refreshRequiredStates();
  });
}

/* ---------- 初始化 ---------- */
function init() {
  initDest();
  initReceiverRegion();
  initGoods();
  initFeesAndPay();
  initCustomerMatch();
  bindEvents();
  renderRoutes();
  $('#contextTime').textContent = nowText();
  $('#bizType').value = '2';
  setFormMode('self');
  $('#preOrderCard').classList.add('hidden');
  $('#preEmpty').classList.add('hidden');
  $('#formArea').classList.remove('hidden');
  renderColumnPanel();
  renderPreColumnPanel();
  renderTable();
  renderPreTable();
  updatePreCount();
  showPage('create');
  const pageParam = new URLSearchParams(location.search).get('page') || location.hash.replace('#', '');
  if (pageParam === 'waybill') showPage('waybill');
  refreshRequiredStates();
}
init();
