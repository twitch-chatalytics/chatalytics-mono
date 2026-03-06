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
}
