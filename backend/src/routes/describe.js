import express from 'express';
import multer from 'multer';
import rateLimit from 'express-rate-limit';
import { describeImage, SYSTEM_PROMPT } from '../services/visionProvider.js';

const router = express.Router();

function isValidImageBuffer(buffer) {
    if (!buffer || buffer.length < 8) return false;
    const isJpeg = buffer[0] === 0xFF && buffer[1] === 0xD8 && buffer[2] === 0xFF;
    const isPng = buffer[0] === 0x89 && buffer[1] === 0x50 && buffer[2] === 0x4E && buffer[3] === 0x47 &&
                  buffer[4] === 0x0D && buffer[5] === 0x0A && buffer[6] === 0x1A && buffer[7] === 0x0A;
    return isJpeg || isPng;
}

// Memory storage only: never write images to disk or logs
const upload = multer({
    storage: multer.memoryStorage(),
    limits: {
        fileSize: 2 * 1024 * 1024 // 2 MB max
    },
    fileFilter: (req, file, cb) => {
        if (file.mimetype === 'image/jpeg' || file.mimetype === 'image/png') {
            cb(null, true);
        } else {
            cb(new Error('INVALID_FILE_TYPE'), false);
        }
    }
}).single('image');

// Per-device rate limiters
const minuteLimiter = rateLimit({
    windowMs: 60 * 1000,
    max: 6,
    keyGenerator: (req) => 'min:' + (req.headers['x-device-id'] || 'unknown'),
    handler: (req, res) => {
        res.status(429).json({ error: 'Rate limit exceeded (per minute)' });
    },
    standardHeaders: true,
    legacyHeaders: false,
});

const dayLimiter = rateLimit({
    windowMs: 24 * 60 * 60 * 1000,
    max: 30,
    keyGenerator: (req) => 'day:' + (req.headers['x-device-id'] || 'unknown'),
    handler: (req, res) => {
        res.status(429).json({ error: 'Rate limit exceeded (per day)' });
    },
    standardHeaders: true,
    legacyHeaders: false,
});

// Per-IP rate limiter keying strictly on req.ip ONLY
const ipDailyLimit = parseInt(process.env.IP_RATE_LIMIT_DAILY || '60', 10);
const ipLimiter = rateLimit({
    windowMs: 24 * 60 * 60 * 1000,
    max: ipDailyLimit,
    keyGenerator: (req) => 'ip:' + (req.ip || 'unknown'),
    handler: (req, res) => {
        res.status(429).json({ error: 'Rate limit exceeded (per IP daily)' });
    },
    standardHeaders: true,
    legacyHeaders: false,
});

router.post('/describe', ipLimiter, minuteLimiter, dayLimiter, (req, res) => {
    const deviceId = req.headers['x-device-id'];
    if (!deviceId || typeof deviceId !== 'string' || deviceId.length < 8 || deviceId.length > 64) {
        return res.status(400).json({ error: 'Missing or invalid X-Device-Id header (must be 8-64 chars)' });
    }

    upload(req, res, async (err) => {
        if (err) {
            if (err.code === 'LIMIT_FILE_SIZE') {
                return res.status(400).json({ error: 'File too large' });
            }
            if (err.message === 'INVALID_FILE_TYPE') {
                return res.status(400).json({ error: 'Invalid file type (only JPEG and PNG allowed)' });
            }
            return res.status(400).json({ error: err.message });
        }

        if (!req.file) {
            return res.status(400).json({ error: 'Image file is required' });
        }

        // Validate real file signature (magic bytes)
        if (!isValidImageBuffer(req.file.buffer)) {
            return res.status(400).json({ error: 'Invalid file signature (magic bytes do not match JPEG or PNG)' });
        }

        const language = req.body.language === 'hi' ? 'hi' : 'en';
        const question = req.body.question ? String(req.body.question).slice(0, 200) : undefined;

        try {
            const text = await describeImage({
                imageBuffer: req.file.buffer,
                mimeType: req.file.mimetype,
                language,
                question
            });

            res.json({ text, language });
        } catch (e) {
            if (e.message && e.message.startsWith('SERVICE_UNAVAILABLE')) {
                return res.status(503).json({ error: 'Service temporarily unavailable. Daily call cap reached.' });
            }
            res.status(502).json({ error: 'Vision provider error' });
        }
    });
});

export { router, SYSTEM_PROMPT };
