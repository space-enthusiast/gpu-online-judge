import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { submissions } from '../api'

const VERDICT_COLOR = {
  AC: '#a6e3a1', WA: '#f38ba8', TLE: '#f9e2af',
  MLE: '#f9e2af', RE: '#f38ba8', CE: '#f38ba8', PENDING: '#6c7086', JUDGING: '#89b4fa',
}

export default function SubmissionDetail() {
  const { id } = useParams()
  const [sub, setSub] = useState(null)
  const [verdict, setVerdict] = useState(null)

  useEffect(() => {
    submissions.get(id).then(r => setSub(r.data))

    const es = submissions.streamStatus(id)
    es.onmessage = (e) => {
      const data = JSON.parse(e.data)
      setVerdict(data)
      setSub(prev => prev ? { ...prev, ...data, status: data.verdict } : null)
      es.close()
    }
    es.onerror = () => es.close()
    return () => es.close()
  }, [id])

  if (!sub) return <p>Loading...</p>

  const status = verdict?.verdict ?? sub.status

  return (
    <div style={{ maxWidth: '900px' }}>
      <h2>Submission</h2>
      <p style={{ fontSize: '2rem', fontWeight: 'bold', color: VERDICT_COLOR[status] ?? '#cdd6f4' }}>
        {status === 'PENDING' || status === 'JUDGING' ? (
          <>{status} <span style={{ fontSize: '1rem' }}>...</span></>
        ) : status}
      </p>

      {verdict && (
        <div style={{ display: 'flex', gap: '2rem', color: '#cdd6f4', marginBottom: '1rem' }}>
          {verdict.wallTimeMs != null && <span>Time: {verdict.wallTimeMs}ms</span>}
          {verdict.peakVramMb != null && <span>VRAM: {verdict.peakVramMb}MB</span>}
          {verdict.speedup != null && <span>Speedup: {verdict.speedup.toFixed(2)}x</span>}
        </div>
      )}

      {verdict?.compileError && (
        <div>
          <h3>Compile Error</h3>
          <pre style={{ background: '#1e1e2e', padding: '1rem', borderRadius: '6px', color: '#f38ba8', overflow: 'auto' }}>
            {verdict.compileError}
          </pre>
        </div>
      )}

      {verdict?.stdout && (
        <div>
          <h3>Output</h3>
          <pre style={{ background: '#1e1e2e', padding: '1rem', borderRadius: '6px', color: '#cdd6f4', overflow: 'auto', maxHeight: '300px' }}>
            {verdict.stdout}
          </pre>
        </div>
      )}

      {verdict?.stderr && (
        <div>
          <h3>Stderr</h3>
          <pre style={{ background: '#1e1e2e', padding: '1rem', borderRadius: '6px', color: '#f38ba8', overflow: 'auto', maxHeight: '200px' }}>
            {verdict.stderr}
          </pre>
        </div>
      )}
    </div>
  )
}
