# Netra AI Backend

Node.js + Express backend providing descriptive vision assistance for the Netra AI app.

## Environment Variables
- `PORT`: Server port (default 3000)
- `VISION_PROVIDER`: Vision provider name (default `mock`)
- `IP_RATE_LIMIT_DAILY`: Daily per-IP request rate limit (default 60)
- `TRUST_PROXY`: Configures Express `trust proxy` setting (e.g., `true`, `false`, or proxy hop count/IP string, default `false`)
