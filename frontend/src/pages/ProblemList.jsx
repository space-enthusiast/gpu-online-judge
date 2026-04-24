import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { problems } from '../api'

const DIFF_COLOR = { EASY: '#a6e3a1', MEDIUM: '#f9e2af', HARD: '#f38ba8' }

export default function ProblemList() {
  const [list, setList] = useState([])
  const [error, setError] = useState(null)

  useEffect(() => {
    problems.list()
      .then(r => setList(r.data))
      .catch(e => setError(e.message))
  }, [])

  if (error) return <p style={{ color: '#f38ba8' }}>Error: {error}</p>

  return (
    <div>
      <h1>Problems</h1>
      <table style={{ width: '100%', borderCollapse: 'collapse' }}>
        <thead>
          <tr style={{ textAlign: 'left', borderBottom: '1px solid #45475a' }}>
            <th style={{ padding: '0.5rem' }}>#</th>
            <th style={{ padding: '0.5rem' }}>Title</th>
            <th style={{ padding: '0.5rem' }}>Difficulty</th>
            <th style={{ padding: '0.5rem' }}>Judge</th>
          </tr>
        </thead>
        <tbody>
          {list.map((p, i) => (
            <tr key={p.id} style={{ borderBottom: '1px solid #313244' }}>
              <td style={{ padding: '0.5rem', color: '#6c7086' }}>{i + 1}</td>
              <td style={{ padding: '0.5rem' }}>
                <Link to={`/problems/${p.id}`} style={{ color: '#89b4fa', textDecoration: 'none' }}>{p.title}</Link>
              </td>
              <td style={{ padding: '0.5rem', color: DIFF_COLOR[p.difficulty] ?? '#cdd6f4' }}>
                {p.difficulty}
              </td>
              <td style={{ padding: '0.5rem', color: '#cdd6f4', textTransform: 'lowercase' }}>{p.judgeType}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
