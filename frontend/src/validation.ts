export function validatePassword(password: string, confirmation: string): string {
  const normalized = password.normalize('NFC')
  const length = Array.from(normalized).length
  if (length < 15) return '密码至少需要 15 个字符，可以使用一段容易记住的短句'
  if (length > 1024) return '密码最多支持 1024 个字符'
  if (normalized !== confirmation.normalize('NFC')) return '两次输入的密码不一致'
  return ''
}

export function validateNickname(nickname: string): string {
  const length = Array.from(nickname.trim()).length
  return length < 1 || length > 50 ? '昵称需要 1–50 个字符' : ''
}
