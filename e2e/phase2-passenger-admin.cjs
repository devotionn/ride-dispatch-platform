const fs = require('node:fs/promises')
const path = require('node:path')
const assert = require('node:assert/strict')
const { chromium } = require('playwright')

const passengerUrl = process.env.PASSENGER_URL ?? 'http://localhost:5173'
const adminUrl = process.env.ADMIN_URL ?? 'http://localhost:5174'
const artifactDir = path.resolve(__dirname, 'artifacts')
const headless = process.env.HEADLESS !== 'false'
const browserChannel = process.env.BROWSER_CHANNEL ?? 'chrome'

let browser
let passengerPage
let adminPage
let lastOrderNo = null
let lastApiResponse = null
let adminAccessToken = null

async function main() {
  await fs.rm(artifactDir, { recursive: true, force: true })
  await fs.mkdir(artifactDir, { recursive: true })
  const browserOptions = { headless }
  if (browserChannel !== 'bundled') browserOptions.channel = browserChannel
  browser = await chromium.launch(browserOptions)
  passengerPage = await browser.newPage({ viewport: { width: 390, height: 844 } })
  adminPage = await browser.newPage({ viewport: { width: 1440, height: 900 } })
  observeApiResponses(passengerPage, 'passenger')
  observeApiResponses(adminPage, 'admin')

  await refreshTestDriverLocations()
  await runTextOnlyPublicOrderGate()
  await runLocatedPublicOrderGate()
  await runDirectedOrderGate()
  console.log(`Phase 2 browser gate passed${lastOrderNo ? `; last order ${lastOrderNo}` : ''}`)
}

async function refreshTestDriverLocations() {
  await refreshTestDriverLocation('D101', 'driver123', 32.392, 119.507)
  await refreshTestDriverLocation('D102', 'driver123', 32.39, 119.509)
}

async function refreshTestDriverLocation(username, password, latitude, longitude) {
  const login = await postJson('/api/v1/auth/driver/login', { username, password })
  await postJson('/api/v1/driver/me/location', {
    latitude,
    longitude,
    accuracyMeters: 10,
    locatedAt: new Date().toISOString(),
    source: 'DRIVER_APP',
  }, login.accessToken)
}

async function postJson(apiPath, body, accessToken) {
  const response = await fetch(new URL(apiPath, passengerUrl), {
    method: 'POST',
    signal: AbortSignal.timeout(15000),
    headers: {
      'Content-Type': 'application/json',
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
    },
    body: JSON.stringify(body),
  })
  lastApiResponse = {
    app: 'setup',
    method: 'POST',
    path: apiPath,
    status: response.status,
    timestamp: new Date().toISOString(),
  }
  if (!response.ok) throw new Error(`测试司机位置刷新失败：${apiPath} ${response.status}`)
  if (response.status === 204) return null
  return response.json()
}

function observeApiResponses(page, app) {
  page.on('response', (response) => {
    const url = new URL(response.url())
    if (!url.pathname.startsWith('/api/')) return
    lastApiResponse = {
      app,
      method: response.request().method(),
      path: url.pathname,
      status: response.status(),
      timestamp: new Date().toISOString(),
    }
  })
}

