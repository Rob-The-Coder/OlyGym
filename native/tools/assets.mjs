/**
 * Emits the three assets the native app reads, from the React sources that own them.
 *
 *   node native/tools/assets.mjs
 *
 * The outputs are committed. They can drift from the JS sources — re-running this script is the
 * fix, and a JVM test asserts each still parses with a plausible number of entries.
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

// The body silhouette: four views (male/female × front/back), each a viewBox and one path list per
// body part. The web lazy-imports it because it is ~93 KB; here it is an asset the map parses once.
const { default: bodyPaths } = await import(resolve(FRONTEND, 'lib/body-paths.js'))
write('body-paths.json', bodyPaths)
