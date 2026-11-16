import { request } from './client'
import type { AccountProfile, AccessRole, BackupRecord, CustomFieldDefinition, CustomFieldValue, FamilyEvent, FamilyTree, Media, Member, MemberInput, NotificationItem, OcrTask, PathNode, RelationType, Relationship, Story, Suggestion, TransferJob, TreeAccess, TreeStatistics, User } from '@/types'

export const authApi = {
  csrf: () => request<{headerName:string;parameterName:string;token:string}>({ url:'/auth/csrf' }),
  login: (data:{username:string;password:string}) => request<User>({ url:'/auth/login', method:'post', data }),
  logout: () => request<void>({ url:'/auth/logout', method:'post' }),
  me: () => request<User>({ url:'/auth/me' }),
}
export const profileApi = {
  get: () => request<AccountProfile>({url:'/profile'}),
  update: (data:{displayName:string;birthDate:string|null;gender:AccountProfile['gender'];spouseStatus?:string;education?:string;biography?:string}) => request<AccountProfile>({url:'/profile',method:'put',data}),
  uploadPortrait: (file:File) => { const data=new FormData();data.append('file',file);return request<AccountProfile>({url:'/profile/portrait',method:'post',data}) },
}
export const treeApi = {
  list: () => request<FamilyTree[]>({ url:'/trees' }),
  create: (data:{name:string;description?:string}) => request<FamilyTree>({ url:'/trees',method:'post',data }),
  update: (treeId:number|string,data:{name:string;description?:string}) => request<FamilyTree>({url:`/trees/${treeId}`,method:'put',data}),
  statistics: (treeId:number|string) => request<TreeStatistics>({ url:`/trees/${treeId}/statistics` }),
}
export const memberApi = {
  list: (treeId:number|string) => request<Member[]>({ url:`/trees/${treeId}/members` }),
  get: (treeId:number|string,id:number|string) => request<Member>({ url:`/trees/${treeId}/members/${id}` }),
  create: (treeId:number|string,data:MemberInput) => request<Member>({ url:`/trees/${treeId}/members`,method:'post',data }),
  update: (treeId:number|string,id:number|string,data:MemberInput) => request<Member>({ url:`/trees/${treeId}/members/${id}`,method:'put',data }),
  remove: (treeId:number|string,id:number|string) => request<void>({ url:`/trees/${treeId}/members/${id}`,method:'delete' }),
  sensitive: (treeId:number|string,id:number|string) => request<Record<string,unknown>>({ url:`/trees/${treeId}/members/${id}/sensitive` }),
  updateSensitive: (treeId:number|string,id:number|string,data:Record<string,unknown>) => request<Member>({ url:`/trees/${treeId}/members/${id}/sensitive`,method:'put',data }),
  relationships: (treeId:number|string) => request<Relationship[]>({ url:`/trees/${treeId}/relationships` }),
  addRelation: (treeId:number|string,data:{fromMemberId:number;toMemberId:number;type:RelationType}) => request<Relationship>({ url:`/trees/${treeId}/relationships`,method:'post',data }),
  removeRelation: (treeId:number|string,id:number|string) => request<void>({ url:`/trees/${treeId}/relationships/${id}`,method:'delete' }),
  ancestors: (treeId:number|string,id:number|string) => request<Member[]>({ url:`/trees/${treeId}/members/${id}/ancestors` }),
  descendants: (treeId:number|string,id:number|string) => request<Member[]>({ url:`/trees/${treeId}/members/${id}/descendants` }),
  generations: (treeId:number|string,id:number|string) => request<Record<string,Member[]>>({ url:`/trees/${treeId}/members/${id}/descendants/generations` }),
  relationshipPath: (treeId:number|string,from:number|string,to:number|string) => request<PathNode[]>({ url:`/trees/${treeId}/relationship-path`,params:{from,to} }),
}
export const customFieldApi = {
  definitions: async (treeId:number|string): Promise<CustomFieldDefinition[]> => {
    const rows = await request<{id:number;field_key:string;label:string;field_type:CustomFieldDefinition['type'];sensitive:boolean;enabled?:boolean;sort_order?:number}[]>({url:`/trees/${treeId}/custom-fields`})
    return rows.map(row => ({id:row.id,key:row.field_key,label:row.label,type:row.field_type,sensitive:row.sensitive,...(row.enabled===undefined?{}:{enabled:row.enabled}),...(row.sort_order===undefined?{}:{sortOrder:row.sort_order})}))
  },
  create: async (treeId:number|string,data:CustomFieldDefinition) => { await request<unknown>({url:`/trees/${treeId}/custom-fields`,method:'post',data}) },
  update: async (treeId:number|string,id:number|string,data:{label:string;enabled:boolean;sortOrder:number}) => { await request<unknown>({url:`/trees/${treeId}/custom-fields/${id}`,method:'put',data}) },
  values: (treeId:number|string,memberId:number|string) => request<CustomFieldValue[]>({url:`/trees/${treeId}/custom-fields/members/${memberId}`}),
  setValue: (treeId:number|string,definitionId:number|string,memberId:number|string,value:string) => request<void>({url:`/trees/${treeId}/custom-fields/${definitionId}/members/${memberId}`,method:'put',data:{value}}),
}
export const featureApi = {
  uploadMedia: (treeId:number|string,data:FormData) => request<Media>({url:`/trees/${treeId}/media`,method:'post',data}),
  mediaUrl: (treeId:number|string,id:number|string) => `/api/v1/trees/${treeId}/media/${id}`,
  suggest: (treeId:number|string,data:{targetType:string;targetId?:number;payload:Record<string,unknown>}) => request<Suggestion>({url:`/trees/${treeId}/suggestions`,method:'post',data}),
  pendingSuggestions: (treeId:number|string) => request<Suggestion[]>({url:`/trees/${treeId}/suggestions/pending`}),
  review: (treeId:number|string,id:number|string,approve:boolean,comment:string) => request<Suggestion>({url:`/trees/${treeId}/suggestions/${id}/review`,method:'post',data:{approve,comment}}),
  stories: (treeId:number|string) => request<Story[]>({url:`/trees/${treeId}/stories`}),
  createStory: (treeId:number|string,data:Story) => request<Story>({url:`/trees/${treeId}/stories`,method:'post',data}),
  events: (treeId:number|string) => request<FamilyEvent[]>({url:`/trees/${treeId}/events`}),
  createEvent: (treeId:number|string,data:FamilyEvent) => request<FamilyEvent>({url:`/trees/${treeId}/events`,method:'post',data}),
  notifications: () => request<NotificationItem[]>({url:'/notifications'}),
  readNotification: (id:number|string) => request<NotificationItem>({url:`/notifications/${id}/read`,method:'post'}),
  exportJob: (treeId:number|string,format:string) => request<TransferJob>({url:`/trees/${treeId}/exports`,method:'post',params:{format}}),
  exportSyncUrl: (treeId:number|string,format:string) => `/api/v1/trees/${treeId}/exports/sync?format=${encodeURIComponent(format)}`,
  importData: (treeId:number|string,format:string,data:FormData) => request<TransferJob>({url:`/trees/${treeId}/imports`,method:'post',params:{format},data}),
  backups: (treeId:number|string) => request<BackupRecord[]>({url:`/trees/${treeId}/backups`}),
  backup: (treeId:number|string) => request<BackupRecord>({url:`/trees/${treeId}/backups`,method:'post'}),
  backupDownloadUrl: (treeId:number|string,id:number|string) => `/api/v1/trees/${treeId}/backups/${id}/download`,
  startOcr: (treeId:number|string,mediaId:number|string,provider?:string) => request<OcrTask>({url:`/trees/${treeId}/ocr`,method:'post',params:{mediaId,provider}}),
  correctOcr: (treeId:number|string,id:number|string,correctedText:string,draftMember:Record<string,unknown>={}) => request<OcrTask>({url:`/trees/${treeId}/ocr/${id}`,method:'put',data:{correctedText,draftMember}}),
  search: (treeId:number|string,q:string) => request<{query:string;members:Member[];count:number}>({url:`/trees/${treeId}/search`,params:{q}}),
}
export const accessApi = {
  list: (treeId:number|string) => request<TreeAccess[]>({url:`/trees/${treeId}/access`}),
  grant: (treeId:number|string,username:string,role:AccessRole) => request<void>({url:`/trees/${treeId}/access`,method:'put',data:{username,role}}),
  revoke: (treeId:number|string,userId:number|string) => request<void>({url:`/trees/${treeId}/access/${userId}`,method:'delete'}),
}
export const adminApi = {
  users: () => request<User[]>({url:'/admin/users'}),
  createUser: (data:{username:string;password:string;role?:'ADMIN'|'FAMILY_ADMIN'|'MEMBER';treeIds?:number[]}) => request<User>({url:'/admin/users',method:'post',data}),
  removeUser: (id:number|string) => request<void>({url:`/admin/users/${id}`,method:'delete'}),
  assignTrees: (id:number|string,treeIds:number[]) => request<User>({url:`/admin/users/${id}/trees`,method:'put',data:{treeIds}}),
  bindMember: (id:number|string,memberId:number|null) => request<User>({url:`/admin/users/${id}/member`,method:'put',data:{memberId}}),
  changeRole: (id:number|string,role:User['role']) => request<User>({url:`/admin/users/${id}/role`,method:'put',data:{role}}),
  setEnabled: (id:number|string,value:boolean) => request<User>({url:`/admin/users/${id}/enabled`,method:'put',params:{value}}),
}
