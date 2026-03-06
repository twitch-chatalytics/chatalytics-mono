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

export interface ChannelStatsEnriched {
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
}
