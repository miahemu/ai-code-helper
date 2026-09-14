import {CHAT_COMMANDS, getCommandHelpMarkdown, getCommandSuggestions, getSkillsMarkdown,
    parseChatCommand} from './commands.js?v=20260914-6';
import {elements} from './elements.js?v=20260914-9';
import {renderMarkdown} from './markdown.js?v=20260911-3';
import {chat, getDocuments, streamChat} from './route.js?v=20260914-3';
import {loadActiveConversationId, loadConversations, saveActiveConversationId, saveConversations}
    from './conversation-store.js?v=20260914-1';
import {createLogoImage, openContentViewer, showToast} from './ui.js?v=20260914-2';

const DEFAULT_QUESTION_PLACEHOLDER = '向 Diving 提问，输入 / 查看命令，Enter 发送';
const FOLLOW_UP_QUESTION_PLACEHOLDER = '可继续输入补充问题，将在当前回答结束后发送';
const KNOWLEDGE_COMMAND = '/kb';

let asking = false;
let conversations = loadConversations();
let conversationId = resolveInitialConversationId();
let conversationVersion = 0;
let activeRequest = null;
let selectedCommandIndex = 0;
let knowledgePickerOpen = false;
let knowledgePickerLoading = false;
let knowledgePickerDocuments = [];
let selectedKnowledgeDocuments = [];
let openConversationMenuId = null;
let conversationMenuPortal = null;
let editingConversationId = null;
let pendingDeleteConversationId = null;
let conversationDeleteTrigger = null;
const pendingQuestions = [];

