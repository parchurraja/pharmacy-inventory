/**
 * Role-based permission helpers.
 * Ensures the frontend reflects backend security constraints.
 */

export const ROLES = {
  ADMIN: 'ROLE_ADMIN',
  PHARMACIST: 'ROLE_PHARMACIST'
};

export function hasRole(user, role) {
  if (!user || !user.role) return false;
  return user.role === role;
}

export function isAdmin(user) {
  return hasRole(user, ROLES.ADMIN);
}

export function isPharmacist(user) {
  return hasRole(user, ROLES.PHARMACIST);
}

/**
 * Medicine deactivation/reactivation is restricted to Admin
 */
export function canDeactivateMedicine(user) {
  return isAdmin(user);
}

/**
 * Expired stock write-off/disposal is restricted to Admin
 */
export function canDisposeExpired(user) {
  return isAdmin(user);
}
