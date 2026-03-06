import './EmptyState.css';

interface EmptyStateProps {
  hasSearched: boolean;
}

export default function EmptyState({ hasSearched }: EmptyStateProps) {
  return (
    <div className="empty-state">
      {hasSearched
        ? 'No messages found for that user.'
        : 'Enter a username to search chat logs.'}
    </div>
  );
}
