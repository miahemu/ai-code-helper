/**
 * 统一处理后端接口响应
 *
 * @param {string} url 接口地址
 * @param {RequestInit} options 请求参数
 * @returns {Promise<*>} 接口数据
 */
export async function request(url, options = {}) {
    const response = await fetch(url, options);
    let result;
    try {
        result = await response.json();
    } catch (error) {
        throw new Error(response.ok ? '服务返回格式不正确' : `请求失败（${response.status}）`);
    }
    if (!response.ok || result.code !== 0) {
        throw new Error(result.message || `请求失败（${response.status}）`);
    }
    return result.data;
}

/**
 * 发起 SSE 请求并逐条处理服务端返回的 JSON 事件
 *
 * @param {string} url 接口地址
 * @param {RequestInit} options 请求参数
 * @param {(event: Object) => void} onMessage SSE 事件处理器
 */
export async function streamRequest(url, options = {}, onMessage) {
    const response = await fetch(url, options);
    if (!response.ok) {
        throw new Error(`请求失败（${response.status}）`);
    }
    if (!response.body) {
        throw new Error('当前浏览器不支持流式响应');
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    while (true) {
        const {value, done} = await reader.read();
        buffer += decoder.decode(value || new Uint8Array(), {stream: !done});
        const events = buffer.split(/\r?\n\r?\n/);
        buffer = events.pop() || '';
        events.forEach(eventText => {
            const data = eventText.split(/\r?\n/)
                    .filter(line => line.startsWith('data:'))
                    .map(line => line.substring(5).trimStart())
                    .join('\n');
            if (data) {
                onMessage(JSON.parse(data));
            }
        });
        if (done) {
            break;
        }
    }
}
