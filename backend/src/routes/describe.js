import express from 'express';
import multer from 'multer';
import rateLimit from 'express-rate-limit';
import { describeImage, SYSTEM_PROMPT } from '../services/visionProvider.js';

const router = express.Router();

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

// Rate limiters per X-Device-Id
const minuteLimiter = rateLimit({
    windowMs: 60 * 1000,
    max: 6,
    keyGenerator: (req) => req.headers['x-device-id'] || 'unknown',
    handler: (req, res) => {
        res.status(429).json({ error: 'Rate limit exceeded (per minute)' });
    },
    standardHeaders: true,
    legacyHeaders: false,
});

const dayLimiter = rateLimit({
    windowMs: 24 * 60 * 60 * 1000,
    max: 30,
    keyGenerator: (req) => req.headers['x-device-id'] || 'unknown',
    handler: (req, res) => {
        res.status(429).json({ error: 'Rate limit exceeded (per day)' });
    },
    standardHeaders: true,
    legacyHeaders: false,
});

router.post('/describe', minuteLimiter, dayLimiter, (req, res) => {
    const deviceId = req.headers['x-device-id'];
    if (!deviceId || typeof deviceId !== 'string' || deviceId.length < 8 || deviceId.length > 64) {
        return res.status(400).json({ error: 'Missing or invalid X-Device-Id header (must be 8-64 chars)' });
    }

    upload(req, res, async (err) => {
        if (err) {
            if (err.code === 'LIMIT_FILE_SIZE') {
                return res.status(400).json({ error: 'File too large (max 2 MB)' });
            }
            if (err.message === 'INVALID_FILE_TYPE') {
                return res.status(400).json({ error: 'Invalid file type (only JPEG and PNG allowed)' });
            }
            return res.status(400).json({ error: err.message });
        }

        if (!req.file) {
            return res.status(400).json({ error: 'Image file is required' });
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
            res.status(500).json({ error: 'Internal server error' });
        }
    });
});

export { router, SYSTEM_PROMPT };