async function runTextOnlyPublicOrderGate() {
  await passengerPage.goto(`${passengerUrl}/ride`, { waitUntil: 'domcontentloaded' })
  await fillManualTextPoint('上车点', '扬州东站')
  await fillManualTextPoint('目的地', '瘦西湖')
  await passengerPage.getByPlaceholder('请输入 11 位手机号').fill('13800000000')
  await passengerPage.getByRole('button', { name: '提交预约' }).click()
  await passengerPage.waitForURL('**/order/**', { timeout: 15000 })

  lastOrderNo = new URL(passengerPage.url()).pathname.split('/').pop()
  if (!lastOrderNo) throw new Error('无法从乘客订单页读取订单号')
  await passengerPage.locator('dd').filter({ hasText: lastOrderNo }).waitFor()

  await adminPage.goto(`${adminUrl}/login`, { waitUntil: 'domcontentloaded' })
  await adminPage.getByPlaceholder('请输入后台账号').fill('admin')
  await adminPage.getByPlaceholder('请输入密码').fill('admin123')
  await adminPage.getByRole('button', { name: '进入调度后台' }).click()
  await adminPage.waitForURL('**/orders', { timeout: 15000 })
  adminAccessToken = await adminPage.evaluate(() => {
    const raw = localStorage.getItem('ride-dispatch:admin-session')
    return raw ? JSON.parse(raw).accessToken : null
  })
  assert.ok(adminAccessToken, 'Admin 登录成功后未找到访问令牌')
  await adminPage.getByText(lastOrderNo).first().waitFor({ timeout: 15000 })

  await adminPage.locator('.orders-table .el-table__row').filter({ hasText: lastOrderNo }).dblclick()
  const dispatchPanel = adminPage.getByTestId('dispatch-panel')
  await dispatchPanel.waitFor({ timeout: 10000 })
  const createdDetail = await getAdminOrderDetail(lastOrderNo)
  assert.equal(createdDetail.order.sourceType, 'PUBLIC_H5', '公共入口订单来源必须为 PUBLIC_H5')
  assert.equal(createdDetail.order.status, 'PENDING_DISPATCH', '公共入口订单初始状态必须为 PENDING_DISPATCH')
  assert.equal(createdDetail.order.pickupLatitude, null, '手工上车点必须不包含纬度')
  assert.equal(createdDetail.order.pickupLongitude, null, '手工上车点必须不包含经度')
  assert.equal(createdDetail.order.destinationLatitude, null, '手工目的地必须不包含纬度')
  assert.equal(createdDetail.order.destinationLongitude, null, '手工目的地必须不包含经度')
  await dispatchPanel.getByText('无定位坐标 · 人工选择司机派单').waitFor({ timeout: 10000 })
  const nearbyRows = adminPage.getByTestId('dispatch-candidates').locator('.el-table__row')
  if (await nearbyRows.count() < 1) throw new Error('无坐标订单未找到符合资格的人工派单司机')
  const candidateText = await adminPage.getByTestId('dispatch-candidates').innerText()
  assert.ok(!/null km|NaN km|undefined km/.test(candidateText), '无坐标候选司机不得显示无效距离')

  await nearbyRows.first().getByRole('button', { name: '派给他' }).click()
  await adminPage.getByRole('button', { name: '确认派单' }).click()
  await adminPage.locator('.drawer-status-line').filter({ hasText: lastOrderNo })
    .getByText('待司机确认', { exact: true }).waitFor({ timeout: 10000 })
  const dispatchedDetail = await waitForAdminOrder(lastOrderNo, (detail) => detail.order.status === 'PENDING_DRIVER_CONFIRM')
  assert.equal(dispatchedDetail.order.sourceType, 'PUBLIC_H5', '派单不能改变公共订单来源')
  const waitingAttempt = dispatchedDetail.dispatchAttempts.find((attempt) => attempt.status === 'WAITING')
  assert.ok(waitingAttempt, '人工派单后必须存在 WAITING 派单尝试')
  assert.equal(waitingAttempt.dispatchType, 'MANUAL', '公共订单后台派单类型必须为 MANUAL')
}

async function runLocatedPublicOrderGate() {
  await passengerPage.context().grantPermissions(['geolocation'], { origin: passengerUrl })
  await passengerPage.context().setGeolocation({ latitude: 32.391, longitude: 119.508, accuracy: 10 })
  await passengerPage.goto(`${passengerUrl}/ride`, { waitUntil: 'domcontentloaded' })
  await fillBrowserLocationPoint('上车点', '扬州东站')
  await fillManualTextPoint('目的地', '瘦西湖')
  await passengerPage.getByPlaceholder('请输入 11 位手机号').fill('13800000002')
  await passengerPage.getByRole('button', { name: '提交预约' }).click()
  await passengerPage.waitForURL('**/order/**', { timeout: 15000 })
  lastOrderNo = new URL(passengerPage.url()).pathname.split('/').pop()
  assert.ok(lastOrderNo, '无法从有坐标订单页读取订单号')
  await adminPage.goto(`${adminUrl}/orders`, { waitUntil: 'domcontentloaded' })
  await adminPage.getByText(lastOrderNo).first().waitFor({ timeout: 15000 })
  await adminPage.locator('.orders-table .el-table__row').filter({ hasText: lastOrderNo }).dblclick()
  const createdDetail = await getAdminOrderDetail(lastOrderNo)
  assert.equal(createdDetail.order.sourceType, 'PUBLIC_H5', '有坐标订单来源必须为 PUBLIC_H5')
  assert.notEqual(createdDetail.order.pickupLatitude, null, '浏览器定位必须保存上车纬度')
  assert.notEqual(createdDetail.order.pickupLongitude, null, '浏览器定位必须保存上车经度')
  const candidates = adminPage.getByTestId('dispatch-candidates')
  await candidates.waitFor({ timeout: 10000 })
  const candidateText = await candidates.innerText()
  assert.match(candidateText, /km/, '有坐标订单必须显示距离候选司机')
}

