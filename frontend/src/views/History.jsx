import { useNavigate } from 'react-router-dom'
import { useStore } from '../store/useStore.js'
import { t } from '../lib/i18n.js'
import { WorkoutRow, workoutDetailSheet, logPastWorkoutSheet } from '../sheets.jsx'
import { Button } from '../components/ui.jsx'
import Icon from '../components/Icon.jsx'
import TopAppBar from '../components/TopAppBar.jsx'

export default function History() {
  const nav = useNavigate()
  const S = useStore(s => s.S)
  return <>
    <TopAppBar title={t('History')} subtitle={t('{0} workouts', S.workouts.length)}
      leading={<button className="iconbtn ab-ico" onClick={() => nav('/stats')} aria-label={t('Stats')}><Icon name="chevronLeft" /></button>} />
    <Button icon="plus" onClick={logPastWorkoutSheet} style={{ marginBottom: 12 }}>{t('Log a past workout')}</Button>
    {S.workouts.length ? <div className="list">{[...S.workouts].reverse().map(w => <WorkoutRow key={w.id} w={w} onClick={() => workoutDetailSheet(w)} />)}</div>
      : <div className="empty"><div className="ico"><Icon name="history" /></div>{t('No workouts yet.')}</div>}
  </>
}
