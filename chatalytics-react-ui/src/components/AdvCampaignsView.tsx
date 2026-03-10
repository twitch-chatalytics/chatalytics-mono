import { useCallback, useEffect, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Campaign, CampaignReport } from '../types/message';
import {
  fetchCampaigns,
  createCampaign,
  deleteCampaign,
  fetchCampaignReport,
} from '../api/client';
import { formatCompact, getScoreColor } from './advertiserUtils';

interface AdvCampaignsViewProps {
  twitchId: number;
}

type ViewState =
  | { mode: 'list' }
  | { mode: 'create' }
  | { mode: 'report'; campaignId: number };

export default function AdvCampaignsView({ twitchId }: AdvCampaignsViewProps) {
  const [campaigns, setCampaigns] = useState<Campaign[]>([]);
  const [loading, setLoading] = useState(true);
  const [viewState, setViewState] = useState<ViewState>({ mode: 'list' });

  const loadCampaigns = useCallback(() => {
    setLoading(true);
    fetchCampaigns(twitchId).then(data => {
      setCampaigns(data);
      setLoading(false);
    });
  }, [twitchId]);

  useEffect(() => {
    loadCampaigns();
  }, [loadCampaigns]);

  return (
    <div className="view-container">
      <AnimatePresence mode="wait">
        {viewState.mode === 'list' && (
          <CampaignListView
            key="list"
            campaigns={campaigns}
            loading={loading}
            onCreateNew={() => setViewState({ mode: 'create' })}
            onViewReport={(id) => setViewState({ mode: 'report', campaignId: id })}
            onDelete={(id) => {
              deleteCampaign(id).then(() => loadCampaigns());
            }}
          />
        )}
        {viewState.mode === 'create' && (
          <CreateCampaignView
            key="create"
            twitchId={twitchId}
            onCancel={() => setViewState({ mode: 'list' })}
            onCreated={() => {
              loadCampaigns();
              setViewState({ mode: 'list' });
            }}
          />
        )}
        {viewState.mode === 'report' && (
          <CampaignReportView
            key="report"
            campaignId={viewState.campaignId}
            onBack={() => setViewState({ mode: 'list' })}
          />
        )}
      </AnimatePresence>
    </div>
  );
}

// ─── Campaign List ───

interface CampaignListViewProps {
  campaigns: Campaign[];
  loading: boolean;
  onCreateNew: () => void;
  onViewReport: (id: number) => void;
  onDelete: (id: number) => void;
}

