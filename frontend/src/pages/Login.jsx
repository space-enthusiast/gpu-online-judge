import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { auth } from '../api'

export default function Login() {
  const navigate = useNavigate()
  const [form, setForm] = useState({ username: '', password: '' })
  const [error, setError] = useState(null)

  async function handleSubmit(e) {
    e.preventDefault()
    setError(null)
    try {
      const { data } = await auth.login(form)
      localStorage.setItem('token', data.token)
      localStorage.setItem('username', data.username)
      navigate('/problems')
    } catch (e) {
      setError(e.response?.data?.message ?? 'Login failed')
    }
  }

  return (
    <div style={{ maxWidth: '360px', margin: '4rem auto' }}>
      <h2>Login</h2>
      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
        <input placeholder="Username" value={form.username}
          onChange={e => setForm(f => ({ ...f, username: e.target.value }))}
          style={inputStyle} required />
        <input type="password" placeholder="Password" value={form.password}
          onChange={e => setForm(f => ({ ...f, password: e.target.value }))}
          style={inputStyle} required />
        {error && <p style={{ color: '#f38ba8' }}>{error}</p>}
        <button type="submit" style={btnStyle}>Login</button>
      </form>
      <p>No account? <Link to="/register" style={{ color: '#89b4fa' }}>Register</Link></p>
    </div>
  )
}

const inputStyle = { padding: '0.6rem', borderRadius: '6px', border: '1px solid #45475a', background: '#1e1e2e', color: '#cdd6f4', fontSize: '1rem' }
const btnStyle = { padding: '0.6rem', background: '#89b4fa', color: '#1e1e2e', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: 'bold', fontSize: '1rem' }
