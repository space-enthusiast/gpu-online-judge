import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { auth } from '../api'

export default function Register() {
  const navigate = useNavigate()
  const [form, setForm] = useState({ username: '', email: '', password: '' })
  const [error, setError] = useState(null)

  async function handleSubmit(e) {
    e.preventDefault()
    setError(null)
    try {
      const { data } = await auth.register(form)
      localStorage.setItem('token', data.token)
      localStorage.setItem('username', data.username)
      navigate('/problems')
    } catch (e) {
      setError(e.response?.data?.message ?? 'Registration failed')
    }
  }

  return (
    <div style={{ maxWidth: '360px', margin: '4rem auto' }}>
      <h2>Register</h2>
      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
        <input placeholder="Username" value={form.username}
          onChange={e => setForm(f => ({ ...f, username: e.target.value }))}
          style={inputStyle} required />
        <input type="email" placeholder="Email" value={form.email}
          onChange={e => setForm(f => ({ ...f, email: e.target.value }))}
          style={inputStyle} required />
        <input type="password" placeholder="Password" value={form.password}
          onChange={e => setForm(f => ({ ...f, password: e.target.value }))}
          style={inputStyle} required />
        {error && <p style={{ color: '#f38ba8' }}>{error}</p>}
        <button type="submit" style={btnStyle}>Register</button>
      </form>
      <p>Have an account? <Link to="/login" style={{ color: '#89b4fa' }}>Login</Link></p>
    </div>
  )
}

const inputStyle = { padding: '0.6rem', borderRadius: '6px', border: '1px solid #45475a', background: '#1e1e2e', color: '#cdd6f4', fontSize: '1rem' }
const btnStyle = { padding: '0.6rem', background: '#89b4fa', color: '#1e1e2e', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: 'bold', fontSize: '1rem' }
