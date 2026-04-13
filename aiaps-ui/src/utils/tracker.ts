import request from './request'

let currentPagePath = ''
let currentEnterTime = 0
let sessionId = ''
let eventQueue: any[] = []

export function initTracker() {
  sessionId = 'S-' + Date.now() + '-' + Math.random().toString(36).substr(2, 6)
}

export function trackPageEnter(path: string) {
  if (currentPagePath && currentEnterTime) {
    const duration = Math.round((Date.now() - currentEnterTime) / 1000)
    reportPageView(currentPagePath, duration)
  }
  currentPagePath = path
  currentEnterTime = Date.now()

  setTimeout(() => {
    try {
      const entries = performance.getEntriesByType('paint')
      const fcp = entries.find(e => e.name === 'first-contentful-paint')
      if (fcp) {
        reportPerformance('PAGE', path, { fcpMs: Math.round(fcp.startTime) })
      }
    } catch { /* ignore */ }
  }, 3000)
}

export function trackAction(eventCode: string, params?: Record<string, string>) {
  const event: any = {
    type: 'action',
    eventCode,
    pagePath: currentPagePath,
    userId: localStorage.getItem('username') || 'anonymous',
    eventTime: new Date().toISOString(),
    sessionId,
  }
  if (params) {
    const keys = Object.keys(params)
    if (keys[0]) { event.param1Key = keys[0]; event.param1Value = params[keys[0]] }
    if (keys[1]) { event.param2Key = keys[1]; event.param2Value = params[keys[1]] }
    if (keys[2]) { event.param3Key = keys[2]; event.param3Value = params[keys[2]] }
  }
  queueEvent(event)
}

export function trackApiPerformance(method: string, path: string, responseMs: number, status: number, isError: boolean) {
  reportPerformance('API', path, { apiMethod: method, responseMs, httpStatus: status, isError })
}

function reportPageView(path: string, durationSeconds: number) {
  queueEvent({
    type: 'pageView',
    pagePath: path,
    durationSeconds,
    userId: localStorage.getItem('username') || 'anonymous',
    sessionId,
    deviceType: /Android|iPhone/i.test(navigator.userAgent) ? 'MOBILE' : 'PC',
  })
}

function reportPerformance(sourceType: string, path: string, data: Record<string, any>) {
  queueEvent({
    type: 'performance',
    sourceType,
    pagePath: sourceType === 'PAGE' ? path : undefined,
    apiPath: sourceType === 'API' ? path : undefined,
    userId: localStorage.getItem('username') || 'anonymous',
    sessionId,
    ...data,
  })
}

function queueEvent(event: any) {
  eventQueue.push(event)
  if (eventQueue.length >= 5) {
    flushEvents()
  }
}

function flushEvents() {
  if (eventQueue.length === 0) return
  const batch = [...eventQueue]
  eventQueue = []
  try {
    if (navigator.sendBeacon) {
      navigator.sendBeacon('/api/v1/analytics/batch', JSON.stringify(batch))
    } else {
      request.post('/v1/analytics/batch', batch).catch(() => {})
    }
  } catch { /* silent */ }
}

if (typeof window !== 'undefined') {
  window.addEventListener('beforeunload', () => {
    if (currentPagePath && currentEnterTime) {
      const duration = Math.round((Date.now() - currentEnterTime) / 1000)
      reportPageView(currentPagePath, duration)
    }
    flushEvents()
  })

  setInterval(flushEvents, 30000)
}
