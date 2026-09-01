const assert = require('node:assert/strict')

const baseUrl = (process.env.DEPLOYED_API_URL ?? 'http://203.0.113.10').replace(/\/$/, '')
const adminUsername = process.env.DEPLOYED_ADMIN_USERNAME
const adminPassword = process.env.DEPLOYED_ADMIN_PASSWORD
const driverUsername = process.env.DEPLOYED_DRIVER_USERNAME
const driverPassword = process.env.DEPLOYED_DRIVER_PASSWORD
const runId = Date.now()
const amountFen = 120000

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

const json = (value) => JSON.stringify(value)

async function login(role, username, password) {
  const response = await request(`/api/v1/auth/${role}/login`, {
    method: 'POST',
    body: json({ username, password }),
  })
  assert.equal(response.status, 200)
  return response.body.accessToken
}

async function main() {
  assert.ok(adminUsername && adminPassword && driverUsername && driverPassword, 'deployment credentials are required')
  const adminToken = await login('admin', adminUsername, adminPassword)
  const driverToken = await login('driver', driverUsername, driverPassword)
  const auth = (token) => ({ Authorization: `Bearer ${token}` })
  const checks = []
  async function check(name, fn) {
    await fn()
    checks.push(name)
    console.log(`PASS ${name}`)
  }

  const drivers = await request('/api/v1/admin/drivers', { headers: auth(adminToken) })
  assert.equal(drivers.status, 200)
  const driver = drivers.body.find((item) => item.driverNo === driverUsername)
  assert.ok(driver, `${driverUsername} not found`)

  const beforeAccount = await request('/api/v1/driver/me/account', { headers: auth(driverToken) })
  assert.equal(beforeAccount.status, 200)
  const beforeBusinessIncome = beforeAccount.body.businessIncome
  const beforeAvailable = beforeAccount.body.availableBalance

  const orderBody = {
    sourceType: 'PUBLIC_H5',
    pickup: { address: '服务器核心链路上车点', latitude: 32.391, longitude: 119.508 },
    destination: { address: '服务器核心链路目的地', latitude: 32.42, longitude: 119.414 },
    passengerCount: 1,
    departureAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
    mobile: `138${String(runId).slice(-8)}`,
    remark: `DEPLOYED-CORE-${runId}`,
  }
  const created = await request('/api/v1/public/orders', {
    method: 'POST',
    headers: { 'Idempotency-Key': `deployed-core-${runId}-order` },
    body: json(orderBody),
  })
  assert.equal(created.status, 201, JSON.stringify(created.body))
  const orderNo = created.body.orderNo
  const passengerToken = created.body.passengerAccessToken

  const dispatched = await request(`/api/v1/admin/orders/${orderNo}/dispatch`, {
    method: 'POST',
    headers: auth(adminToken),
    body: json({ driverId: driver.id }),
  })
  assert.equal(dispatched.status, 200, JSON.stringify(dispatched.body))
  const attemptId = dispatched.body.attemptId

  await check('server dispatch creates waiting attempt', async () => {
    const pending = await request('/api/v1/driver/orders/pending-confirmation', { headers: auth(driverToken) })
    assert.equal(pending.status, 200)
    assert.ok(pending.body.some((item) => item.attemptId === attemptId))
  })

  const accepted = await request(`/api/v1/driver/dispatch-attempts/${attemptId}/accept`, {
    method: 'POST', headers: auth(driverToken),
  })
  assert.equal(accepted.status, 200)

  await check('server four-stage fulfilment', async () => {
    for (const stage of ['ARRIVED_PICKUP', 'PASSENGER_ONBOARD', 'IN_TRANSIT', 'ARRIVED_DESTINATION']) {
      const response = await request(`/api/v1/driver/orders/${orderNo}/progress`, {
        method: 'POST', headers: auth(driverToken), body: json({ stage }),
      })
      assert.equal(response.status, 200, `${stage}: ${JSON.stringify(response.body)}`)
    }
  })

  const finalAmount = await request(`/api/v1/driver/orders/${orderNo}/final-amount`, {
    method: 'POST', headers: auth(driverToken), body: json({ amount: amountFen }),
  })
  console.log(`final amount response: status=${finalAmount.status} body=${JSON.stringify(finalAmount.body)}`)
  assert.equal(finalAmount.status, 200, JSON.stringify(finalAmount.body))
  assert.equal(finalAmount.body.finalAmount, amountFen)
  const passengerView = await request(`/api/v1/public/orders/${orderNo}`, {
    headers: { 'X-Passenger-Token': passengerToken },
  })
  assert.equal(passengerView.status, 200)
  const paymentToken = passengerView.body.paymentToken
  assert.ok(paymentToken)

  const payment = await request(`/api/v1/public/payments/${paymentToken}`)
  console.log(`payment context: status=${payment.status} body=${JSON.stringify(payment.body)}`)
  assert.equal(payment.status, 200)
  const paymentNo = payment.body.paymentNo
  const attempt = await request(`/api/v1/public/payments/${paymentToken}/attempts`, {
    method: 'POST',
    headers: { 'Idempotency-Key': `deployed-core-${runId}-attempt` },
    body: json({ channel: 'MOCK_ALIPAY' }),
  })
  console.log(`payment attempt: status=${attempt.status} body=${JSON.stringify(attempt.body)}`)
  assert.equal(attempt.status, 200)
  const success = await request(`/api/v1/local/mock-payments/${attempt.body.attemptNo}/success`, {
    method: 'POST', body: json({ thirdPartyTransactionNo: `DEPLOYED-${runId}` }),
  })
  console.log(`payment success: status=${success.status} body=${JSON.stringify(success.body)}`)
  let settledOnline = false
  if (success.status === 200) {
    settledOnline = true
    checks.push('server mock payment callback (local/staging profile)')
    console.log('PASS server mock payment callback (local/staging profile)')
  } else {
    // The production profile intentionally does not expose the local mock callback.
    // Continue the deployed business-flow test through the supported offline-confirmation path.
    assert.equal(success.status, 404, `unexpected payment provider boundary response: ${JSON.stringify(success.body)}`)
    console.log('PASS production payment provider boundary (local mock callback disabled)')
    checks.push('production payment provider boundary (local mock callback disabled)')
    const offline = await request(`/api/v1/driver/orders/${orderNo}/offline-payment/confirm`, {
      method: 'POST', headers: auth(driverToken), body: json({ confirmation: 'CONFIRM' }),
    })
    assert.equal(offline.status, 200, JSON.stringify(offline.body))
  }

  await check('server payment completes order and records amount', async () => {
    const status = await request(`/api/v1/public/payments/${paymentToken}/status`)
    assert.equal(status.status, 200)
    assert.equal(status.body.status, 'PAID')
    const order = await request(`/api/v1/public/orders/${orderNo}`, {
      headers: { 'X-Passenger-Token': passengerToken },
    })
    assert.equal(order.status, 200)
    assert.equal(order.body.status, 'COMPLETED')
    assert.equal(order.body.finalAmount, amountFen)
  })

  await check('server income settlement delta', async () => {
    const account = await request('/api/v1/driver/me/account', { headers: auth(driverToken) })
    assert.equal(account.status, 200)
    assert.equal(account.body.businessIncome - beforeBusinessIncome, amountFen)
    assert.equal(account.body.availableBalance - beforeAvailable, settledOnline ? amountFen : 0)
  })

  if (settledOnline) {
    const withdrawal = await request('/api/v1/driver/me/withdrawals', {
      method: 'POST',
      headers: { ...auth(driverToken), 'Idempotency-Key': `deployed-core-${runId}-withdrawal` },
      body: json({ amount: 10000, channel: 'BANK', account: `6222${String(runId).slice(-13)}` }),
    })
    assert.equal(withdrawal.status, 200, JSON.stringify(withdrawal.body))
    const withdrawalNo = withdrawal.body.withdrawalNo
    const approved = await request(`/api/v1/admin/withdrawals/${withdrawalNo}/approve`, {
      method: 'POST', headers: auth(adminToken),
    })
    assert.equal(approved.status, 200)
    const markedPaid = await request(`/api/v1/admin/withdrawals/${withdrawalNo}/mark-paid`, {
      method: 'POST', headers: auth(adminToken),
    })
    assert.equal(markedPaid.status, 200)

    await check('server withdrawal approval and ledger', async () => {
      assert.equal(markedPaid.body.status, 'PAID')
      const ledger = await request('/api/v1/driver/me/ledger', { headers: auth(driverToken) })
      assert.equal(ledger.status, 200)
      assert.ok(ledger.body.some((item) => item.ledgerType === 'WITHDRAWAL_PAID'))
    })
  } else {
    const withdrawal = await request('/api/v1/driver/me/withdrawals', {
      method: 'POST',
      headers: { ...auth(driverToken), 'Idempotency-Key': `deployed-core-${runId}-withdrawal-insufficient` },
      body: json({ amount: 10000, channel: 'BANK', account: `6222${String(runId).slice(-13)}` }),
    })
    assert.equal(withdrawal.status, 409, JSON.stringify(withdrawal.body))
    assert.equal(withdrawal.body.code, 'INSUFFICIENT_AVAILABLE_BALANCE')
    await check('server offline income remains non-withdrawable until online settlement', async () => {})
  }

  const exception = await request('/api/v1/admin/payment-exceptions', {
    method: 'POST',
    headers: { ...auth(adminToken), 'Idempotency-Key': `deployed-core-${runId}-exception` },
    body: json({ paymentNo, requestedAmount: 1000, reason: `DEPLOYED-CORE-${runId}` }),
  })
  assert.equal(exception.status, 200)
  const rejected = await request(`/api/v1/admin/payment-exceptions/${exception.body.exceptionNo}/reject`, {
    method: 'POST', headers: auth(adminToken), body: json({ note: '部署回归自动驳回' }),
  })
  assert.equal(rejected.status, 200)

  await check('server payment exception rejection', async () => {
    assert.equal(rejected.body.status, 'REJECTED')
    const list = await request('/api/v1/admin/payment-exceptions', { headers: auth(adminToken) })
    assert.equal(list.status, 200)
    assert.ok(list.body.some((item) => item.exceptionNo === exception.body.exceptionNo))
  })

  console.log(`\nDeployed core flow passed: ${checks.length} checks; order=${orderNo}; base=${baseUrl}`)
}

main().catch((error) => {
  console.error(`DEPLOYED CORE FLOW FAILED: ${error.message}`)
  process.exitCode = 1
})
