import test from 'node:test';
import assert from 'node:assert';
import app from '../src/server.js';
import http from 'node:http';

function startTestServer() {
    return new Promise((resolve) => {
        const server = http.createServer(app);
        server.listen(0, () => {
            const port = server.address().port;
            resolve({ server, port });
        });
    });
}

test('GET /health returns ok:true', async () => {
    const { server, port } = await startTestServer();
    try {
        const res = await fetch(`http://localhost:${port}/health`);
        const json = await res.json();
        assert.strictEqual(res.status, 200);
        assert.deepStrictEqual(json, { ok: true });
    } finally {
        server.close();
    }
});

test('POST /describe missing X-Device-Id returns 400', async () => {
    const { server, port } = await startTestServer();
    try {
        const res = await fetch(`http://localhost:${port}/describe`, {
            method: 'POST'
        });
        assert.strictEqual(res.status, 400);
    } finally {
        server.close();
    }
});

test('POST /describe missing image returns 400', async () => {
    const { server, port } = await startTestServer();
    try {
        const res = await fetch(`http://localhost:${port}/describe`, {
            method: 'POST',
            headers: { 'x-device-id': 'device-test-12345' }
        });
        assert.strictEqual(res.status, 400);
    } finally {
        server.close();
    }
});

test('POST /describe invalid file type returns 400', async () => {
    const { server, port } = await startTestServer();
    try {
        const boundary = '----WebKitFormBoundary7MA4YWxkTrZu0gW';
        const body = Buffer.from(
            `--${boundary}\r\n` +
            `Content-Disposition: form-data; name="image"; filename="test.txt"\r\n` +
            `Content-Type: text/plain\r\n\r\n` +
            `hello text\r\n` +
            `--${boundary}--\r\n`
        );
        const res = await fetch(`http://localhost:${port}/describe`, {
            method: 'POST',
            headers: {
                'x-device-id': 'device-test-12345',
                'Content-Type': `multipart/form-data; boundary=${boundary}`
            },
            body
        });
        assert.strictEqual(res.status, 400);
    } finally {
        server.close();
    }
});

test('POST /describe rate limit per minute', async () => {
    const { server, port } = await startTestServer();
    try {
        const boundary = '----WebKitFormBoundary7MA4YWxkTrZu0gW';
        const fakeJpeg = Buffer.from([0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46]);
        const body = Buffer.concat([
            Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="image"; filename="test.jpg"\r\nContent-Type: image/jpeg\r\n\r\n`),
            fakeJpeg,
            Buffer.from(`\r\n--${boundary}--\r\n`)
        ]);

        const deviceId = 'device-rate-limit-67890';
        let lastStatus = 200;
        // Limit is 6 requests per minute
        for (let i = 0; i < 7; i++) {
            const res = await fetch(`http://localhost:${port}/describe`, {
                method: 'POST',
                headers: {
                    'x-device-id': deviceId,
                    'Content-Type': `multipart/form-data; boundary=${boundary}`
                },
                body
            });
            lastStatus = res.status;
        }
        assert.strictEqual(lastStatus, 429);
    } finally {
        server.close();
    }
});

test('POST /describe successful mock describe', async () => {
    const { server, port } = await startTestServer();
    try {
        const boundary = '----WebKitFormBoundary7MA4YWxkTrZu0gW';
        const fakeJpeg = Buffer.from([0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46]);
        const body = Buffer.concat([
            Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="image"; filename="test.jpg"\r\nContent-Type: image/jpeg\r\n\r\n`),
            fakeJpeg,
            Buffer.from(`\r\n--${boundary}\r\nContent-Disposition: form-data; name="language"\r\n\r\nen\r\n--${boundary}--\r\n`)
        ]);

        const res = await fetch(`http://localhost:${port}/describe`, {
            method: 'POST',
            headers: {
                'x-device-id': 'device-success-12345',
                'Content-Type': `multipart/form-data; boundary=${boundary}`
            },
            body
        });
        const json = await res.json();
        assert.strictEqual(res.status, 200);
        assert.strictEqual(typeof json.text, 'string');
    } finally {
        server.close();
    }
});
