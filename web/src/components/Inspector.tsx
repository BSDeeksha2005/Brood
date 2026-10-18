import { useEffect, useState } from 'react'
import type { Creature, CreatureRule, RuleAction, RuleCondition } from '../simulation/types'

const family = [
  { id: 'c1', name: 'Mallow', kind: 'moss' }, { id: 'c2', name: 'Pip', kind: 'coral' },
  { id: 'c3', name: 'Lumen', kind: 'lilac' }, { id: 'c4', name: 'Nori', kind: 'moss' },
  { id: 'c5', name: 'Peach', kind: 'coral' }, { id: 'c6', name: 'Thimble', kind: 'lilac' },
]
const copyRules = (rules: CreatureRule[]) => rules.map((rule) => ({ ...rule }))
const equalRules = (left: CreatureRule[], right: CreatureRule[]) => JSON.stringify(left) === JSON.stringify(right)

export function Inspector({ creature, select, onSaveRules, onApplyRules }: {
  creature: Creature; select: (id: string) => void
  onSaveRules: (id: string, rules: CreatureRule[]) => void
  onApplyRules: (id: string, rules: CreatureRule[]) => void
}) {
  const [editing, setEditing] = useState(false), [draft, setDraft] = useState(() => copyRules(creature.savedRules))
  useEffect(() => { setDraft(copyRules(creature.savedRules)); setEditing(false) }, [creature.id])
  const dirty = !equalRules(draft, creature.savedRules)
  const applied = equalRules(creature.savedRules, creature.rules)
  const updateRule = (id: string, field: 'condition' | 'action', value: RuleCondition | RuleAction) => setDraft((rules) => rules.map((rule) => rule.id === id ? { ...rule, [field]: value } : rule))
  return <aside className="paper-card">
    <div className="paper-top"><span className="eyebrow">FIELD NOTES <span>№ 0{creature.id.slice(1)}</span></span><span className="saved-state"><i /> {dirty ? 'Unsaved edits' : applied ? 'Applied' : 'Saved · not applied'}</span></div>
    <div className="creature-heading"><span className={`creature-stamp ${creature.kind}`}><i /></span><div><h2>{creature.name}</h2><p>{creature.kind === 'moss' ? 'Mosskin' : creature.kind === 'coral' ? 'Sunspore' : 'Moonlet'} <span>·</span> generation {String(creature.generation).padStart(2, '0')}</p></div></div>
    <div className="energy-row"><span>ENERGY</span><strong>{Math.round(creature.energy)}%</strong></div><div className="energy-track"><span style={{ width: `${creature.energy}%` }} /></div>
    <div className="creature-readout"><div><span>RIGHT NOW</span><strong>{creature.goal || 'Finding a small adventure'}</strong></div><div><span>LAST RULE</span><strong>{creature.lastRule}</strong></div></div>
    <div className="section-label rule-heading">LITTLE RULES <button onClick={() => { setDraft((rules) => [...rules, { id: `r${Math.max(0, ...rules.map((rule) => Number(rule.id.slice(1)) || 0)) + 1}`, condition: 'near-food', action: 'follow-scent' }]); setEditing(true) }} aria-label="Add a rule">+ Add rule</button></div>
    {!editing && <div className="rule-list">{creature.rules.map((rule, index) => <button className="rule-card" key={rule.id} onClick={() => { setDraft(copyRules(creature.savedRules)); setEditing(true) }}><span className="rule-index">{String(index + 1).padStart(2, '0')}</span><span>If <b>{rule.condition === 'near-food' ? 'near food' : 'energy is low'}</b><br/>then <em>{rule.action === 'follow-scent' ? 'follow scent' : 'rest quietly'}</em></span><span className="rule-dot" /></button>)}{creature.rules.length === 0 && <p className="empty-rules">No rules yet. Add one to shape this little life.</p>}</div>}
    {editing && <div className="rule-editor"><div className="editor-title"><span>RULE EDITOR</span><button onClick={() => { setDraft(copyRules(creature.savedRules)); setEditing(false) }}>Close</button></div>{draft.map((rule, index) => <div className="edit-rule" key={rule.id}><span className="rule-index">{String(index + 1).padStart(2, '0')}</span><label><span>If</span><select value={rule.condition} onChange={(event) => updateRule(rule.id, 'condition', event.target.value as RuleCondition)}><option value="near-food">near food</option><option value="energy-low">energy is low</option></select></label><label><span>Then</span><select value={rule.action} onChange={(event) => updateRule(rule.id, 'action', event.target.value as RuleAction)}><option value="follow-scent">follow scent</option><option value="rest">rest quietly</option></select></label><button className="remove-rule" onClick={() => setDraft((rules) => rules.filter((item) => item.id !== rule.id))} aria-label={`Remove rule ${index + 1}`}>×</button></div>)}<div className="editor-actions"><button className="save-rule" disabled={!dirty} onClick={() => onSaveRules(creature.id, draft)}>Save edits</button><button className="apply-rule" disabled={!applied && !dirty && equalRules(creature.rules, draft)} onClick={() => { onApplyRules(creature.id, draft); setEditing(false) }}>Apply to creature</button></div><p className="editor-hint">Saving keeps a note. Applying changes this creature’s behaviour.</p></div>}
    <div className="siblings"><div className="section-label">THE LITTLE FAMILY <span>06</span></div><div className="avatar-row">{family.map((s) => <button key={s.id} onClick={() => select(s.id)} className={`mini-avatar ${s.kind} ${creature.id === s.id ? 'selected' : ''}`} aria-label={`Select ${s.name}`} title={s.name}><i /></button>)}</div></div>
  </aside>
}
