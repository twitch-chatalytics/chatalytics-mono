import { ChannelStats, ChatterProfile, Message, SessionSummaryView, StreamRecap } from '../types/message';

const DEFAULT_TWITCH_ID = 552120296;

export interface DateRange {
  from?: string;
  to?: string;
}

export async function fetchStats(
  twitchId: number = DEFAULT_TWITCH_ID,
): Promise<ChannelStats> {
  const response = await fetch(`/public/stats?twitchId=${twitchId}`);

  if (!response.ok) {
    throw new Error(`Failed to fetch stats: ${response.statusText}`);
  }

  return response.json();
}

export async function searchAuthors(
  query: string,
  twitchId: number = DEFAULT_TWITCH_ID,
): Promise<string[]> {
  const params = new URLSearchParams({
    q: query,
    twitchId: String(twitchId),
  });

  const response = await fetch(`/public/authors?${params}`);

  if (!response.ok) {
    return [];
  }

  return response.json();
}

export async function fetchMessageContext(
  messageId: number,
  seconds: number = 30,
  twitchId: number = DEFAULT_TWITCH_ID,
): Promise<Message[]> {
  const params = new URLSearchParams({
    twitchId: String(twitchId),
    seconds: String(seconds),
  });

  const response = await fetch(`/public/messages/${messageId}/context?${params}`);

  if (!response.ok) {
    return [];
  }

  return response.json();
}

export async function fetchChatterProfile(
  author: string,
  twitchId: number = DEFAULT_TWITCH_ID,
): Promise<ChatterProfile | null> {
  const params = new URLSearchParams({
    author,
    twitchId: String(twitchId),
  });

  const response = await fetch(`/public/chatter-profile?${params}`);

  if (!response.ok) {
    return null;
  }

  return response.json();
}

export async function fetchChatterSummary(
  author: string,
  twitchId: number = DEFAULT_TWITCH_ID,
): Promise<string | null> {
  const params = new URLSearchParams({
    author,
    twitchId: String(twitchId),
  });

  const response = await fetch(`/public/chatter-summary?${params}`);

  if (!response.ok) {
    return null;
  }

  const data: { summary: string } = await response.json();
  return data.summary;
}

export const PAGE_SIZE = 50;

export async function fetchMessagesByAuthor(
  author: string,
  dateRange?: DateRange,
  cursor?: { timestamp: string; id: number },
  limit: number = PAGE_SIZE,
  twitchId: number = DEFAULT_TWITCH_ID,
): Promise<Message[]> {
  const params = new URLSearchParams({
    author,
    twitchId: String(twitchId),
    limit: String(limit),
  });

  if (dateRange?.from) {
    params.set('from', dateRange.from);
  }
  if (dateRange?.to) {
    params.set('to', dateRange.to);
  }
  if (cursor) {
    params.set('beforeTimestamp', cursor.timestamp);
    params.set('beforeId', String(cursor.id));
  }

  const response = await fetch(`/public/messages?${params}`);

  if (!response.ok) {
    throw new Error(`Failed to fetch messages: ${response.statusText}`);
  }

  return response.json();
}

export async function fetchSessions(
  twitchId: number = DEFAULT_TWITCH_ID,
  limit: number = 50,
): Promise<SessionSummaryView[]> {
  const params = new URLSearchParams({
    twitchId: String(twitchId),
    limit: String(limit),
  });

  const response = await fetch(`/public/sessions?${params}`);

  if (!response.ok) {
    return [];
  }

  return response.json();
}

export async function fetchSessionRecap(
  sessionId: number,
): Promise<StreamRecap | null> {
  const response = await fetch(`/public/sessions/${sessionId}/recap`);

  if (!response.ok) {
    return null;
  }

  return response.json();
}
