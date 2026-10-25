export const SYSTEM_PROMPT = `Answer in at most 2 short sentences in the requested language. Mention the most important hazard first. Give direction (left, ahead, right) and a rough distance with "about". If unsure say you are not sure. Never claim that stairs, drops, or open drains are absent.`;

let dailyCallCount = 0;
let lastResetDate = new Date().toDateString();

function checkDailyCap() {
    const today = new Date().toDateString();
    if (today !== lastResetDate) {
        lastResetDate = today;
        dailyCallCount = 0;
    }
    const maxCalls = parseInt(process.env.DAILY_PROVIDER_CALLS_MAX || '500', 10);
    if (dailyCallCount >= maxCalls) {
        throw new Error('SERVICE_UNAVAILABLE_DAILY_CAP');
    }
}

export async function describeImage({ imageBuffer, mimeType, language, question }) {
    const provider = process.env.VISION_PROVIDER || 'mock';

    if (provider === 'mock') {
        if (language === 'hi') {
            return 'सामने एक कुर्सी है, करीब 2 मीटर की दूरी पर।';
        }
        return 'Chair ahead, about 2 meters away.';
    }

    if (provider === 'gemini') {
        checkDailyCap();

        const apiKey = process.env.VISION_API_KEY;
        const model = process.env.VISION_MODEL;
        if (!apiKey) {
            throw new Error('MISSING_API_KEY');
        }
        if (!model) {
            console.error('VISION_MODEL missing');
            throw new Error('VISION_MODEL_MISSING');
        }

        const langPrompt = language === 'hi'
            ? '\nIMPORTANT: Respond ONLY in Hindi language (हिंदी में उत्तर दें).'
            : '\nIMPORTANT: Respond ONLY in English language.';

        const userPrompt = question ? `\nUser question: ${question}` : '';
        const prompt = SYSTEM_PROMPT + langPrompt + userPrompt;

        const base64Data = imageBuffer.toString('base64');

        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), 15000); // 15s timeout

        try {
            const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`;
            const response = await fetch(url, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'x-goog-api-key': apiKey
                },
                signal: controller.signal,
                body: JSON.stringify({
                    contents: [{
                        parts: [
                            { text: prompt },
                            { inline_data: { mime_type: mimeType, data: base64Data } }
                        ]
                    }],
                    generationConfig: {
                        maxOutputTokens: 400,
                        temperature: 0.2
                    }
                })
            });

            clearTimeout(timeoutId);

            if (!response.ok) {
                console.error(response.status);
                throw new Error('PROVIDER_ERROR');
            }

            const data = await response.json();
            const text = data?.candidates?.[0]?.content?.parts?.[0]?.text;
            if (!text) {
                throw new Error('INVALID_PROVIDER_RESPONSE');
            }

            dailyCallCount++;
            return text.trim();
        } catch (error) {
            clearTimeout(timeoutId);
            if (error.message === 'SERVICE_UNAVAILABLE_DAILY_CAP') {
                throw error;
            }
            if (error.name === 'AbortError') {
                throw new Error('PROVIDER_TIMEOUT');
            }
            throw error;
        }
    }

    throw new Error(`Unsupported vision provider: ${provider}`);
}
