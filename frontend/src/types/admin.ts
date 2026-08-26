import type { OfferStatus } from '@/types/api'

export type RepeatPolicy = 'INHERIT_PLATFORM' | 'ONE_PER_ORDER' | 'UP_TO_INVENTORY'

export interface AdminCredentials {
  username: string
  password: string
}

export interface AdminDatasetCandidateSummary {
  uploadId: string
  baseDatasetVersion: string
  originalFilename: string
  fileSha256: string
  byteSize: number
  schemaVersion: string
  rowCount: number
  isbnCount: number
  reuseConsent: boolean
  reviewStatus: string
  consentTextVersion: string
  consentAt: string
  createdAt: string
  expiresAt: string
}

export interface AdminDatasetCandidateOffer {
  platformId: string
  status: OfferStatus
  unitPriceCents: number
  repeatPolicy: RepeatPolicy
}

export interface AdminDatasetCandidateBook {
  isbn: string
  title: string
  quantity: number
  offers: AdminDatasetCandidateOffer[]
}

export interface AdminDatasetCandidateDetails {
  uploadId: string
  baseDatasetVersion: string
  originalFilename: string
  byteSize: number
  schemaVersion: string
  rowCount: number
  isbnCount: number
  reuseConsent: boolean
  reviewStatus: string
  consentTextVersion: string
  consentAt: string
  createdAt: string
  expiresAt: string
  books: AdminDatasetCandidateBook[]
}

export interface AdminDatasetPublication {
  datasetVersion: string
  baseDatasetVersion: string
  sourceUploadId: string
  fileSha256: string
  status: string
  publishedBy: string
  publishedAt: string
}
