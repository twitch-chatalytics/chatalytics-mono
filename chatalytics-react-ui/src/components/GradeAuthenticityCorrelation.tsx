interface GradeAuthenticityCorrelationProps {
  grade: string;
  authenticityScore: number;
}

export default function GradeAuthenticityCorrelation({ grade, authenticityScore }: GradeAuthenticityCorrelationProps) {
  const tier = grade.charAt(0).toUpperCase();
  const interpretation = getInterpretation(tier, authenticityScore, grade);
  const alignment = getAlignment(tier, authenticityScore);

  return (
    <div className="grade-auth-card">
      <div className="grade-auth-row">
        <div className="grade-auth-item">
          <span className={`sb-grade sb-grade-${tier.toLowerCase()}`}>{grade}</span>
          <span className="grade-auth-label">SB Grade</span>
        </div>
        <div className="grade-auth-vs">
          <span className={`grade-auth-alignment grade-auth-${alignment}`}>
            {alignment === 'aligned' ? 'Aligned' : alignment === 'mismatch' ? 'Mismatch' : 'Mixed'}
          </span>
        </div>
        <div className="grade-auth-item">
          <span className={`grade-auth-score ${authenticityScore >= 70 ? 'score-good' : authenticityScore >= 40 ? 'score-fair' : 'score-poor'}`}>
            {authenticityScore}
          </span>
          <span className="grade-auth-label">Authenticity</span>
        </div>
      </div>
      <p className="grade-auth-interpretation">{interpretation}</p>
    </div>
  );
}

function getAlignment(tier: string, score: number): 'aligned' | 'mixed' | 'mismatch' {
  const highGrade = tier === 'A';
  const highScore = score >= 70;
  const lowScore = score < 40;

  if (highGrade && highScore) return 'aligned';
  if (highGrade && lowScore) return 'mismatch';
  if (!highGrade && highScore) return 'mixed';
  if (tier === 'D' || tier === 'F') return lowScore ? 'aligned' : 'mixed';
  return 'mixed';
}

function getInterpretation(tier: string, score: number, grade: string): string {
  if (tier === 'A') {
    if (score >= 70) {
      return 'High external reputation aligns with strong authenticity signals. This channel appears genuinely healthy.';
    }
    if (score >= 40) {
      return `This channel has a strong SocialBlade grade (${grade}) but only moderate authenticity signals. SocialBlade grades reflect growth metrics, not audience quality \u2014 the gap is worth noting.`;
    }
    return `Notable mismatch: this channel carries a high SocialBlade grade (${grade}) but shows low authenticity (${score}/100). High grades reflect growth velocity, not audience genuineness \u2014 this gap warrants caution.`;
  }

  if (tier === 'B' || tier === 'C') {
    if (score >= 70) {
      return 'Despite a moderate SocialBlade grade, this channel shows strong authenticity signals. The audience appears genuine even if growth metrics are modest.';
    }
    if (score >= 40) {
      return 'Both external reputation and authenticity signals are in the moderate range. Not unusual, but worth monitoring as more data becomes available.';
    }
    return 'Both external metrics and authenticity signals suggest concerns. Multiple independent indicators point to potential audience quality issues.';
  }

  // D or F
  if (score >= 70) {
    return `SocialBlade rates this channel poorly (${grade}), but our authenticity signals are strong. The low grade may reflect slow growth rather than audience quality issues.`;
  }
  return `SocialBlade rates this channel poorly (${grade}), which adds additional context to the authenticity assessment.`;
}
