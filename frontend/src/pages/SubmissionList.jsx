import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { submissions } from '../api'

const VERDICT_COLOR = {
  AC: '#a6e3a1', WA: '#f38ba8', TLE: '#f9e2af',
  MLE: '#f9e2af', RE: '#f38ba8', CE: '#f38ba8',
}

export default function SubmissionList() {
  const [list, setList] = useState([])
  const [error, setError] = useState(null)

  useEffect(() => {
    submissions.list()
      .then(r => setList(r.data))
      .catch(e => setError(e.message))
  }, [])

  if (error) return <p style={{ color: '#f38ba8' }}>Error: {error}</p>

  return (
    <div>
      <h1>My Submissions</h1>
      {list.length === 0 ? (
        <p style={{ color: '#6c7086' }}>No submissions yet.</p>
      ) : (
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead>
            <tr style={{ textAlign: 'left', borderBottom: '1px solid #45475a' }}>
              <th style={{ padding: '0.5rem' }}>Problem</th>
              <th style={{ padding: '0.5rem' }}>Verdict</th>
              <th style={{ padding: '0.5rem' }}>Time</th>
              <th style={{ padding: '0.5rem' }}>Submitted</th>
            </tr>
          </thead>
          <tbody>
            {list.map(s => (
              <tr key={s.id} style={{ borderBottom: '1px solid #313244' }}>
                <td style={{ padding: '0.5rem' }}>
                  <Link to={`/problems/${s.problemId}`} style={{ color: '#89b4fa', textDecoration: 'none' }}>
                    {s.problemId}
                  </Link>
                </td>
                <td style={{ padding: '0.5rem' }}>
                  <Link to={`/submissions/${s.id}`} style={{ textDecoration: 'none' }}>
                    <span style={{ color: VERDICT_COLOR[s.verdict] ?? '#6c7086', fontWeight: 'bold' }}>
                      {s.verdict ?? s.status}
                    </span>
                  </Link>
                </td>
                <td style={{ padding: '0.5rem', color: '#6c7086' }}>
                  {s.wallTimeMs != null ? `${s.wallTimeMs}ms` : '—'}
                </td>
                <td style={{ padding: '0.5rem', color: '#6c7086' }}>
                  {new Date(s.submittedAt).toLocaleString()}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}
