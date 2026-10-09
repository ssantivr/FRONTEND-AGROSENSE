# AgroSense Frontend

Web client for AgroSense, built with Vite, React and TypeScript. The interface is in Spanish; the code is in English.

## Commands

```bash
npm install
npm run dev      # development server
npm run build    # type-check and production build
npm test         # unit tests (Vitest + Testing Library)
```

## Data source

- Without `VITE_API_URL`, the app runs in demo mode with in-memory sample data
  (sign in with `demo@agrosense.co` / `agrosense`).
- With `VITE_API_URL` set (see `.env.example`), it calls the backend using HTTP Basic.
  The REST routes it expects are listed in `src/data/dataSource.ts`; the backend does not
  implement them yet.

## Note on Rollup

`package.json` overrides `rollup` with `@rollup/wasm-node` so the build works on machines
where the native Rollup binary cannot be loaded.
