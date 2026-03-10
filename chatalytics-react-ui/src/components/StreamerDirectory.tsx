import { useState, useEffect, useRef } from 'react';
import { motion, useScroll, useTransform, useInView } from 'framer-motion';
import { AuthUser, ChannelProfile, ChannelStats, GlobalStats, StreamerRequestSummary } from '../types/message';
import { fetchFeaturedChannels, fetchGlobalStats, fetchPendingRequests, requestStreamer } from '../api/client';
import { useAnimatedNumber } from '../hooks/useAnimatedNumber';
import { useGlobalLiveCounter } from '../hooks/useGlobalLiveCounter';
import TickerNumber from './TickerNumber';
import './StreamerDirectory.css';

interface Props {
  user: AuthUser | null;
}

interface FeaturedEntry {
  channel: ChannelProfile;
  stats: ChannelStats;
}

// ─── Helpers ───

function formatNumber(n: number): string {
  return n.toLocaleString();
}

function formatCompact(n: number): string {
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(1).replace(/\.0$/, '') + 'M';
  if (n >= 1_000) return (n / 1_000).toFixed(1).replace(/\.0$/, '') + 'K';
  return n.toLocaleString();
}

function formatHour(hour: number | null): string {
  if (hour === null) return '';
  const suffix = hour >= 12 ? 'PM' : 'AM';
  const h = hour % 12 || 12;
  return `${h} ${suffix}`;
}

/** Pick `count` items from `list` with daily rotation, preserving order stability */
function pickDaily<T>(list: T[], count: number): T[] {
  if (list.length <= count) return list;
  const today = new Date();
  const dayHash = today.getFullYear() * 10000 + (today.getMonth() + 1) * 100 + today.getDate();
  const offset = dayHash % list.length;
  const picked: T[] = [];
  for (let i = 0; i < count; i++) {
    picked.push(list[(offset + i) % list.length]);
  }
  return picked;
}

