export interface Message {
  id: number;
  twitchId: number;
  messageText: string;
  timestamp: string;
  sessionId: number;
  author: string;
}

export interface TopChatter {
  author: string;
  messageCount: number;
}

export interface ChannelStats {
  totalMessages: number;
  uniqueChatters: number;
  topChatters: TopChatter[];
  peakHour: number | null;
  totalSessions: number;
  avgMessagesPerSession: number;
  avgChattersPerSession: number;
  avgStreamDurationMinutes: number | null;
  topGames: { gameName: string; sessionCount: number }[];
}

export interface ChatterProfile {
  author: string;
  totalMessages: number;
  firstSeen: string;
  lastSeen: string;
  distinctSessions: number;
  peakHour: number | null;
  avgMessagesPerSession: number;
  repeatedMessages: RepeatGroup[];
}

export interface ActivityBucket {
  date: string;
  count: number;
}

export interface RepeatGroup {
  text: string;
  count: number;
}

export interface SessionSummaryView {
  sessionId: number;
  twitchId: number;
  startTime: string;
  endTime: string | null;
  totalMessages: number;
  totalChatters: number;
  lastGameName: string | null;
  peakViewerCount: number | null;
  messagesPerMinute: number | null;
  durationMinutes: number | null;
}

export interface StreamSnapshot {
  id: number;
  sessionId: number;
  twitchId: number;
  timestamp: string;
  gameName: string | null;
  title: string | null;
  viewerCount: number;
}

export interface ChatActivityBucket {
  bucketStart: string;
  messageCount: number;
  uniqueChatters: number;
}

export interface TopWord {
  word: string;
  count: number;
}

export interface GameSegment {
  gameName: string;
  startTime: string;
  endTime: string;
  durationMinutes: number;
  messageCount: number;
  avgViewers: number;
  peakViewers: number;
}

export interface ChatMoment {
  timestamp: string;
  messageCount: number;
  uniqueChatters: number;
}

export interface MessageAnalysis {
  avgMessageLength: number;
  medianMessageLength: number;
  commandCount: number;
  shortMessageRatio: number;
  capsRatio: number;
  questionRatio: number;
  exclamationRatio: number;
  linkCount: number;
}

export interface StreamClip {
  id: string;
  url: string;
  embedUrl: string;
  title: string;
  viewCount: number;
  createdAt: string;
  thumbnailUrl: string;
  duration: number;
  creatorName: string;
}

export interface HypeMoment {
  timestamp: string;
  messageCount: number;
  uniqueChatters: number;
  multiplier: number;
}

export interface ChannelProfile {
  id: number;
  login: string;
  displayName: string;
  broadcasterType: string | null;
  description: string | null;
  profileImageUrl: string | null;
  offlineImageUrl: string | null;
  createdAt: string;
}

export interface StreamRecap {
  sessionId: number;
  startTime: string;
  endTime: string | null;
  totalMessages: number;
  totalChatters: number;
  snapshots: StreamSnapshot[];
  chatActivity: ChatActivityBucket[];
  topChatters: TopChatter[];
  aiSummary: string | null;
  messagesPerMinute: number;
  chattersPerMinute: number;
  peakViewerCount: number | null;
  avgViewerCount: number | null;
  minViewerCount: number | null;
  messageAnalysis: MessageAnalysis | null;
  newChatterCount: number;
  returningChatterCount: number;
  topWords: TopWord[];
  gameSegments: GameSegment[];
  chatParticipationRate: number | null;
  peakMoment: ChatMoment | null;
  topClips: StreamClip[];
  hypeMoments: HypeMoment[];
}

export interface CompareItem {
  sessionId: number;
  channelLogin: string;
  channelDisplayName: string;
  profileImageUrl?: string;
  startTime: string;
  gameName?: string;
}

export interface AuthUser {
  twitchId: number;
  login: string;
  displayName: string;
  profileImageUrl: string;
}

export interface TwitchSearchResult {
  id: number;
  login: string;
  displayName: string;
  profileImageUrl: string;
  broadcasterType: string;
  alreadyTracked: boolean;
}

export interface StreamerRequestSummary {
  streamerLogin: string;
  streamerId: number;
  displayName: string;
  profileImageUrl: string;
  voteCount: number;
}

export interface VoteResponse {
  voted: boolean;
  voteCount: number;
  added: boolean;
}

export interface GlobalStats {
  totalMessages: number;
  uniqueChatters: number;
  totalStreams: number;
  trackedChannels: number;
  topChatters: TopChatter[];
  updatedAt: string;
}
