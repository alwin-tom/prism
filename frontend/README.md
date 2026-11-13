# WebAux frontend

React 19, TypeScript, Vite, Tailwind, React Router, TanStack Query, React Hook Form, and Zod.

```bash
npm install
npm run dev
npm test
npm run build
```

Routes:

- `/login`, `/register` — customer OTP
- `/staff` — admin and reviewer password
- `/app` — customer sites, editor, payment, RSVP
- `/admin` — review queue and catalogue
- `/{slug}` — published event site

Authentication uses the `prism_access`, `prism_refresh`, and `prism_csrf` cookies. Tokens are not written to localStorage. Mutations send `X-CSRF-Token`.

`npm run dev` proxies `/api` and `/render` to port 8080. The production image is Nginx: API calls stay on the same origin, and a single-segment public slug is proxied to the backend HTML renderer.

Template pictures in `public/placeholders/` are drawn marks, not photographs. Replace them with real event media before treating a site as finished. The ten seeded templates use sample names and dates; those strings are demo content.
