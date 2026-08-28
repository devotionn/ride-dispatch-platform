const assert = require('node:assert/strict')

const baseUrl = (process.env.API_URL ?? 'http://localhost:8082').replace(/\/$/, '')
// Business-facing amounts are entered in yuan; the API persists fen.
const expectedFinalAmountFen = 120000
const runId = Date.now()
const now = new Date(Date.now() + 60 * 60 * 1000).toISOString()

let passed = 0
let failed = 0

async function request(path, options = {}) {
  const response = await fetch(`${baseUrl}${path}`, {
    ...options,
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
    // Keep non-JSON error bodies as text for diagnostics.
  }
  return { status: response.status, body }
}

function jsonBody(value) {
  return JSON.stringify(value)
}

function orderBody(mobile, overrides = {}) {
  return {
    sourceType: 'PUBLIC_H5',
    pickup: { address: '扬州东站', latitude: 32.391, longitude: 119.508 },
    destination: { address: '瘦西湖', latitude: 32.42, longitude: 119.414 },
    passengerCount: 1,
    departureAt: now,
    mobile,
    ...overrides,
  }
}

async function expect(name, fn) {
  try {
    await fn()
    passed += 1
    console.log(`PASS ${name}`)
  } catch (error) {
    failed += 1
    console.error(`FAIL ${name}: ${error.message}`)
  }
}

function assertCode(response, code) {
  assert.equal(response.body?.code, code, `expected ${code}, got ${JSON.stringify(response.body)}`)
}

async function main() {
  const health = await request('/actuator/health')
  assert.equal(health.status, 200)

  const adminLogin = await request('/api/v1/auth/admin/login', {
    method: 'POST',
    body: jsonBody({ username: 'admin', password: 'admin123' }),
  })
  assert.equal(adminLogin.status, 200)
  const adminToken = adminLogin.body.accessToken

  const dispatcherLogin = await request('/api/v1/auth/admin/login', {
    method: 'POST',
    body: jsonBody({ username: 'dispatcher', password: 'dispatcher123' }),
  })
  assert.equal(dispatcherLogin.status, 200)
  const dispatcherToken = dispatcherLogin.body.accessToken

  await expect('调度员不能进入退款异常后台', async () => {
    const response = await request('/api/v1/admin/payment-exceptions', {
      headers: { Authorization: `Bearer ${dispatcherToken}` },
    })
    assert.equal(response.status, 403)
  })

  const driverLogin = await request('/api/v1/auth/driver/login', {
    method: 'POST',
    body: jsonBody({ username: 'D101', password: 'driver123' }),
  })
  assert.equal(driverLogin.status, 200)
  const driverToken = driverLogin.body.accessToken

  const secondDriverLogin = await request('/api/v1/auth/driver/login', {
    method: 'POST',
    body: jsonBody({ username: 'D102', password: 'driver123' }),
  })
  assert.equal(secondDriverLogin.status, 200)
  const secondDriverToken = secondDriverLogin.body.accessToken

  const drivers = await request('/api/v1/admin/drivers', {
    headers: { Authorization: `Bearer ${adminToken}` },
  })
  assert.equal(drivers.status, 200)
  const d101 = drivers.body.find((driver) => driver.driverNo === 'D101')
  assert.ok(d101, 'D101 seed driver not found')

  await expect('短幂等键被拒绝', async () => {
    const response = await request('/api/v1/public/orders', {
      method: 'POST',
      headers: { 'Idempotency-Key': 'short-key' },
      body: jsonBody(orderBody('13800000011')),
    })
    assert.equal(response.status, 409)
    assertCode(response, 'IDEMPOTENCY_KEY_INVALID')
  })

  const idempotencyKey = `depth-test-key-${runId}-0001`
  const first = await request('/api/v1/public/orders', {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: jsonBody(orderBody('13800000012')),
  })
  assert.equal(first.status, 201)
  const orderNo = first.body.orderNo
  const passengerToken = first.body.passengerAccessToken

  await expect('相同幂等键重放同一订单', async () => {
    const response = await request('/api/v1/public/orders', {
      method: 'POST',
      headers: { 'Idempotency-Key': idempotencyKey },
      body: jsonBody(orderBody('13800000012')),
    })
    assert.equal(response.status, 201)
    assert.equal(response.body.orderNo, orderNo)
    assert.notEqual(response.body.passengerAccessToken, passengerToken)
  })

  await expect('相同幂等键不同内容被拒绝', async () => {
    const response = await request('/api/v1/public/orders', {
      method: 'POST',
      headers: { 'Idempotency-Key': idempotencyKey },
      body: jsonBody(orderBody('13800000013')),
    })
    assert.equal(response.status, 409)
    assertCode(response, 'IDEMPOTENCY_CONFLICT')
  })

  const secondOrder = await request('/api/v1/public/orders', {
    method: 'POST',
    headers: { 'Idempotency-Key': `depth-test-key-${runId}-0002` },
    body: jsonBody(orderBody('13800000014')),
  })
  assert.equal(secondOrder.status, 201)

  await expect('乘客 Token 不能读取其他订单', async () => {
    const response = await request(`/api/v1/public/orders/${secondOrder.body.orderNo}`, {
      headers: { 'X-Passenger-Token': passengerToken },
    })
    assert.equal(response.status, 409)
    assertCode(response, 'ORDER_ACCESS_DENIED')
  })

  await expect('接单前乘客可以取消', async () => {
    const response = await request(`/api/v1/public/orders/${secondOrder.body.orderNo}/cancel`, {
      method: 'POST',
      headers: { 'X-Passenger-Token': secondOrder.body.passengerAccessToken },
    })
    assert.equal(response.status, 200)
    assert.equal(response.body, 'CANCELLED')
  })

  await expect('取消后重复取消被拒绝', async () => {
    const response = await request(`/api/v1/public/orders/${secondOrder.body.orderNo}/cancel`, {
      method: 'POST',
      headers: { 'X-Passenger-Token': secondOrder.body.passengerAccessToken },
    })
    assert.equal(response.status, 409)
  })

  const dispatch = await request(`/api/v1/admin/orders/${orderNo}/dispatch`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${adminToken}` },
    body: jsonBody({ driverId: d101.id }),
  })
  assert.equal(dispatch.status, 200)
  const attemptId = dispatch.body.attemptId

  await expect('目标司机能看到待确认派单', async () => {
    const response = await request('/api/v1/driver/orders/pending-confirmation', {
      headers: { Authorization: `Bearer ${driverToken}` },
    })
    assert.equal(response.status, 200)
    assert.ok(response.body.some((item) => item.attemptId === attemptId))
  })

  await expect('其他司机不能读取该订单为活动订单', async () => {
    const response = await request('/api/v1/driver/orders/active', {
      headers: { Authorization: `Bearer ${secondDriverToken}` },
    })
    assert.equal(response.status, 200)
    assert.equal(response.body.some((item) => item.orderNo === orderNo), false)
  })

  const accept = await request(`/api/v1/driver/dispatch-attempts/${attemptId}/accept`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${driverToken}` },
  })
  assert.equal(accept.status, 200)

  await expect('同一派单不能重复接受', async () => {
    const response = await request(`/api/v1/driver/dispatch-attempts/${attemptId}/accept`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${driverToken}` },
    })
    assert.equal(response.status, 409)
  })

  await expect('不能跳过履约阶段', async () => {
    const response = await request(`/api/v1/driver/orders/${orderNo}/progress`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${driverToken}` },
      body: jsonBody({ stage: 'IN_TRANSIT' }),
    })
    assert.equal(response.status, 409)
  })

  await expect('到达目的地前不能录入金额', async () => {
    const response = await request(`/api/v1/driver/orders/${orderNo}/final-amount`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${driverToken}` },
      body: jsonBody({ amount: expectedFinalAmountFen }),
    })
    assert.equal(response.status, 409)
  })

  for (const stage of ['ARRIVED_PICKUP', 'PASSENGER_ONBOARD', 'IN_TRANSIT', 'ARRIVED_DESTINATION']) {
    const response = await request(`/api/v1/driver/orders/${orderNo}/progress`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${driverToken}` },
      body: jsonBody({ stage }),
    })
    assert.equal(response.status, 200, `stage ${stage}`)
  }

  await expect('金额必须大于零', async () => {
    const response = await request(`/api/v1/driver/orders/${orderNo}/final-amount`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${driverToken}` },
      body: jsonBody({ amount: 0 }),
    })
    assert.equal(response.status, 400)
  })

  const finalAmount = await request(`/api/v1/driver/orders/${orderNo}/final-amount`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${driverToken}` },
    body: jsonBody({ amount: expectedFinalAmountFen }),
  })
  assert.equal(finalAmount.status, 200)
  assert.equal(finalAmount.body.status, 'PENDING_PAYMENT')
  assert.equal(finalAmount.body.finalAmount, expectedFinalAmountFen)

  let passengerView
  await expect('乘客能看到最终金额和待付款状态', async () => {
    const response = await request(`/api/v1/public/orders/${orderNo}`, {
      headers: { 'X-Passenger-Token': passengerToken },
    })
    passengerView = response
    assert.equal(response.status, 200)
    assert.equal(response.body.status, 'PENDING_PAYMENT')
    assert.equal(response.body.finalAmount, expectedFinalAmountFen)
    assert.equal(response.body.tripStage, 'ARRIVED_DESTINATION')
  })

  const paymentToken = passengerView.body?.paymentToken
  assert.ok(paymentToken, 'pending payment response must include paymentToken')
  let paymentNo

  await expect('付款 Token 能读取付款上下文', async () => {
    const response = await request(`/api/v1/public/payments/${paymentToken}`)
    assert.equal(response.status, 200)
    paymentNo = response.body.paymentNo
    assert.equal(response.body.amount, expectedFinalAmountFen)
    assert.equal(response.body.status, 'PENDING')
  })

  const firstAttempt = await request(`/api/v1/public/payments/${paymentToken}/attempts`, {
    method: 'POST',
    headers: { 'Idempotency-Key': `e2e-attempt-${runId}-wechat` },
    body: jsonBody({ channel: 'MOCK_WECHAT' }),
  })
  assert.equal(firstAttempt.status, 200)
  const firstAttemptNo = firstAttempt.body.attemptNo

  await expect('Mock 微信失败后允许重试', async () => {
    const response = await request(`/api/v1/local/mock-payments/${firstAttemptNo}/failure`, { method: 'POST' })
    assert.equal(response.status, 200)
    assert.equal(response.body.status, 'FAILED')
  })

  const secondAttempt = await request(`/api/v1/public/payments/${paymentToken}/attempts`, {
    method: 'POST',
    headers: { 'Idempotency-Key': `e2e-attempt-${runId}-alipay` },
    body: jsonBody({ channel: 'MOCK_ALIPAY' }),
  })
  assert.equal(secondAttempt.status, 200)
  const secondAttemptNo = secondAttempt.body.attemptNo

  await expect('回调金额不一致被拒绝', async () => {
    const response = await request(`/api/v1/local/mock-payments/${secondAttemptNo}/success`, {
      method: 'POST',
      body: jsonBody({ thirdPartyTransactionNo: `mismatch-${runId}`, amount: expectedFinalAmountFen + 1 }),
    })
    assert.equal(response.status, 409)
    assertCode(response, 'PAYMENT_AMOUNT_MISMATCH')
  })

  const successCallback = await request(`/api/v1/local/mock-payments/${secondAttemptNo}/success`, {
    method: 'POST',
    body: jsonBody({ thirdPartyTransactionNo: `mock-success-${runId}` }),
  })
  assert.equal(successCallback.status, 200)
  assert.equal(successCallback.body.status, 'SUCCEEDED')

  await expect('重复成功回调幂等且不重复结算', async () => {
    const response = await request(`/api/v1/local/mock-payments/${secondAttemptNo}/success`, {
      method: 'POST',
      body: jsonBody({ thirdPartyTransactionNo: `mock-success-${runId}` }),
    })
    assert.equal(response.status, 200)
    assert.equal(response.body.status, 'SUCCEEDED')
  })

  await expect('付款状态恢复为已支付', async () => {
    const response = await request(`/api/v1/public/payments/${paymentToken}/status`)
    assert.equal(response.status, 200)
    assert.equal(response.body.status, 'PAID')
    assert.equal(response.body.attempts.length, 2)
  })

  await expect('线上支付成功后订单进入已完成', async () => {
    const response = await request(`/api/v1/public/orders/${orderNo}`, {
      headers: { 'X-Passenger-Token': passengerToken },
    })
    assert.equal(response.status, 200)
    assert.equal(response.body.status, 'COMPLETED')
    assert.equal(response.body.paymentStatus, 'PAID')
  })

  await expect('财务能查看支付单和多次尝试', async () => {
    const response = await request('/api/v1/admin/payments', {
      headers: { Authorization: `Bearer ${adminToken}` },
    })
    assert.equal(response.status, 200)
    assert.ok(response.body.some((item) => item.status === 'PAID' && item.attempts.length === 2))
  })

  const refundException = await request('/api/v1/admin/payment-exceptions', {
    method: 'POST',
    headers: { Authorization: `Bearer ${adminToken}`, 'Idempotency-Key': `refund-exception-${runId}-open` },
    body: jsonBody({ paymentNo, requestedAmount: 60000, reason: '乘客申请部分退款' }),
  })
  assert.equal(refundException.status, 200)
  assert.equal(refundException.body.status, 'OPEN')
  const refundExceptionNo = refundException.body.exceptionNo

  await expect('重复登记请求按幂等键返回同一退款异常', async () => {
    const response = await request('/api/v1/admin/payment-exceptions', {
      method: 'POST',
      headers: { Authorization: `Bearer ${adminToken}`, 'Idempotency-Key': `refund-exception-${runId}-open` },
      body: jsonBody({ paymentNo, requestedAmount: 60000, reason: '乘客申请部分退款' }),
    })
    assert.equal(response.status, 200)
    assert.equal(response.body.exceptionNo, refundExceptionNo)
  })

  await expect('财务能登记部分退款异常并查询', async () => {
    const response = await request('/api/v1/admin/payment-exceptions', {
      headers: { Authorization: `Bearer ${adminToken}` },
    })
    assert.equal(response.status, 200)
    assert.ok(response.body.some((item) => item.exceptionNo === refundExceptionNo && item.requestedAmount === 60000))
  })

  await expect('人工退款凭证能解决退款异常且支付仍保持已支付', async () => {
    const response = await request(`/api/v1/admin/payment-exceptions/${refundExceptionNo}/resolve`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${adminToken}` },
      body: jsonBody({ externalRefundRef: `MANUAL-REFUND-${runId}`, note: '财务线下退款已核验' }),
    })
    assert.equal(response.status, 200)
    assert.equal(response.body.status, 'RESOLVED')
    const paymentStatus = await request(`/api/v1/public/payments/${paymentToken}/status`)
    assert.equal(paymentStatus.status, 200)
    assert.equal(paymentStatus.body.status, 'PAID')
  })

  await expect('已解决退款异常不可重复处理', async () => {
    const response = await request(`/api/v1/admin/payment-exceptions/${refundExceptionNo}/reject`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${adminToken}` },
      body: jsonBody({ note: '重复处理测试' }),
    })
    assert.equal(response.status, 409)
    assertCode(response, 'PAYMENT_EXCEPTION_STATE_CONFLICT')
  })

  const rejectedException = await request('/api/v1/admin/payment-exceptions', {
    method: 'POST',
    headers: { Authorization: `Bearer ${adminToken}`, 'Idempotency-Key': `refund-exception-${runId}-reject` },
    body: jsonBody({ paymentNo, requestedAmount: 10000, reason: '材料不完整测试' }),
  })
  assert.equal(rejectedException.status, 200)
  const rejectedExceptionNo = rejectedException.body.exceptionNo

  await expect('退款异常累计金额不能超过支付金额', async () => {
    const response = await request('/api/v1/admin/payment-exceptions', {
      method: 'POST',
      headers: { Authorization: `Bearer ${adminToken}`, 'Idempotency-Key': `refund-exception-${runId}-over-budget` },
      body: jsonBody({ paymentNo, requestedAmount: 60001, reason: '超额累计测试' }),
    })
    assert.equal(response.status, 409)
    assertCode(response, 'PAYMENT_EXCEPTION_TOTAL_AMOUNT_INVALID')
  })

  await expect('退款异常可以被驳回并保留处理备注', async () => {
    const response = await request(`/api/v1/admin/payment-exceptions/${rejectedExceptionNo}/reject`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${adminToken}` },
      body: jsonBody({ note: '材料不完整，驳回本次申请' }),
    })
    assert.equal(response.status, 200)
    assert.equal(response.body.status, 'REJECTED')
    assert.equal(response.body.resolutionNote, '材料不完整，驳回本次申请')
  })

  await expect('线上支付收入进入可提现余额', async () => {
    const response = await request('/api/v1/driver/me/account', {
      headers: { Authorization: `Bearer ${driverToken}` },
    })
    assert.equal(response.status, 200)
    assert.equal(response.body.businessIncome, expectedFinalAmountFen)
    assert.equal(response.body.availableBalance, expectedFinalAmountFen)
    assert.equal(response.body.frozenBalance, 0)
  })

  await expect('账本只写入一笔线上成功收入', async () => {
    const response = await request('/api/v1/driver/me/ledger', {
      headers: { Authorization: `Bearer ${driverToken}` },
    })
    assert.equal(response.status, 200)
    assert.equal(response.body.length, 1)
    assert.equal(response.body[0].withdrawableDelta, expectedFinalAmountFen)
  })

  const withdrawal = await request('/api/v1/driver/me/withdrawals', {
    method: 'POST',
    headers: { Authorization: `Bearer ${driverToken}`, 'Idempotency-Key': `e2e-withdrawal-${runId}-reserve` },
    body: jsonBody({ amount: 100000, channel: 'BANK', account: '6222000000000000001' }),
  })
  assert.equal(withdrawal.status, 200)
  assert.equal(withdrawal.body.status, 'PENDING_REVIEW')
  const withdrawalNo = withdrawal.body.withdrawalNo

  await expect('提现申请会冻结可提现余额', async () => {
    const response = await request('/api/v1/driver/me/account', {
      headers: { Authorization: `Bearer ${driverToken}` },
    })
    assert.equal(response.status, 200)
    assert.equal(response.body.availableBalance, 20000)
    assert.equal(response.body.frozenBalance, 100000)
  })

  await expect('超额提现被拒绝且不产生负余额', async () => {
    const response = await request('/api/v1/driver/me/withdrawals', {
      method: 'POST',
      headers: { Authorization: `Bearer ${driverToken}`, 'Idempotency-Key': `e2e-withdrawal-${runId}-excess` },
      body: jsonBody({ amount: 20001, channel: 'BANK', account: '6222000000000000002' }),
    })
    assert.equal(response.status, 409)
    assertCode(response, 'INSUFFICIENT_AVAILABLE_BALANCE')
  })

  const approve = await request(`/api/v1/admin/withdrawals/${withdrawalNo}/approve`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${adminToken}` },
  })
  assert.equal(approve.status, 200)
  assert.equal(approve.body.status, 'APPROVED_PENDING_PAYMENT')

  const markPaid = await request(`/api/v1/admin/withdrawals/${withdrawalNo}/mark-paid`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${adminToken}` },
  })
  assert.equal(markPaid.status, 200)
  assert.equal(markPaid.body.status, 'PAID')

  await expect('人工打款后冻结余额核销', async () => {
    const response = await request('/api/v1/driver/me/account', {
      headers: { Authorization: `Bearer ${driverToken}` },
    })
    assert.equal(response.status, 200)
    assert.equal(response.body.availableBalance, 20000)
    assert.equal(response.body.frozenBalance, 0)
  })

  const rejectedWithdrawal = await request('/api/v1/driver/me/withdrawals', {
    method: 'POST',
    headers: { Authorization: `Bearer ${driverToken}`, 'Idempotency-Key': `e2e-withdrawal-${runId}-reject` },
    body: jsonBody({ amount: 10000, channel: 'BANK', account: '6222000000000000003' }),
  })
  assert.equal(rejectedWithdrawal.status, 200)
  const rejectedWithdrawalNo = rejectedWithdrawal.body.withdrawalNo
  const reject = await request(`/api/v1/admin/withdrawals/${rejectedWithdrawalNo}/reject`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${adminToken}` },
    body: jsonBody({ reason: '本地拒绝测试' }),
  })
  assert.equal(reject.status, 200)
  assert.equal(reject.body.status, 'REJECTED')

  await expect('提现冻结/核销逐笔落账', async () => {
    const response = await request('/api/v1/driver/me/ledger', {
      headers: { Authorization: `Bearer ${driverToken}` },
    })
    assert.equal(response.status, 200)
    assert.equal(response.body.length, 5)
    const ledgerTypes = response.body.map((item) => item.ledgerType)
    assert.equal(ledgerTypes.filter((type) => type === 'WITHDRAWAL_RESERVE').length, 2)
    assert.ok(ledgerTypes.includes('ONLINE_PAYMENT_INCOME'))
    assert.ok(ledgerTypes.includes('WITHDRAWAL_PAID'))
    assert.ok(ledgerTypes.includes('WITHDRAWAL_REJECT'))
  })

  const offlineOrder = await request('/api/v1/public/orders', {
    method: 'POST',
    headers: { 'Idempotency-Key': `depth-test-key-${runId}-offline` },
    body: jsonBody(orderBody('13800000015')),
  })
  assert.equal(offlineOrder.status, 201)
  const offlineDispatch = await request(`/api/v1/admin/orders/${offlineOrder.body.orderNo}/dispatch`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${adminToken}` },
    body: jsonBody({ driverId: drivers.body.find((driver) => driver.driverNo === 'D102').id }),
  })
  assert.equal(offlineDispatch.status, 200)
  const offlineAccept = await request(`/api/v1/driver/dispatch-attempts/${offlineDispatch.body.attemptId}/accept`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${secondDriverToken}` },
  })
  assert.equal(offlineAccept.status, 200)
  for (const stage of ['ARRIVED_PICKUP', 'PASSENGER_ONBOARD', 'IN_TRANSIT', 'ARRIVED_DESTINATION']) {
    const response = await request(`/api/v1/driver/orders/${offlineOrder.body.orderNo}/progress`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${secondDriverToken}` },
      body: jsonBody({ stage }),
    })
    assert.equal(response.status, 200, `offline stage ${stage}`)
  }
  const offlineAmount = await request(`/api/v1/driver/orders/${offlineOrder.body.orderNo}/final-amount`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${secondDriverToken}` },
    body: jsonBody({ amount: 50000 }),
  })
  assert.equal(offlineAmount.status, 200)

  await expect('司机二次确认线下收款完成订单', async () => {
    const response = await request(`/api/v1/driver/orders/${offlineOrder.body.orderNo}/offline-payment/confirm`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${secondDriverToken}` },
      body: jsonBody({ confirmation: 'CONFIRM' }),
    })
    assert.equal(response.status, 200)
    assert.equal(response.body.status, 'COMPLETED')
    assert.equal(response.body.settlementMethod, 'OFFLINE')
  })

  await expect('线下收入进入业务收入但不增加可提现余额', async () => {
    const response = await request('/api/v1/driver/me/account', {
      headers: { Authorization: `Bearer ${secondDriverToken}` },
    })
    assert.equal(response.status, 200)
    assert.equal(response.body.businessIncome, 50000)
    assert.equal(response.body.availableBalance, 0)
    assert.equal(response.body.frozenBalance, 0)
  })

  await expect('线下收款账本明确记录 withdrawableDelta 为零', async () => {
    const response = await request('/api/v1/driver/me/ledger', {
      headers: { Authorization: `Bearer ${secondDriverToken}` },
    })
    assert.equal(response.status, 200)
    assert.equal(response.body.length, 1)
    assert.equal(response.body[0].ledgerType, 'OFFLINE_INCOME')
    assert.equal(response.body[0].withdrawableDelta, 0)
  })

  console.log(`\nHTTP depth summary: ${passed} passed, ${failed} failed, base=${baseUrl}`)
  if (failed > 0) process.exitCode = 1
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
