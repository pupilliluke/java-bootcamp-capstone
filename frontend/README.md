# Frontend — Neural

React + **TypeScript** (Vite) admin UI for the capstone, modeled on the
"Customer Management Screens" mockup. Plain CSS, no UI/router/chart libraries —
navigation is plain React state and the charts are hand-rolled SVG, to stay
inside the bootcamp toolset (Lab 33–36 / Lab 50 idioms).

## Run it

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173. The dev server proxies `/api/*` to the Spring Boot
backend on `http://localhost:8080` (see `vite.config.ts`), so run the backend
too for the real screens to show live data.

## Commands

```bash
npm run dev      # dev server
npm run build    # tsc -b && vite build (typecheck + production build)
npm test         # vitest
npm run test:ci  # one non-watch Vitest run
npm run test:e2e # Playwright journey; requires PostgreSQL and Kafka
```

## Screens

| # | Screen | Data source |
|---|--------|-------------|
| — | Dashboard | **Real** — counts derived from `GET /api/customers` |
| 1 | Customer List (search, status filter, pagination) | **Real** — `GET /api/customers` |
| 2 | Customer Details (Overview tab) | **Real** — `GET /api/customers/{id}` |
| 2 | Details → Activities tab (record interaction) | **Real** — `POST /api/interactions` |
| 2 | Details → Activities tab (interaction history) | **Real** — `GET /api/customers/{id}/interactions` |
| 3 | Add Customer | **Real** — `POST /api/customers` |
| 3 | Edit Customer | **Real** — `PUT /api/customers/{id}` |
| 4 | Contacts | ⚠️ **Demo data** — no backend endpoint |
| 5 | Global Activities screen | ⚠️ **Demo data** — no global activity endpoint |
| 6 | Reports (KPIs + charts) | ⚠️ **Demo data** — no aggregation endpoint |

Every screen driven by mock data shows a visible **◇ Demo data** tag so
fabricated numbers are never mistaken for real, persisted data. All mock data
lives in one file: `src/mock/mockData.ts` — delete it (and the screens that
import it) once real endpoints exist.

## Structure

```
src/
  api/            # http wrapper + ApiError + customersApi / interactionsApi
  hooks/          # useCustomers (list), useCustomer (by id)
  components/     # Sidebar, StatusBadge, Pagination, DonutChart, BarChart, DemoTag, icons
  pages/          # one file per screen (Dashboard, CustomerList, CustomerDetails, …)
  mock/mockData.ts# ⚠️ all hardcoded demo data, isolated here
  types/customer.ts
  nav.ts          # Page union + Navigate type (state-based navigation)
  App.tsx         # composition root: sidebar + page switch
```

## Backend contract used

- `GET /api/customers` → `CustomerResponseDTO[]`
- `GET /api/customers/{customerId}` → `CustomerResponseDTO`
- `GET /api/customers/{customerId}/interactions` → `InteractionResponseDTO[]`
- `POST /api/customers` ← `CustomerRequestDTO { customerId, fullName, email, phone?, status }`
- `PUT /api/customers/{customerId}` ← `CustomerUpdateDTO`
- `POST /api/interactions` ← `CreateInteractionRequest { customerId, channel, notes }` (202, saved then published)

## Stack

Vite + React 18 + TypeScript, Vitest, and Playwright.
