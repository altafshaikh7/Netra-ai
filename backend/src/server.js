import express from 'express';
import helmet from 'helmet';
import dotenv from 'dotenv';
import { fileURLToPath } from 'url';
import { router as describeRouter } from './routes/describe.js';

dotenv.config();

const app = express();

const trustProxyEnv = process.env.TRUST_PROXY;
if (trustProxyEnv) {
    if (trustProxyEnv.toLowerCase() === 'true') {
        app.set('trust proxy', true);
    } else if (trustProxyEnv.toLowerCase() === 'false') {
        app.set('trust proxy', false);
    } else {
        app.set('trust proxy', trustProxyEnv);
    }
} else if (process.env.NODE_ENV === 'test') {
    app.set('trust proxy', true);
} else {
    app.set('trust proxy', false);
}

app.use(helmet());
app.use(express.json());

app.get('/health', (req, res) => {
    res.json({ ok: true });
});

app.use('/', describeRouter);

const PORT = process.env.PORT || 3000;

const __filename = fileURLToPath(import.meta.url);
const isMain = process.argv[1] === __filename;

if (isMain && process.env.NODE_ENV !== 'test') {
    app.listen(PORT, () => {
        console.log(`Netra backend listening on port ${PORT}`);
    });
}

export default app;
