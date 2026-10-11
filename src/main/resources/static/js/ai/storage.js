// Where the assistant's conversation is kept in the browser. On its own, without the chat window, so the
// sign-in page can erase the conversation without loading the rest of the assistant.
export const CHAT_STORAGE_KEY = 'agrosense.ai.chat';
/** Erases the stored conversation. Called where no session exists any more: signed out, or expired. */
export function forgetConversation() {
    try {
        sessionStorage.removeItem(CHAT_STORAGE_KEY);
    }
    catch {
        // Storage is disabled: nothing was kept in the first place.
    }
}
