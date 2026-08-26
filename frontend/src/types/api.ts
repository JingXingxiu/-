export type SourceKind = 'OBSERVED' | 'SYNTHETIC' | 'MIXED'
export type OfferStatus = 'ACCEPTED' | 'REJECTED' | 'UNKNOWN'
export type DataMode = 'SYSTEM_ONLY' | 'USER_ONLY' | 'USER_OVERLAY'
export type DataOrigin = 'SYSTEM' | 'USER'
export type CatalogStatus = 'FOUND' | 'ISBN_NOT_IN_DATASET'
export type SolveStatus = 'OPTIMAL' | 'FEASIBLE' | 'UNKNOWN' | 'INFEASIBLE'
export type PlanKind =
  | 'RECOMMENDED'
  | 'FEWER_PLATFORMS_AND_ORDERS'
  | 'BEST_SINGLE_PLATFORM'
  | 'MOST_MONEY'
export type UnallocatedReason =
  | 'ISBN_NOT_IN_DATASET'
  | 'NO_CONFIRMED_ACCEPTING_OFFER'
  | 'UNALLOCATED_BY_ORDER_CONSTRAINTS'

export interface Disclaimer {
  code: string
  text: string
}

export interface PlatformOffer {
  platformCode: string
  platformDisplayName: string
  status: OfferStatus
  unitPriceCents: number | null
  dataOrigin: DataOrigin
}

export interface OfferLookupBook {
  isbn: string
  title: string | null
  catalogStatus: CatalogStatus
  offers: PlatformOffer[]
}

export interface OfferLookupResponse {
  datasetVersion: string
  dataMode: DataMode
  uploadId: string | null
  sourceKind: SourceKind
  amountUnit: string
  disclaimers: Disclaimer[]
  books: OfferLookupBook[]
}

export interface InventoryRequestItem {
  isbn: string
  quantity: number
}

export interface DataSelection {
  dataMode: DataMode
  uploadId?: string
  accessToken?: string
}

export interface UserDatasetBook {
  isbn: string
  title: string | null
  quantity: number
}

export interface UserDatasetUploadResponse {
  uploadId: string
  accessToken: string
  baseDatasetVersion: string
  status: string
  expiresAt: string
  books: UserDatasetBook[]
}

export interface UserDatasetSession extends DataSelection {
  baseDatasetVersion?: string
  status?: string
  expiresAt?: string
  books?: UserDatasetBook[]
}

export interface DecisionLine {
  isbn: string
  title: string
  quantity: number
  unitPriceCents: number
  lineAmountCents: number
  currency: string
  dataOrigin: DataOrigin
}

export interface DecisionOrder {
  orderNo: number
  platformCode: string
  platformDisplayName: string
  ruleSummary: string
  bookCount: number
  estimatedAmountCents: number
  currency: string
  lines: DecisionLine[]
}

export interface UnallocatedBook {
  isbn: string
  title: string | null
  quantity: number
  reason: UnallocatedReason
}

export interface DecisionResponse {
  datasetVersion: string
  dataMode: DataMode
  uploadId: string | null
  objectivePolicyVersion: string
  engineVersion: string
  requestFingerprint: string
  sourceKind: SourceKind
  disclaimers: Disclaimer[]
  solveStatus: SolveStatus
  solveStatusMessage: string
  input: number
  sold: number
  unsold: number
  estimatedAmountCents: number
  currency: string
  platformCount: number
  orderCount: number
  orders: DecisionOrder[]
  unallocated: UnallocatedBook[]
  dataWarnings: string[]
  durationMs: number
}

export interface DecisionPlan {
  kind: PlanKind
  title: string
  description: string
  decision: DecisionResponse
}

export interface DecisionOptionsResponse {
  plans: DecisionPlan[]
}

export interface ProblemErrorItem {
  field?: string | null
  code?: string
  message?: string
}

export interface ProblemDetail {
  title?: string
  detail?: string
  status?: number
  errorCode?: string
  traceId?: string
  errors?: ProblemErrorItem[]
}

export interface InventoryEntry extends OfferLookupBook {
  quantity: number
}
