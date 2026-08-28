const assert = require('node:assert/strict')
const fs = require('node:fs/promises')
const { chromium } = require('playwright')

const passengerUrl = (process.env.PASSENGER_URL ?? 'http://localhost:5173').replace(/\/$/, '')
const adminUrl = (process.env.ADMIN_URL ?? 'http://localhost:5174').replace(/\/$/, '')
const apiUrl = (process.env.API_URL ?? 'http://localhost:8081').replace(/\/$/, '')
const browserChannel = process.env.BROWSER_CHANNEL ?? 'bundled'
const headless = process.env.HEADLESS !== 'false'
const runId = Date.now()
const amountFen = 120000
const now = new Date(Date.now() + 60 * 60 * 1000).toISOString()

async function request(path, options = {}) {
  const response = await fetch(`${apiUrl}${path}`, {
    ...options,
    headers: {
      Accept: 'application/json',
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(options.headers ?? {}),
    },
  })
  const raw = await response.text()
  let body = raw
  try { body = raw ? JSON.parse(raw) : null } catch { /* retain text */ }
  if (!response.ok) throw new Error(`${options.method ?? 'GET'} ${path} failed: ${response.status} ${JSON.stringify(body)}`)
  return body
}

async function main() {
  const admin = await request('/api/v1/auth/admin/login', {
    method: 'POST', body: JSON.stringify({ username: 'admin', password: 'admin123' }),
  })
  const driver = await request('/api/v1/auth/driver/login', {
    method: 'POST', body: JSON.stringify({ username: 'D101', password: 'driver123' }),
  })
  const drivers = await request('/api/v1/admin/drivers', {
    headers: { Authorization: `Bearer ${admin.accessToken}` },
  })
  const d101 = drivers.find((item) => item.driverNo === 'D101')
  assert.ok(d101, 'D101 seed driver not found')

  const order = await request('/api/v1/public/orders', {
    method: 'POST',
    headers: { 'Idempotency-Key': `browser-payment-${runId}-0001` },
    body: JSON.stringify({
      sourceType: 'PUBLIC_H5',
      pickup: { address: '扬州东站', latitude: 32.391, longitude: 119.508 },
      destination: { address: '瘦西湖', latitude: 32.42, longitude: 119.414 },
      passengerCount: 1,
      departureAt: now,
      mobile: '13800000021',
    }),
  })
  const orderNo = order.orderNo
  const dispatched = await request(`/api/v1/admin/orders/${orderNo}/dispatch`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${admin.accessToken}` },
    body: JSON.stringify({ driverId: d101.id }),
  })
  await request(`/api/v1/driver/dispatch-attempts/${dispatched.attemptId}/accept`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${driver.accessToken}` },
  })
  for (const stage of ['ARRIVED_PICKUP', 'PASSENGER_ONBOARD', 'IN_TRANSIT', 'ARRIVED_DESTINATION']) {
    await request(`/api/v1/driver/orders/${orderNo}/progress`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${driver.accessToken}` },
      body: JSON.stringify({ stage }),
    })
  }
  await request(`/api/v1/driver/orders/${orderNo}/final-amount`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${driver.accessToken}` },
    body: JSON.stringify({ amount: amountFen }),
  })
  const passengerOrder = await request(`/api/v1/public/orders/${orderNo}`, {
    headers: { 'X-Passenger-Token': order.passengerAccessToken },
  })
  assert.equal(passengerOrder.status, 'PENDING_PAYMENT')
  assert.equal(passengerOrder.finalAmount, amountFen)
  assert.ok(passengerOrder.paymentToken)

  const browserOptions = { headless }
  if (browserChannel !== 'bundled') browserOptions.channel = browserChannel
  const browser = await chromium.launch(browserOptions)
  try {
    const page = await browser.newPage({ viewport: { width: 390, height: 844 } })
    const storageKey = `ride-dispatch:passenger-token:${orderNo}`
    await page.addInitScript(({ key, token }) => localStorage.setItem(key, token), {
      key: storageKey,
      token: order.passengerAccessToken,
    })
    await page.goto(`${passengerUrl}/order/${orderNo}`, { waitUntil: 'domcontentloaded' })
    await page.getByText('等待付款').waitFor({ timeout: 15000 })
    await page.getByRole('button', { name: '去付款' }).click()
    await page.waitForURL('**/payment/**', { timeout: 15000 })
    // Order status GET rotates the one-time payment token; use the token carried by the payment route.
    const activePaymentToken = decodeURIComponent(new URL(page.url()).pathname.split('/').pop())
    assert.ok(activePaymentToken, 'payment route must carry an active payment token')
    await page.getByText('安全付款').waitFor()
    await page.getByText('¥1200.00').waitFor()
    const qrSrc = await page.locator('.payment-qr').getAttribute('src')
    assert.ok(qrSrc?.startsWith('data:image/png'), 'payment page should render a local QR image')
    await page.getByRole('button', { name: '发起支付' }).click()
    await page.getByText('等待模拟回调').waitFor({ timeout: 10000 })
    await page.getByRole('button', { name: '模拟成功' }).click()
    await page.getByText('支付已完成').waitFor({ timeout: 15000 })
    await page.getByText('已支付').first().waitFor()
    const paid = await request(`/api/v1/public/payments/${activePaymentToken}/status`)
    assert.equal(paid.status, 'PAID')
    const completed = await request(`/api/v1/public/orders/${orderNo}`, {
      headers: { 'X-Passenger-Token': order.passengerAccessToken },
    })
    assert.equal(completed.status, 'COMPLETED')

    const refundException = await request('/api/v1/admin/payment-exceptions', {
      method: 'POST',
      headers: { Authorization: `Bearer ${admin.accessToken}`, 'Idempotency-Key': `browser-refund-${runId}-open` },
      body: JSON.stringify({ paymentNo: paid.paymentNo, requestedAmount: 60000, reason: '浏览器回归人工退款异常' }),
    })
    assert.equal(refundException.status, 'OPEN')

    await request('/api/v1/driver/me/withdrawals', {
      method: 'POST',
      headers: { Authorization: `Bearer ${driver.accessToken}`, 'Idempotency-Key': `browser-withdrawal-${runId}-001` },
      body: JSON.stringify({ amount: 50000, channel: 'BANK', account: '6222020202020202' }),
    })

    const adminPage = await browser.newPage({ viewport: { width: 1440, height: 900 } })
    await adminPage.addInitScript((session) => localStorage.setItem('ride-dispatch:admin-session', JSON.stringify(session)), {
      accessToken: admin.accessToken,
      expiresAt: admin.expiresAt,
      authority: admin.authority,
    })
    await adminPage.goto(`${adminUrl}/payment-exceptions`, { waitUntil: 'domcontentloaded' })
    await adminPage.getByRole('heading', { name: '退款异常' }).waitFor({ timeout: 15000 })
    await adminPage.getByText(refundException.exceptionNo).waitFor({ timeout: 15000 })
    await adminPage.goto(`${adminUrl}/payments`, { waitUntil: 'domcontentloaded' })
    await adminPage.getByRole('heading', { name: '支付记录' }).waitFor({ timeout: 15000 })
    const paymentDownload = await Promise.all([
      adminPage.waitForEvent('download'),
      adminPage.getByRole('button', { name: '导出 CSV' }).click(),
    ])
    const paymentCsvPath = await paymentDownload[0].path()
    assert.ok(paymentCsvPath, 'payment export should produce a download')
    const paymentCsv = await fs.readFile(paymentCsvPath, 'utf8')
    assert.match(paymentCsv, /支付单号/)
    assert.match(paymentCsv, /1200\.00/)

    await adminPage.goto(`${adminUrl}/withdrawals`, { waitUntil: 'domcontentloaded' })
    await adminPage.getByRole('heading', { name: '提现审核' }).waitFor({ timeout: 15000 })
    const withdrawalDownload = await Promise.all([
      adminPage.waitForEvent('download'),
      adminPage.getByRole('button', { name: '导出 CSV' }).click(),
    ])
    const withdrawalCsvPath = await withdrawalDownload[0].path()
    assert.ok(withdrawalCsvPath, 'withdrawal export should produce a download')
    const withdrawalCsv = await fs.readFile(withdrawalCsvPath, 'utf8')
    assert.match(withdrawalCsv, /收款账号（脱敏）/)
    assert.equal(withdrawalCsv.includes('6222020202020202'), false, 'withdrawal export must mask account')
    await adminPage.close()
    console.log(`Payment browser gate passed; order=${orderNo}; amount=¥1200.00`)
  } finally {
    await browser.close()
  }
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