function createConversationId() {
    if (window.crypto && typeof window.crypto.randomUUID === 'function') {
        return window.crypto.randomUUID();
    }
    return `conversation-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function createConversation() {
    return {
        id: createConversationId(),
        title: '新对话',
        updatedAt: Date.now(),
        messages: []
    };
}

function resolveInitialConversationId() {
    const activeConversationId = loadActiveConversationId();
    const activeConversation = conversations.find(item => item.id === activeConversationId);
    if (activeConversation) {
        return activeConversation.id;
    }
    if (conversations.length) {
        return conversations[0].id;
    }
    const conversation = createConversation();
    conversations.push(conversation);
    saveConversations(conversations);
    saveActiveConversationId(conversation.id);
    return conversation.id;
}

function getCurrentConversation() {
    return conversations.find(item => item.id === conversationId);
}

function persistConversationState() {
    conversations.sort((left, right) => right.updatedAt - left.updatedAt);
    saveConversations(conversations);
    saveActiveConversationId(conversationId);
    renderConversationList();
}

function getConversationTitle(question) {
    const commandInfo = parseChatCommand(question);
    const title = commandInfo?.content || commandInfo?.command?.title || question;
    const compactTitle = title.replace(/\s+/g, ' ').trim();
    return compactTitle.length > 30 ? `${compactTitle.substring(0, 30)}…` : compactTitle;
}

function persistMessage(type, text, references = [], relatedUrls = [], knowledgeDocuments = []) {
    const conversation = getCurrentConversation();
    if (!conversation) {
        return;
    }
    conversation.messages.push({
        role: type,
        text: text || '',
        references: references || [],
        relatedUrls: relatedUrls || [],
        knowledgeDocuments: knowledgeDocuments || [],
        createdAt: Date.now()
    });
    conversation.messages = conversation.messages.slice(-100);
    if (type === 'user' && conversation.title === '新对话') {
        conversation.title = getConversationTitle(text);
    }
    conversation.updatedAt = Date.now();
    persistConversationState();
}

function getConversationGroup(updatedAt) {
    const date = new Date(updatedAt);
    if (Number.isNaN(date.getTime())) {
        return '更早';
    }
    const now = new Date();
    const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    const dateStart = new Date(date.getFullYear(), date.getMonth(), date.getDate());
    const dayDiff = Math.floor((today - dateStart) / 86400000);
    if (dayDiff <= 0) {
        return '今天';
    }
    if (dayDiff < 7) {
        return '7 天内';
    }
    return '更早';
}

function renderConversationList() {
    if (!elements.conversationList) {
        return;
    }
    conversationMenuPortal?.remove();
    conversationMenuPortal = null;
    elements.conversationList.replaceChildren();
    if (!conversations.length) {
        const empty = document.createElement('div');
        empty.className = 'conversation-empty';
        empty.textContent = '暂无对话';
        elements.conversationList.appendChild(empty);
        return;
    }

    let currentGroup = '';
    conversations.forEach(conversation => {
        const group = getConversationGroup(conversation.updatedAt);
        if (group !== currentGroup) {
            currentGroup = group;
            const heading = document.createElement('div');
            heading.className = 'conversation-group-title';
            heading.textContent = group;
            elements.conversationList.appendChild(heading);
        }

        const item = document.createElement('div');
        item.className = `conversation-item${conversation.id === conversationId ? ' active' : ''}`;
        const isEditing = conversation.id === editingConversationId;
        let conversationContent;
        if (isEditing) {
            const input = document.createElement('input');
            input.type = 'text';
            input.className = 'conversation-title-input';
            input.value = conversation.title;
            input.maxLength = 60;
            input.setAttribute('aria-label', `重命名会话：${conversation.title}`);
            input.addEventListener('click', event => event.stopPropagation());
            input.addEventListener('keydown', event => {
                if (event.key === 'Enter') {
                    event.preventDefault();
                    saveConversationRename(conversation.id, input.value);
                } else if (event.key === 'Escape') {
                    event.preventDefault();
                    cancelConversationRename();
                }
            });
            input.addEventListener('blur', () => saveConversationRename(conversation.id, input.value));
            conversationContent = input;
        } else {
            const selectButton = document.createElement('button');
            selectButton.type = 'button';
            selectButton.className = 'conversation-select-button';
            selectButton.title = conversation.title;
            selectButton.setAttribute('aria-label', `打开会话：${conversation.title}`);
            selectButton.addEventListener('click', () => switchConversation(conversation.id));
            const title = document.createElement('span');
            title.className = 'conversation-item-title';
            title.textContent = conversation.title;
            const meta = document.createElement('span');
            meta.className = 'conversation-item-meta';
            meta.textContent = `${conversation.messages.length} 条消息`;
            selectButton.append(title, meta);
            conversationContent = selectButton;
        }

        const actions = document.createElement('div');
        actions.className = 'conversation-actions';
        const menuButton = document.createElement('button');
        menuButton.type = 'button';
        menuButton.className = 'conversation-menu-button';
        menuButton.title = '会话操作';
        menuButton.setAttribute('aria-label', `会话操作：${conversation.title}`);
        menuButton.setAttribute('aria-expanded', String(openConversationMenuId === conversation.id));
        menuButton.textContent = '⋯';
        menuButton.addEventListener('click', event => {
            event.stopPropagation();
            openConversationMenuId = openConversationMenuId === conversation.id
                    ? null : conversation.id;
            renderConversationList();
        });

        const menu = document.createElement('div');
        menu.className = 'conversation-menu';
        const menuOpen = openConversationMenuId === conversation.id;
        menu.hidden = !menuOpen;
        const renameButton = document.createElement('button');
        renameButton.type = 'button';
        renameButton.className = 'conversation-menu-item';
        renameButton.textContent = '重命名';
        prependConversationMenuIcon(renameButton, 'rename');
        renameButton.addEventListener('click', event => {
            event.stopPropagation();
            openConversationMenuId = null;
            renameConversation(conversation.id);
        });
        const deleteButton = document.createElement('button');
        deleteButton.type = 'button';
        deleteButton.className = 'conversation-menu-item danger';
        deleteButton.textContent = '删除';
        prependConversationMenuIcon(deleteButton, 'delete');
        deleteButton.addEventListener('click', event => {
            event.stopPropagation();
            openConversationMenuId = null;
            openConversationDeleteDialog(conversation.id);
        });
        menu.append(renameButton, deleteButton);
        actions.appendChild(menuButton);
        if (menuOpen) {
            menu.classList.add('conversation-menu-portal');
            conversationMenuPortal = menu;
            document.body.appendChild(menu);
        } else {
            actions.appendChild(menu);
        }
        item.append(conversationContent);
        if (!isEditing) {
            item.append(actions);
        }
        elements.conversationList.appendChild(item);
        if (menuOpen) {
            positionConversationMenu(menu, menuButton);
        }
    });
}

function positionConversationMenu(menu, menuButton) {
    const buttonRect = menuButton.getBoundingClientRect();
    const margin = 8;
    const menuWidth = menu.offsetWidth;
    let left = buttonRect.right + 6;
    if (left + menuWidth > window.innerWidth - margin) {
        left = buttonRect.left - menuWidth - 6;
    }
    left = Math.max(margin, Math.min(left, window.innerWidth - menuWidth - margin));
    let top = buttonRect.bottom + 6;
    if (top + menu.offsetHeight > window.innerHeight - margin) {
        top = buttonRect.top - menu.offsetHeight - 6;
    }
    menu.style.left = `${left}px`;
    menu.style.top = `${Math.max(margin, top)}px`;
}

function prependConversationMenuIcon(button, type) {
    const icon = document.createElement('span');
    icon.className = `conversation-menu-icon ${type}`;
    icon.setAttribute('aria-hidden', 'true');
    icon.innerHTML = type === 'rename'
            ? '<svg viewBox="0 0 24 24"><path d="m4 16-.7 3.4L6.7 18 17.9 6.8a2.1 2.1 0 0 0-3-3L3 15z"></path><path d="m13.6 5.1 3.3 3.3"></path></svg>'
            : '<svg viewBox="0 0 24 24"><path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"></path></svg>';
    button.prepend(icon);
}

function renameConversation(targetConversationId) {
    const conversation = conversations.find(item => item.id === targetConversationId);
    if (!conversation) {
        return;
    }
    openConversationMenuId = null;
    editingConversationId = targetConversationId;
    renderConversationList();
    const input = elements.conversationList.querySelector('.conversation-title-input');
    if (input) {
        input.focus();
        input.select();
    }
}

function saveConversationRename(targetConversationId, nextTitle) {
    if (editingConversationId !== targetConversationId) {
        return;
    }
    const title = nextTitle.trim();
    editingConversationId = null;
    if (!title) {
        showToast('会话名称不能为空', true);
        renderConversationList();
        return;
    }
    const conversation = conversations.find(item => item.id === targetConversationId);
    if (!conversation) {
        renderConversationList();
        return;
    }
    conversation.title = title.substring(0, 60);
    conversation.updatedAt = Date.now();
    persistConversationState();
}

function cancelConversationRename() {
    editingConversationId = null;
    renderConversationList();
}

function openConversationDeleteDialog(targetConversationId) {
    const conversation = conversations.find(item => item.id === targetConversationId);
    if (!conversation) {
        return;
    }
    conversationMenuPortal?.remove();
    conversationMenuPortal = null;
    conversationDeleteTrigger = document.activeElement;
    pendingDeleteConversationId = targetConversationId;
    elements.conversationDeleteDescription.textContent =
            `删除“${conversation.title}”后，该对话及其中的消息记录将被移除。`;
    elements.conversationDeleteDialog.hidden = false;
    document.body.classList.add('delete-dialog-open');
    elements.conversationDeleteCancel.focus();
}

function closeConversationDeleteDialog() {
    elements.conversationDeleteDialog.hidden = true;
    document.body.classList.remove('delete-dialog-open');
    pendingDeleteConversationId = null;
    if (conversationDeleteTrigger && typeof conversationDeleteTrigger.focus === 'function') {
        conversationDeleteTrigger.focus();
    }
    conversationDeleteTrigger = null;
}

function confirmConversationDelete() {
    if (!pendingDeleteConversationId) {
        return;
    }
    const targetConversationId = pendingDeleteConversationId;
    closeConversationDeleteDialog();
    deleteConversation(targetConversationId);
    showToast('对话已删除');
}

function renderConversationMessages() {
    const conversation = getCurrentConversation();
    if (!conversation || !conversation.messages.length) {
        renderWelcome();
        return;
    }
    elements.chatList.replaceChildren();
    conversation.messages.forEach(message => {
        if (message.role === 'user' || message.role === 'assistant') {
            appendMessage(message.role, message.text, message.references || [],
                    message.relatedUrls || [], false, message.knowledgeDocuments || []);
        }
    });
}

function stopCurrentRequest() {
    conversationVersion++;
    pendingQuestions.length = 0;
    if (activeRequest) {
        activeRequest.controller.abort();
        activeRequest = null;
    }
    asking = false;
}

function switchConversation(nextConversationId) {
    if (nextConversationId === conversationId) {
        return;
    }
    if (!conversations.some(item => item.id === nextConversationId)) {
        return;
    }
    stopCurrentRequest();
    conversationId = nextConversationId;
    clearKnowledgeDocumentSelection();
    renderConversationMessages();
    persistConversationState();
    updateQuestionCount();
    elements.question.focus();
}

function createNewConversation() {
    const currentConversation = getCurrentConversation();
    if (currentConversation && !currentConversation.messages.length) {
        elements.question.focus();
        return;
    }
    stopCurrentRequest();
    const conversation = createConversation();
    conversations.unshift(conversation);
    conversationId = conversation.id;
    clearKnowledgeDocumentSelection();
    renderConversationMessages();
    persistConversationState();
    updateQuestionCount();
    elements.question.focus();
}

function deleteConversation(targetConversationId) {
    const targetIndex = conversations.findIndex(item => item.id === targetConversationId);
    if (targetIndex < 0) {
        return;
    }
    openConversationMenuId = null;
    const deletingCurrent = targetConversationId === conversationId;
    conversations.splice(targetIndex, 1);
    if (!conversations.length) {
        conversations.push(createConversation());
    }
    if (deletingCurrent) {
        stopCurrentRequest();
        conversationId = conversations[0].id;
        clearKnowledgeDocumentSelection();
        renderConversationMessages();
        updateQuestionCount();
    }
    persistConversationState();
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
    createNewConversation();
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

function appendKnowledgeDocumentContext(message, knowledgeDocuments) {
    if (!knowledgeDocuments || !knowledgeDocuments.length) {
        return;
    }
    const context = document.createElement('div');
    context.className = 'message-knowledge-context';
    const label = document.createElement('span');
    label.className = 'message-knowledge-label';
    label.textContent = '参考文档';
    context.appendChild(label);
    knowledgeDocuments.forEach(documentInfo => {
        const chip = document.createElement('span');
        chip.className = 'message-knowledge-chip';
        chip.title = documentInfo.title || documentInfo.documentId || '知识库文档';
        chip.textContent = documentInfo.title || documentInfo.documentId || '知识库文档';
        context.appendChild(chip);
    });
    message.appendChild(context);
}

function appendMessage(type, text, references = [], relatedUrls = [], shouldPersist = true,
        knowledgeDocuments = []) {
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
        appendKnowledgeDocumentContext(message, knowledgeDocuments);
    }

    row.appendChild(message);
    elements.chatList.appendChild(row);
    elements.chatList.scrollTop = elements.chatList.scrollHeight;
    if (shouldPersist) {
        persistMessage(type, text, references, relatedUrls, knowledgeDocuments);
    }
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
    const row = appendMessage('user', questionInfo.question, [], [], false,
            questionInfo.knowledgeDocuments);
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
    const commandInfo = parseChatCommand(elements.question.value);
    if (selectedKnowledgeDocuments.length && commandInfo?.command?.name !== KNOWLEDGE_COMMAND) {
        clearKnowledgeDocumentSelection();
    }
    elements.questionCount.textContent = `${elements.question.value.length} / 2000`;
    resizeQuestionInput();
    updateCommandMenu();
    updateAskButtonState();
}

function updateCommandMenu() {
    if (knowledgePickerOpen) {
        renderKnowledgePicker();
        return;
    }
    const commands = getCommandSuggestions(elements.question.value);
    elements.commandMenu.replaceChildren();
    if (!commands.length) {
        hideCommandMenu();
        return;
    }

    selectedCommandIndex = Math.min(selectedCommandIndex, commands.length - 1);
    commands.forEach((command, index) => {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'command-menu-item';
        button.setAttribute('role', 'option');
        button.setAttribute('aria-selected', String(index === selectedCommandIndex));
        button.classList.toggle('active', index === selectedCommandIndex);

        const name = document.createElement('span');
        name.className = 'command-menu-name';
        name.textContent = command.name;
        const content = document.createElement('span');
        content.className = 'command-menu-content';
        const title = document.createElement('strong');
        title.textContent = command.title;
        const description = document.createElement('span');
        description.textContent = command.description;
        content.append(title, description);
        button.append(name, content);
        // 鼠标点击命令时保持输入框焦点，避免 blur 定时器先关闭知识库选择器
        button.addEventListener('mousedown', event => event.preventDefault());
        button.addEventListener('click', () => selectCommand(command));
        elements.commandMenu.appendChild(button);
    });
    elements.commandMenu.querySelector('.command-menu-item.active')
            ?.scrollIntoView({block: 'nearest'});
    elements.commandMenu.hidden = false;
    elements.question.setAttribute('aria-expanded', 'true');
}

function hideCommandMenu() {
    elements.commandMenu.hidden = true;
    elements.question.setAttribute('aria-expanded', 'false');
    selectedCommandIndex = 0;
    knowledgePickerOpen = false;
}

async function selectCommand(command) {
    elements.question.value = `${command.name} `;
    if (command.name === KNOWLEDGE_COMMAND) {
        await openKnowledgePicker();
        return;
    }
    hideCommandMenu();
    updateQuestionCount();
    elements.question.focus();
}

async function openKnowledgePicker() {
    knowledgePickerOpen = true;
    knowledgePickerLoading = true;
    renderKnowledgePicker();
    elements.question.focus();
    try {
        knowledgePickerDocuments = await getDocuments();
    } catch (error) {
        knowledgePickerDocuments = [];
        showToast('知识库读取失败：' + error.message, true);
    } finally {
        knowledgePickerLoading = false;
        if (knowledgePickerOpen) {
            renderKnowledgePicker();
        }
    }
}

function renderKnowledgePicker() {
    elements.commandMenu.replaceChildren();

    const title = document.createElement('div');
    title.className = 'knowledge-picker-title';
    title.textContent = '选择回答时使用的知识库文档（可多选）';
    elements.commandMenu.appendChild(title);

    if (knowledgePickerLoading) {
        appendKnowledgePickerMessage('正在读取已导入文档…');
    } else if (!knowledgePickerDocuments.length) {
        appendKnowledgePickerMessage('暂无文档，请先在右侧导入资料');
    } else {
        knowledgePickerDocuments.forEach(documentInfo => {
            const selected = selectedKnowledgeDocuments.some(item =>
                    item.documentId === documentInfo.documentId);
            const button = document.createElement('button');
            button.type = 'button';
            button.className = `command-menu-item knowledge-picker-item${selected ? ' selected' : ''}`;
            button.setAttribute('aria-pressed', String(selected));
            button.addEventListener('mousedown', event => event.preventDefault());

            const check = document.createElement('span');
            check.className = 'knowledge-picker-check';
            check.textContent = '✓';
            const content = document.createElement('span');
            content.className = 'command-menu-content';
            const name = document.createElement('strong');
            name.textContent = documentInfo.title;
            const description = document.createElement('span');
            description.textContent = `${documentInfo.documentType.toUpperCase()} · ${documentInfo.chunkCount} 个切片`;
            content.append(name, description);
            button.append(check, content);
            button.addEventListener('click', () => toggleKnowledgeDocument(documentInfo));
            elements.commandMenu.appendChild(button);
        });
    }

    const footer = document.createElement('div');
    footer.className = 'knowledge-picker-footer';
    const confirmButton = document.createElement('button');
    confirmButton.type = 'button';
    confirmButton.className = 'knowledge-picker-confirm';
    confirmButton.textContent = `完成${selectedKnowledgeDocuments.length ? `（${selectedKnowledgeDocuments.length}）` : ''}`;
    confirmButton.addEventListener('mousedown', event => event.preventDefault());
    confirmButton.addEventListener('click', confirmKnowledgeDocumentSelection);
    footer.appendChild(confirmButton);
    elements.commandMenu.appendChild(footer);
    elements.commandMenu.hidden = false;
    elements.question.setAttribute('aria-expanded', 'true');
}

function appendKnowledgePickerMessage(message) {
    const empty = document.createElement('div');
    empty.className = 'knowledge-picker-empty';
    empty.textContent = message;
    elements.commandMenu.appendChild(empty);
}

function toggleKnowledgeDocument(documentInfo) {
    const selectedIndex = selectedKnowledgeDocuments.findIndex(item =>
            item.documentId === documentInfo.documentId);
    if (selectedIndex >= 0) {
        selectedKnowledgeDocuments.splice(selectedIndex, 1);
    } else {
        selectedKnowledgeDocuments.push(documentInfo);
    }
    renderSelectedKnowledgeDocuments();
    renderKnowledgePicker();
}

function confirmKnowledgeDocumentSelection() {
    if (!selectedKnowledgeDocuments.length) {
        showToast('请至少选择一个知识库文档', true);
        return;
    }
    hideCommandMenu();
    updateQuestionCount();
    elements.question.focus();
}

function renderSelectedKnowledgeDocuments() {
    elements.selectedKnowledgeDocuments.replaceChildren();
    selectedKnowledgeDocuments.forEach(documentInfo => {
        const chip = document.createElement('span');
        chip.className = 'knowledge-document-chip';
        chip.title = documentInfo.title;
        const title = document.createElement('span');
        title.textContent = documentInfo.title;
        const removeButton = document.createElement('button');
        removeButton.type = 'button';
        removeButton.textContent = '×';
        removeButton.setAttribute('aria-label', `移除 ${documentInfo.title}`);
        removeButton.addEventListener('click', () => {
            selectedKnowledgeDocuments = selectedKnowledgeDocuments.filter(item =>
                    item.documentId !== documentInfo.documentId);
            renderSelectedKnowledgeDocuments();
            updateAskButtonState();
        });
        chip.append(title, removeButton);
        elements.selectedKnowledgeDocuments.appendChild(chip);
    });
    elements.selectedKnowledgeDocuments.hidden = !selectedKnowledgeDocuments.length;
}

function clearKnowledgeDocumentSelection() {
    selectedKnowledgeDocuments = [];
    knowledgePickerOpen = false;
    renderSelectedKnowledgeDocuments();
}

function moveCommandSelection(offset) {
    const commands = getCommandSuggestions(elements.question.value);
    if (!commands.length) {
        return;
    }
    selectedCommandIndex = (selectedCommandIndex + offset + commands.length) % commands.length;
    updateCommandMenu();
}

function selectCurrentCommand() {
    const commands = getCommandSuggestions(elements.question.value);
    if (!commands.length) {
        return false;
    }
    selectCommand(commands[selectedCommandIndex]);
    return true;
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
            ? FOLLOW_UP_QUESTION_PLACEHOLDER
            : selectedKnowledgeDocuments.length
                    ? '基于所选知识库提问，可继续输入 / 查看命令'
                    : DEFAULT_QUESTION_PLACEHOLDER;
}

function ask() {
    const question = elements.question.value.trim();
    if (!question) {
        return;
    }

    const commandInfo = parseChatCommand(question);
    if (commandInfo && !commandInfo.command) {
        showToast(`不支持命令 ${commandInfo.commandName}，输入 / 查看可用命令`, true);
        return;
    }
    if (commandInfo?.command.local) {
        elements.question.value = '';
        updateQuestionCount();
        appendMessage('user', commandInfo.command.name);
        appendMessage('assistant', commandInfo.command.action === 'skills'
                ? getSkillsMarkdown() : getCommandHelpMarkdown(), [], []);
        elements.question.focus();
        return;
    }
    if (commandInfo?.command.name === KNOWLEDGE_COMMAND && !selectedKnowledgeDocuments.length) {
        showToast('请先选择至少一个知识库文档', true);
        openKnowledgePicker();
        return;
    }
    if (commandInfo && !commandInfo.content) {
        showToast(`请在 ${commandInfo.command.name} 后输入问题`, true);
        return;
    }

    const questionInfo = {
        question,
        topK: Number(elements.topK.value),
        streamEnabled: elements.streamMode.checked,
        webSearchEnabled: elements.webSearchButton.getAttribute('aria-pressed') === 'true',
        knowledgeDocumentIds: selectedKnowledgeDocuments.map(item => item.documentId),
        knowledgeDocuments: selectedKnowledgeDocuments.map(item => ({
            documentId: item.documentId,
            title: item.title
        })),
        row: null
    };
    elements.question.value = '';
    clearKnowledgeDocumentSelection();
    updateQuestionCount();
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
    const userRow = questionInfo.row || appendMessage('user', questionInfo.question, [], [], true,
            questionInfo.knowledgeDocuments);
    if (questionInfo.row) {
        persistMessage('user', questionInfo.question, [], [], questionInfo.knowledgeDocuments);
    }
    markQuestionSending(userRow);
    const loadingMessage = appendLoadingMessage(userRow);
    const controller = new AbortController();
    activeRequest = {controller, conversationVersion: requestConversationVersion};
    updateAskButtonState();
    try {
        const request = {
            conversationId: requestConversationId,
            question: questionInfo.question,
            topK: questionInfo.topK,
            webSearchEnabled: questionInfo.webSearchEnabled,
            knowledgeDocumentIds: questionInfo.knowledgeDocumentIds
        };
        if (questionInfo.streamEnabled) {
            await sendStreamQuestion(request, controller.signal, loadingMessage,
                    requestConversationVersion);
        } else {
            const result = await chat(request, controller.signal);
            if (requestConversationVersion === conversationVersion) {
                replaceLoadingMessage(loadingMessage, result.answer,
                        result.references || [], result.relatedUrls || []);
            }
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

async function sendStreamQuestion(request, signal, loadingMessage, requestConversationVersion) {
    let answer = '';
    let result = null;
    await streamChat(request, signal, event => {
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
    elements.webSearchButton.addEventListener('click', () => {
        const enabled = elements.webSearchButton.getAttribute('aria-pressed') !== 'true';
        elements.webSearchButton.setAttribute('aria-pressed', String(enabled));
    });
    elements.stopButton.addEventListener('click', stopAnswer);
    elements.clearChatButton.addEventListener('click', clearChat);
    elements.newConversationButton.addEventListener('click', createNewConversation);
    elements.conversationDeleteBackdrop.addEventListener('click', closeConversationDeleteDialog);
    elements.conversationDeleteCancel.addEventListener('click', closeConversationDeleteDialog);
    elements.conversationDeleteClose.addEventListener('click', closeConversationDeleteDialog);
    elements.conversationDeleteConfirm.addEventListener('click', confirmConversationDelete);
    document.addEventListener('click', event => {
        if (openConversationMenuId === null
                || event.target.closest('.conversation-actions')) {
            return;
        }
        openConversationMenuId = null;
        renderConversationList();
    });
    elements.question.addEventListener('input', updateQuestionCount);
    elements.commandMenu.addEventListener('wheel', event => {
        if (elements.commandMenu.scrollHeight <= elements.commandMenu.clientHeight) {
            return;
        }
        event.preventDefault();
        elements.commandMenu.scrollTop += event.deltaY;
    }, {passive: false});
    elements.question.addEventListener('keydown', event => {
        if (!elements.commandMenu.hidden && event.key === 'ArrowDown') {
            event.preventDefault();
            moveCommandSelection(1);
            return;
        }
        if (!elements.commandMenu.hidden && event.key === 'ArrowUp') {
            event.preventDefault();
            moveCommandSelection(-1);
            return;
        }
        if (!elements.commandMenu.hidden && event.key === 'Escape') {
            event.preventDefault();
            hideCommandMenu();
            return;
        }
        if (!elements.commandMenu.hidden && event.key === 'Enter' && !event.shiftKey) {
            event.preventDefault();
            if (knowledgePickerOpen) {
                confirmKnowledgeDocumentSelection();
                return;
            }
            selectCurrentCommand();
            return;
        }
        if (event.key === 'Enter' && !event.shiftKey) {
            event.preventDefault();
            ask();
        }
    });
    elements.question.addEventListener('blur', () => {
        window.setTimeout(() => {
            if (!elements.commandMenu.contains(document.activeElement)) {
                hideCommandMenu();
            }
        }, 120);
    });
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && !elements.conversationDeleteDialog.hidden) {
            closeConversationDeleteDialog();
        }
    });
    renderConversationList();
    renderConversationMessages();
    updateQuestionCount();
}
