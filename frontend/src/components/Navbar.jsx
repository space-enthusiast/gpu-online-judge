import { Link, useNavigate } from 'react-router-dom'

export default function Navbar() {
  const navigate = useNavigate()
  const token = localStorage.getItem('token')
  const username = localStorage.getItem('username')

  function logout() {
    localStorage.removeItem('token')
    localStorage.removeItem('username')
    navigate('/login')
  }

  return (
    <nav style={{ display: 'flex', gap: '1rem', padding: '0.75rem 2rem', background: '#1e1e2e', color: '#fff', alignItems: 'center' }}>
      <Link to="/problems" style={{ color: '#cdd6f4', textDecoration: 'none', fontWeight: 'bold', fontSize: '1.1rem' }}>
        GPU-OJ
      </Link>
      <Link to="/problems" style={{ color: '#cdd6f4', textDecoration: 'none', marginLeft: '1rem' }}>Problems</Link>
      {localStorage.getItem('token') && (
        <Link to="/submissions" style={{ color: '#cdd6f4', textDecoration: 'none' }}>My Submissions</Link>
      )}
      <span style={{ flex: 1 }} />
      {token ? (
        <>
          <span style={{ color: '#a6e3a1' }}>{username}</span>
          <button onClick={logout} style={{ background: 'none', border: '1px solid #cdd6f4', color: '#cdd6f4', cursor: 'pointer', padding: '0.25rem 0.75rem', borderRadius: '4px' }}>
            Logout
          </button>
        </>
      ) : (
        <>
          <Link to="/login" style={{ color: '#cdd6f4', textDecoration: 'none' }}>Login</Link>
          <Link to="/register" style={{ color: '#cdd6f4', textDecoration: 'none' }}>Register</Link>
        </>
      )}
    </nav>
  )
}
