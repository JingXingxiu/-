import { API_BASE_URL, ApiError } from '@/services/api'
import type {
  AdminCredentials,
  AdminDatasetCandidateDetails,
  AdminDatasetCandidateSummary,
  AdminDatasetPublication,
} from '@/types/admin'
import type { ProblemDetail } from '@/types/api'

const ADMIN_CANDIDATES_PATH = '/api/v1/admin/user-datasets/candidates'

function utf8Bytes(value: string): Uint8Array {
  const encoded = encodeURIComponent(value)
  const bytes: number[] = []
  for (let index = 0; index < encoded.length; index += 1) {
    if (encoded[index] === '%') {
      bytes.push(Number.parseInt(encoded.slice(index + 1, index + 3), 16))
      index += 2
      continue
    }
    bytes.push(encoded.charCodeAt(index))
  }
  return Uint8Array.from(bytes)
}

function authorizationHeader(credentials: AdminCredentials): string {
  const bytes = utf8Bytes(`${credentials.username}:${credentials.password}`)
  return `Basic ${uni.arrayBufferToBase64(bytes.buffer as ArrayBuffer)}`
}

function adminErrorMessage(problem: ProblemDetail | undefined, statusCode: number): string {
  if (statusCode === 401) {
    return '管理员账号或密码错误'
  }
  if (statusCode === 403) {
    return '当前账号没有管理员权限'
  }
  const fieldMessage = problem?.errors?.find((item) => item.message)?.message
  if (fieldMessage) {
    return fieldMessage
  }
  if (problem?.detail) {
    return problem.detail
  }
  if (statusCode === 404) {
    return '管理端未开启，或候选数据已经不存在'
  }
  if (statusCode === 409) {
    return '候选状态或数据版本发生冲突，请刷新后重试'
  }
  return statusCode >= 500 ? '管理服务暂时不可用，请稍后重试' : '管理操作未能完成'
}

function adminRequest<T>(
  credentials: AdminCredentials,
  method: 'GET' | 'POST',
  path: string,
  data?: Record<string, unknown>,
): Promise<T> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${API_BASE_URL}${path}`,
      method,
      data: data as Record<string, any> | undefined,
      timeout: 30000,
      header: {
        accept: 'application/json, application/problem+json',
        authorization: authorizationHeader(credentials),
        ...(data ? { 'content-type': 'application/json' } : {}),
      },
      success: (response) => {
        if (response.statusCode >= 200 && response.statusCode < 300) {
          resolve(response.data as T)
          return
        }
        const problem = response.data as ProblemDetail | undefined
        reject(new ApiError(adminErrorMessage(problem, response.statusCode), response.statusCode, problem))
      },
      fail: (error) => {
        reject(new ApiError(error.errMsg || '无法连接管理服务，请确认后端已经启动'))
      },
    })
  })
}

export function listAdminCandidates(
  credentials: AdminCredentials,
): Promise<AdminDatasetCandidateSummary[]> {
  return adminRequest(credentials, 'GET', ADMIN_CANDIDATES_PATH)
}

export function loadAdminCandidate(
  credentials: AdminCredentials,
  uploadId: string,
): Promise<AdminDatasetCandidateDetails> {
  return adminRequest(credentials, 'GET', `${ADMIN_CANDIDATES_PATH}/${encodeURIComponent(uploadId)}`)
}

export function publishAdminCandidate(
  credentials: AdminCredentials,
  uploadId: string,
  datasetVersion: string,
): Promise<AdminDatasetPublication> {
  return adminRequest(
    credentials,
    'POST',
    `${ADMIN_CANDIDATES_PATH}/${encodeURIComponent(uploadId)}/publish`,
    { datasetVersion },
  )
}

export function rejectAdminCandidate(
  credentials: AdminCredentials,
  uploadId: string,
  reason: string,
): Promise<void> {
  return adminRequest(
    credentials,
    'POST',
    `${ADMIN_CANDIDATES_PATH}/${encodeURIComponent(uploadId)}/reject`,
    { reason },
  )
}