async function runDirectedOrderGate() {
  await passengerPage.goto(`${passengerUrl}/ride/d/QRD101`, { waitUntil: 'domcontentloaded' })
  await passengerPage.getByRole('heading', { name: '李师傅' }).waitFor({ timeout: 10000 })
  await fillManualTextPoint('上车点', '扬州东站')
  await fillManualTextPoint('目的地', '瘦西湖')
  await passengerPage.getByPlaceholder('请输入 11 位手机号').fill('13800000001')
  await passengerPage.getByRole('button', { name: '提交给该司机确认' }).click()
  await passengerPage.waitForURL('**/order/**', { timeout: 15000 })
  lastOrderNo = new URL(passengerPage.url()).pathname.split('/').pop()
  assert.ok(lastOrderNo, '无法从司机定向订单页读取订单号')
  await passengerPage.getByText('待司机确认').waitFor({ timeout: 10000 })

  const drivers = await getAdminJson('/api/v1/admin/drivers')
  const directedDriver = drivers.find((driver) => driver.driverNo === 'D101')
  assert.ok(directedDriver, '本地种子司机 D101 不存在')
  const directedDetail = await getAdminOrderDetail(lastOrderNo)
  assert.equal(directedDetail.order.sourceType, 'DRIVER_QR', '司机定向订单来源必须为 DRIVER_QR')
  assert.equal(directedDetail.order.status, 'PENDING_DRIVER_CONFIRM', '司机定向订单必须直接等待司机确认')
  assert.equal(directedDetail.order.sourceDriverId, directedDriver.id, 'QRD101 订单必须绑定 D101')
  const directedAttempt = directedDetail.dispatchAttempts.find((attempt) => attempt.status === 'WAITING')
  assert.ok(directedAttempt, '司机定向订单必须存在 WAITING 派单尝试')
  assert.equal(directedAttempt.dispatchType, 'DIRECT_QR', '司机定向派单类型必须为 DIRECT_QR')
  assert.equal(directedAttempt.targetDriverId, directedDriver.id, '司机定向派单目标必须为 D101')
}

async function getAdminOrderDetail(orderNo) {
  return getAdminJson(`/api/v1/admin/orders/${encodeURIComponent(orderNo)}`)
}

async function getAdminJson(apiPath) {
  assert.ok(adminAccessToken, 'Admin 访问令牌不可用')
  const response = await fetch(new URL(apiPath, adminUrl), {
    headers: { Authorization: `Bearer ${adminAccessToken}` },
    signal: AbortSignal.timeout(15000),
  })
  lastApiResponse = {
    app: 'assertion',
    method: 'GET',
    path: apiPath,
    status: response.status,
    timestamp: new Date().toISOString(),
  }
  if (!response.ok) throw new Error(`验收断言请求失败：${apiPath} ${response.status}`)
  return response.json()
}

async function waitForAdminOrder(orderNo, predicate) {
  const deadline = Date.now() + 15000
  do {
    const detail = await getAdminOrderDetail(orderNo)
    if (predicate(detail)) return detail
    await new Promise((resolve) => setTimeout(resolve, 250))
  } while (Date.now() < deadline)
  throw new Error(`等待订单 ${orderNo} 达到目标状态超时`)
}

async function fillManualTextPoint(targetText, address) {
  await passengerPage.locator('button.route-select-row', { hasText: targetText }).click()
  const dialog = passengerPage.getByRole('dialog')
  await dialog.waitFor()
  await dialog.getByTestId('manual-place-address').fill(address)
  await dialog.getByTestId('confirm-manual-place').click()
  await dialog.waitFor({ state: 'hidden' })
}

async function fillBrowserLocationPoint(targetText, address) {
  await passengerPage.locator('button.route-select-row', { hasText: targetText }).click()
  const dialog = passengerPage.getByRole('dialog')
  await dialog.getByRole('button', { name: '使用我的当前位置' }).click()
  await dialog.getByText('当前位置已获取').waitFor({ timeout: 10000 })
  await dialog.locator('.current-location-card input').fill(address)
  await dialog.getByRole('button', { name: '使用此位置' }).click()
  await dialog.waitFor({ state: 'hidden' })
}

async function saveDiagnostics(error) {
  const details = {
    message: error instanceof Error ? error.message : String(error),
    passengerUrl: passengerPage?.url() ?? null,
    adminUrl: adminPage?.url() ?? null,
    orderNo: lastOrderNo,
    lastApiResponse,
    timestamp: new Date().toISOString(),
  }
  await fs.writeFile(path.join(artifactDir, 'failure.json'), JSON.stringify(details, null, 2))
  await passengerPage?.screenshot({ path: path.join(artifactDir, 'passenger-failure.png'), fullPage: true }).catch(() => {})
  await adminPage?.screenshot({ path: path.join(artifactDir, 'admin-failure.png'), fullPage: true }).catch(() => {})
}

main()
  .catch(async (error) => {
    await saveDiagnostics(error)
    console.error(error)
    process.exitCode = 1
  })
  .finally(async () => {
    await browser?.close()
  })
