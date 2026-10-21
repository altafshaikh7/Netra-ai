import express from 'express';
import helmet from 'helmet';
import dotenv from 'dotenv';
import { router as describeRouter } from './routes/describe.js';

dotenv.config();

const app = express();
app.use(helmet());
app.use(express.json());

app.get('/health', (req, res) => {
    res.json({ ok: true });
});

app.use('/', describeRouter);

const PORT = process.env.PORT || 3000;

if (process.env.NODE_ENV !== 'test') {
    app.listen(PORT, () => {
        console.log(`Netra backend listening on port ${PORT}`);
    });
}

export default app;
