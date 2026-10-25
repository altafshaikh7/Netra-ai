process.env.NODE_ENV = 'test';

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

function closeServer(server) {
    return new Promise((resolve) => {
        if (server.closeAllConnections) {
            server.closeAllConnections();
        }
        server.close(resolve);
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
        await closeServer(server);
    }
});

test('POST /describe missing X-Device-Id returns 400', async () => {
    const { server, port } = await startTestServer();
    try {
        const res = await fetch(`http://localhost:${port}/describe`, {
            method: 'POST',
            headers: { 'x-forwarded-for': '10.1.0.1' }
        });
        assert.strictEqual(res.status, 400);
    } finally {
        await closeServer(server);
    }
});

test('POST /describe missing image returns 400', async () => {
    const { server, port } = await startTestServer();
    try {
        const res = await fetch(`http://localhost:${port}/describe`, {
            method: 'POST',
            headers: { 'x-device-id': 'device-test-12345', 'x-forwarded-for': '10.2.0.1' }
        });
        assert.strictEqual(res.status, 400);
    } finally {
        await closeServer(server);
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
                'x-forwarded-for': '10.3.0.1',
                'Content-Type': `multipart/form-data; boundary=${boundary}`
            },
            body
        });
        assert.strictEqual(res.status, 400);
    } finally {
        await closeServer(server);
    }
});

test('POST /describe oversize image (>2MB) returns 400 and File too large', async () => {
    const { server, port } = await startTestServer();
    try {
        const boundary = '----WebKitFormBoundary7MA4YWxkTrZu0gW';
        const largeBuffer = Buffer.alloc(2.1 * 1024 * 1024, 0xFF);
        const header = Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="image"; filename="large.jpg"\r\nContent-Type: image/jpeg\r\n\r\n`);
        const footer = Buffer.from(`\r\n--${boundary}--\r\n`);
        const body = Buffer.concat([header, largeBuffer, footer]);

        const res = await fetch(`http://localhost:${port}/describe`, {
            method: 'POST',
            headers: {
                'x-device-id': 'device-oversize-12345',
                'x-forwarded-for': '10.4.0.1',
                'Content-Type': `multipart/form-data; boundary=${boundary}`
            },
            body
        });
        assert.strictEqual(res.status, 400);
        const json = await res.json();
        assert.strictEqual(json.error, 'File too large');
    } finally {
        await closeServer(server);
    }
});

test('POST /describe text file renamed to .jpg (invalid magic bytes) returns 400', async () => {
    const { server, port } = await startTestServer();
    try {
        const boundary = '----WebKitFormBoundary7MA4YWxkTrZu0gW';
        const fakeTextBuffer = Buffer.from('This is just text, not a jpeg or png');
        const header = Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="image"; filename="fake.jpg"\r\nContent-Type: image/jpeg\r\n\r\n`);
        const footer = Buffer.from(`\r\n--${boundary}--\r\n`);
        const body = Buffer.concat([header, fakeTextBuffer, footer]);

        const res = await fetch(`http://localhost:${port}/describe`, {
            method: 'POST',
            headers: {
                'x-device-id': 'device-fake-jpg-12345',
                'x-forwarded-for': '10.5.0.1',
                'Content-Type': `multipart/form-data; boundary=${boundary}`
            },
            body
        });
        assert.strictEqual(res.status, 400);
        const json = await res.json();
        assert.strictEqual(json.error.includes('Invalid file signature'), true);
    } finally {
        await closeServer(server);
    }
});

test('POST /describe successful mock describe', async () => {
    const { server, port } = await startTestServer();
    try {
        const boundary = '----WebKitFormBoundary7MA4YWxkTrZu0gW';
        const fakeJpeg = Buffer.from([0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46]);
        const header = Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="image"; filename="test.jpg"\r\nContent-Type: image/jpeg\r\n\r\n`);
        const footer = Buffer.from(`\r\n--${boundary}--\r\n`);
        const body = Buffer.concat([header, fakeJpeg, footer]);

        const res = await fetch(`http://localhost:${port}/describe`, {
            method: 'POST',
            headers: {
                'x-device-id': 'device-success-12345',
                'x-forwarded-for': '10.8.0.1',
                'Content-Type': `multipart/form-data; boundary=${boundary}`
            },
            body
        });
        const json = await res.json();
        assert.strictEqual(res.status, 200);
        assert.strictEqual(typeof json.text, 'string');
    } finally {
        await closeServer(server);
    }
});

test('POST /describe same device id from different IPs is still limited', async () => {
    const { server, port } = await startTestServer();
    try {
        const boundary = '----WebKitFormBoundary7MA4YWxkTrZu0gW';
        const fakeJpeg = Buffer.from([0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46]);
        const header = Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="image"; filename="test.jpg"\r\nContent-Type: image/jpeg\r\n\r\n`);
        const footer = Buffer.from(`\r\n--${boundary}--\r\n`);
        const body = Buffer.concat([header, fakeJpeg, footer]);

        const sharedDeviceId = 'device-shared-limit-11111';
        let lastStatus = 200;
        for (let i = 0; i < 7; i++) {
            const res = await fetch(`http://localhost:${port}/describe`, {
                method: 'POST',
                headers: {
                    'x-device-id': sharedDeviceId,
                    'x-forwarded-for': `10.6.0.${i + 1}`,
                    'Content-Type': `multipart/form-data; boundary=${boundary}`
                },
                body
            });
            lastStatus = res.status;
        }
        assert.strictEqual(lastStatus, 429);
    } finally {
        await closeServer(server);
    }
});

test('POST /describe spoofed x-forwarded-for does NOT bypass IP limit when TRUST_PROXY is false', async () => {
    const { server, port } = await startTestServer();
    try {
        const boundary = '----WebKitFormBoundary7MA4YWxkTrZu0gW';
        const fakeJpeg = Buffer.from([0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46]);
        const header = Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="image"; filename="test.jpg"\r\nContent-Type: image/jpeg\r\n\r\n`);
        const footer = Buffer.from(`\r\n--${boundary}--\r\n`);
        const body = Buffer.concat([header, fakeJpeg, footer]);

        // When trust proxy is false (or default test server), Express resolves req.ip to connection IP (127.0.0.1) ignoring x-forwarded-for
        // So spoofing different x-forwarded-for headers will still accumulate on 127.0.0.1 and trigger 429 after 60 requests
        let hit429 = false;
        for (let i = 0; i < 65; i++) {
            const res = await fetch(`http://localhost:${port}/describe`, {
                method: 'POST',
                headers: {
                    'x-device-id': `device-spoof-${i}-99999`,
                    'x-forwarded-for': `203.0.113.${i + 1}`,
                    'Content-Type': `multipart/form-data; boundary=${boundary}`
                },
                body
            });
            if (res.status === 429) {
                hit429 = true;
                break;
            }
        }
        assert.strictEqual(hit429, true);
    } finally {
        await closeServer(server);
    }
});
