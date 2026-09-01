import type {
  DataSelection,
  DecisionOptionsResponse,
  DemoCatalogResponse,
  InventoryRequestItem,
  OfferLookupResponse,
  ProblemDetail,
  UserDatasetSession,
  UserDatasetUploadResponse,
} from '@/types/api'

const DATASET_VERSION = 'mixed-demo-v1'
const configuredBaseUrl = (import.meta.env.VITE_API_BASE_URL ?? '/backend').trim()
const API_BASE_URL = configuredBaseUrl.replace(/\/$/, '')
const USER_DATASET_SESSION_KEY = 'book-decision:user-dataset-session:v1'

export const USER_DATASET_ENDPOINTS = {
  upload: '/api/v1/user-datasets/uploads',
  template: '/api/v1/user-datasets/template.csv',
  example: '/api/v1/user-datasets/example.csv',
} as const

export class ApiError extends Error {
  readonly statusCode: number
  readonly errorCode?: string
  readonly traceId?: string

  constructor(message: string, statusCode = 0, problem?: ProblemDetail) {
    super(message)
    this.name = 'ApiError'
    this.statusCode = statusCode
    this.errorCode = problem?.errorCode
    this.traceId = problem?.traceId
  }
}

function problemMessage(problem: ProblemDetail | undefined, statusCode: number): string {
  const firstError = problem?.errors?.find((item) => item.message)?.message
  if (firstError) {
    return firstError
  }
  if (problem?.detail) {
    return problem.detail
  }
  if (problem?.title) {
    return problem.title
  }
  return statusCode >= 500 ? '服务暂时不可用，请稍后重试' : '请求未能完成，请检查输入'
}

function selectionPayload(selection?: DataSelection): Record<string, unknown> {
  return {
    dataMode: selection?.dataMode ?? 'SYSTEM_ONLY',
    ...(selection?.uploadId ? { uploadId: selection.uploadId } : {}),
  }
}

function selectionHeader(selection?: DataSelection): Record<string, string> {
  return selection?.accessToken ? { 'X-Upload-Token': selection.accessToken } : {}
}

function post<T>(
  path: string,
  data: Record<string, unknown>,
  timeout = 30000,
  headers: Record<string, string> = {},
): Promise<T> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${API_BASE_URL}${path}`,
      method: 'POST',
      data: data as Record<string, any>,
      timeout,
      header: {
        'content-type': 'application/json',
        accept: 'application/json, application/problem+json',
        ...headers,
      },
      success: (response) => {
        if (response.statusCode >= 200 && response.statusCode < 300) {
          resolve(response.data as T)
          return
        }
        const problem = response.data as ProblemDetail | undefined
        reject(new ApiError(problemMessage(problem, response.statusCode), response.statusCode, problem))
      },
      fail: (error) => {
        reject(new ApiError(error.errMsg || '无法连接后端服务，请确认服务已经启动'))
      },
    })
  })
}

function get<T>(path: string, data?: Record<string, string>): Promise<T> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${API_BASE_URL}${path}`,
      method: 'GET',
      data,
      timeout: 30000,
      header: {
        accept: 'application/json, application/problem+json',
      },
      success: (response) => {
        if (response.statusCode >= 200 && response.statusCode < 300) {
          resolve(response.data as T)
          return
        }
        const problem = response.data as ProblemDetail | undefined
        reject(new ApiError(problemMessage(problem, response.statusCode), response.statusCode, problem))
      },
      fail: (error) => {
        reject(new ApiError(error.errMsg || '无法连接后端服务，请确认服务已经启动'))
      },
    })
  })
}

export function loadDemoCatalog(): Promise<DemoCatalogResponse> {
  return get('/api/v1/demo/catalog', { datasetVersion: DATASET_VERSION })
}

export function lookupOffers(isbns: string[], selection?: DataSelection): Promise<OfferLookupResponse> {
  return post('/api/v1/books/offers:lookup', {
    datasetVersion: DATASET_VERSION,
    isbns,
    ...selectionPayload(selection),
  }, 30000, selectionHeader(selection))
}