function CampaignListView({ campaigns, loading, onCreateNew, onViewReport, onDelete }: CampaignListViewProps) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.2 }}
    >
      <h1 className="view-title">Campaign Verification</h1>

      <div className="adv-card">
        <p className="card-desc" style={{ margin: '0 0 20px' }}>
          Tag sponsored streams and verify real engagement was delivered.
        </p>
        <button className="camp-create-btn" onClick={onCreateNew}>
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round">
            <line x1="8" y1="3" x2="8" y2="13" />
            <line x1="3" y1="8" x2="13" y2="8" />
          </svg>
          New Campaign
        </button>
      </div>

      {loading && (
        <div className="adv-loading" style={{ minHeight: '200px' }}>
          <div className="recap-spinner" />
          Loading campaigns...
        </div>
      )}

      {!loading && campaigns.length === 0 && (
        <motion.div
          className="adv-card"
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3, delay: 0.05 }}
        >
          <div className="cmp-empty-state">
            <svg width="48" height="48" viewBox="0 0 48 48" fill="none" stroke="#333" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
              <rect x="8" y="6" width="32" height="36" rx="3" />
              <line x1="16" y1="14" x2="32" y2="14" />
              <line x1="16" y1="22" x2="28" y2="22" />
              <line x1="16" y1="30" x2="24" y2="30" />
            </svg>
            <p className="cmp-empty-title">No campaigns yet</p>
            <p className="cmp-empty-desc">Create a campaign to tag sponsored streams and verify that real engagement was delivered to the brand.</p>
          </div>
        </motion.div>
      )}

      {!loading && campaigns.length > 0 && (
        <div className="camp-list">
          {campaigns.map((c, i) => (
            <motion.div
              key={c.id}
              className="adv-card camp-card"
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.2, delay: i * 0.03 }}
            >
              <div className="camp-card-header">
                <div className="camp-card-info">
                  <span className="camp-card-name">{c.campaignName}</span>
                  {c.brandName && <span className="camp-card-brand">{c.brandName}</span>}
                </div>
                <button
                  className="camp-delete-btn"
                  onClick={(e) => { e.stopPropagation(); onDelete(c.id); }}
                  aria-label="Delete campaign"
                >
                  <svg width="14" height="14" viewBox="0 0 14 14" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round">
                    <line x1="3" y1="3" x2="11" y2="11" />
                    <line x1="11" y1="3" x2="3" y2="11" />
                  </svg>
                </button>
              </div>
              <div className="camp-card-dates">
                {formatDate(c.startDate)} &ndash; {formatDate(c.endDate)}
              </div>
              {c.brandKeywords && c.brandKeywords.length > 0 && (
                <div className="camp-card-keywords">
                  {c.brandKeywords.map((kw, ki) => (
                    <span key={ki} className="camp-keyword-pill">{kw}</span>
                  ))}
                </div>
              )}
              <button className="camp-report-btn" onClick={() => onViewReport(c.id)}>
                View Report
                <svg width="14" height="14" viewBox="0 0 14 14" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                  <line x1="3" y1="7" x2="11" y2="7" />
                  <polyline points="7.5,3.5 11,7 7.5,10.5" />
                </svg>
              </button>
            </motion.div>
          ))}
        </div>
      )}
    </motion.div>
  );
}

// ─── Create Campaign Form ───

interface CreateCampaignViewProps {
  twitchId: number;
  onCancel: () => void;
  onCreated: () => void;
}

function CreateCampaignView({ twitchId, onCancel, onCreated }: CreateCampaignViewProps) {
  const [campaignName, setCampaignName] = useState('');
  const [brandName, setBrandName] = useState('');
  const [brandKeywordsStr, setBrandKeywordsStr] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [dealPrice, setDealPrice] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = () => {
    if (!campaignName.trim() || !startDate || !endDate) return;
    setSubmitting(true);

    const keywords = brandKeywordsStr
      .split(',')
      .map(k => k.trim())
      .filter(k => k.length > 0);

    createCampaign(twitchId, {
      campaignName: campaignName.trim(),
      brandName: brandName.trim() || undefined,
      brandKeywords: keywords,
      startDate,
      endDate,
      dealPrice: dealPrice ? parseFloat(dealPrice) : undefined,
    })
      .then(() => onCreated())
      .catch(() => setSubmitting(false));
  };

  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.2 }}
    >
      <h1 className="view-title">New Campaign</h1>

      <div className="adv-card">
        <div className="camp-form">
          <div className="camp-form-row">
            <div className="av-input-group" style={{ maxWidth: '100%' }}>
              <label className="av-input-label">Campaign Name *</label>
              <input
                className="av-input"
                type="text"
                placeholder="e.g. Q1 Energy Drink Sponsorship"
                value={campaignName}
                onChange={e => setCampaignName(e.target.value)}
              />
            </div>
          </div>

          <div className="camp-form-row">
            <div className="av-input-group" style={{ maxWidth: '100%' }}>
              <label className="av-input-label">Brand Name</label>
              <input
                className="av-input"
                type="text"
                placeholder="e.g. GamerFuel"
                value={brandName}
                onChange={e => setBrandName(e.target.value)}
              />
            </div>
          </div>

          <div className="camp-form-row">
            <div className="av-input-group" style={{ maxWidth: '100%' }}>
              <label className="av-input-label">Brand Keywords (comma-separated)</label>
              <input
                className="av-input"
                type="text"
                placeholder="e.g. gamerfuel, energy, #ad"
                value={brandKeywordsStr}
                onChange={e => setBrandKeywordsStr(e.target.value)}
              />
            </div>
          </div>

          <div className="camp-form-row camp-form-row-split">
            <div className="av-input-group">
              <label className="av-input-label">Start Date *</label>
              <input
                className="av-input camp-date-input"
                type="date"
                value={startDate}
                onChange={e => setStartDate(e.target.value)}
              />
            </div>
            <div className="av-input-group">
              <label className="av-input-label">End Date *</label>
              <input
                className="av-input camp-date-input"
                type="date"
                value={endDate}
                onChange={e => setEndDate(e.target.value)}
              />
            </div>
          </div>

          <div className="camp-form-row">
            <div className="av-input-group">
              <label className="av-input-label">Deal Price ($)</label>
              <input
                className="av-input"
                type="number"
                placeholder="e.g. 5000"
                value={dealPrice}
                onChange={e => setDealPrice(e.target.value)}
                min="0"
                step="0.01"
              />
            </div>
          </div>

          <div className="camp-form-actions">
            <button className="camp-cancel-btn" onClick={onCancel} disabled={submitting}>
              Cancel
            </button>
            <button
              className="camp-submit-btn"
              onClick={handleSubmit}
              disabled={submitting || !campaignName.trim() || !startDate || !endDate}
            >
              {submitting ? 'Creating...' : 'Create Campaign'}
            </button>
          </div>
        </div>
      </div>
    </motion.div>
  );
}

