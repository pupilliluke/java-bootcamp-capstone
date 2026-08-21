# Frontend — Northstar CRM (skeleton)

Bare-bones React + **TypeScript** (Vite) UI for the capstone demo journey:
**search → profile → record interaction.**

Same scaffolding and idioms as the bootcamp **Lab 35** `crm-ui` reference
(TypeScript, `api/` client + `ApiError` + hook + split components,
`VITE_API_BASE_URL`, Vitest) — just wired to the capstone's search → profile →
interaction journey.

## Run it

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173 and search `CUS-1001`.

The dev server proxies `/api/*` to the Spring Boot backend on
`http://localhost:8080` (see `vite.config.ts`), so run the backend too for the
customer lookup to return data. To hit a backend directly instead (the way the
lab does), copy `.env.example` to `.env.local` and set `VITE_API_BASE_URL`.

## Commands

```bash
npm run dev      # dev server
npm run build    # tsc -b && vite build (typecheck + production build)
npm test         # vitest
```

## Structure

```
src/
  api/
    http.ts          # one fetch wrapper: headers, base URL, maps errors -> ApiError
    ApiError.ts      # error type with a "kind" (network/http/abort/parse)
    customers.ts     # customersApi.list() / .get(id)  -> Lab 49 contract
    customers.test.ts
    interactions.ts  # interactionsApi.create(id, body)  (endpoint WIP on backend)
  hooks/
    useCustomerSearch.ts   # search(id) -> { customer, loading, error }
  components/
    SearchBar.tsx
    CustomerProfile.tsx
    InteractionForm.tsx
    InteractionTimeline.tsx
  types/customer.ts  # Customer / CustomerStatus / Channel / Interaction
  App.tsx            # composition root wiring hook + api + components
```

## What works today

- Search a customer by ID → `GET /api/customers/{id}`
- Show the profile (name, status, email, phone)
- Add an interaction (channel + summary) with a local timeline

## Known gap

`interactionsApi.create` POSTs to `/api/customers/{id}/interactions`, but the
backend `InteractionController` is empty, so that call 404s for now. The UI
handles it gracefully and still shows the entry locally, flagged as
"not persisted." Wire it up once the backend interaction endpoint lands.

## Stack

Vite + React 18 + TypeScript, Vitest. No extra UI libraries — kept minimal on
purpose. Mirrors the Lab 33–36 `crm-ui` reference conventions.
