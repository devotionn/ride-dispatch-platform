# Passenger H5

Phase 2 passenger-facing H5 for the ride dispatch platform.

## Routes

- `/ride` — public booking entry.
- `/ride/d/:driverShortCode` — driver-directed QR booking entry.
- `/order/:orderNo` — passenger order status page.

## Local development

Requirements: Node.js 22.12+ and the backend running on port 8080.

```bash
cd passenger-h5
npm install
npm run dev
```

Vite proxies `/api` to `http://localhost:8080` in development. Production can set `VITE_API_BASE_URL` when the API is hosted on a different origin.

## Current scope

The first functional slice uses browser geolocation plus manual address/coordinate entry so the end-to-end booking flow is usable without a map-provider credential. Map search / point selection will be added behind a provider adapter in a later slice; the H5 does not pretend the raw browser location is a full map/search implementation.
