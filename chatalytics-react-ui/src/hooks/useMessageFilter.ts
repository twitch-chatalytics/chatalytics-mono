import { useMemo } from 'react';
import { Message } from '../types/message';

interface FilterResult {
  filtered: Message[];
  total: number;
}

export function useMessageFilter(messages: Message[], term: string): FilterResult {
  return useMemo(() => {
    if (!term.trim()) return { filtered: messages, total: messages.length };
    const lower = term.toLowerCase();
    const filtered = messages.filter((m) => m.messageText.toLowerCase().includes(lower));
    return { filtered, total: messages.length };
  }, [messages, term]);
}
