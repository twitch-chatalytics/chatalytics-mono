import { AdvertiserAccount, AlertEvent, AlertRule, AuthenticityTrendPoint, AuthUser, Campaign, CampaignReport, ChannelAuthenticityReport, ChannelBenchmark, ChannelBrandSafety, ChannelProfile, ChannelStats, ChatterProfile, FeaturedChannel, GlobalStats, Message, SessionAuthenticityReport, SessionSummaryView, SocialBladeChannel, SocialBladeDailyPoint, StreamerRequestSummary, StreamRecap, TwitchSearchResult, VoteResponse } from '../types/message';

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

export async function fetchStatsBatch(
  ids: number[],
): Promise<Record<number, ChannelStats>> {
  const params = new URLSearchParams();
  ids.forEach(id => params.append('ids', String(id)));
  const response = await fetch(`/public/stats/batch?${params}`);
  if (!response.ok) return {};
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

export async function fetchChannel(
  twitchId: number = DEFAULT_TWITCH_ID,
): Promise<ChannelProfile | null> {
  const response = await fetch(`/public/channel?twitchId=${twitchId}`);
  if (!response.ok) return null;
  return response.json();
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

export const SESSIONS_PAGE_SIZE = 20;

export async function fetchSessions(
  dateRange?: DateRange,
  cursor?: { startTime: string; id: number },
  limit: number = SESSIONS_PAGE_SIZE,
  twitchId: number = DEFAULT_TWITCH_ID,
): Promise<SessionSummaryView[]> {
  const params = new URLSearchParams({
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
    params.set('beforeStartTime', cursor.startTime);
    params.set('beforeId', String(cursor.id));
  }

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

// ─── Auth ───

export async function fetchMe(): Promise<AuthUser | null> {
  const response = await fetch('/auth/me', { credentials: 'include' });
  if (!response.ok) return null;
  return response.json();
}

export async function logout(): Promise<void> {
  await fetch('/auth/logout', { method: 'POST', credentials: 'include' });
}

// ─── Channel Directory ───

export async function fetchFeaturedChannels(): Promise<FeaturedChannel[]> {
  const response = await fetch('/public/channels/featured');
  if (!response.ok) return [];
  return response.json();
}

export async function fetchChannels(): Promise<ChannelProfile[]> {
  const response = await fetch('/public/channels');
  if (!response.ok) return [];
  return response.json();
}

export async function searchChannels(query: string): Promise<TwitchSearchResult[]> {
  const response = await fetch(`/public/channels/search?q=${encodeURIComponent(query)}`);
  if (!response.ok) return [];
  return response.json();
}

export async function fetchPendingRequests(): Promise<StreamerRequestSummary[]> {
  const response = await fetch('/public/channels/requests');
  if (!response.ok) return [];
  return response.json();
}

export async function requestStreamer(streamerLogin: string): Promise<VoteResponse> {
  const response = await fetch('/public/channels/request', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({ streamerLogin }),
  });
  if (!response.ok) throw new Error('Failed to request streamer');
  return response.json();
}

export async function fetchPendingRequestsPaged(
  limit: number = 20,
  offset: number = 0,
): Promise<{ items: StreamerRequestSummary[]; total: number }> {
  const params = new URLSearchParams({ limit: String(limit), offset: String(offset) });
  const response = await fetch(`/public/channels/requests/paged?${params}`);
  if (!response.ok) return { items: [], total: 0 };
  return response.json();
}

export async function fetchGlobalStats(): Promise<GlobalStats | null> {
  const response = await fetch('/public/channels/global-stats');
  if (!response.ok || response.status === 204) return null;
  return response.json();
}

export async function fetchChannelByLogin(login: string): Promise<ChannelProfile | null> {
  const response = await fetch(`/public/channels/by-login/${encodeURIComponent(login)}`);
  if (!response.ok) return null;
  return response.json();
}

// ─── Advertiser ───

export async function fetchChannelAuthenticity(
  twitchId: number,
): Promise<ChannelAuthenticityReport | null> {
  const response = await fetch(`/advertiser/channel/${twitchId}/authenticity`, { credentials: 'include' });
  if (!response.ok) return null;
  return response.json();
}

export async function fetchSessionAuthenticity(
  sessionId: number,
): Promise<SessionAuthenticityReport | null> {
  const response = await fetch(`/advertiser/session/${sessionId}/authenticity`, { credentials: 'include' });
  if (!response.ok) return null;
  return response.json();
}

export async function fetchAuthenticityTrend(
  twitchId: number,
  limit: number = 50,
): Promise<AuthenticityTrendPoint[]> {
  const response = await fetch(`/advertiser/channel/${twitchId}/trend?limit=${limit}`, { credentials: 'include' });
  if (!response.ok) return [];
  return response.json();
}

export async function fetchAdvertiserMe(): Promise<AdvertiserAccount | null> {
  const response = await fetch('/advertiser/me', { credentials: 'include' });
  if (!response.ok) return null;
  return response.json();
}

export async function fetchChannelSessions(
  twitchId: number,
  limit: number = 100,
): Promise<SessionAuthenticityReport[]> {
  const response = await fetch(`/advertiser/channel/${twitchId}/sessions?limit=${limit}`, { credentials: 'include' });
  if (!response.ok) return [];
  return response.json();
}

// ─── SocialBlade ───

export async function fetchSocialBladeChannel(
  twitchId: number,
): Promise<SocialBladeChannel | null> {
  const response = await fetch(`/advertiser/channel/${twitchId}/socialblade`, { credentials: 'include' });
  if (!response.ok) return null;
  return response.json();
}

export async function fetchSocialBladeDaily(
  twitchId: number,
  limit: number = 90,
): Promise<SocialBladeDailyPoint[]> {
  const response = await fetch(`/advertiser/channel/${twitchId}/socialblade/daily?limit=${limit}`, { credentials: 'include' });
  if (!response.ok) return [];
  return response.json();
}

// --- Benchmark ---

export async function fetchChannelBenchmark(
  twitchId: number,
): Promise<ChannelBenchmark | null> {
  const response = await fetch(`/advertiser/channel/${twitchId}/benchmark`, { credentials: 'include' });
  if (!response.ok) return null;
  return response.json();
}

// --- Brand Safety ---

export async function fetchChannelBrandSafety(
  twitchId: number,
): Promise<ChannelBrandSafety | null> {
  const response = await fetch(`/advertiser/channel/${twitchId}/brand-safety`, { credentials: 'include' });
  if (!response.ok) return null;
  return response.json();
}

// ─── Alerts ───

export async function fetchAlertRules(twitchId: number): Promise<AlertRule[]> {
  const response = await fetch(`/advertiser/channel/${twitchId}/alerts/rules`, { credentials: 'include' });
  if (!response.ok) return [];
  return response.json();
}

export async function createAlertRule(
  twitchId: number,
  alertType: string,
  thresholdValue: number | null,
): Promise<AlertRule> {
  const response = await fetch(`/advertiser/channel/${twitchId}/alerts/rules`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({ alertType, thresholdValue }),
  });
  if (!response.ok) throw new Error('Failed to create alert rule');
  return response.json();
}

export async function deleteAlertRule(ruleId: number): Promise<void> {
  const response = await fetch(`/advertiser/alerts/rules/${ruleId}`, {
    method: 'DELETE',
    credentials: 'include',
  });
  if (!response.ok) throw new Error('Failed to delete alert rule');
}

export async function fetchAlertEvents(
  twitchId: number,
  limit: number = 50,
): Promise<AlertEvent[]> {
  const response = await fetch(`/advertiser/channel/${twitchId}/alerts/events?limit=${limit}`, { credentials: 'include' });
  if (!response.ok) return [];
  return response.json();
}

export async function acknowledgeAlertEvent(eventId: number): Promise<void> {
  const response = await fetch(`/advertiser/alerts/events/${eventId}/acknowledge`, {
    method: 'POST',
    credentials: 'include',
  });
  if (!response.ok) throw new Error('Failed to acknowledge event');
}

// ─── Campaigns ───

export async function fetchCampaigns(twitchId: number): Promise<Campaign[]> {
  const response = await fetch(`/advertiser/channel/${twitchId}/campaigns`, { credentials: 'include' });
  if (!response.ok) return [];
  return response.json();
}

export async function createCampaign(
  twitchId: number,
  data: { campaignName: string; brandName?: string; brandKeywords: string[]; startDate: string; endDate: string; dealPrice?: number },
): Promise<Campaign> {
  const response = await fetch(`/advertiser/channel/${twitchId}/campaigns`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify(data),
  });
  if (!response.ok) throw new Error('Failed to create campaign');
  return response.json();
}

export async function deleteCampaign(campaignId: number): Promise<void> {
  const response = await fetch(`/advertiser/campaigns/${campaignId}`, {
    method: 'DELETE',
    credentials: 'include',
  });
  if (!response.ok) throw new Error('Failed to delete campaign');
}

export async function fetchCampaignReport(campaignId: number): Promise<CampaignReport | null> {
  const response = await fetch(`/advertiser/campaigns/${campaignId}/report`, { credentials: 'include' });
  if (!response.ok) return null;
  return response.json();
}
