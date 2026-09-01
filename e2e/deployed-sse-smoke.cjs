const assert = require('node:assert/strict')

const baseUrl = (process.env.DEPLOYED_API_URL ?? '').replace(/\/$/, '')
if (!baseUrl) throw new Error('DEPLOYED_API_URL is required for deployed E2E checks')
const adminUsername = process.env.DEPLOYED_ADMIN_USERNAME
const adminPassword = process.env.DEPLOYED_ADMIN_PASSWORD
const driverUsername = process.env.DEPLOYED_DRIVER_USERNAME
const driverPassword = process.env.DEPLOYED_DRIVER_PASSWORD
const runId = Date.now()

function json(value) { return JSON.stringify(value) }

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
  try { body = raw ? JSON.parse(raw) : null } catch { /* keep text */ }
  return { status: response.status, body }
}

async function login(role, username, password) {
  const response = await request(`/api/v1/auth/${role}/login`, {
    method: 'POST', body: json({ username, password }),
  })
  assert.equal(response.status, 200, JSON.stringify(response.body))
  return response.body.accessToken
}

function openDriverStream(token) {
  const controller = new AbortController()
  const connected = new Promise((resolve, reject) => { controller._connected = { resolve, reject } })
  const dispatch = new Promise((resolve, reject) => { controller._dispatch = { resolve, reject } })
  const consume = (async () => {
    const response = await fetch(`${baseUrl}/api/v1/driver/events`, {
      signal: controller.signal,
      headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
    })
    assert.equal(response.status, 200)
    assert.ok(response.body)
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let eventName = null
    let data = ''
    const flush = () => {
      if (!eventName) return
      let parsed = data
      try { parsed = data ? JSON.parse(data) : data } catch { /* keep text */ }
      if (eventName === 'CONNECTED') controller._connected.resolve(parsed)
      if (eventName === 'DRIVER_NEW_DISPATCH') controller._dispatch.resolve(parsed)
      eventName = null
      data = ''
    }
    while (true) {
      const chunk = await reader.read()
      if (chunk.done) break
      buffer += decoder.decode(chunk.value, { stream: true })
      const lines = buffer.split(/\r?\n/)
      buffer = lines.pop() ?? ''
      for (const line of lines) {
        if (line === '') flush()
        else if (line.startsWith('event:')) eventName = line.slice(6).trim()
        else if (line.startsWith('data:')) data += line.slice(5).trim()
      }
    }
  })().catch((error) => {
    if (!controller.signal.aborted) {
      controller._connected.reject(error)
      controller._dispatch.reject(error)
    }
  })
  return {
    connected,
    dispatch,
    close: () => controller.abort(),
    consume,
  }
}

async function main() {
  assert.ok(adminUsername && adminPassword && driverUsername && driverPassword, 'deployment credentials are required')
  const adminToken = await login('admin', adminUsername, adminPassword)
  const driverToken = await login('driver', driverUsername, driverPassword)
  const auth = (token) => ({ Authorization: `Bearer ${token}` })
  const drivers = await request('/api/v1/admin/drivers', { headers: auth(adminToken) })
  assert.equal(drivers.status, 200)
  const driver = drivers.body.find((item) => item.driverNo === driverUsername)
  assert.ok(driver)

  const stream = openDriverStream(driverToken)
  try {
    const connected = await Promise.race([
      stream.connected,
      new Promise((_, reject) => setTimeout(() => reject(new Error('SSE CONNECTED timeout')), 10000)),
    ])
    assert.equal(Number(connected.driverId), driver.id)
    console.log('PASS deployed SSE CONNECTED is scoped to driver')

    const orderBody = {
      sourceType: 'PUBLIC_H5',
      pickup: { address: 'SSE回归上车点', latitude: 32.391, longitude: 119.508 },
      destination: { address: 'SSE回归目的地', latitude: 32.42, longitude: 119.414 },
      passengerCount: 1,
      departureAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
      mobile: `137${String(runId).slice(-8)}`,
      remark: `DEPLOYED-SSE-${runId}`,
    }
    const created = await request('/api/v1/public/orders', {
      method: 'POST',
      headers: { 'Idempotency-Key': `deployed-sse-${runId}-order` },
      body: json(orderBody),
    })
    assert.equal(created.status, 201, JSON.stringify(created.body))

    const dispatched = await request(`/api/v1/admin/orders/${created.body.orderNo}/dispatch`, {
      method: 'POST', headers: auth(adminToken), body: json({ driverId: driver.id }),
    })
    assert.equal(dispatched.status, 200, JSON.stringify(dispatched.body))
    const event = await Promise.race([
      stream.dispatch,
      new Promise((_, reject) => setTimeout(() => reject(new Error('SSE DRIVER_NEW_DISPATCH timeout')), 10000)),
    ])
    assert.equal(event.driverId, driver.id)
    assert.equal(event.attemptId, dispatched.body.attemptId)
    assert.equal(event.orderNo, created.body.orderNo)
    assert.equal(event.eventType, 'DRIVER_NEW_DISPATCH')
    console.log('PASS deployed SSE dispatch event matches driver, attempt and order')

    const rejected = await request(`/api/v1/driver/dispatch-attempts/${dispatched.body.attemptId}/reject`, {
      method: 'POST', headers: auth(driverToken), body: json({ reasonCode: 'TEST', reasonText: 'SSE自动化回归' }),
    })
    assert.equal(rejected.status, 200, JSON.stringify(rejected.body))
    const cancelled = await request(`/api/v1/public/orders/${created.body.orderNo}/cancel`, {
      method: 'POST', headers: { 'X-Passenger-Token': created.body.passengerAccessToken },
    })
    assert.equal(cancelled.status, 200)
    console.log('PASS SSE test order rejected and cancelled')
  } finally {
    stream.close()
  }
  console.log(`\nDeployed SSE smoke passed: base=${baseUrl}`)
}

main().catch((error) => {
  console.error(`DEPLOYED SSE SMOKE FAILED: ${error.message}`)
  process.exitCode = 1
})
