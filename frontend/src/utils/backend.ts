import type { Gender, Member, MemberInput } from '@/types'

export const genderLabel:Record<Gender,string>={MALE:'男',FEMALE:'女',OTHER:'其他',UNKNOWN:'未标注'}
export function toBackendGender(value:unknown):Gender { return ({男:'MALE',女:'FEMALE',其他:'OTHER'} as Record<string,Gender>)[String(value)] || (['MALE','FEMALE','OTHER','UNKNOWN'].includes(String(value)) ? value as Gender : 'UNKNOWN') }
export function memberInput(member:Partial<Member> & {gender?:unknown}):MemberInput {
  return {name:member.name||'',gender:toBackendGender(member.gender),birthDate:member.birthDate||undefined,birthPrecision:member.birthPrecision||undefined,birthPlace:member.birthPlace||undefined,deathDate:member.deathDate||undefined,deathPrecision:member.deathPrecision||undefined,deathPlace:member.deathPlace||undefined,occupation:member.occupation||undefined,heightCm:member.heightCm||undefined,address:member.address||undefined,notes:member.notes||undefined,avatarMediaId:member.avatarMediaId||undefined}
}
export function requireTreeId(value:string|number|undefined){const id=Number(value);if(!Number.isFinite(id)||id<=0)throw new Error('请先选择家谱');return id}
