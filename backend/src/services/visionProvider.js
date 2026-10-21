export const SYSTEM_PROMPT = `Answer in at most 2 short sentences in the requested language. Mention the most important hazard first. Give direction (left, ahead, right) and a rough distance with "about". If unsure say you are not sure. Never claim that stairs, drops, or open drains are absent.`;

/**
 * TODO: Implement real vision provider (e.g., Gemini API) here in the future.
 * Do not invent API URLs, model names, or keys.
 */
export async function describeImage({ imageBuffer, mimeType, language, question }) {
    const provider = process.env.VISION_PROVIDER || 'mock';
    if (provider === 'mock') {
        if (language === 'hi') {
            return 'सामने एक कुर्सी है, करीब 2 मीटर की दूरी पर।';
        }
        return 'Chair ahead, about 2 meters away.';
    }
    throw new Error(`Unsupported vision provider: ${provider}`);
}
