import { useCallback, useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { AlertEvent, AlertRule } from '../types/message';
import {
  fetchAlertRules,
  createAlertRule,
  deleteAlertRule,
  fetchAlertEvents,
  acknowledgeAlertEvent,
} from '../api/client';

interface AdvAlertsViewProps {
  twitchId: number;
}

const ALERT_TYPE_OPTIONS = [
  { value: 'authenticity_drop', label: 'Authenticity Drops Below' },
  { value: 'growth_anomaly', label: 'Growth Anomaly Detected' },
  { value: 'viewer_change', label: 'Significant Viewer Count Change' },
];

function formatAlertTypeLabel(alertType: string, threshold: number | null): string {
  switch (alertType) {
    case 'authenticity_drop':
      return `Authenticity Drops Below ${threshold ?? '...'}`;
    case 'growth_anomaly':
      return 'Growth Anomaly Detected';
    case 'viewer_change':
      return 'Significant Viewer Count Change';
    default:
      return alertType;
  }
}

function timeAgo(dateStr: string): string {
  const now = Date.now();
  const then = new Date(dateStr).getTime();
  const diffMs = now - then;
  const minutes = Math.floor(diffMs / 60000);
  if (minutes < 1) return 'Just now';
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days}d ago`;
  const months = Math.floor(days / 30);
  return `${months}mo ago`;
}

function severityClass(severity: string): string {
  switch (severity) {
    case 'critical': return 'alert-severity-critical';
    case 'warning': return 'alert-severity-warning';
    default: return 'alert-severity-info';
  }
}

export default function AdvAlertsView({ twitchId }: AdvAlertsViewProps) {
  const [rules, setRules] = useState<AlertRule[]>([]);
  const [events, setEvents] = useState<AlertEvent[]>([]);
  const [loading, setLoading] = useState(true);

  // New rule form state
  const [newType, setNewType] = useState('authenticity_drop');
  const [newThreshold, setNewThreshold] = useState('50');
  const [saving, setSaving] = useState(false);

  const loadData = useCallback(async () => {
    if (!twitchId) return;
    setLoading(true);
    try {
      const [rulesData, eventsData] = await Promise.all([
        fetchAlertRules(twitchId),
        fetchAlertEvents(twitchId),
      ]);
      setRules(rulesData);
      setEvents(eventsData);
    } catch {
      // silent
    }
    setLoading(false);
  }, [twitchId]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleAddRule = async () => {
    if (!twitchId || saving) return;
    setSaving(true);
    try {
      const threshold = newType === 'authenticity_drop' ? parseFloat(newThreshold) : null;
      const created = await createAlertRule(twitchId, newType, threshold);
      setRules(prev => [created, ...prev]);
    } catch {
      // silent
    }
    setSaving(false);
  };

  const handleDeleteRule = async (ruleId: number) => {
    try {
      await deleteAlertRule(ruleId);
      setRules(prev => prev.filter(r => r.id !== ruleId));
    } catch {
      // silent
    }
  };

  const handleAcknowledge = async (eventId: number) => {
    try {
      await acknowledgeAlertEvent(eventId);
      setEvents(prev => prev.map(e => e.id === eventId ? { ...e, acknowledged: true } : e));
    } catch {
      // silent
    }
  };

  if (loading) {
    return (
      <div className="view-container">
        <h1 className="view-title">Alerts</h1>
        <div className="adv-card">
          <p className="empty-text">Loading alerts...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="view-container">
      <h1 className="view-title">Alerts</h1>

      {/* Alert Configuration */}
      <motion.div
        className="adv-card"
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3 }}
      >
        <h3 className="card-label">Alert Configuration</h3>

        {rules.length > 0 ? (
          <div className="alert-rules-list">
            {rules.map((rule, i) => (
              <motion.div
                key={rule.id}
                className="alert-rule-row"
                initial={{ opacity: 0, x: -8 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ duration: 0.2, delay: i * 0.03 }}
              >
                <div className="alert-rule-info">
                  <span className={`alert-type-badge alert-type-${rule.alertType}`}>
                    {alertTypeIcon(rule.alertType)}
                  </span>
                  <span className="alert-rule-label">
                    {formatAlertTypeLabel(rule.alertType, rule.thresholdValue)}
                  </span>
                </div>
                <button
                  className="alert-rule-delete"
                  onClick={() => handleDeleteRule(rule.id)}
                  title="Delete rule"
                >
                  <svg width="14" height="14" viewBox="0 0 14 14" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round">
                    <line x1="3" y1="3" x2="11" y2="11" />
                    <line x1="11" y1="3" x2="3" y2="11" />
                  </svg>
                </button>
              </motion.div>
            ))}
          </div>
        ) : (
          <p className="empty-text">No alert rules configured yet.</p>
        )}

        {/* Add Rule Form */}
        <div className="alert-add-form">
          <div className="alert-add-row">
            <div className="av-input-group" style={{ maxWidth: 240 }}>
              <label className="av-input-label">Alert Type</label>
              <select
                className="av-input alert-select"
                value={newType}
                onChange={e => setNewType(e.target.value)}
              >
                {ALERT_TYPE_OPTIONS.map(opt => (
                  <option key={opt.value} value={opt.value}>{opt.label}</option>
                ))}
              </select>
            </div>

            {newType === 'authenticity_drop' && (
              <div className="av-input-group" style={{ maxWidth: 120 }}>
                <label className="av-input-label">Threshold</label>
                <input
                  type="number"
                  className="av-input"
                  placeholder="50"
                  value={newThreshold}
                  onChange={e => setNewThreshold(e.target.value)}
                  min="0"
                  max="100"
                  step="1"
                />
              </div>
            )}

            <button
              className="alert-add-btn"
              onClick={handleAddRule}
              disabled={saving}
            >
              {saving ? 'Adding...' : 'Add Rule'}
            </button>
          </div>
        </div>
      </motion.div>

      {/* Alert History */}
      <motion.div
        className="adv-card"
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3, delay: 0.05 }}
      >
        <h3 className="card-label">Alert History</h3>

        {events.length > 0 ? (
          <div className="alert-events-list">
            {events.map((event, i) => (
              <motion.div
                key={event.id}
                className={`alert-event-row ${!event.acknowledged ? 'alert-event-unread' : ''}`}
                initial={{ opacity: 0, y: 6 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.2, delay: i * 0.02 }}
              >
                <div className="alert-event-left">
                  <span className={`alert-severity-badge ${severityClass(event.severity)}`}>
                    {event.severity}
                  </span>
                  <div className="alert-event-content">
                    <span className="alert-event-type">
                      {alertTypeIcon(event.alertType)}
                      {' '}
                      {ALERT_TYPE_OPTIONS.find(o => o.value === event.alertType)?.label ?? event.alertType}
                    </span>
                    <span className="alert-event-message">{event.message}</span>
                    <span className="alert-event-time">{timeAgo(event.createdAt)}</span>
                  </div>
                </div>
                {!event.acknowledged && (
                  <button
                    className="alert-dismiss-btn"
                    onClick={() => handleAcknowledge(event.id)}
                  >
                    Dismiss
                  </button>
                )}
              </motion.div>
            ))}
          </div>
        ) : (
          <p className="empty-text">No alerts triggered yet.</p>
        )}
      </motion.div>
    </div>
  );
}

function alertTypeIcon(alertType: string): string {
  switch (alertType) {
    case 'authenticity_drop': return '\u26A0';
    case 'growth_anomaly': return '\uD83D\uDCC8';
    case 'viewer_change': return '\uD83D\uDC41';
    default: return '\uD83D\uDD14';
  }
}
