export interface ShortenResponse {
  shortUrl: string
  shortCode: string
  originalUrl: string
  createdAt: string
}

export interface ClickDetail {
  ipAddress: string
  userAgent: string | null
  referrer: string | null
  clickedAt: string
}

export interface AnalyticsResponse {
  shortCode: string
  originalUrl: string
  totalClicks: number
  createdAt: string
  recentClicks: ClickDetail[]
}

/** The uniform error body every failing endpoint returns. */
export interface ApiError {
  status: number
  error: string
  message: string
  timestamp: string
}
