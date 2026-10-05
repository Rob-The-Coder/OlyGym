import { useNavigate } from 'react-router-dom'
import { t } from '../lib/i18n.js'
import MuscleExplorer from '../components/MuscleExplorer.jsx'
import { exerciseDetailSheet } from '../sheets.jsx'
import Icon from '../components/Icon.jsx'
import TopAppBar from '../components/TopAppBar.jsx'

export default function Muscles() {
  const nav = useNavigate()
  return <>
    <TopAppBar title={t('Explore muscles')} subtitle={t('Choose a muscle to see exercises that train it.')}
      leading={<button className="iconbtn ab-ico" onClick={() => nav('/library')} aria-label={t('Exercises')}><Icon name="chevronLeft" /></button>} />

    <MuscleExplorer onDetail={exerciseDetailSheet} />
  </>
}
