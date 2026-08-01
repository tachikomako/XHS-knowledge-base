export function proxiedImageUrl(value: string | null | undefined) {
  if (!value) return ''
  try {
    const url = new URL(value)
    const host = url.hostname.toLowerCase()
    if (host.endsWith('.xhscdn.com') || host.endsWith('.xiaohongshu.com')) {
      return `/api/v1/media/proxy?url=${encodeURIComponent(url.toString())}`
    }
  } catch {
    return value
  }
  return value
}
