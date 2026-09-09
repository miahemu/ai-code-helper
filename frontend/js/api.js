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
