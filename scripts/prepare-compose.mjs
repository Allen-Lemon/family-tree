import { readFileSync, writeFileSync, existsSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..')
const source=path.join(root,'.env.local'),target=path.join(root,'.env')
if(existsSync(target))throw new Error('.env already exists; it was not changed. Review it manually.')
if(!existsSync(source))throw new Error('Missing .env.local. Copy .env.example to .env and configure it instead.')
const values=new Map(readFileSync(source,'utf8').replace(/^﻿/,'').split(/\r?\n/).filter(line=>line.trim()&&!line.trimStart().startsWith('#')).map(line=>{const index=line.indexOf('=');if(index<1)throw new Error('Invalid .env.local line');return[line.slice(0,index).trim(),line.slice(index+1)]}))
for(const key of ['DB_URL','DB_USERNAME','DB_PASSWORD','APP_DATA_ENCRYPTION_KEY','BOOTSTRAP_ADMIN_USERNAME','BOOTSTRAP_ADMIN_PASSWORD']){
 if(!values.get(key)||values.get(key).includes('CHANGE_ME'))throw new Error(`Configure ${key} in .env.local first`)
}
if(values.get('APP_DATA_ENCRYPTION_KEY').length<32)throw new Error('Existing encryption key is too short')
// Compose dotenv single quotes prevent interpolation of $ in passwords and JDBC parameters.
function quote(value){if(/[\r\n\0]/.test(value))throw new Error('Multiline environment values are not supported');return "'"+value.replace(/'/g,"\\'")+"'"}
const config={COMPOSE_PROJECT_NAME:'family-tree',IMAGE_TAG:'local',TZ:'Asia/Shanghai',HTTP_BIND:'127.0.0.1',HTTP_PORT:'8088',DB_URL:values.get('DB_URL'),DB_USERNAME:values.get('DB_USERNAME'),DB_PASSWORD:values.get('DB_PASSWORD'),BOOTSTRAP_ADMIN_USERNAME:values.get('BOOTSTRAP_ADMIN_USERNAME'),BOOTSTRAP_ADMIN_PASSWORD:values.get('BOOTSTRAP_ADMIN_PASSWORD'),APP_DATA_ENCRYPTION_KEY:values.get('APP_DATA_ENCRYPTION_KEY'),SESSION_COOKIE_SECURE:values.get('SESSION_COOKIE_SECURE')||'false',UPLOADS_PATH:'./data/uploads',MAX_UPLOAD_SIZE:values.get('MAX_UPLOAD_SIZE')||'20MB',MAX_UPLOAD_BYTES:values.get('MAX_UPLOAD_BYTES')||'20971520',OCR_ENABLED:values.get('OCR_ENABLED')||'false',OCR_PROVIDER:values.get('OCR_PROVIDER')||'disabled',OCR_ENDPOINT:values.get('OCR_ENDPOINT')||'',OCR_API_KEY:values.get('OCR_API_KEY')||'',CLAMAV_ENABLED:values.get('CLAMAV_ENABLED')||'false'}
writeFileSync(target,'# Private Docker Compose configuration, copied without changing existing secrets.\n'+Object.entries(config).map(([key,value])=>`${key}=${quote(value)}`).join('\n')+'\n',{encoding:'utf8',flag:'wx',mode:0o600})
console.log('Created private .env using the existing database credentials, administrator settings and encryption key. Values were not printed.')
