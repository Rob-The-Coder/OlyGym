import { useEffect, useRef, useState, forwardRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { useStore, DEF } from '../store/useStore.js'
import { workoutControls } from '../lib/workout-controls.js'
import { videoMode } from '../lib/video.js'
import { useUI } from '../store/useUI.js'
import { ACCENTS, DEFAULT_ACCENT, todayISO, localTZ, weekStartOf, MONDAY, SUNDAY } from '../lib/format.js'
import { effortOf } from '../lib/history.js'
import { unlock, playOnSilentSupported } from '../lib/sound.js'
import { wakeLockSupported } from '../lib/wakelock.js'
import { t, LANGS, INSTR_LANGS } from '../lib/i18n.js'
import TopAppBar from '../components/TopAppBar.jsx'
import { MOBILE, isAndroid, shareExport, syncReminder } from '../lib/mobile.js'
import { checkForUpdate, downloadAndInstall, RELEASES_PAGE } from '../lib/update.js'
import { starterPlanSheet, confirmSheet, importCoachPlan, importCoachPlanFromDrive, equipmentProfileSheet, effortHelpSheet, timePickerSheet, REMINDER_TIMES, weightClassesSheet } from '../sheets.jsx'
import Icon from '../components/Icon.jsx'
import { Section, Row, SelectRow, Switch, Segmented } from '../components/ui.jsx'

// Web-only "Add to Home screen" hint, kept local now that lib/api.js is gone.
const IS_ANDROID = /Android/.test(navigator.userAgent)

export default function Settings() {
  const nav = useNavigate()
  const S = useStore(s => s.S)
  const { update, replaceState } = useStore()
  const toast = useUI(s => s.toast)
  const fileRef = useRef(null)
  const coachRef = useRef(null)
  const wakeOK = wakeLockSupported()

  // --- update check state ---
  const [updateInfo, setUpdateInfo] = useState(null) // { hasUpdate, latestVersion, apkUrl, hashUrl } | null
  const [android, setAndroid] = useState(false)
  const [checking, setChecking] = useState(false)

  useEffect(() => {
    // The in-app updater installs an .apk, so it only applies to the native Android build.
    // On iOS and the web this check is skipped and the update row never appears. isAndroid()
    // already answers false off the mobile build; the MOBILE check on top keeps the web bundle
    // from even asking (and from calling github.com on every Settings visit).
    if (!MOBILE) return
    isAndroid().then(ok => { setAndroid(ok); if (ok) checkForUpdate().then(setUpdateInfo).catch(() => {}) })
  }, [])

  // The same check, on demand: the automatic one is silent when it finds nothing or cannot
  // reach github.com, and a person who taps "Check for updates" deserves an answer either way.
  const checkNow = async () => {
    if (checking) return
    setChecking(true)
    try {
      const info = await checkForUpdate()
      setUpdateInfo(info)
      if (!info.hasUpdate) toast(t('You have the latest version.'))
    } catch {
      toast(t('Could not check for updates — are you online?'))
    }
    setChecking(false)
  }

  const onUpdateRowClick = () => {
    if (!updateInfo?.hasUpdate) return
    if (updateInfo.apkUrl) {
      // Start download & install
      const version = updateInfo.latestVersion
      confirmSheet({
        title: t('Update to {0}?', version),
        message: t('The latest version will be downloaded and the installer will open.'),
        confirmText: t('Download & Install'),
        onConfirm: async () => {
          // Open a progress sheet
          let closeProgress = null
          let setProgress = null
          useUI.getState().openSheet(close => {
            closeProgress = close
            return <DownloadProgress ref={fn => { setProgress = fn }} />
          }, { locked: true })
          try {
            // GitHub hashes every uploaded asset, so the checksum usually comes with the release
            // JSON the check already fetched (apkSha256). A .sha256 published beside the APK is
            // the fallback for hosts that do not. Without one the file is not installed — a
            // sideloaded binary is exactly the thing that should be verified.
            let expectedHash = updateInfo.apkSha256 || null
            if (!expectedHash && updateInfo.hashUrl) {
              try {
                const hashRes = await fetch(updateInfo.hashUrl)
                if (hashRes.ok) expectedHash = (await hashRes.text()).split(/\s/)[0]
              } catch (e) { /* reported below */ }
            }
            if (!/^[0-9a-f]{64}$/i.test(expectedHash || '')) throw new Error(t('Checksum not available — not installing'))
            await downloadAndInstall(updateInfo.apkUrl, expectedHash, (received, total) => {
              if (setProgress) setProgress(received, total)
            })
            if (closeProgress) closeProgress()
          } catch (e) {
            if (closeProgress) closeProgress()
            toast(t('Update failed: {0}', e.message))
          }
        },
      })
    } else {
      // Update available but no APK asset — open the releases page
      window.open(RELEASES_PAGE, '_blank', 'noopener')
    }
  }

  const doExport = async () => {
    const json = JSON.stringify(S, null, 2)
    const name = 'opengym-backup-' + todayISO() + '.json'
    // WKWebView can't download blob URLs — the native build hands the file to the share sheet.
    if (MOBILE) {
      try { await shareExport(json, name); toast(t('Backup exported')) } catch (e) { /* share sheet dismissed */ }
      return
    }
    const blob = new Blob([json], { type: 'application/json' })
    const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = name; a.click(); URL.revokeObjectURL(a.href)
    toast(t('Backup exported'))
  }
  const doImport = ev => {
    const f = ev.target.files[0]; if (!f) return
    const rd = new FileReader()
    rd.onload = () => {
      try {
        const data = JSON.parse(rd.result)
        // `weeks` is the plan now; a backup from before the dated weeks carries the repeating
        // plan instead, and loadState derives the weeks from it on the next boot.
        const { workouts, weeks, routines: legacyPlan } = data || {}
        if (!workouts || (!weeks && !legacyPlan)) throw new Error('not an OlyGym backup')
        confirmSheet({ title: t('Import backup?'), message: t('This replaces all current data with the backup file.'), confirmText: t('Import'), danger: true, onConfirm: () => { replaceState(Object.assign(JSON.parse(JSON.stringify(DEF)), data), true); toast(t('Backup imported')) } })
      } catch (e) { toast(t('Import failed: {0}', e.message)) }
    }
    rd.readAsText(f)
  }
  const resetEverything = () => confirmSheet({
    title: t('Reset everything?'),
    message: t('Deletes your plan, workouts and body weight on this device. This cannot be undone.'),
    confirmText: t('Delete everything'), danger: true,
    onConfirm: () => {
      replaceState(JSON.parse(JSON.stringify(DEF)), true)
      nav('/home'); toast(t('All data reset'))
    },
  })

  return <div className="narrow">
    <TopAppBar title={t('Settings')}
      leading={<button className="iconbtn ab-ico" onClick={() => nav('/home')} aria-label={t('Home')}><Icon name="chevronLeft" /></button>} />

    {/* ---------- general ---------- */}
    <Section title={t('General')}>
      <SelectRow
        icon="globe" title={t('Language')}
        value={S.lang || 'en'} onChange={v => update(s => { s.lang = v })}
        options={Object.entries(LANGS).map(([k, name]) => ({
          value: k, label: name,
          subtitle: INSTR_LANGS.includes(k) ? null : t("Exercise instructions aren't available in this language yet — they stay in English."),
        }))}
      />
      {/* Display only: one decimal reads fine for plate-loadable numbers, two for anyone whose
          per-side figure lands on .25 or .75, or who loads microplates (issue #139). Nothing is
          stored or rounded differently — lib/format.js fmtNum just prints what is already there. */}
      <Row icon="plate" title={t('Weight decimals')} subtitle={t('How precisely weights are shown.')}>
        <Segmented className="seg-inline"
          options={[{ value: 1, label: t('0.5') }, { value: 2, label: t('0.25') }]}
          value={S.wdec === 2 ? 2 : 1} onChange={v => update(s => { s.wdec = v })} />
      </Row>
      {/* Monday or Sunday — the Plan list, the Home strip, the calendar grid and every
          "this week" total follow it. Stored as a getDay() index (see lib/format.js). */}
      <Row icon="calendar" title={t('Week starts on')}>
        <Segmented className="seg-inline"
          options={[{ value: MONDAY, label: t('Monday') }, { value: SUNDAY, label: t('Sunday') }]}
          value={weekStartOf(S)} onChange={v => update(s => { s.weekStart = v })} />
      </Row>

    </Section>

    {/* ---------- during a workout ---------- */}
    <Section title={t('Workout')} footer={wakeOK ? t('The screen stays on while a workout is running, so you don’t have to unlock your phone between sets.') : null}>
      {/* The quick weigh-in that opens on Start (sheets.jsx startFlow, issue #137); off skips straight
          to the session. Home and Stats still log weight by hand. */}
      <Row icon="scale" title={t('Weigh in before workouts')}
        subtitle={t('Asks for your body weight when a workout starts. Off starts the session straight away.')}>
        <Switch checked={S.weighIn !== false} onChange={v => update(s => { s.weighIn = v })} />
      </Row>
      {/* Automatic progression (lib/progression.js policyFor / defaultPolicy). Off is the
          default: the weight on the plan is the weight on the bar, and hitting every rep means
          nothing changes until you edit the plan. On restores the linear rule for every exercise
          whose own rule is still "follow the routine". The two states stay a button group with
          the (i) beside them rather than a value row: two options are not worth a tap.
          The arrowUp + accent treatment is the one the workout's .progline already uses for
          "the load went up". */}
      <Row icon="arrowUp" title={t('Automatic progression')}>
        <button className="helpbtn" aria-label={t('How does automatic progression work?')} onClick={progressionHelpSheet}><Icon name="info" /></button>
        <Segmented className="seg-inline"
          options={[{ value: 'off', label: t('Off') }, { value: 'on', label: t('On') }]}
          value={S.autoProg ? 'on' : 'off'}
          onChange={v => update(s => { s.autoProg = v === 'on' })} />
      </Row>
      {/* One exercise at a time (cards with Prev/Next), the whole session stacked as a
          scrollable list, or that list stripped to just names and set rows (compact).
          Legacy/unknown values read as cards. The running session can override this from
          the workout header's ⋮ menu without changing this default. */}
      <SelectRow icon="list" title={t('Workout view')}
        value={['list', 'compact'].includes(S.workoutView) ? S.workoutView : 'cards'}
        onChange={v => update(s => { s.workoutView = v })}
        options={[{ value: 'cards', label: t('Cards') }, { value: 'list', label: t('List') }, { value: 'compact', label: t('Compact') }]} />
      {/* The lean workout screen keeps the sets and one "more" button per exercise; each switch
          brings one of the old always-visible button groups back for people who liked them. */}
      <Row icon="wrench" title={t('Workout controls')} accessory="chevron"
        subtitle={t('Everything hidden here stays one tap away: the ⋯ button of an exercise and the number of a set.')}
        onClick={() => workoutControlsSheet()} />
      <SelectRow icon="timer" title={t('Rest timer')}
        value={S.restSec} onChange={v => update(s => { s.restSec = v })}
        options={[{ value: 0, label: t('Off') }, ...[60, 90, 120, 150, 180].map(v => ({ value: v, label: v + 's' }))]} />
      {(wakeOK || !MOBILE) && (
        <Row icon="sun" title={t('Keep screen awake')}
          subtitle={wakeOK ? null : t('Not supported in this browser.')}>
          <Switch checked={wakeOK && S.keepAwake !== false} disabled={!wakeOK}
            onChange={v => update(s => { s.keepAwake = v })} />
        </Row>
      )}
      {/* The picture is a thumbnail in the exercise header now and opens to full size on tap, so
          the old three-way size ('full'/'mini'/'off') has one option left that means anything:
          hidden. Legacy 'mini' reads as 'full' — it meant "always small", which is what the
          collapsed header does anyway. The key is still called gifSize: renaming it would be a
          state migration for no behaviour. */}
      <Row icon="figureRun" title={t('Exercise pictures')}>
        <Segmented className="seg-inline"
          options={[{ value: 'full', label: t('On tap') }, { value: 'off', label: t('Hidden') }]}
          value={S.gifSize === 'off' ? 'off' : 'full'}
          onChange={v => update(s => { s.gifSize = v })} />
      </Row>
      {/* A layer of its own, deliberately not the same setting as the picture above: the frame
          comes from img.youtube.com, the video is an embed. 'On tap' is the default and the
          reason the poster exists at all — nothing is requested from YouTube until you ask. */}
      <SelectRow icon="play" title={t('Demo videos')}
        value={videoMode(S.video)}
        onChange={v => update(s => { s.video = v })}
        options={[{ value: 'off', label: t('Hidden') }, { value: 'button', label: t('On tap') }, { value: 'inline', label: t('Always') }]} />
      <Row icon="bell" title={t('Sounds')}>
        {/* Turning Sounds on is a tap: unlock the audio context now so a timer that ends before
            the next set check can already sound (iOS, #152). */}
        <Switch checked={!!S.sound} onChange={v => { if (v) unlock(true); update(s => { s.sound = v }) }} />
      </Row>
      {/* iOS only (WebKit's audio-session API, iOS 17+): with it off the ring/silent switch mutes
          the timer. On, the phone treats the timer like a music player — exclusive, and the
          music app is not told it may resume — so it is a choice, off by default (lib/sound.js). */}
      {S.sound && playOnSilentSupported() && (
        <Row icon="bell" title={t('Play sounds when the phone is on silent')}
          subtitle={t('Music playing on this phone stops during a workout and does not resume by itself.')}>
          <Switch checked={!!S.soundOnSilent} onChange={v => update(s => { s.soundOnSilent = v })} />
        </Row>
      )}
      <Row icon="sun" title={t('Flash screen when timer ends')}>
        <Switch checked={!!S.timerFlash} onChange={v => update(s => { s.timerFlash = v })} />
      </Row>
      {/* Two names for the same judgement, so the picker asks in the scale you already think in.
          The (i) sits before the value, the way it always did: Row renders a row that carries its
          own help as a container with an overlay tap target, because a button may not contain a
          button. The same table is also one tap away inside the picker. */}
      <SelectRow icon="target" title={t('Effort per set')}
        value={effortOf(S)} onChange={v => update(s => { s.effort = v; delete s.showRir })}
        options={[{ value: 'none', label: t('Off') }, { value: 'rir', label: t('RIR') }, { value: 'rpe', label: t('RPE') }]}
        help={<button className="helpbtn" aria-label={t('What are RIR and RPE?')} onClick={effortHelpSheet}><Icon name="info" /></button>} />
    </Section>

    {MOBILE && <MobileReminderCard S={S} update={update} toast={toast} />}

    {/* ---------- equipment ---------- */}
    <EquipmentCard S={S} update={update} />

    {/* ---------- appearance ---------- */}
    {/* ---------- competition: the categories belong to a federation, not the app ---- */}
    <Section title={t('Competition')}>
      <Row icon="trophy" title={t('Weight classes')}
        subtitle={t('The categories your federation runs. Edit them when the rules change.')}
        accessory="chevron" onClick={weightClassesSheet} />
    </Section>

    <Section title={t('Appearance')}>
      <Row icon="moon" title={t('Theme')}>
        <Segmented
          className="seg-inline"
          options={[
            { value: 'dark', icon: 'moon', label: t('Dark') },
            { value: 'light', icon: 'sun', label: t('Light') },
            { value: 'system', icon: 'gear', label: t('System') },
          ]}
          value={S.theme || 'dark'}
          onChange={v => update(s => { s.theme = v })}
        />
      </Row>
      {/* Purely how the muscle map is drawn — nothing else in the app reads this. */}
      <Row icon="figureStrength" title={t('Body diagram')}>
        <Segmented
          className="seg-inline"
          options={[{ value: 'male', label: t('Male') }, { value: 'female', label: t('Female') }]}
          value={S.body === 'female' ? 'female' : 'male'}
          onChange={v => update(s => { s.body = v })}
        />
      </Row>
      <div className="lrow" style={{ flexDirection: 'column', alignItems: 'stretch', gap: 12, paddingTop: 13, paddingBottom: 14 }}>
        <span className="lrow-t">{t('Accent color')}</span>
        <div className="swatches">
          {Object.entries(ACCENTS).map(([k, seed]) => (
            // The dot is that accent's own primary for the theme that is on — the colour a filled
            // button will actually be — not the seed hex, which is a tone-40 value that reads muddy
            // in dark. --m3-accent-* is generated with the schemes; the seed is the fallback.
            <button key={k} className={'swatch' + ((S.accent || DEFAULT_ACCENT) === k ? ' on' : '')}
              style={{ background: 'var(--m3-accent-' + k + ', ' + seed + ')' }}
              onClick={() => update(s => { s.accent = k })} aria-label={k} />
          ))}
        </div>
      </div>
    </Section>

    {/* ---------- data: fill it, bring things over, back it up, wipe it ---------- */}
    <Section title={t('Data')}>
      <Row icon="sparkles" title={t('Load starter plan')} accessory="chevron" onClick={starterPlanSheet} />
      <Row icon="upload" title={t('Import a coach’s plan')}
        subtitle={t('An Excel, CSV or Google Sheets week: his exercises, sets, reps and loads, read and reviewed before they land in your plan')}
        accessory="chevron" onClick={() => coachRef.current.click()} />
      <Row icon="folder" title={t('Import from Google Drive')}
        subtitle={t('Pick a Google Sheets plan shared with you, straight from Drive')}
        accessory="chevron" onClick={importCoachPlanFromDrive} />
      <Row icon="upload" title={t('Import backup')} accessory="chevron" onClick={() => fileRef.current.click()} />
      <Row icon="download" title={t('Export backup (JSON)')} accessory="chevron" onClick={doExport} />
      {MOBILE && <Row icon="history" title={t('Auto-backup on changes')}
        subtitle={t('Saves a dated copy to the Documents folder after finishing a workout or editing a routine — point a sync app at it, or copy it out by hand.')}>
        <Switch checked={!!S.autoBackup} onChange={v => update(s => { s.autoBackup = v })} />
      </Row>}
      <Row icon="trash" title={t('Reset everything')} danger onClick={resetEverything} />
    </Section>
    <input ref={fileRef} type="file" accept=".json,application/json" style={{ display: 'none' }} onChange={doImport} />
    <input ref={coachRef} type="file" accept=".xlsx,.csv" style={{ display: 'none' }}
      onChange={ev => { const f = ev.target.files[0]; ev.target.value = ''; if (f) importCoachPlan(f) }} />

    {/* "Add to Home screen" makes no sense inside the native app */}
    {!MOBILE && <Section title={t('Tip')}>
      <Row icon="lightbulb"
        title={IS_ANDROID ? t('In Chrome: ⋮ menu → Add to Home screen') : t('In Safari: Share → Add to Home Screen')}
        subtitle={t('to install OlyGym as a full-screen app.') + ' ' + t('Guest data stays on this device — export a backup now and then!')} />
    </Section>}

    {/* ---------- updates: the last thing on the page, so keeping OlyGym current is one tap ----------
        On Android the row is always there — it checks on demand and installs when a release is
        newer (checksum verified, see onUpdateRowClick). On the web the app updates with its
        server, so the row points at the APK for the phone instead. iOS has no APK: nothing. */}
    {(!MOBILE || android) && <Section title={t('Updates')}
      footer={MOBILE ? t('Releases are checked on gitlab.com. The download is verified against its checksum before the installer opens.') : t('The web app updates together with your server. The Android app installs its own updates from here.')}>
      {MOBILE
        ? <Row icon="download"
            title={updateInfo?.hasUpdate ? t('Update to OlyGym v{0}', updateInfo.latestVersion) : t('Check for updates')}
            subtitle={checking ? t('Checking…') : t('You have v{0}', __APP_VERSION__)}
            accessory="chevron"
            onClick={() => (updateInfo?.hasUpdate ? onUpdateRowClick() : checkNow())} />
        : <Row icon="download" title={t('Get the Android app')}
            subtitle={t('Download the APK from opengym.duarte-santos.ch')} accessory="chevron"
            onClick={() => window.open('https://opengym.duarte-santos.ch/#download', '_blank', 'noopener')} />}
    </Section>}

    {/* The version, at the bottom of Settings — which is where the support template has been
        telling people to look for it, and where it was not. On the phone build there is no
        address bar and no about box, so without this there is no way to tell which build you
        are running, or whether an update actually installed. */}
    <div className="dim small" style={{ textAlign: 'center', marginTop: 4, lineHeight: 1.6 }}>
      OlyGym v{__APP_VERSION__} · {t('free & open source (AGPL v3)')}<br />
      <a href="https://gitlab.com/DuarteSantos8/opengym" target="_blank" rel="noopener">source code</a> · exercise data: <a href="https://www.catalystathletics.com/exercises/" target="_blank" rel="noopener">Catalyst Athletics</a><br />
      {t('demo videos: YouTube — frames hotlinked, nothing downloaded')}
    </div>
  </div>
}

// Settings → During a workout → Workout controls. S.wc overlays DEF.wc, so a profile from
// before this setting existed reads as the lean default.
function WorkoutControlsSheet() {
  const S = useStore(s => s.S)
  const update = useStore(s => s.update)
  const wc = workoutControls(S)
  const set = (k, v) => update(s => { s.wc = { ...workoutControls(s), [k]: v } })
  return <>
    <h3>{t('Workout controls')}</h3>
    <div className="muted small" style={{ marginBottom: 12 }}>{t('Everything hidden here stays one tap away: the ⋯ button of an exercise and the number of a set.')}</div>
    <Section>
      <Row icon="plus" title={t('Weight and reps buttons')} subtitle={t('Off: tap the number and type it')}>
        <Switch checked={wc.steppers} onChange={v => set('steppers', v)} />
      </Row>
      <Row icon="link" title={t('Complex buttons in the exercise header')}>
        <Switch checked={wc.pairButtons} onChange={v => set('pairButtons', v)} />
      </Row>
      <Row icon="shuffle" title={t('Move, swap and remove buttons below the exercise')}>
        <Switch checked={wc.exerciseButtons} onChange={v => set('exerciseButtons', v)} />
      </Row>
    </Section>
  </>
}
function workoutControlsSheet() {
  useUI.getState().openSheet(() => <WorkoutControlsSheet />)
}

// Download progress sheet — receives a ref callback that exposes a (received, total) setter.
// Uses forwardRef so the caller can push byte counts in without re-rendering the whole Settings tree.
const DownloadProgress = forwardRef(function DownloadProgress(_, ref) {
  const [pct, setPct] = useState(0)
  const [text, setText] = useState(t('Starting download…'))
  // Expose a setter the caller can invoke directly
  if (ref) ref(function update(received, total) {
    if (total > 0) {
      const p = Math.min(100, Math.round((received / total) * 100))
      setPct(p)
      setText(t('{0} %', p))
    } else {
      setText(t('{0} MB', (received / 1_000_000).toFixed(1)))
    }
  })
  return (
    <div style={{ textAlign: 'center', padding: '8px 0' }}>
      <h3>{t('Downloading update…')}</h3>
      <div style={{ margin: '16px 0', height: 6, borderRadius: 3, background: 'var(--fill-3)', overflow: 'hidden' }}>
        <div style={{ height: '100%', width: pct + '%', background: 'var(--acc)', borderRadius: 3, transition: 'width .2s' }} />
      </div>
      <div className="muted small">{text}</div>
    </div>
  )
})

// Settings → Workout → Automatic progression, the (i) beside the button group. The row can only
// say off or on, so the reasoning lives here, the way the effort picker carries the RIR/RPE table.
// Everything below has to stay true to lib/progression.js: the step
// (defaultIncrement), the deload (DELOAD_FACTOR / DELOAD_AFTER) and the per-exercise override
// (policyFor), which is what makes the switch a default rather than a setting that overrules you.
const PROG_STATES = [
  ['xmark', 'With the switch off',
    'Every planned weight stays exactly as written. A session starts there and the next one starts there again, so the only way the weight changes is you editing the plan.'],
  ['check', 'With the switch on',
    'Hit every rep in every set and the next session starts one step heavier. Fall short and the weight holds where it is until you hit it.'],
]
const PROG_NOTES = [
  'Most lifts add 2.5 kg. Lower-body work (quads, glutes, hamstrings, lower back, adductors, calves) adds 5 kg.',
  'Each exercise can set its own step in its Progression row, and that step is what the weight steppers on the set rows use too.',
  'Three sessions in a row missed at the same weight back it off by 10%, rounded to something you can load. One clean session starts the climb again.',
  'Bodyweight work adds a rep instead of a weight. Timed holds never move on their own.',
  'A rule set on the exercise itself, under Progression → Rule, wins over this switch in both directions.',
]

function ProgressionHelpSheet() {
  return <>
    <h3>{t('Automatic progression')}</h3>
    <div className="muted small" style={{ lineHeight: 1.5 }}>
      {t('Whether OlyGym moves the weight for you. It decides what a session starts at and what the next one is prescribed; it never rewrites a workout you have already logged.')}
    </div>
    <Section>
      {PROG_STATES.map(([icon, title, body]) => (
        <Row key={title} icon={icon} title={t(title)} subtitle={t(body)} />
      ))}
    </Section>
    <div className="sech">{t('How the step is worked out')}</div>
    <div className="dim small" style={{ lineHeight: 1.5, display: 'grid', gap: 8 }}>
      {PROG_NOTES.map(note => <div key={note}>{t(note)}</div>)}
    </div>
    <div style={{ height: 8 }} />
  </>
}
function progressionHelpSheet() {
  useUI.getState().openSheet(() => <ProgressionHelpSheet />)
}

// Mobile build: the reminder is a native local notification scheduled on planned weekdays —
// no push server involved. The schedule itself is (re)synced by the store on every persist;
// this card only owns the OS permission prompt when the switch turns on.
function MobileReminderCard({ S, update, toast }) {
  const setReminder = patch => update(s => { s.reminder = { ...(s.reminder || DEF.reminder), ...patch, tz: localTZ() } })
  const toggle = async () => {
    const on = !S.reminder?.on
    if (on) {
      const ok = await syncReminder({ ...S, reminder: { ...(S.reminder || DEF.reminder), on: true } }, true)
      if (!ok) { toast(t('Could not change notification settings')); return }
    }
    setReminder({ on })
  }
  return (
    <Section title={t('Notifications')}
      footer={S.reminder?.on ? t('Reminds you at this time on days that have a routine planned.') : null}>
      <Row icon="calendar" title={t('Workout day reminder')}>
        <Switch checked={!!S.reminder?.on} onChange={toggle} />
      </Row>
      {S.reminder?.on && (
        <Row icon="clock" title={t('Reminder time')} value={S.reminder?.time || DEF.reminder.time}
          accessory="chevron"
          onClick={() => timePickerSheet({
            value: S.reminder?.time || DEF.reminder.time,
            title: t('Reminder time'), presets: REMINDER_TIMES,
            onPick: v => setReminder({ time: v })
          })} />
      )}
    </Section>
  )
}

// Equipment profiles ("Home", "Gym", ...) — each an id/name/eq-list; the active one filters
// the Library, exercise picker, and flags routine entries that need something outside it
// (see lib/equipment.js). Purely local/synced state — no server changes needed.
function EquipmentCard({ S, update }) {
  const profiles = S.equipProfiles || []
  const remove = p => confirmSheet({
    title: t('Delete profile?'), message: t('"{0}" and its equipment list will be removed.', p.name),
    confirmText: t('Delete'), danger: true,
    onConfirm: () => update(s => {
      s.equipProfiles = (s.equipProfiles || []).filter(x => x.id !== p.id)
      if (s.activeEquipId === p.id) s.activeEquipId = (s.equipProfiles[0] && s.equipProfiles[0].id) || null
    }),
  })
  return <Section title={t('Equipment')} footer={t('Filters the exercise library and picker, and flags routine exercises that need something you don’t have in the active profile.')}>
    {profiles.length > 0 && <Row icon="dumbbell" title={t('Filter by equipment')}>
      <Switch checked={!!S.equipFilterOn} onChange={v => update(s => { s.equipFilterOn = v })} />
    </Row>}
    {profiles.length > 0 && <SelectRow icon="list" title={t('Active profile')}
      value={S.activeEquipId || ''} onChange={v => update(s => { s.activeEquipId = v })}
      options={profiles.map(p => ({ value: p.id, label: p.name }))} />}
    {profiles.map(p => (
      <Row key={p.id} icon="dumbbell" title={p.name}
        subtitle={t('{0} equipment types', p.equipment.length)} accessory="chevron"
        onClick={() => equipmentProfileSheet(p)}>
        <button className="iconbtn" aria-label={t('Delete')} onClick={ev => { ev.stopPropagation(); remove(p) }}><Icon name="trash" /></button>
      </Row>
    ))}
    <Row icon="plus" title={t('Add equipment profile')} accessory="chevron" onClick={() => equipmentProfileSheet(null)} />
  </Section>
}


