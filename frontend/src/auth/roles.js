export const ROLE = Object.freeze({
  CUSTOMER: 'CUSTOMER',
  SUPPORT_AGENT: 'SUPPORT_AGENT',
  ADMIN: 'ADMIN'
})

const ROLE_PRIORITY = [ROLE.ADMIN, ROLE.SUPPORT_AGENT, ROLE.CUSTOMER]

export function primaryRole(user) {
  const roles = Array.isArray(user?.roles) ? user.roles.map((role) => String(role).toUpperCase()) : []
  return ROLE_PRIORITY.find((role) => roles.includes(role)) || ROLE.CUSTOMER
}

export function roleLabel(role) {
  return {
    [ROLE.ADMIN]: '系统管理员',
    [ROLE.SUPPORT_AGENT]: '客服人员',
    [ROLE.CUSTOMER]: '消费者'
  }[role] || '消费者'
}

export function roleHome(role) {
  return {
    [ROLE.ADMIN]: '/personal-center?view=overview',
    [ROLE.SUPPORT_AGENT]: '/personal-center?view=workspace',
    [ROLE.CUSTOMER]: '/personal-center?view=service'
  }[role] || '/personal-center'
}
