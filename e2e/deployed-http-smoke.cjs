const assert = require('node:assert/strict')

const baseUrl = (process.env.DEPLOYED_API_URL ?? 'http://8.138.144.54').replace(/\/$/, '')
const adminUsername = process.env.DEPLOYED_ADMIN_USERNAME
const adminPassword = process.env.DEPLOYED_ADMIN_PASSWORD
const driverUsername = process.env.DEPLOYED_DRIVER_USERNAME
const driverPassword = process.env.DEPLOYED_DRIVER_PASSWORD
const runId = Date.now()

function required(name, value) {
  if (!value) throw new Error(`${name} is required; credentials are never hard-coded in this test`)
}

async function request(path, options = {}) {
  const response = await fetch(`${baseUrl}${path}`, {
    ...options,
    signal: options.signal ?? AbortSignal.timeout(15000),
    headers: {
      Accept: 'application/json',
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(options.headers ?? {}),
    },
  })
  const raw = await response.text()
  let body = raw
  try {
    body = raw ? JSON.parse(raw) : null
  } catch {
    // Keep non-JSON content as text for diagnostics.
  }
  return { status: response.status, body }
}

function json(value) {
  return JSON.stringify(value)
}

async function login(username, password, role) {
  const response = await request(`/api/v1/auth/${role}/login`, {
    method: 'POST',
    body: json({ username, password }),
  })
  assert.equal(response.status, 200, `${role} login failed: ${response.status}`)
  assert.ok(response.body?.accessToken, `${role} login did not return a token`)
  return response.body.accessToken
}

async function readSseConnected(token) {
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), 5000)
  try {
    const response = await fetch(`${baseUrl}/api/v1/driver/events`, {
      signal: controller.signal,
      headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
    })
    assert.equal(response.status, 200)
    assert.ok(response.body, 'SSE response body is missing')
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let text = ''
    while (!text.includes('CONNECTED')) {
      const chunk = await reader.read()
      if (chunk.done) break
      text += decoder.decode(chunk.value, { stream: true })
    }
    await reader.cancel()
    assert.match(text, /CONNECTED/)
  } finally {
    clearTimeout(timeout)
    controller.abort()
  }
}

async function main() {
  required('DEPLOYED_ADMIN_USERNAME', adminUsername)
  required('DEPLOYED_ADMIN_PASSWORD', adminPassword)
  required('DEPLOYED_DRIVER_USERNAME', driverUsername)
  required('DEPLOYED_DRIVER_PASSWORD', driverPassword)

  const checks = []
  async function check(name, fn) {
    await fn()
    checks.push(name)
    console.log(`PASS ${name}`)
  }

  await check('public brand through Nginx', async () => {
    const response = await request('/api/v1/public/brand')
    assert.equal(response.status, 200)
    assert.equal(response.body.companyName, 'Ride Dispatch Platform')
  })

  const adminToken = await login(adminUsername, adminPassword, 'admin')
  const driverToken = await login(driverUsername, driverPassword, 'driver')

  await check('admin driver list', async () => {
    const response = await request('/api/v1/admin/drivers', {
      headers: { Authorization: `Bearer ${adminToken}` },
    })
    assert.equal(response.status, 200)
    assert.ok(response.body.some((item) => item.driverNo === driverUsername))
  })

  await check('driver state and account', async () => {
    const state = await request('/api/v1/driver/me/state', {
      headers: { Authorization: `Bearer ${driverToken}` },
    })
    assert.equal(state.status, 200)
    assert.ok(Number.isInteger(state.body.driverId))
    assert.ok(['AVAILABLE', 'OFFLINE', 'BUSY'].includes(state.body.workStatus))
    const account = await request('/api/v1/driver/me/account', {
      headers: { Authorization: `Bearer ${driverToken}` },
    })
    assert.equal(account.status, 200)
    assert.ok(Number.isInteger(account.body.businessIncome))
  })

  await check('driver cannot access admin API', async () => {
    const response = await request('/api/v1/admin/drivers', {
      headers: { Authorization: `Bearer ${driverToken}` },
    })
    assert.equal(response.status, 403)
  })

  const orderBody = {
    sourceType: 'PUBLIC_H5',
    pickup: { address: '服务器回归上车点', latitude: 32.391, longitude: 119.508 },
    destination: { address: '服务器回归目的地', latitude: 32.42, longitude: 119.414 },
    passengerCount: 1,
    departureAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
    mobile: `139${String(runId).slice(-8)}`,
    remark: `DEPLOYED-SMOKE-${runId}`,
  }
  const idempotencyKey = `deployed-smoke-${runId}-0001`
  const created = await request('/api/v1/public/orders', {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: json(orderBody),
  })
  assert.equal(created.status, 201, `order creation failed: ${JSON.stringify(created.body)}`)
  const orderNo = created.body.orderNo
  const passengerToken = created.body.passengerAccessToken

  await check('public order idempotent replay', async () => {
    const replay = await request('/api/v1/public/orders', {
      method: 'POST',
      headers: { 'Idempotency-Key': idempotencyKey },
      body: json(orderBody),
    })
    assert.equal(replay.status, 201)
    assert.equal(replay.body.orderNo, orderNo)
  })

  await check('passenger order read and cancellation', async () => {
    const read = await request(`/api/v1/public/orders/${orderNo}`, {
      headers: { 'X-Passenger-Token': passengerToken },
    })
    assert.equal(read.status, 200)
    assert.equal(read.body.status, 'PENDING_DISPATCH')
    const cancel = await request(`/api/v1/public/orders/${orderNo}/cancel`, {
      method: 'POST',
      headers: { 'X-Passenger-Token': passengerToken },
    })
    assert.equal(cancel.status, 200)
    assert.equal(cancel.body, 'CANCELLED')
  })

  await check('driver SSE connected event', async () => {
    await readSseConnected(driverToken)
  })

  console.log(`\nDeployed smoke passed: ${checks.length} checks; base=${baseUrl}; order=${orderNo}`)
}

main().catch((error) => {
  console.error(`DEPLOYED SMOKE FAILED: ${error.message}`)
  process.exitCode = 1
})
