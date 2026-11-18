export const CHAT_STORAGE_KEY = 'agrosense.ai.chat';
export function forgetConversation() {
    try {
        sessionStorage.removeItem(CHAT_STORAGE_KEY);
    }
    catch {
    }
}
