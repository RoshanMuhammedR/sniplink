import type { AnalyticsResponse, ApiError, ShortenResponse } from './types'

/**
 * Requests go to /api/... and Vite proxies them to the backend in dev, so there
 * is no cross-origin hop and no API base URL to configure here.
 */
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(path, init)
  } catch {
    throw new Error('Could not reach the API. Is the backend running on :8080?')
  }

  if (!response.ok) {
    // Every endpoint is meant to return the shared error shape, but a proxy or
    // container error could still hand back HTML — fall back to the status line.
    let message = `Request failed (${response.status})`
    try {
      const body = (await response.json()) as ApiError
      if (body?.message) message = body.message
    } catch {
      /* keep the fallback */
    }
    throw new Error(message)
  }

  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

export function shortenUrl(url: string): Promise<ShortenResponse> {
  return request<ShortenResponse>('/api/v1/shorten', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ url }),
  })
}

export function fetchAnalytics(code: string): Promise<AnalyticsResponse> {
  return request<AnalyticsResponse>(`/api/v1/analytics/${encodeURIComponent(code)}`)
}
