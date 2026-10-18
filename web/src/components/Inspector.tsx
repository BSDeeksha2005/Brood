import type { CSSProperties } from 'react'
import { useEffect, useState } from 'react'
import type { Creature, Food } from '../simulation/types'

export function Inspector({ creature, creatures, food, select, onApplyProgram, onClose }: {
  creature: Creature
  creatures: Creature[]
  food: Food[]
  select: (id: string) => void
  onApplyProgram: (id: string, source: string) => Promise<void>
  onClose: () => void
}) {
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(creature.programSource)
  const [applyError, setApplyError] = useState('')
  const [applying, setApplying] = useState(false)
  useEffect(() => {
    setDraft(creature.programSource)
    setEditing(false)
    setApplyError('')
  }, [creature.id, creature.programSource])

  const snackNumber = creature.goal.match(/snack\s+(\d+)/i)?.[1]
  const target = snackNumber ? food.find((item) => item.id === snackNumber || item.id === `f${snackNumber}`) : undefined
  const gazeX = target ? Math.sign(target.x - creature.x) * 2.5 : 0
  const gazeY = target ? Math.sign(target.y - creature.y) * 2 : 0
  const gazeStyle = { '--gaze-x': `${gazeX}px`, '--gaze-y': `${gazeY}px` } as CSSProperties

  const apply = async () => {
    setApplyError('')
    setApplying(true)
    try {
      await onApplyProgram(creature.id, draft)
      setEditing(false)
    } catch (error) {
      setApplyError(error instanceof Error ? error.message : 'The program could not be applied.')
    } finally {
      setApplying(false)
    }
  }

  return <aside className={`journal-page modern-journal ${creature.kind}`} aria-label={`${creature.name}'s creature journal`}>
    <div className="journal-page-inner">
      <div className="journal-topline"><span>Creature journal <b>№ {creature.id.padStart(2, '0')}</b></span><button className="journal-close" onClick={onClose} aria-label="Close field journal">×</button></div>
      <div className={`journal-portrait ${creature.kind}`}>
        <div className="portrait-wash"/>
        <div className="portrait-creature" style={gazeStyle}><i className="portrait-eye left"/><i className="portrait-eye right"/><i className="portrait-cheek left"/><i className="portrait-cheek right"/><i className="portrait-sprout"/></div>
      </div>
      <div className="journal-identity">
        <div><p className="journal-kicker">{creature.kind === 'moss' ? 'Mosskin' : creature.kind === 'coral' ? 'Sunspore' : 'Moonlet'} <span>·</span> Generation {String(creature.generation).padStart(2, '0')}</p><h2>{creature.name}</h2></div>
        <div className="energy-seal"><strong>{Math.round(creature.energy)}<small>%</small></strong><span>Energy</span></div>
      </div>
      <div className="journal-energy" aria-label={`Energy ${Math.round(creature.energy)} percent`}><span style={{ width: `${creature.energy}%` }}/></div>
      <p className="journal-observation"><span>Observation</span>{creature.observation}</p>
      <div className="journal-facts"><div><span>Right now</span><strong>{creature.goal || 'Holding position'}</strong></div><div><span>Last rule</span><strong>{creature.lastRule}</strong></div></div>
      <div className="journal-section-title"><span>Rules</span><span className={`note-state ${editing && draft !== creature.programSource ? 'unsaved' : 'applied'}`}>{editing && draft !== creature.programSource ? 'Unsaved edits' : 'Running in Java'}</span><button onClick={() => { setDraft(creature.programSource); setApplyError(''); setEditing(true) }} aria-label="Edit creature program">{editing ? 'Edit program' : 'Edit program'}</button></div>
      {!editing && <div className="journal-rule-list">{creature.rules.map((rule, index) => <button className="rule-card journal-rule" key={rule.id} onClick={() => { setDraft(creature.programSource); setEditing(true) }} aria-label={`Edit ${rule.id}: ${rule.source}`}><span className="rule-index">{String(index + 1).padStart(2, '0')}</span><code>{rule.source}</code><span className={`rule-dot ${index % 2 ? 'lilac' : ''}`}/></button>)}{creature.rules.length === 0 && <p className="empty-rules">No executable rules are installed.</p>}</div>}
      {editing && <div className="rule-editor journal-editor"><div className="editor-title"><span>Creature program</span><button onClick={() => { setDraft(creature.programSource); setApplyError(''); setEditing(false) }}>Close</button></div><label className="program-label" htmlFor="creature-program">Edit the rules that run on the next tick</label><textarea id="creature-program" className="program-source" spellCheck={false} value={draft} onChange={(event) => setDraft(event.target.value)} placeholder={'when energy <= 25000: rest\nwhen nearest_food <= 300000: seek nearest_food\notherwise: wander'}/><p className="editor-hint">One rule per line. Try <code>when energy &lt; 25000: rest</code> or <code>otherwise: seek nearest_food</code>.</p>{applyError && <p className="apply-error" role="alert">{applyError}</p>}<div className="editor-actions"><button className="save-rule" onClick={() => { setDraft(creature.programSource); setApplyError(''); setEditing(false) }}>Cancel</button><button className="apply-rule" disabled={applying} onClick={() => void apply()}>{applying ? 'Compiling…' : 'Apply and step'}</button></div></div>}
      <div className="journal-family"><div className="journal-section-title"><span>In this world</span><b>{String(creatures.length).padStart(2, '0')}</b></div><div className="avatar-row">{creatures.map((member) => <button key={member.id} onClick={() => select(member.id)} className={`mini-avatar ${member.kind} ${creature.id === member.id ? 'selected' : ''}`} aria-label={`Select ${member.name}`} title={member.name}><i/><span>{member.name}</span></button>)}</div></div>
    </div>
  </aside>
}
