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
            method: 'POST'
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
            headers: { 'x-device-id': 'device-test-12345' }
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
        // Create 2.1 MB buffer
        const largeBuffer = Buffer.alloc(2.1 * 1024 * 1024, 0xFF);
        const header = Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="image"; filename="large.jpg"\r\nContent-Type: image/jpeg\r\n\r\n`);
        const footer = Buffer.from(`\r\n--${boundary}--\r\n`);
        const body = Buffer.concat([header, largeBuffer, footer]);

        const res = await fetch(`http://localhost:${port}/describe`, {
            method: 'POST',
            headers: {
                'x-device-id': 'device-oversize-12345',
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

test('POST /describe rate limit per minute with valid multipart closing boundary', async () => {
    const { server, port } = await startTestServer();
    try {
        const boundary = '----WebKitFormBoundary7MA4YWxkTrZu0gW';
        const fakeJpeg = Buffer.from([0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46]);
        const header = Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="image"; filename="test.jpg"\r\nContent-Type: image/jpeg\r\n\r\n`);
        const footer = Buffer.from(`\r\n--${boundary}--\r\n`);
        const body = Buffer.concat([header, fakeJpeg, footer]);

        const deviceId = 'device-rate-limit-67890';
        let lastStatus = 200;
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
