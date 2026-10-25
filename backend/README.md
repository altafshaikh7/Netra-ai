# Netra AI Backend

Node.js + Express backend providing descriptive vision assistance for the Netra AI app.

## Environment Variables
Set these variables in `.env` (copy from `.env.example`):
- `PORT`: Server port (default `3000`)
- `VISION_PROVIDER`: Vision provider name (`mock` or `gemini`, default `mock`)
- `VISION_API_KEY`: API key for Gemini vision provider (leave empty for mock)
- `VISION_MODEL`: Model ID for Gemini (default `gemini-1.5-flash`)
- `DAILY_PROVIDER_CALLS_MAX`: Global daily call cap to prevent surprise bills (default `500`)
- `IP_RATE_LIMIT_DAILY`: Daily per-IP request rate limit (default `60`)
- `TRUST_PROXY`: Configures Express `trust proxy` setting (default `false`)