// ─── Campaign Report ───

interface CampaignReportViewProps {
  campaignId: number;
  onBack: () => void;
}

function CampaignReportView({ campaignId, onBack }: CampaignReportViewProps) {
  const [report, setReport] = useState<CampaignReport | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    setError(null);
    fetchCampaignReport(campaignId)
      .then(data => {
        if (!data) {
          setError('Failed to generate report');
        } else {
          setReport(data);
        }
        setLoading(false);
      })
      .catch(() => {
        setError('Failed to load report');
        setLoading(false);
      });
  }, [campaignId]);

  if (loading) {
    return (
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
      >
        <div className="adv-loading" style={{ minHeight: '300px' }}>
          <div className="recap-spinner" />
          Generating campaign report...
        </div>
      </motion.div>
    );
  }

  if (error || !report) {
    return (
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
      >
        <button className="camp-back-btn" onClick={onBack}>
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <polyline points="8.5,3 4.5,7 8.5,11" />
          </svg>
          Back to Campaigns
        </button>
        <div className="adv-error" style={{ minHeight: '200px' }}>
          {error || 'No report data available'}
        </div>
      </motion.div>
    );
  }

  const c = report.campaign;
  const deltaIsNegative = report.scoreDelta < 0;
  const overpayPct = report.reportedCpm != null && report.realCpm != null && report.reportedCpm > 0
    ? Math.round(((report.realCpm - report.reportedCpm) / report.reportedCpm) * 100)
    : null;

  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.2 }}
    >
      <button className="camp-back-btn" onClick={onBack}>
        <svg width="14" height="14" viewBox="0 0 14 14" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
          <polyline points="8.5,3 4.5,7 8.5,11" />
        </svg>
        Back to Campaigns
      </button>

      <h1 className="view-title">{c.campaignName}</h1>

      {/* Campaign header */}
      <motion.div
        className="adv-card"
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3 }}
      >
        <div className="camp-report-header">
          {c.brandName && <span className="camp-report-brand">{c.brandName}</span>}
          <span className="camp-report-dates">{formatDate(c.startDate)} &ndash; {formatDate(c.endDate)}</span>
        </div>
      </motion.div>

      {/* Stats row */}
      <motion.div
        className="adv-card"
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3, delay: 0.05 }}
      >
        <h3 className="card-label">Campaign Metrics</h3>
        <div className="camp-stats-grid">
          <div className="camp-stat">
            <span className="camp-stat-value">{report.sponsoredSessions}</span>
            <span className="camp-stat-label">Sponsored Sessions</span>
          </div>
          <div className="camp-stat">
            <span className="camp-stat-value" style={{ color: getScoreColor(report.baselineAvgScore) }}>
              {report.baselineAvgScore.toFixed(1)}
            </span>
            <span className="camp-stat-label">Baseline Score</span>
          </div>
          <div className="camp-stat">
            <span className="camp-stat-value" style={{ color: getScoreColor(report.sponsoredAvgScore) }}>
              {report.sponsoredAvgScore.toFixed(1)}
            </span>
            <span className="camp-stat-label">Sponsored Score</span>
          </div>
          <div className="camp-stat">
            <span className={`camp-delta-badge ${deltaIsNegative ? 'camp-delta-negative' : 'camp-delta-positive'}`}>
              {report.scoreDelta > 0 ? '+' : ''}{report.scoreDelta.toFixed(1)}
            </span>
            <span className="camp-stat-label">Score Delta</span>
          </div>
          <div className="camp-stat">
            <span className="camp-stat-value">{report.brandMentions.toLocaleString()}</span>
            <span className="camp-stat-label">Brand Mentions</span>
          </div>
          <div className="camp-stat">
            <span className="camp-stat-value">{(report.brandMentionRate * 100).toFixed(2)}%</span>
            <span className="camp-stat-label">Mention Rate</span>
          </div>
        </div>
      </motion.div>

      {/* Score delta interpretation */}
      <motion.div
        className={`adv-card camp-interpretation ${deltaIsNegative ? 'camp-interpretation-warning' : 'camp-interpretation-success'}`}
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3, delay: 0.1 }}
      >
        <div className="camp-interpretation-row">
          <span className="camp-interpretation-icon">
            {deltaIsNegative ? (
              <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round">
                <circle cx="10" cy="10" r="8" />
                <line x1="10" y1="6" x2="10" y2="10.5" />
                <circle cx="10" cy="13.5" r="0.7" fill="currentColor" stroke="none" />
              </svg>
            ) : (
              <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round">
                <circle cx="10" cy="10" r="8" />
                <polyline points="6.5,10 9,12.5 13.5,7.5" />
              </svg>
            )}
          </span>
          <span className="camp-interpretation-text">
            {deltaIsNegative
              ? 'Authenticity dropped during sponsored streams. The audience quality may be lower when sponsored content is active.'
              : 'Authenticity held steady during sponsored streams. The channel maintained genuine engagement throughout the campaign.'}
          </span>
        </div>
      </motion.div>

      {/* Real Impressions */}
      <motion.div
        className="adv-card"
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3, delay: 0.15 }}
      >
        <h3 className="card-label">Estimated Real Impressions</h3>
        <div className="camp-impressions-row">
          <span className="camp-impressions-value">{formatCompact(report.estimatedRealImpressions)}</span>
          <span className="camp-impressions-desc">
            Real impressions delivered, adjusted for authenticity score per session.
          </span>
        </div>
      </motion.div>

      {/* CPM comparison */}
      {report.reportedCpm != null && report.realCpm != null && (
        <motion.div
          className="adv-card"
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3, delay: 0.2 }}
        >
          <h3 className="card-label">CPM Comparison</h3>
          <p className="card-desc">
            Comparing cost-per-thousand impressions based on reported vs. authenticity-adjusted viewer counts.
          </p>
          <div className="av-cpm-results">
            <div className="av-cpm-result">
              <span className="av-cpm-value">${report.reportedCpm.toFixed(2)}</span>
              <span className="av-cpm-label">Reported CPM</span>
            </div>
            <div className="av-cpm-result av-cpm-result-real">
              <span className="av-cpm-value">${report.realCpm.toFixed(2)}</span>
              <span className="av-cpm-label">Real CPM</span>
            </div>
            {overpayPct != null && overpayPct > 0 && (
              <div className="av-discount-block">
                <span className={`av-overpay-badge ${overpayPct > 50 ? 'av-overpay-high' : overpayPct > 20 ? 'av-overpay-med' : 'av-overpay-low'}`}>
                  +{overpayPct}%
                </span>
                <span className="av-viewer-label">Overpay</span>
              </div>
            )}
          </div>
        </motion.div>
      )}
    </motion.div>
  );
}

function formatDate(dateStr: string): string {
  const d = new Date(dateStr + 'T00:00:00');
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
}
