/**
 * Emits the two assets the native app reads, from the React sources that own them.
 *
 *   node native/tools/assets.mjs
 *
 * The outputs are committed. They can drift from the JS sources — re-running this script is the
 * fix, and a JVM test asserts both still parse with a plausible number of entries.
 */
import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = dirname(fileURLToPath(import.meta.url))
const FRONTEND = resolve(HERE, '../../frontend/src')
const ASSETS = resolve(HERE, '../app/src/main/assets')

const write = (rel, value) => {
  const out = resolve(ASSETS, rel)
  mkdirSync(dirname(out), { recursive: true })
  writeFileSync(out, JSON.stringify(value) + '\n')
  const size = JSON.stringify(value).length
  console.log(`${rel}: ${size} bytes`)
}

const { default: it } = await import(resolve(FRONTEND, 'locales/it.js'))
write('i18n/it.json', it)

const { EXDB } = await import(resolve(FRONTEND, 'lib/exercises-data.js'))
write('exercises-data.json', EXDB)
