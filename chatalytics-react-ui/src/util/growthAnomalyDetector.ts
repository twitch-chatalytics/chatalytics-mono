import { SocialBladeDailyPoint } from '../types/message';

export interface GrowthAnomaly {
  type: 'spike' | 'linear' | 'purge_cycle';
  severity: 'warning' | 'critical';
  date: string;
  detail: string;
  value: number;
  rollingAvg?: number;
}

export interface GrowthAnomalyResult {
  anomalies: GrowthAnomaly[];
  riskScore: number;
  summary: string;
}

export function analyzeGrowthAnomalies(data: SocialBladeDailyPoint[]): GrowthAnomalyResult {
  if (data.length < 7) {
    return { anomalies: [], riskScore: 0, summary: 'Not enough daily data to analyze growth patterns.' };
  }

  // Sort chronologically
  const sorted = [...data].sort((a, b) => a.date.localeCompare(b.date));

  // Compute daily changes (use followerChange if available, otherwise compute from absolute)
  const changes: { date: string; change: number }[] = [];
  for (let i = 0; i < sorted.length; i++) {
    const change = sorted[i].followerChange != null
      ? sorted[i].followerChange!
      : (i > 0 && sorted[i].followers != null && sorted[i - 1].followers != null)
        ? sorted[i].followers! - sorted[i - 1].followers!
        : null;
    if (change != null) {
      changes.push({ date: sorted[i].date, change });
    }
  }

  if (changes.length < 7) {
    return { anomalies: [], riskScore: 0, summary: 'Not enough daily change data to analyze.' };
  }

  const anomalies: GrowthAnomaly[] = [];

  // --- Spike detection ---
  detectSpikes(changes, anomalies);

  // --- Linear growth detection ---
  detectLinearGrowth(changes, anomalies);

  // --- Purge cycle detection ---
  detectPurgeCycles(changes, anomalies);

  // Deduplicate by date+type
  const seen = new Set<string>();
  const unique = anomalies.filter(a => {
    const key = `${a.date}-${a.type}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });

  // Risk score
  let risk = 0;
  for (const a of unique) {
    risk += a.severity === 'critical' ? 20 : 10;
  }
  // Scale down if anomalies are rare relative to data volume
  if (changes.length > 30 && unique.length <= 2) {
    risk = Math.round(risk * 0.6);
  }
  risk = Math.min(100, risk);

  return {
    anomalies: unique.sort((a, b) => b.date.localeCompare(a.date)),
    riskScore: risk,
    summary: buildSummary(unique),
  };
}

function detectSpikes(
  changes: { date: string; change: number }[],
  anomalies: GrowthAnomaly[],
) {
  const WINDOW = 7;
  const SPIKE_THRESHOLD = 3;
  const CRITICAL_THRESHOLD = 5;
  const MIN_ABSOLUTE = 100;

  for (let i = WINDOW; i < changes.length; i++) {
    // Rolling average of previous WINDOW days
    let sum = 0;
    for (let j = i - WINDOW; j < i; j++) {
      sum += Math.abs(changes[j].change);
    }
    const avg = sum / WINDOW;

    const current = changes[i].change;
    if (avg > 0 && current > MIN_ABSOLUTE && current > avg * SPIKE_THRESHOLD) {
      const ratio = current / avg;
      anomalies.push({
        type: 'spike',
        severity: ratio >= CRITICAL_THRESHOLD ? 'critical' : 'warning',
        date: changes[i].date,
        detail: `+${current.toLocaleString()} followers in a single day (${ratio.toFixed(1)}x the 7-day average of ${Math.round(avg).toLocaleString()})`,
        value: current,
        rollingAvg: avg,
      });
    }
  }
}

function detectLinearGrowth(
  changes: { date: string; change: number }[],
  anomalies: GrowthAnomaly[],
) {
  const WINDOW = 14;
  const CV_THRESHOLD = 0.1;
  const MIN_MEAN = 10; // Ignore tiny channels

  for (let i = 0; i <= changes.length - WINDOW; i++) {
    const window = changes.slice(i, i + WINDOW).map(c => c.change);

    // Only check positive growth periods
    const positiveCount = window.filter(v => v > 0).length;
    if (positiveCount < WINDOW * 0.8) continue;

    const mean = window.reduce((s, v) => s + v, 0) / WINDOW;
    if (mean < MIN_MEAN) continue;

    const variance = window.reduce((s, v) => s + (v - mean) ** 2, 0) / WINDOW;
    const stdDev = Math.sqrt(variance);
    const cv = stdDev / mean;

    if (cv < CV_THRESHOLD) {
      const startDate = changes[i].date;
      const endDate = changes[i + WINDOW - 1].date;
      anomalies.push({
        type: 'linear',
        severity: 'warning',
        date: startDate,
        detail: `Follower gains were suspiciously uniform from ${formatShortDate(startDate)} to ${formatShortDate(endDate)} (CV: ${cv.toFixed(2)}, avg: +${Math.round(mean).toLocaleString()}/day)`,
        value: Math.round(mean),
      });
      // Skip ahead to avoid overlapping flags
      break;
    }
  }
}

function detectPurgeCycles(
  changes: { date: string; change: number }[],
  anomalies: GrowthAnomaly[],
) {
  const LOOKBACK = 7;
  const RECOVERY_RATIO = 0.5;
  const MIN_SPIKE = 200;

  for (let i = 0; i < changes.length; i++) {
    if (changes[i].change < MIN_SPIKE) continue;

    // Look for a significant drop within LOOKBACK days
    for (let j = i + 1; j < Math.min(i + LOOKBACK + 1, changes.length); j++) {
      if (changes[j].change < 0 && Math.abs(changes[j].change) >= changes[i].change * RECOVERY_RATIO) {
        anomalies.push({
          type: 'purge_cycle',
          severity: 'critical',
          date: changes[i].date,
          detail: `+${changes[i].change.toLocaleString()} spike on ${formatShortDate(changes[i].date)} followed by ${changes[j].change.toLocaleString()} drop on ${formatShortDate(changes[j].date)} (${(j - i)}-day gap)`,
          value: changes[i].change,
        });
        break;
      }
    }
  }
}

function buildSummary(anomalies: GrowthAnomaly[]): string {
  if (anomalies.length === 0) {
    return 'No unusual growth patterns detected in the available data.';
  }
  const criticalCount = anomalies.filter(a => a.severity === 'critical').length;
  const types = new Set(anomalies.map(a => a.type));
  const parts: string[] = [];
  if (types.has('spike')) parts.push('unusual follower spikes');
  if (types.has('linear')) parts.push('suspiciously uniform growth');
  if (types.has('purge_cycle')) parts.push('spike-and-purge patterns');
  const prefix = criticalCount > 0 ? 'Growth analysis detected' : 'Growth analysis flagged';
  return `${prefix} ${parts.join(', ')} across ${anomalies.length} ${anomalies.length === 1 ? 'incident' : 'incidents'}.`;
}

function formatShortDate(dateStr: string): string {
  const d = new Date(dateStr);
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}