export function requestDecisionOptions(
  inventory: InventoryRequestItem[],
  selection?: DataSelection,
): Promise<DecisionOptionsResponse> {
  return post(
    '/api/v1/decision-options',
    {
      datasetVersion: DATASET_VERSION,
      inventory,
      ...selectionPayload(selection),
    },
    90000,
    selectionHeader(selection),
  )
}

function parseUploadProblem(raw: string, statusCode: number): ApiError {
  try {
    const problem = JSON.parse(raw) as ProblemDetail
    return new ApiError(problemMessage(problem, statusCode), statusCode, problem)
  } catch {
    return new ApiError(statusCode >= 500 ? 'CSV 上传服务暂时不可用' : 'CSV 未通过校验，请检查模板格式', statusCode)
  }
}

export function uploadUserDataset(
  filePath: string,
  reuseConsent: boolean,
): Promise<UserDatasetUploadResponse> {
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: `${API_BASE_URL}${USER_DATASET_ENDPOINTS.upload}`,
      filePath,
      name: 'file',
      timeout: 60000,
      header: {
        accept: 'application/json, application/problem+json',
      },
      formData: {
        baseDatasetVersion: DATASET_VERSION,
        reuseConsent: String(reuseConsent),
      },
      success: (response) => {
        if (response.statusCode >= 200 && response.statusCode < 300) {
          try {
            resolve(JSON.parse(response.data) as UserDatasetUploadResponse)
          } catch {
            reject(new ApiError('上传成功，但后端返回了无法识别的数据'))
          }
          return
        }
        reject(parseUploadProblem(response.data, response.statusCode))
      },
      fail: (error) => {
        reject(new ApiError(error.errMsg || 'CSV 上传失败，请确认后端服务已经启动'))
      },
    })
  })
}

export function deleteUserDataset(uploadId: string, accessToken: string): Promise<void> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${API_BASE_URL}${USER_DATASET_ENDPOINTS.upload}/${encodeURIComponent(uploadId)}`,
      method: 'DELETE',
      timeout: 30000,
      header: {
        accept: 'application/json, application/problem+json',
        'X-Upload-Token': accessToken,
      },
      success: (response) => {
        if (response.statusCode >= 200 && response.statusCode < 300) {
          resolve()
          return
        }
        const problem = response.data as ProblemDetail | undefined
        reject(new ApiError(problemMessage(problem, response.statusCode), response.statusCode, problem))
      },
      fail: (error) => {
        reject(new ApiError(error.errMsg || '无法删除上传，请稍后重试'))
      },
    })
  })
}

export function loadUserDatasetSession(): UserDatasetSession {
  try {
    const raw = uni.getStorageSync(USER_DATASET_SESSION_KEY) as string | UserDatasetSession | undefined
    if (!raw) {
      return { dataMode: 'SYSTEM_ONLY' }
    }
    const parsed = typeof raw === 'string' ? JSON.parse(raw) as UserDatasetSession : raw
    if (parsed.expiresAt && Date.parse(parsed.expiresAt) <= Date.now()) {
      uni.removeStorageSync(USER_DATASET_SESSION_KEY)
      return { dataMode: 'SYSTEM_ONLY' }
    }
    if (!['SYSTEM_ONLY', 'USER_ONLY', 'USER_OVERLAY'].includes(parsed.dataMode)) {
      uni.removeStorageSync(USER_DATASET_SESSION_KEY)
      return { dataMode: 'SYSTEM_ONLY' }
    }
    if (parsed.dataMode !== 'SYSTEM_ONLY' && (!parsed.uploadId || !parsed.accessToken)) {
      return { dataMode: 'SYSTEM_ONLY' }
    }
    return parsed
  } catch {
    uni.removeStorageSync(USER_DATASET_SESSION_KEY)
    return { dataMode: 'SYSTEM_ONLY' }
  }
}

export function saveUserDatasetSession(session: UserDatasetSession): void {
  uni.setStorageSync(USER_DATASET_SESSION_KEY, JSON.stringify(session))
}

export function clearUserDatasetSession(): void {
  uni.removeStorageSync(USER_DATASET_SESSION_KEY)
}

export function userDatasetDownloadUrl(kind: 'template' | 'example'): string {
  return `${API_BASE_URL}${USER_DATASET_ENDPOINTS[kind]}`
}

export { API_BASE_URL, DATASET_VERSION }
