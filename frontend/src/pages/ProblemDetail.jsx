import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import MonacoEditor from '@monaco-editor/react'
import { problems, submissions } from '../api'

const STARTER = `#include <stdio.h>
#include <cuda_runtime.h>

__global__ void kernel() {
    // your CUDA kernel here
}

int main() {
    kernel<<<1, 1>>>();
    cudaDeviceSynchronize();
    return 0;
}
`

export default function ProblemDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [problem, setProblem] = useState(null)
  const [code, setCode] = useState(STARTER)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    problems.get(id).then(r => setProblem(r.data)).catch(e => setError(e.message))
  }, [id])

  async function handleSubmit() {
    const token = localStorage.getItem('token')
    if (!token) { navigate('/login'); return }
    setSubmitting(true)
    setError(null)
    try {
      const { data } = await submissions.submit({ problemId: id, sourceCode: code })
      navigate(`/submissions/${data.id}`)
    } catch (e) {
      setError(e.response?.data?.message ?? e.message)
      setSubmitting(false)
    }
  }

  if (error) return <p style={{ color: '#f38ba8' }}>Error: {error}</p>
  if (!problem) return <p>Loading...</p>

  return (
    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem', height: 'calc(100vh - 120px)' }}>
      <div style={{ overflow: 'auto' }}>
        <h2>{problem.title}</h2>
        <p><strong>Difficulty:</strong> {problem.difficulty} &nbsp; <strong>Judge:</strong> {problem.judgeType}</p>
        <p><strong>Time limit:</strong> {problem.timeLimitMs}ms
          {problem.vramLimitMb && <> &nbsp; <strong>VRAM:</strong> {problem.vramLimitMb}MB</>}
          {problem.speedupThreshold && <> &nbsp; <strong>Speedup:</strong> {problem.speedupThreshold}x</>}
        </p>
        <hr style={{ borderColor: '#45475a' }} />
        <p style={{ whiteSpace: 'pre-wrap', color: '#cdd6f4' }}>
          (Load statement from /api/problems/{id}/statement if implemented)
        </p>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
        <MonacoEditor
          height="80%"
          language="cpp"
          theme="vs-dark"
          value={code}
          onChange={v => setCode(v ?? '')}
          options={{ fontSize: 14, minimap: { enabled: false } }}
        />
        <button
          onClick={handleSubmit}
          disabled={submitting}
          style={{ padding: '0.6rem 1.5rem', background: submitting ? '#45475a' : '#89b4fa', color: '#1e1e2e', border: 'none', borderRadius: '6px', cursor: submitting ? 'default' : 'pointer', fontWeight: 'bold', fontSize: '1rem' }}
        >
          {submitting ? 'Submitting...' : 'Submit'}
        </button>
        {error && <p style={{ color: '#f38ba8' }}>{error}</p>}
      </div>
    </div>
  )
}
