import { expect, test } from '@playwright/test'

type ApiCall = {
  method: string
  path: string
  status: number
  requestCorrelationId?: string
  responseCorrelationId?: string
}

test('agent can create a customer, log an interaction, and read it back', async ({ page }, testInfo) => {
  const suffix = `${Date.now().toString(36)}-${testInfo.workerIndex}-${testInfo.retry}`
  const customerId = `E2E-${suffix}`.toUpperCase()
  const customerName = `E2E Customer ${suffix}`
  const customerEmail = `e2e-${suffix}@example.test`
  const interactionNotes = `E2E interaction ${suffix}`

  const apiCalls: ApiCall[] = []
  const browserErrors: string[] = []

  page.on('response', (response) => {
    const url = new URL(response.url())
    if (!url.pathname.startsWith('/api/')) return

    apiCalls.push({
      method: response.request().method(),
      path: `${url.pathname}${url.search}`,
      status: response.status(),
      requestCorrelationId: response.request().headers()['x-correlation-id'],
      responseCorrelationId: response.headers()['x-correlation-id'],
    })
  })
  page.on('console', (message) => {
    if (message.type() !== 'error') return
    // Same intentional cancellations the requestfailed handler below skips —
    // Chromium also surfaces them on the console.
    if (message.text().includes('ERR_ABORTED')) return
    browserErrors.push(`console: ${message.text()}`)
  })
  page.on('pageerror', (error) => browserErrors.push(`page: ${error.message}`))
  page.on('requestfailed', (request) => {
    const failure = request.failure()?.errorText ?? 'unknown failure'
    // React development mode mounts effects twice and aborts the first fetch.
    // Those intentional cancellations are not application/network failures.
    if (failure.includes('ERR_ABORTED')) return
    const url = new URL(request.url())
    browserErrors.push(
      `request: ${request.method()} ${url.pathname} - ${failure}`,
    )
  })

  try {
    await test.step('Login as an agent', async () => {
      await page.goto('/')
      await page.getByLabel('Username').fill('agent1')
      await page.getByLabel('Password').fill('agent1')

      const loginResponsePromise = page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === '/api/auth/login' &&
          response.request().method() === 'POST',
      )
      await page.getByRole('button', { name: 'Sign in', exact: true }).click()

      const loginResponse = await loginResponsePromise
      expect(loginResponse.status()).toBe(200)
      expect(await loginResponse.headerValue('x-correlation-id')).toBeTruthy()
      await expect(page.getByText(/Signed in as\s+agent1\s+\(AGENT\)/)).toBeVisible()
    })

    await test.step('Create a customer', async () => {
      await page
        .getByRole('navigation')
        .getByRole('button', { name: 'Add Customer', exact: true })
        .click()

      await page.getByPlaceholder('CUS-1006').fill(customerId)
      await page.getByPlaceholder('Acme Corporation').fill(customerName)
      await page.getByPlaceholder('info@acme.com').fill(customerEmail)
      await page.getByPlaceholder('(555) 123-4567').fill('555-0199')

      const createResponsePromise = page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === '/api/customers' &&
          response.request().method() === 'POST',
      )
      await page.getByRole('button', { name: 'Save', exact: true }).click()

      const createResponse = await createResponsePromise
      expect(createResponse.status()).toBe(201)
      expect(await createResponse.headerValue('x-correlation-id')).toBeTruthy()
      await expect(page.getByRole('heading', { name: 'Customer Details' })).toBeVisible()
      await expect(page.getByText(customerId, { exact: true }).first()).toBeVisible()
      await expect(page.getByText(customerEmail, { exact: true }).first()).toBeVisible()
    })

    await test.step('Log an interaction', async () => {
      await page
        .getByRole('main')
        .getByRole('button', { name: 'Activities', exact: true })
        .click()
      await page.getByLabel('Channel').selectOption('EMAIL')
      await page.getByLabel('Notes').fill(interactionNotes)

      const interactionResponsePromise = page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === '/api/interactions' &&
          response.request().method() === 'POST',
      )
      await page.getByRole('button', { name: 'Add Activity', exact: true }).click()

      const interactionResponse = await interactionResponsePromise
      expect(interactionResponse.status()).toBe(202)
      expect(await interactionResponse.headerValue('x-correlation-id')).toBeTruthy()

      const sessionRow = page.getByRole('row').filter({ hasText: interactionNotes })
      await expect(sessionRow).toBeVisible()
      await expect(sessionRow).toContainText('EMAIL')
    })

    await test.step('Read the interaction back after remounting the customer screen', async () => {
      await page
        .getByRole('navigation')
        .getByRole('button', { name: 'Customers', exact: true })
        .click()
      await expect(page.getByRole('heading', { name: 'Customers' })).toBeVisible()

      await page.getByPlaceholder('Search customers…').fill(customerId)
      const customerRow = page.getByRole('row').filter({ hasText: customerId })
      await expect(customerRow).toBeVisible()
      await customerRow.click()

      await expect(page.getByRole('heading', { name: 'Customer Details' })).toBeVisible()
      await page
        .getByRole('main')
        .getByRole('button', { name: 'Activities', exact: true })
        .click()

      // Navigating away unmounted CustomerDetailsPage and discarded its local
      // `recorded` state. This row can now exist only if the API returned it.
      const persistedRow = page.getByRole('row').filter({ hasText: interactionNotes })
      await expect(persistedRow).toBeVisible()
      await expect(persistedRow).toContainText('EMAIL')
    })
  } finally {
    await testInfo.attach('api-journey.json', {
      body: JSON.stringify(apiCalls, null, 2),
      contentType: 'application/json',
    })
    if (browserErrors.length > 0) {
      await testInfo.attach('browser-errors.txt', {
        body: browserErrors.join('\n'),
        contentType: 'text/plain',
      })
    }
  }

  // Assertions, not just attachments. Collected evidence nobody checks lets the
  // journey stay green through an uncaught error, a failed request, or a
  // correlation id the backend quietly stopped echoing.
  expect(browserErrors, 'no console, page, or network errors during the journey').toEqual([])

  expect(
    apiCalls.filter(
      (call) =>
        call.requestCorrelationId && call.responseCorrelationId !== call.requestCorrelationId,
    ),
    'every API response echoes the correlation id its request carried',
  ).toEqual([])

  // The read-back step proves persistence only if it really went to the server.
  expect(
    apiCalls.some(
      (call) =>
        call.method === 'GET' &&
        call.path === `/api/customers/${encodeURIComponent(customerId)}/interactions`,
    ),
    'the interaction history was read from the nested customer endpoint',
  ).toBe(true)
})
