import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import * as adminService from '../../services/adminService.js';
import useApi from '../../hooks/useApi.js';
import { useAuth } from '../../context/AuthContext.jsx';
import { AdminNav } from './AdminDashboardPage.jsx';
import Pagination from '../../components/Pagination.jsx';
import Spinner from '../../components/Spinner.jsx';
import ErrorMessage from '../../components/ErrorMessage.jsx';
import { formatDateTime, formatEnum } from '../../utils/format.js';
import { getErrorMessage } from '../../utils/errors.js';

export default function AdminUsersPage() {
  const { user: me } = useAuth();
  const [params, setParams] = useSearchParams();
  const filters = { role: params.get('role') ?? '', keyword: params.get('keyword') ?? '', page: Number(params.get('page') ?? 0) };
  const users = useApi(() => adminService.getUsers(filters), [params.toString()]);
  const [keyword, setKeyword] = useState(filters.keyword);
  const [error, setError] = useState(null);

  function setFilter(name, value) {
    const next = new URLSearchParams(params);
    if (value) next.set(name, value);
    else next.delete(name);
    if (name !== 'page') next.delete('page');
    setParams(next);
  }

  async function toggle(user) {
    const action = user.enabled ? 'Disable' : 'Enable';
    if (!window.confirm(`${action} ${user.email}?${user.enabled ? ' They will be logged out immediately.' : ''}`)) return;
    setError(null);
    try {
      const updated = await adminService.setUserEnabled(user.id, !user.enabled);
      users.setData((page) => ({ ...page, content: page.content.map((u) => (u.id === updated.id ? updated : u)) }));
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  return (
    <section className="page">
      <h1>Users</h1>
      <AdminNav />
      <form
        className="filters filters-3 card"
        onSubmit={(e) => {
          e.preventDefault();
          setFilter('keyword', keyword.trim());
        }}
      >
        <input type="search" placeholder="Name or email" value={keyword} onChange={(e) => setKeyword(e.target.value)} aria-label="Search users" />
        <select value={filters.role} onChange={(e) => setFilter('role', e.target.value)} aria-label="Role">
          <option value="">All roles</option>
          <option value="CUSTOMER">Customers</option>
          <option value="RESTAURANT_OWNER">Restaurant owners</option>
          <option value="ADMIN">Admins</option>
        </select>
        <button type="submit" className="btn btn-primary">
          Search
        </button>
      </form>
      {error && <div className="alert alert-error">{error}</div>}
      {users.loading && <Spinner />}
      <ErrorMessage error={users.error} onRetry={users.reload} />
      <div className="table-wrap">
        <table className="table card">
          <thead>
            <tr>
              <th>Name</th>
              <th>Email</th>
              <th>Role</th>
              <th>Joined</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {users.data?.content.map((user) => (
              <tr key={user.id}>
                <td>{user.name}</td>
                <td>{user.email}</td>
                <td>{formatEnum(user.role)}</td>
                <td>{formatDateTime(user.createdAt)}</td>
                <td>
                  {user.id === me.id ? (
                    <span className="muted small">You</span>
                  ) : (
                    <button type="button" className={`btn btn-sm ${user.enabled ? 'btn-outline' : 'btn-primary'}`} onClick={() => toggle(user)}>
                      {user.enabled ? 'Disable' : 'Enable'}
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Pagination page={users.data} onChange={(page) => setFilter('page', String(page))} />
    </section>
  );
}