const arrow = (
  <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
    <path d="M6 3l5 5-5 5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);

// ─── Sub-components ───

function HeroCounter({ value }: { value: number }) {
  return <TickerNumber value={value} size="hero" />;
}

/**
 * Layout A — "Volume"
 * Left-aligned. Focuses on total messages with editorial context.
 */
function FeatureVolume({ channel, stats, index, onClick }: FeaturedEntry & { index: number; onClick: () => void }) {
  const ref = useRef<HTMLElement>(null);
  const numRef = useRef<HTMLSpanElement>(null);
  const isInView = useInView(numRef, { once: true, amount: 0.5 });
  const animated = useAnimatedNumber(isInView ? stats.totalMessages : 0, 1600);
  const { scrollYProgress } = useScroll({ target: ref, offset: ['start end', 'end start'] });
  const glowY = useTransform(scrollYProgress, [0, 1], [100, -100]);

  const topGame = stats.topGames?.[0]?.gameName;

  return (
    <section ref={ref} className="dir-feature dir-feature--left">
      <motion.div className="dir-feature__glow" style={{ y: glowY }} />
      <div className="dir-feature__inner dir-feature__inner--left">
        <motion.span className="dir-feature__index" initial={{ opacity: 0 }} whileInView={{ opacity: 1 }} viewport={{ once: true }} transition={{ duration: 0.8 }}>
          {String(index).padStart(2, '0')}
        </motion.span>

        <motion.div className="dir-feature__identity" initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} transition={{ duration: 0.6, delay: 0.1 }}>
          {channel.profileImageUrl && <img src={channel.profileImageUrl} alt={channel.displayName} className="dir-feature__avatar dir-feature__avatar--sm" />}
          <span className="dir-feature__identity-name">{channel.displayName}</span>
        </motion.div>

        <motion.div className="dir-feature__big-stat" initial={{ opacity: 0, y: 40 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true, amount: 0.3 }} transition={{ duration: 0.8, delay: 0.2 }}>
          <span ref={numRef} className="dir-feature__big-number">{formatNumber(animated)}</span>
          <span className="dir-feature__big-label">messages captured</span>
        </motion.div>

        <motion.p className="dir-feature__description" initial={{ opacity: 0, y: 16 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} transition={{ duration: 0.6, delay: 0.4 }}>
          Every message tells a story. From hype trains to heated debates,
          we've captured {formatCompact(stats.totalMessages)} messages from {channel.displayName}'s
          chat{topGame ? ` — mostly while streaming ${topGame}` : ''}.
          {stats.avgMessagesPerSession > 0 && ` That's roughly ${formatCompact(Math.round(stats.avgMessagesPerSession))} messages per stream.`}
        </motion.p>

        <motion.button className="dir-feature__cta" initial={{ opacity: 0 }} whileInView={{ opacity: 1 }} viewport={{ once: true }} transition={{ duration: 0.5, delay: 0.55 }} onClick={onClick}>
          Explore channel {arrow}
        </motion.button>
      </div>
    </section>
  );
}

/**
 * Layout B — "Community"
 * Centered portrait. Focuses on unique chatters and community identity.
 */
function FeatureCommunity({ channel, stats, index, onClick }: FeaturedEntry & { index: number; onClick: () => void }) {
  const ref = useRef<HTMLElement>(null);
  const numRef = useRef<HTMLSpanElement>(null);
  const isInView = useInView(numRef, { once: true, amount: 0.5 });
  const animated = useAnimatedNumber(isInView ? stats.uniqueChatters : 0, 1600);
  const { scrollYProgress } = useScroll({ target: ref, offset: ['start end', 'end start'] });
  const glowY = useTransform(scrollYProgress, [0, 1], [-80, 80]);

  return (
    <section ref={ref} className="dir-feature dir-feature--center">
      <motion.div className="dir-feature__glow dir-feature__glow--right" style={{ y: glowY }} />
      <div className="dir-feature__inner dir-feature__inner--center">
        <motion.span className="dir-feature__index" initial={{ opacity: 0 }} whileInView={{ opacity: 1 }} viewport={{ once: true }} transition={{ duration: 0.8 }}>
          {String(index).padStart(2, '0')}
        </motion.span>

        <motion.div className="dir-feature__portrait" initial={{ opacity: 0, scale: 0.92 }} whileInView={{ opacity: 1, scale: 1 }} viewport={{ once: true, amount: 0.3 }} transition={{ duration: 0.7, delay: 0.1 }}>
          {channel.profileImageUrl && <img src={channel.profileImageUrl} alt={channel.displayName} className="dir-feature__avatar dir-feature__avatar--lg" />}
        </motion.div>

        <motion.h2 className="dir-feature__name" initial={{ opacity: 0, y: 24 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true, amount: 0.3 }} transition={{ duration: 0.7, delay: 0.2 }}>
          {channel.displayName}
        </motion.h2>

        <motion.div className="dir-feature__narrative" initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} transition={{ duration: 0.7, delay: 0.35 }}>
          <span ref={numRef} className="dir-feature__inline-number">{formatNumber(animated)}</span>
          <span className="dir-feature__inline-label"> unique voices in chat</span>
        </motion.div>

        <motion.p className="dir-feature__description" initial={{ opacity: 0, y: 16 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} transition={{ duration: 0.6, delay: 0.48 }}>
          {formatCompact(stats.uniqueChatters)} different people have shown up to participate in
          {' '}{channel.displayName}'s community.
          {stats.avgChattersPerSession > 0 && ` On average, ${formatCompact(Math.round(stats.avgChattersPerSession))} chatters join each stream`}
          {stats.peakHour !== null ? `, with the most activity around ${formatHour(stats.peakHour)}.` : '.'}
        </motion.p>

        <motion.button className="dir-feature__cta" initial={{ opacity: 0 }} whileInView={{ opacity: 1 }} viewport={{ once: true }} transition={{ duration: 0.5, delay: 0.6 }} onClick={onClick}>
          Explore channel {arrow}
        </motion.button>
      </div>
    </section>
  );
}

/**
 * Layout C — "Rhythm"
 * Right-aligned. Focuses on streaming cadence — sessions and duration.
 */
function FeatureRhythm({ channel, stats, index, onClick }: FeaturedEntry & { index: number; onClick: () => void }) {
  const ref = useRef<HTMLElement>(null);
  const numRef = useRef<HTMLSpanElement>(null);
  const isInView = useInView(numRef, { once: true, amount: 0.5 });
  const animated = useAnimatedNumber(isInView ? stats.totalSessions : 0, 1600);
  const { scrollYProgress } = useScroll({ target: ref, offset: ['start end', 'end start'] });
  const glowY = useTransform(scrollYProgress, [0, 1], [60, -120]);

  const avgHours = stats.avgStreamDurationMinutes ? Math.round(stats.avgStreamDurationMinutes / 60) : null;
  const topGame = stats.topGames?.[0]?.gameName;

  return (
    <section ref={ref} className="dir-feature dir-feature--right">
      <motion.div className="dir-feature__glow dir-feature__glow--left" style={{ y: glowY }} />
      <div className="dir-feature__inner dir-feature__inner--right">
        <motion.span className="dir-feature__index" initial={{ opacity: 0 }} whileInView={{ opacity: 1 }} viewport={{ once: true }} transition={{ duration: 0.8 }}>
          {String(index).padStart(2, '0')}
        </motion.span>

        <motion.div className="dir-feature__identity dir-feature__identity--right" initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} transition={{ duration: 0.6, delay: 0.1 }}>
          {channel.profileImageUrl && <img src={channel.profileImageUrl} alt={channel.displayName} className="dir-feature__avatar dir-feature__avatar--sm" />}
          <span className="dir-feature__identity-name">{channel.displayName}</span>
        </motion.div>

        <motion.div className="dir-feature__big-stat" initial={{ opacity: 0, y: 40 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true, amount: 0.3 }} transition={{ duration: 0.8, delay: 0.2 }}>
          <span ref={numRef} className="dir-feature__big-number">{formatNumber(animated)}</span>
          <span className="dir-feature__big-label">streams tracked</span>
        </motion.div>

        <motion.p className="dir-feature__description" initial={{ opacity: 0, y: 16 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} transition={{ duration: 0.6, delay: 0.4 }}>
          {avgHours !== null && avgHours > 0
            ? `Averaging roughly ${avgHours} hours per session, ${channel.displayName} brings consistency to their community. `
            : `${channel.displayName} is a regular presence on Twitch. `}
          {topGame ? `Most streams feature ${topGame}. ` : ''}
          {stats.totalMessages > 0 && `Across all sessions, we've captured ${formatCompact(stats.totalMessages)} total messages.`}
        </motion.p>

        <motion.button className="dir-feature__cta" initial={{ opacity: 0 }} whileInView={{ opacity: 1 }} viewport={{ once: true }} transition={{ duration: 0.5, delay: 0.55 }} onClick={onClick}>
          Explore channel {arrow}
        </motion.button>
      </div>
    </section>
  );
}

const LAYOUTS = [FeatureVolume, FeatureCommunity, FeatureRhythm];

// ─── Main component ───

export default function StreamerDirectory({ user }: Props) {
  const [globalStats, setGlobalStats] = useState<GlobalStats | null>(null);
  const [featuredData, setFeaturedData] = useState<FeaturedEntry[]>([]);
  const [pendingRequests, setPendingRequests] = useState<StreamerRequestSummary[]>([]);
  const [votedFor, setVotedFor] = useState<Set<string>>(new Set());
  const [loading, setLoading] = useState(true);
  const { totalMessages: liveTotalMessages, connected: liveConnected } = useGlobalLiveCounter();

  const heroRef = useRef<HTMLElement>(null);
  const { scrollYProgress: heroScroll } = useScroll({
    target: heroRef,
    offset: ['start start', 'end start'],
  });
  const heroContentY = useTransform(heroScroll, [0, 1], [0, 80]);
  const heroGlowY = useTransform(heroScroll, [0, 1], [0, -160]);
  const heroOpacity = useTransform(heroScroll, [0, 0.8], [1, 0]);

  useEffect(() => {
    Promise.all([fetchFeaturedChannels(), fetchGlobalStats(), fetchPendingRequests()])
      .then(([featured, gs, pr]) => {
        setGlobalStats(gs);
        setPendingRequests(pr);

        // Backend returns top 8 channels pre-sorted by message count
        const withData: FeaturedEntry[] = featured
          .filter(f => f.stats.totalMessages > 0 && f.stats.totalSessions > 0)
          .map(f => ({ channel: f.channel, stats: f.stats }));

        // Daily-rotate within the top candidates
        const picked = pickDaily(withData, 3);
        setFeaturedData(picked);
      })
      .finally(() => setLoading(false));
  }, []);

  const handleVote = async (login: string) => {
    try {
      const result = await requestStreamer(login);
      setVotedFor(prev => new Set(prev).add(login.toLowerCase()));
      if (result.added) {
        const updatedRequests = await fetchPendingRequests();
        setPendingRequests(updatedRequests);
      } else if (result.voted) {
        setPendingRequests(prev =>
          prev.map(r =>
            r.streamerLogin === login.toLowerCase()
              ? { ...r, voteCount: result.voteCount }
              : r,
          ),
        );
      }
    } catch {
      // requires login
    }
  };

  const navigateToChannel = (login: string) => {
    window.history.pushState(null, '', `/channel/${login}`);
    window.dispatchEvent(new PopStateEvent('popstate'));
  };

  if (loading) {
    return (
      <div className="dir">
        <svg className="dir-noise" aria-hidden="true">
          <filter id="dir-grain">
            <feTurbulence type="fractalNoise" baseFrequency="0.65" numOctaves="3" stitchTiles="stitch" />
            <feColorMatrix type="saturate" values="0" />
          </filter>
          <rect width="100%" height="100%" filter="url(#dir-grain)" />
        </svg>
        {/* Skeleton hero */}
        <section className="dir-hero">
          <div className="dir-hero__content" style={{ opacity: 1 }}>
            <div className="dir-skel dir-skel--badge" />
            <div className="dir-skel dir-skel--number" />
            <div className="dir-skel dir-skel--label" />
            <div className="dir-skel dir-skel--subtitle" />
            <div className="dir-skel-pills">
              <div className="dir-skel dir-skel--pill" />
              <div className="dir-skel dir-skel--pill" />
              <div className="dir-skel dir-skel--pill" />
            </div>
          </div>
        </section>
        {/* Skeleton interlude */}
        <section className="dir-interlude">
          <div className="dir-interlude__inner">
            <div className="dir-skel dir-skel--heading" />
            <div className="dir-skel dir-skel--text" />
            <div className="dir-skel dir-skel--text dir-skel--text-short" />
          </div>
        </section>
      </div>
    );
  }

  return (
    <div className="dir">
      {/* SVG noise texture — breaks up gradient banding */}
      <svg className="dir-noise" aria-hidden="true">
        <filter id="dir-grain">
          <feTurbulence type="fractalNoise" baseFrequency="0.65" numOctaves="3" stitchTiles="stitch" />
          <feColorMatrix type="saturate" values="0" />
        </filter>
        <rect width="100%" height="100%" filter="url(#dir-grain)" />
      </svg>

      {/* ═══ Hero ═══ */}
      <section ref={heroRef} className="dir-hero">
        <motion.div className="dir-hero__glow" style={{ y: heroGlowY }} />

        {/* Fireflies — ambient floating particles */}
        <div className="dir-fireflies" aria-hidden="true">
          <div className="dir-firefly dir-firefly--g1" />
          <div className="dir-firefly dir-firefly--g2" />
          <div className="dir-firefly dir-firefly--g3" />
          <div className="dir-firefly dir-firefly--g4" />
          <div className="dir-firefly dir-firefly--g5" />
          <div className="dir-firefly dir-firefly--g6" />
          <div className="dir-firefly dir-firefly--a1" />
          <div className="dir-firefly dir-firefly--a2" />
          <div className="dir-firefly dir-firefly--a3" />
          <div className="dir-firefly dir-firefly--a4" />
          <div className="dir-firefly dir-firefly--a5" />
          <div className="dir-firefly dir-firefly--a6" />
        </div>

        <motion.div className="dir-hero__content" style={{ y: heroContentY, opacity: heroOpacity }}>
          <motion.span className="dir-hero__index" initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 1, delay: 0.2 }}>
            01
          </motion.span>

          <motion.div className={`dir-hero__badge${liveConnected ? ' dir-hero__badge--live' : ''}`} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.6, delay: 0.3 }}>
            {liveConnected && <span className="dir-hero__live-dot" />}
            Live Analytics
          </motion.div>

          {globalStats && (
            <motion.div className="dir-hero__number-block" initial={{ opacity: 0, y: 30 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.8, delay: 0.5 }}>
              <HeroCounter value={liveTotalMessages ?? globalStats.totalMessages} />
              <span className="dir-hero__number-label">
                messages analyzed
                {liveConnected && <span className="dir-hero__live-tag">LIVE</span>}
              </span>
            </motion.div>
          )}

          <motion.p className="dir-hero__subtitle" initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.8, delay: 0.9 }}>
            Real-time chat intelligence across {globalStats?.trackedChannels ?? '—'} Twitch
            channels. Every message. Every pattern. Every trend.
          </motion.p>

          {globalStats && (
            <motion.div className="dir-hero__meta-row" initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.6, delay: 1.2 }}>
              <span className="dir-hero__meta-pill">{formatCompact(globalStats.uniqueChatters)} chatters</span>
              <span className="dir-hero__meta-pill">{formatCompact(globalStats.totalStreams)} streams</span>
              <span className="dir-hero__meta-pill">{globalStats.trackedChannels} channels</span>
            </motion.div>
          )}
        </motion.div>

        <motion.div className="dir-hero__scroll" initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 1.8, duration: 0.6 }}>
          <motion.span animate={{ y: [0, 6, 0] }} transition={{ repeat: Infinity, duration: 2, ease: 'easeInOut' }}>
            <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
              <path d="M4 7l6 6 6-6" stroke="rgba(255,255,255,0.3)" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </motion.span>
        </motion.div>
      </section>

      {/* ═══ About Interlude ═══ */}
      <section className="dir-interlude">
        <div className="dir-interlude__inner">
          <motion.h2 className="dir-interlude__heading" initial={{ opacity: 0, y: 24 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true, amount: 0.4 }} transition={{ duration: 0.7 }}>
            What is Chatalytics?
          </motion.h2>
          <motion.p className="dir-interlude__body" initial={{ opacity: 0, y: 16 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true, amount: 0.4 }} transition={{ duration: 0.7, delay: 0.12 }}>
            Chatalytics captures and processes every message across tracked Twitch channels in real time.
            We analyze chat velocity, emote usage, viewer engagement, and community behavior patterns —
            turning millions of raw messages into structured, searchable analytics.
          </motion.p>
          <motion.p className="dir-interlude__body" initial={{ opacity: 0, y: 16 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true, amount: 0.4 }} transition={{ duration: 0.7, delay: 0.24 }}>
            Browse individual streams with full session recaps, explore chatter profiles across channels,
            or dig into long-term trends. Every data point is stored and queryable — nothing gets lost.
          </motion.p>
        </div>
      </section>

      {/* ═══ Streamer Features — unique layouts ═══ */}
      {featuredData.map(({ channel, stats }, i) => {
        const Layout = LAYOUTS[i % LAYOUTS.length];
        return (
          <Layout
            key={channel.id}
            channel={channel}
            stats={stats}
            index={i + 2}
            onClick={() => navigateToChannel(channel.login)}
          />
        );
      })}

      {/* ═══ What We Track ═══ */}
      <section className="dir-capabilities">
        <div className="dir-capabilities__inner">
          <motion.h2 className="dir-capabilities__heading" initial={{ opacity: 0, y: 24 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} transition={{ duration: 0.7 }}>
            What we track
          </motion.h2>
          <motion.p className="dir-capabilities__subtitle" initial={{ opacity: 0 }} whileInView={{ opacity: 1 }} viewport={{ once: true }} transition={{ duration: 0.6, delay: 0.1 }}>
            Every metric is captured automatically, in real time, with no manual setup required.
          </motion.p>

          <div className="dir-capabilities__grid">
            {[
              {
                title: 'Messages & Patterns',
                body: 'Every chat message is stored and indexed. Search by author, keyword, or time range. Track message velocity spikes and chat trends across sessions.',
              },
              {
                title: 'Community Profiles',
                body: 'Individual chatter profiles built from activity across all tracked channels. See message counts, first appearance dates, and cross-channel behavior.',
              },
              {
                title: 'Stream Sessions',
                body: 'Automatic session detection with full recaps — total messages, unique chatters, peak moments, top emotes, and AI-generated stream summaries.',
              },
              {
                title: 'Real-Time Processing',
                body: 'Messages flow through a Kafka pipeline for instant processing. Stats update continuously as streams happen — no batch jobs, no delays.',
              },
            ].map((item, i) => (
              <motion.div
                key={item.title}
                className="dir-capabilities__card"
                initial={{ opacity: 0, y: 20 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true, amount: 0.3 }}
                transition={{ duration: 0.5, delay: i * 0.08 }}
              >
                <h3 className="dir-capabilities__card-title">{item.title}</h3>
                <p className="dir-capabilities__card-body">{item.body}</p>
              </motion.div>
            ))}
          </div>
        </div>
      </section>

      {/* ═══ Community Requests ═══ */}
      {pendingRequests.length > 0 && (
        <section className="dir-requests">
          <div className="dir-requests__inner">
            <motion.h2 className="dir-requests__title" initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} transition={{ duration: 0.6 }}>
              Community Requests
            </motion.h2>
            <motion.p className="dir-requests__subtitle" initial={{ opacity: 0 }} whileInView={{ opacity: 1 }} viewport={{ once: true }} transition={{ duration: 0.5, delay: 0.1 }}>
              Don't see your favorite streamer? Request them below and vote to help prioritize.
              Once a channel reaches 10 votes, we start tracking automatically.
            </motion.p>

            <div className="dir-requests__list">
              {pendingRequests.map((request, i) => (
                <motion.div
                  key={request.streamerId}
                  className="dir-requests__card"
                  initial={{ opacity: 0, y: 16 }}
                  whileInView={{ opacity: 1, y: 0 }}
                  viewport={{ once: true }}
                  transition={{ duration: 0.4, delay: i * 0.06 }}
                >
                  {request.profileImageUrl && (
                    <img src={request.profileImageUrl} alt={request.displayName} className="dir-requests__avatar" />
                  )}
                  <div className="dir-requests__info">
                    <span className="dir-requests__name">{request.displayName}</span>
                    <div className="dir-requests__bar">
                      <div className="dir-requests__bar-fill" style={{ width: `${Math.min((request.voteCount / 10) * 100, 100)}%` }} />
                    </div>
                    <span className="dir-requests__votes">{request.voteCount}/10 votes</span>
                  </div>
                  <button
                    className={`dir-requests__vote-btn${!user ? ' dir-requests__vote-btn--disabled' : ''}${votedFor.has(request.streamerLogin.toLowerCase()) ? ' dir-requests__vote-btn--voted' : ''}`}
                    onClick={() => user && handleVote(request.streamerLogin)}
                    disabled={!user || votedFor.has(request.streamerLogin.toLowerCase())}
                    title={!user ? 'Login to vote' : undefined}
                  >
                    {votedFor.has(request.streamerLogin.toLowerCase()) ? 'Voted' : 'Vote'}
                  </button>
                </motion.div>
              ))}
            </div>

            {!user && (
              <p className="dir-requests__hint">Log in with Twitch to vote for streamers</p>
            )}

            <div className="dir-requests__cta">
              <button
                className="dir-requests__view-all"
                onClick={() => {
                  window.history.pushState(null, '', '/suggested');
                  window.dispatchEvent(new PopStateEvent('popstate'));
                }}
              >
                View all requests {arrow}
              </button>
            </div>
          </div>
        </section>
      )}
    </div>
  );
}
