const CONVERSATIONS_KEY = 'diving.conversations.v1';
const ACTIVE_CONVERSATION_KEY = 'diving.active-conversation.v1';

export function loadConversations() {
    try {
        const value = JSON.parse(window.localStorage.getItem(CONVERSATIONS_KEY) || '[]');
        return Array.isArray(value) ? value.filter(isConversation) : [];
    } catch (error) {
        return [];
    }
}

export function saveConversations(conversations) {
    try {
        window.localStorage.setItem(CONVERSATIONS_KEY, JSON.stringify(conversations));
    } catch (error) {
        // 浏览器存储空间不足时，当前页面仍可以继续临时使用。
    }
}

export function loadActiveConversationId() {
    try {
        return window.localStorage.getItem(ACTIVE_CONVERSATION_KEY);
    } catch (error) {
        return null;
    }
}

export function saveActiveConversationId(conversationId) {
    try {
        window.localStorage.setItem(ACTIVE_CONVERSATION_KEY, conversationId);
    } catch (error) {
        // 当前会话 ID 无法持久化时，不影响本次对话。
    }
}

function isConversation(value) {
    return value && typeof value.id === 'string'
            && typeof value.title === 'string'
            && Array.isArray(value.messages);
}
