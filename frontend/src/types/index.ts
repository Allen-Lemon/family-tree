export type Permission = 'dashboard:view' | 'tree:view' | 'tree:edit' | 'member:view' | 'member:edit' | 'relation:edit' | 'suggestion:review' | 'data:manage' | 'user:manage' | 'template:edit'
export type BackendRole = 'ADMIN' | 'FAMILY_ADMIN' | 'MEMBER'
export type AccessRole = 'OWNER' | 'EDITOR' | 'VIEWER'
export type Gender = 'MALE' | 'FEMALE' | 'OTHER' | 'UNKNOWN'
export type DatePrecision = 'DAY' | 'MONTH' | 'YEAR' | 'APPROXIMATE' | 'UNKNOWN'
export type RelationType = 'PARENT' | 'SPOUSE' | 'SIBLING'
export type JobStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'RUNNING' | 'COMPLETED' | 'FAILED'

export interface User { id: number; username: string; role: BackendRole; enabled?: boolean; createdAt?: string; assignedTreeIds?: number[]; memberId?: number | null; memberTreeId?: number | null; memberName?: string | null }
export interface AccountProfile { username:string; displayName:string|null; birthDate:string|null; age:number|null; gender:Gender; hasPortrait:boolean; portraitVersion?:string|null; spouseStatus?:string; education?:string; biography?:string }
export interface PageResult<T> { items: T[]; total: number; page: number; pageSize: number }
export interface FamilyTree { id: number; name: string; description?: string; ownerId: number; createdAt?: string; version: number; accessRole?: AccessRole }
export interface Member {
  id: number; treeId: number; name: string; gender: Gender; birthDate?: string; birthPrecision?: DatePrecision; birthPlace?: string;
  deathDate?: string; deathPrecision?: DatePrecision; deathPlace?: string; occupation?: string; heightCm?: number; address?: string;
  notes?: string; avatarMediaId?: number; deleted?: boolean; createdAt?: string; updatedAt?: string; version?: number
}
export type MemberInput = Pick<Member, 'name'> & Partial<Pick<Member, 'gender'|'birthDate'|'birthPrecision'|'birthPlace'|'deathDate'|'deathPrecision'|'deathPlace'|'occupation'|'heightCm'|'address'|'notes'|'avatarMediaId'>>
export interface Relationship { id:number; treeId:number; fromMemberId:number; toMemberId:number; relationType:RelationType; startDate?:string; endDate?:string; notes?:string; createdAt?:string; version:number }
export interface PathNode { memberId:number; relation:string }
export interface TreeStatistics { members:number; relationships:number; byGender:Record<Gender,number> }
export interface Media { id:number; treeId:number; uploaderId:number; originalName:string; storedName:string; mimeType:string; sizeBytes:number; sha256:string; scanStatus:string; createdAt:string }
export interface Suggestion { id:number; treeId:number; submitterId:number; targetType:string; targetId?:number; payloadJson:string; status:JobStatus; reviewerId?:number; reviewComment?:string; createdAt?:string; reviewedAt?:string }
export interface Story { id?:number; treeId?:number; memberId?:number; authorId?:number; title:string; content:string; createdAt?:string; updatedAt?:string; version?:number }
export interface FamilyEvent { id?:number; treeId?:number; memberId?:number; title:string; eventType?:string; eventDate?:string; datePrecision?:DatePrecision; place?:string; description?:string; remindAt?:string; createdBy?:number; version?:number }
export interface NotificationItem { id:number; userId:number; treeId?:number; type:string; message:string; readAt?:string; createdAt:string }
export interface TransferJob { id:number; treeId:number; userId:number; direction:string; format:string; status:JobStatus; sourceMediaId?:number; resultPath?:string; errorMessage?:string; createdAt:string; finishedAt?:string }
export interface BackupRecord { id:number; treeId:number; createdBy:number; storedPath:string; sizeBytes:number; createdAt:string }
export interface OcrTask { id:number; treeId:number; mediaId:number; requestedBy:number; provider:string; status:JobStatus; rawText?:string; correctedText?:string; draftMemberJson?:string; createdAt:string; finishedAt?:string }
export interface CustomFieldDefinition { id?:number; key:string; label:string; type:'TEXT'|'NUMBER'|'DATE'|'BOOLEAN'; sensitive:boolean; enabled?:boolean; sortOrder?:number }
export interface CustomFieldValue { field_key:string; label:string; field_type:CustomFieldDefinition['type']; sensitive:boolean; value_text?:string | null; enabled?:boolean; sort_order?:number; definition_id?:number }
export interface TreeAccess { id:number; username:string; access_role:AccessRole }

export interface GraphNode { id: string; name: string; gender?: string; generation: number; life?: string; status?: string }
export type RelationKind = '亲子' | '父子' | '母子' | '配偶' | '收养' | '兄弟姐妹'
export interface GraphLink { source: string; target: string; kind: RelationKind; id?: string; relationType?: RelationType; label?: string }
export interface GraphData { nodes: GraphNode[]; links: GraphLink[]; rootId?: string }
export interface DashboardStats { trees:number; members:number; generations:number; pending:number; gender:{name:string;value:number}[]; generationsSeries:{generation:string;members:number;events:number}[] }
