export const CHAT_STORAGE_KEY = 'agrosense.ai.chat';

export function forgetConversation(): void {
  try {
    sessionStorage.removeItem(CHAT_STORAGE_KEY);
  } catch {
  }
}
