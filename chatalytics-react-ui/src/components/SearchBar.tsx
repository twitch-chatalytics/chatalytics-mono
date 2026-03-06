import { useEffect, useRef, useState } from 'react';
import { DateRange, searchAuthors } from '../api/client';
import './SearchBar.css';

type PresetKey = 'all' | '15m' | '1h' | '6h' | '24h' | '7d' | 'custom';

const PRESETS: { key: PresetKey; label: string; minutes?: number }[] = [
  { key: 'all', label: 'All time' },
  { key: '15m', label: '15 min', minutes: 15 },
  { key: '1h', label: '1 hour', minutes: 60 },
  { key: '6h', label: '6 hours', minutes: 360 },
  { key: '24h', label: '24 hours', minutes: 1440 },
  { key: '7d', label: '7 days', minutes: 10080 },
  { key: 'custom', label: 'Custom' },
];

function toLocalDatetime(date: Date): string {
  const offset = date.getTimezoneOffset();
  const local = new Date(date.getTime() - offset * 60000);
  return local.toISOString().slice(0, 16);
}

interface SearchBarProps {
  onSearch: (username: string) => void;
  onDateRangeChange: (range: DateRange) => void;
  isLoading: boolean;
  initialValue?: string;
}

export default function SearchBar({ onSearch, onDateRangeChange, isLoading, initialValue }: SearchBarProps) {
  const [username, setUsername] = useState(initialValue ?? '');
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [highlightIndex, setHighlightIndex] = useState(-1);
  const [showDatePicker, setShowDatePicker] = useState(false);
  const [activePreset, setActivePreset] = useState<PresetKey>('all');
  const [customFrom, setCustomFrom] = useState('');
  const [customTo, setCustomTo] = useState('');
  const debounceRef = useRef<ReturnType<typeof setTimeout>>(null);
  const wrapperRef = useRef<HTMLDivElement>(null);
  const datePickerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (initialValue !== undefined) setUsername(initialValue);
  }, [initialValue]);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) {
        setShowSuggestions(false);
      }
      if (datePickerRef.current && !datePickerRef.current.contains(e.target as Node)) {
        setShowDatePicker(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleChange = (value: string) => {
    setUsername(value);
    setHighlightIndex(-1);

    if (debounceRef.current) clearTimeout(debounceRef.current);

    if (value.trim().length < 2) {
      setSuggestions([]);
      setShowSuggestions(false);
      return;
    }

    debounceRef.current = setTimeout(async () => {
      const results = await searchAuthors(value.trim());
      setSuggestions(results);
      setShowSuggestions(results.length > 0);
    }, 250);
  };

  const selectSuggestion = (name: string) => {
    setUsername(name);
    setSuggestions([]);
    setShowSuggestions(false);
    onSearch(name);
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = username.trim();
    if (trimmed) {
      setShowSuggestions(false);
      onSearch(trimmed);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (!showSuggestions || suggestions.length === 0) return;

    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setHighlightIndex((i) => (i < suggestions.length - 1 ? i + 1 : 0));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setHighlightIndex((i) => (i > 0 ? i - 1 : suggestions.length - 1));
    } else if (e.key === 'Enter' && highlightIndex >= 0) {
      e.preventDefault();
      selectSuggestion(suggestions[highlightIndex]);
    } else if (e.key === 'Escape') {
      setShowSuggestions(false);
    }
  };

  const handlePreset = (preset: (typeof PRESETS)[number]) => {
    setActivePreset(preset.key);

    if (preset.key === 'all') {
      onDateRangeChange({});
      setShowDatePicker(false);
    } else if (preset.key === 'custom') {
      const now = new Date();
      const oneHourAgo = new Date(now.getTime() - 3600000);
      setCustomFrom(toLocalDatetime(oneHourAgo));
      setCustomTo(toLocalDatetime(now));
      onDateRangeChange({
        from: oneHourAgo.toISOString(),
        to: now.toISOString(),
      });
    } else if (preset.minutes) {
      const now = new Date();
      const from = new Date(now.getTime() - preset.minutes * 60000);
      onDateRangeChange({ from: from.toISOString(), to: now.toISOString() });
      setShowDatePicker(false);
    }
  };

  const handleCustomChange = (field: 'from' | 'to', value: string) => {
    const nextFrom = field === 'from' ? value : customFrom;
    const nextTo = field === 'to' ? value : customTo;

    if (field === 'from') setCustomFrom(value);
    if (field === 'to') setCustomTo(value);

    if (nextFrom && nextTo) {
      onDateRangeChange({
        from: new Date(nextFrom).toISOString(),
        to: new Date(nextTo).toISOString(),
      });
    }
  };

  const hasActiveFilter = activePreset !== 'all';

  return (
    <form className="search-bar" onSubmit={handleSubmit}>
      <div className="search-input-wrapper" ref={wrapperRef}>
        <input
          type="text"
          className="search-input"
          placeholder="Search by username..."
          value={username}
          onChange={(e) => handleChange(e.target.value)}
          onFocus={() => suggestions.length > 0 && setShowSuggestions(true)}
          onKeyDown={handleKeyDown}
          disabled={isLoading}
          autoComplete="off"
        />
        <div className="search-actions">
        <div className="search-date-toggle" ref={datePickerRef}>
          <button
            type="button"
            className={`date-toggle-btn${hasActiveFilter ? ' date-toggle-active' : ''}`}
            onClick={() => setShowDatePicker((v) => !v)}
            title="Time range filter"
          >
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
              <circle cx="8" cy="8" r="6.5" stroke="currentColor" strokeWidth="1.5" />
              <path d="M8 4.5V8L10.5 10" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
            </svg>
            {hasActiveFilter && <span className="date-toggle-dot" />}
          </button>

          {showDatePicker && (
            <div className="date-dropdown">
              <div className="date-dropdown-presets">
                {PRESETS.map((preset) => (
                  <button
                    key={preset.key}
                    type="button"
                    className={`preset-btn${activePreset === preset.key ? ' active' : ''}`}
                    onClick={() => handlePreset(preset)}
                  >
                    {preset.label}
                  </button>
                ))}
              </div>

              {activePreset === 'custom' && (
                <div className="date-dropdown-custom">
                  <label className="custom-label">
                    From
                    <input
                      type="datetime-local"
                      className="custom-input"
                      value={customFrom}
                      onChange={(e) => handleCustomChange('from', e.target.value)}
                    />
                  </label>
                  <label className="custom-label">
                    To
                    <input
                      type="datetime-local"
                      className="custom-input"
                      value={customTo}
                      onChange={(e) => handleCustomChange('to', e.target.value)}
                    />
                  </label>
                </div>
              )}
            </div>
          )}
        </div>
        <button
          type="submit"
          className={`search-submit-btn${username.trim() ? ' search-submit-ready' : ''}`}
          disabled={isLoading || !username.trim()}
          title="Search (Enter)"
        >
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
            <path d="M3 8h9M8 4l4 4-4 4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </button>
        </div>

        {showSuggestions && (
          <ul className="suggestions">
            {suggestions.map((name, i) => (
              <li
                key={name}
                className={`suggestion-item${i === highlightIndex ? ' highlighted' : ''}`}
                onMouseDown={() => selectSuggestion(name)}
                onMouseEnter={() => setHighlightIndex(i)}
              >
                {name}
              </li>
            ))}
          </ul>
        )}
      </div>
    </form>
  );
}
