export const CHAT_COMMANDS = [
    {
        name: '/auto',
        title: '自动模式',
        description: '由 Agent 根据问题自动选择是否使用工具'
    },
    {
        name: '/kb',
        title: '知识库',
        description: '多选已导入文档，仅根据所选资料回答'
    },
    {
        name: '/web',
        title: '联网搜索',
        description: '仅通过联网搜索回答问题'
    },
    {
        name: '/interview',
        title: '面试题',
        description: '仅搜索相关技术面试题'
    },
    {
        name: '/skills',
        title: '能力列表',
        description: '查看 Diving 当前可使用的能力',
        local: true,
        action: 'skills'
    },
    {
        name: '/help',
        title: '命令帮助',
        description: '查看当前支持的全部斜杠命令',
        local: true,
        action: 'help'
    }
];

export function getCommandSuggestions(input) {
    const value = input.trimStart();
    if (!value.startsWith('/') || /\s/.test(value)) {
        return [];
    }
    const keyword = value.toLowerCase();
    return CHAT_COMMANDS.filter(command => command.name.startsWith(keyword));
}

export function parseChatCommand(input) {
    const value = input.trim();
    if (!value.startsWith('/')) {
        return null;
    }
    const separatorIndex = value.search(/\s/);
    const commandName = (separatorIndex < 0 ? value : value.substring(0, separatorIndex)).toLowerCase();
    const command = CHAT_COMMANDS.find(item => item.name === commandName);
    return {
        command,
        commandName,
        content: separatorIndex < 0 ? '' : value.substring(separatorIndex).trim()
    };
}

export function getCommandHelpMarkdown() {
    const commandLines = CHAT_COMMANDS
            .map(command => `- \`${command.name}\`：${command.description}`)
            .join('\n');
    return `### 可用命令\n\n${commandLines}\n\n输入 \`/\` 可以随时打开命令菜单。`;
}

export function getSkillsMarkdown() {
    return `### 可用能力

- **知识库检索**：从已导入的一个或多个文档中查找资料
- **联网搜索**：搜索需要时效性或外部核验的信息
- **面试题搜索**：查找指定技术方向的相关面试题`;
}
