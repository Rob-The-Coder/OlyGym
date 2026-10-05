import { useNavigate } from 'react-router-dom'
import { Segmented } from './ui.jsx'
import { t } from '../lib/i18n.js'

// Plan and Competitions are one destination with two modes: the weeks you train and the meets
// they are built around. The bottom bar keeps Plan lit for both (components/TabBar.jsx), so the
// switcher — not the bar — says which mode you are in.
export default function PlanTabs({ current }) {
  const nav = useNavigate()
  return <div className="plan-tabs">
    <Segmented value={current}
      onChange={v => nav(v === 'competitions' ? '/competitions' : '/plan')}
      options={[{ value: 'plan', label: t('Plan') }, { value: 'competitions', label: t('Competitions') }]} />
  </div>
}
