import { ChannelAuthenticityReport, SocialBladeChannel } from '../types/message';

export function interpretChannelScore(avg: number, report: ChannelAuthenticityReport): string {
  const name = report.sessionsAnalyzed === 1 ? 'session' : 'sessions';
  const range = report.maxAuthenticityScore != null && report.minAuthenticityScore != null
    ? report.maxAuthenticityScore - report.minAuthenticityScore
    : 0;

  if (avg >= 80) {
    return `Across ${report.sessionsAnalyzed} ${name}, this channel shows strong indicators of authentic audience engagement. Chat participation, message quality, and behavioral patterns are all consistent with a genuine community.`;
  }
  if (avg >= 60) {
    let text = `This channel scores in the moderate range across ${report.sessionsAnalyzed} ${name}, suggesting a largely organic audience with some areas to monitor.`;
    if (range > 20) {
      text += ` Scores varied notably between sessions (${report.minAuthenticityScore}\u2013${report.maxAuthenticityScore}), which may reflect different stream conditions or viewer composition.`;
    }
    return text;
  }
  if (avg >= 40) {
    return `This channel shows mixed authenticity signals across ${report.sessionsAnalyzed} ${name}. Some metrics suggest organic engagement while others fall below expected ranges. We recommend reviewing the session-level breakdown for specific areas of concern before committing ad spend.`;
  }
  return `Multiple signals suggest this channel's audience may include significant artificial activity. Across ${report.sessionsAnalyzed} ${name}, chat behavior patterns deviate from what we'd expect of an organic audience. Detailed signal analysis below identifies the specific indicators.`;
}

export function interpretChatRatio(actual: number | null, expected: number | null, deviation: number | null): string {
  if (actual == null || expected == null || deviation == null) return '';
  const pct = (actual * 100).toFixed(1);
  const expPct = (expected * 100).toFixed(0);
  if (deviation >= 0.8) return `${pct}% of viewers participated in chat, which is in line with the expected ~${expPct}% for a channel of this size. This suggests viewers are genuinely engaged.`;
  if (deviation >= 0.5) return `Chat participation (${pct}%) is somewhat below the expected ~${expPct}% for this viewer count. This could reflect a more passive audience or suggest some viewer inflation.`;
  return `Only ${pct}% of viewers chatted, significantly below the expected ~${expPct}%. This gap between viewer count and chat activity may indicate that a portion of the audience is not genuine.`;
}

export function interpretMessageQuality(vocab: number | null, repetitive: number | null): string {
  if (vocab == null) return '';
  const rep = repetitive ?? 0;
  if (vocab > 0.6 && rep < 0.15) return 'Chat shows healthy vocabulary diversity with low repetition \u2014 consistent with genuine human conversation.';
  if (rep > 0.4) return `${(rep * 100).toFixed(0)}% of messages are repetitive (appearing 3+ times), which is higher than typical organic chat. This can indicate coordinated messaging or simple bot scripts.`;
  if (vocab < 0.3) return 'Vocabulary diversity is low, meaning chatters used a limited set of words. This pattern is more common in bot-driven chat.';
  return 'Message quality is moderate \u2014 vocabulary diversity and repetition rates fall within an acceptable range, though not strongly indicative of either organic or artificial behavior.';
}

export function interpretChatterBehavior(singleMsg: number | null, timing: number | null): string {
  if (singleMsg == null) return '';
  const t = timing ?? 0;
  if (singleMsg < 0.4 && t < 0.3) return 'Most chatters sent multiple messages and timing patterns appear natural. This is characteristic of an engaged, returning community.';
  if (singleMsg > 0.7) return `${(singleMsg * 100).toFixed(0)}% of chatters sent exactly one message. While some drive-by chatting is normal, rates this high may suggest bot accounts that send a single message to appear active.`;
  if (t > 0.6) return 'Message timing is unusually uniform \u2014 messages arrive at suspiciously regular intervals. Organic chat tends to come in bursts around stream events.';
  return `${(singleMsg * 100).toFixed(0)}% of chatters sent only one message. This is within a moderate range \u2014 some single-message chatters are normal, especially in larger streams.`;
}

export function interpretEngagement(flow: number | null, depth: number | null): string {
  if (flow == null) return '';
  const d = depth ?? 0;
  if (flow > 0.7 && d > 0.5) return 'Chat activity ebbs and flows naturally with stream events, and includes meaningful engagement like questions and longer messages. This pattern is strongly indicative of real audience interaction.';
  if (flow < 0.3) return 'Chat volume is unusually flat throughout the stream, lacking the natural peaks and valleys that occur when a real audience reacts to events. This uniformity may suggest automated chat generation.';
  if (d < 0.2) return 'While chat flow appears somewhat natural, conversation depth is low \u2014 few questions or substantive messages. The chat may be active but lacks the engagement depth typical of an invested audience.';
  return 'Engagement patterns are moderate. Chat shows some natural variation but could reflect a less interactive audience or stream format rather than artificial activity.';
}

export function interpretCrossSession(correlation: number | null): string {
  if (correlation == null) return 'Not enough sessions to evaluate cross-stream patterns.';
  if (correlation > 0.5) return `Viewer count and chat participation are positively correlated (${correlation.toFixed(2)}) across sessions. When this channel has more viewers, proportionally more people chat \u2014 a natural pattern for authentic audiences.`;
  if (correlation > 0) return `There is a weak positive correlation (${correlation.toFixed(2)}) between viewer count and chat activity. The relationship is present but not as strong as typically seen in channels with fully organic audiences.`;
  if (correlation > -0.3) return `Viewer-chat correlation is near zero (${correlation.toFixed(2)}), meaning chat activity doesn't consistently scale with viewership. This can occur with limited data or may warrant monitoring as more sessions complete.`;
  return `Viewer count and chat activity show a negative correlation (${correlation.toFixed(2)}) \u2014 chat participation tends to decrease as viewer counts rise. This inverse relationship may suggest viewer count inflation during certain streams.`;
}

export function formatCompact(n: number): string {
  if (n >= 1_000_000_000) return `${(n / 1_000_000_000).toFixed(1)}B`;
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`;
  return n.toLocaleString();
}

export function hasSocialLinks(sb: SocialBladeChannel): boolean {
  return !!(sb.twitterUrl || sb.youtubeUrl || sb.instagramUrl || sb.discordUrl || sb.tiktokUrl);
}

export function getScoreColor(score: number): string {
  if (score >= 70) return '#22c55e';
  if (score >= 40) return '#eab308';
  return '#ef4444';
}

export function formatShortDate(dateStr: string): string {
  const d = new Date(dateStr);
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}
