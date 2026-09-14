import {elements} from './elements.js?v=20260914-2';
import {renderMarkdown} from './markdown.js?v=20260911-3';
import {streamChat} from './route.js?v=20260914-2';
import {createLogoImage, openContentViewer, showToast} from './ui.js?v=20260914-2';

const DEFAULT_QUESTION_PLACEHOLDER = '向 Diving 提问，Enter 发送，Shift + Enter 换行';
const FOLLOW_UP_QUESTION_PLACEHOLDER = '可继续输入补充问题，将在当前回答结束后发送';

let asking = false;
let conversationId = createConversationId();
let conversationVersion = 0;
let activeRequest = null;
const pendingQuestions = [];

function createConversationId() {
    if (window.crypto && typeof window.crypto.randomUUID === 'function') {
        return window.crypto.randomUUID();
    }
    return `conversation-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function createAssistantAvatar() {
    const avatar = document.createElement('div');
    avatar.className = 'avatar';
    avatar.appendChild(createLogoImage());
    return avatar;
}

function renderWelcome() {
    elements.chatList.replaceChildren();
    const welcome = document.createElement('div');
    welcome.className = 'welcome';

    const content = document.createElement('div');
    content.className = 'welcome-inner';
    const icon = document.createElement('div');
    icon.className = 'welcome-icon';
    icon.appendChild(createLogoImage());
    const title = document.createElement('h2');
    title.textContent = '你好，我是 Diving';
    const description = document.createElement('p');
    description.textContent = '你可以直接提问。当问题需要内部资料时，我会自动检索右侧知识库并标注引用。';
    const suggestions = document.createElement('div');
    suggestions.className = 'suggestions';

    ['介绍一下你能做什么', '总结知识库中的核心内容', '搜索最新的 AI 行业动态'].forEach(text => {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'suggestion-button';
        button.textContent = text;
        button.addEventListener('click', () => {
            elements.question.value = text;
            updateQuestionCount();
            elements.question.focus();
        });
        suggestions.appendChild(button);
    });

    content.append(icon, title, description, suggestions);
    welcome.appendChild(content);
    elements.chatList.appendChild(welcome);
}

function clearChat() {
    conversationVersion++;
    pendingQuestions.length = 0;
    if (activeRequest) {
        activeRequest.controller.abort();
        activeRequest = null;
    }
    asking = false;
    conversationId = createConversationId();
    renderWelcome();
    updateAskButtonState();
}

function openReference(reference) {
    openContentViewer(
            '引用资料',
            reference.title,
            `原文片段 ${reference.chunkIndex + 1} · 相似度 ${reference.score.toFixed(3)}`,
            reference.content);
}

function appendReferenceList(message, references) {
    const referenceList = document.createElement('div');
    referenceList.className = 'references';
    references.forEach(reference => {
        const item = document.createElement('button');
        item.type = 'button';
        item.className = 'reference-chip';
        item.textContent = `${reference.title} · 原文片段 ${reference.chunkIndex + 1} · ${reference.score.toFixed(3)}`;
        item.title = '查看引用片段';
        item.addEventListener('click', () => openReference(reference));
        referenceList.appendChild(item);
    });
    message.appendChild(referenceList);
}

function appendMessage(type, text, references, relatedUrls) {
    const welcome = elements.chatList.querySelector('.welcome');
    if (welcome) {
        welcome.remove();
    }

    const row = document.createElement('div');
    row.className = `message-row ${type}-row`;
    const message = document.createElement('div');
    message.className = 'message';

    if (type === 'assistant') {
        row.appendChild(createAssistantAvatar());
        const referencedIndexes = renderMarkdown(message, text, {
            references,
            relatedUrls,
            onReferenceClick: openReference
        });
        if (references && references.length && referencedIndexes.size === 0) {
            appendReferenceList(message, references);
        }
    } else {
        message.textContent = text;
    }

    row.appendChild(message);
    elements.chatList.appendChild(row);
    elements.chatList.scrollTop = elements.chatList.scrollHeight;
    return row;
}

function appendLoadingMessage(afterRow) {
    const row = document.createElement('div');
    row.className = 'message-row assistant-row';
    const avatar = createAssistantAvatar();
    const message = document.createElement('div');
    message.className = 'message loading-message';
    message.setAttribute('aria-label', '正在生成回答');
    message.append(document.createElement('span'), document.createElement('span'), document.createElement('span'));
    row.append(avatar, message);
    if (afterRow && afterRow.isConnected) {
        afterRow.after(row);
    } else {
        elements.chatList.appendChild(row);
    }
    elements.chatList.scrollTop = elements.chatList.scrollHeight;
    return row;
}

function replaceLoadingMessage(loadingMessage, text, references, relatedUrls) {
    const nextRow = loadingMessage.nextSibling;
    loadingMessage.remove();
    const answerRow = appendMessage('assistant', text, references, relatedUrls);
    if (nextRow) {
        elements.chatList.insertBefore(answerRow, nextRow);
    }
    elements.chatList.scrollTop = elements.chatList.scrollHeight;
}

function updateStreamingMessage(loadingMessage, text) {
    const message = loadingMessage.querySelector('.message');
    message.classList.remove('loading-message');
    message.removeAttribute('aria-label');
    message.textContent = text;
    elements.chatList.scrollTop = elements.chatList.scrollHeight;
}

function appendPendingQuestion(questionInfo) {
    const row = appendMessage('user', questionInfo.question);
    row.classList.add('pending-row');
    const footer = document.createElement('span');
    footer.className = 'pending-message-footer';
    const status = document.createElement('span');
    status.className = 'pending-message-status';
    status.textContent = '等待当前回答完成后发送';
    const cancelButton = document.createElement('button');
    cancelButton.type = 'button';
    cancelButton.className = 'pending-cancel-button';
    cancelButton.textContent = '取消排队';
    cancelButton.addEventListener('click', () => cancelPendingQuestion(questionInfo));
    footer.append(status, cancelButton);
    row.querySelector('.message').appendChild(footer);
    return row;
}

function cancelPendingQuestion(questionInfo) {
    const questionIndex = pendingQuestions.indexOf(questionInfo);
    if (questionIndex < 0) {
        return;
    }
    pendingQuestions.splice(questionIndex, 1);
    questionInfo.row.remove();
    showToast('已取消等待中的问题');
}

function markQuestionSending(row) {
    if (!row) {
        return;
    }
    row.classList.remove('pending-row');
    row.querySelector('.pending-message-footer')?.remove();
}

function resizeQuestionInput() {
    elements.question.style.height = 'auto';
    elements.question.style.height = `${Math.min(elements.question.scrollHeight, 150)}px`;
}

function updateQuestionCount() {
    elements.questionCount.textContent = `${elements.question.value.length} / 2000`;
    resizeQuestionInput();
    updateAskButtonState();
}

function updateAskButtonState() {
    const hasQuestion = Boolean(elements.question.value.trim());
    elements.askButton.disabled = !hasQuestion;
    elements.askButtonWrapper.classList.toggle('show-empty-tip', !hasQuestion);
    elements.askButtonWrapper.classList.remove('is-loading');
    elements.askButtonTip.textContent = asking
            ? '输入补充问题，将在当前回答结束后发送' : '请输入你的问题';
    elements.stopButton.hidden = !asking;
    elements.stopButton.disabled = !activeRequest;
    elements.question.placeholder = asking
            ? FOLLOW_UP_QUESTION_PLACEHOLDER : DEFAULT_QUESTION_PLACEHOLDER;
}

function ask() {
    const question = elements.question.value.trim();
    if (!question) {
        return;
    }

    elements.question.value = '';
    updateQuestionCount();
    const questionInfo = {
        question,
        topK: Number(elements.topK.value),
        row: null
    };
    if (asking) {
        questionInfo.row = appendPendingQuestion(questionInfo);
        pendingQuestions.push(questionInfo);
        showToast('补充问题已加入等待队列');
        elements.question.focus();
        return;
    }
    sendQuestion(questionInfo);
}

async function sendQuestion(questionInfo) {
    const requestConversationId = conversationId;
    const requestConversationVersion = conversationVersion;
    asking = true;
    const userRow = questionInfo.row || appendMessage('user', questionInfo.question);
    markQuestionSending(userRow);
    const loadingMessage = appendLoadingMessage(userRow);
    const controller = new AbortController();
    activeRequest = {controller, conversationVersion: requestConversationVersion};
    updateAskButtonState();
    try {
        let answer = '';
        let result = null;
        await streamChat({
            conversationId: requestConversationId,
            question: questionInfo.question,
            topK: questionInfo.topK
        }, controller.signal, event => {
            if (event.type === 'content') {
                answer += event.content || '';
                updateStreamingMessage(loadingMessage, answer);
            } else if (event.type === 'complete') {
                result = event.result;
            } else if (event.type === 'error') {
                throw new Error(event.content || '生成回答失败');
            }
        });
        if (requestConversationVersion === conversationVersion) {
            replaceLoadingMessage(loadingMessage, result?.answer || answer,
                    result?.references || [], result?.relatedUrls || []);
        }
    } catch (error) {
        if (requestConversationVersion !== conversationVersion) {
            return;
        }
        if (error.name === 'AbortError') {
            replaceLoadingMessage(loadingMessage, '已停止生成。');
        } else {
            replaceLoadingMessage(loadingMessage, '请求失败：' + error.message);
        }
    } finally {
        if (requestConversationVersion !== conversationVersion) {
            return;
        }
        if (activeRequest && activeRequest.controller === controller) {
            activeRequest = null;
        }
        asking = false;
        updateAskButtonState();
        const nextQuestion = pendingQuestions.shift();
        if (nextQuestion) {
            sendQuestion(nextQuestion);
        } else {
            elements.question.focus();
        }
    }
}

function stopAnswer() {
    if (!activeRequest) {
        return;
    }
    elements.stopButton.disabled = true;
    activeRequest.controller.abort();
}

export function initChat() {
    elements.askButton.addEventListener('click', ask);
    elements.stopButton.addEventListener('click', stopAnswer);
    elements.clearChatButton.addEventListener('click', clearChat);
    elements.question.addEventListener('input', updateQuestionCount);
    elements.question.addEventListener('keydown', event => {
        if (event.key === 'Enter' && !event.shiftKey) {
            event.preventDefault();
            ask();
        }
    });
    renderWelcome();
    updateQuestionCount();
}
