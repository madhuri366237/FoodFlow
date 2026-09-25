// Where each role lands after logging in (unless it was sent to /login from a specific page).
export const HOME_BY_ROLE = {
  CUSTOMER: '/dashboard',
  RESTAURANT_OWNER: '/owner',
  ADMIN: '/admin',
};

export function homeFor(user) {
  return HOME_BY_ROLE[user?.role] ?? '/';
}
