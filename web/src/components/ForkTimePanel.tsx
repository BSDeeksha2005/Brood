import { useEffect, useMemo, useState } from 'react'
import { forkStore } from '../simulation/ForkStore'
import type { ForkExperiment, WorldSnapshot } from '../simulation/types'

function formatTick(tick: number) { return Math.floor(tick).toLocaleString('en-US', { minimumIntegerDigits: 5 }) }
function shortHash(hash: string) { return `${hash.slice(0, 12)}…` }
function isMissingExperiment(reason: unknown) {
  return reason instanceof Error && 'status' in reason && reason.status === 404
}

function WorldMiniature({ world, placeholder = false }: { world: WorldSnapshot | null; placeholder?: boolean }) {
  return <div className={`timeline-world ${placeholder ? 'alternate' : ''}`} aria-label={placeholder ? 'Alternate world preview' : 'Saved simulation branch'}>
    {world && <>
      {world.food.map((food) => <i key={food.id} className="mini-snack" style={{ left: `${food.x * 100}%`, top: `${food.y * 100}%` }} />)}
      {world.creatures.map((creature) => <i key={creature.id} className={`mini-life ${creature.kind}`} style={{ left: `${creature.x * 100}%`, top: `${creature.y * 100}%` }} />)}
    </>}
    {placeholder && !world && <span className="alternate-placeholder">A second world starts from the same saved moment</span>}
  </div>
}

export function ForkTimePanel({ world, experiment, onExperimentChange, onClose, onReturn }: {
  world: WorldSnapshot | null
  experiment: ForkExperiment | null
  onExperimentChange: (experiment: ForkExperiment | null) => void
  onClose: () => void
  onReturn: () => void
}) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [selectedCreatureId, setSelectedCreatureId] = useState('1')
  const [programDraft, setProgramDraft] = useState('')
  const [advanceTicks, setAdvanceTicks] = useState(10)

  useEffect(() => {
    if (experiment) return
    let active = true
    setBusy(true)
    void forkStore.load().then((saved) => { if (active && saved) onExperimentChange(saved) })
      .catch((reason: unknown) => { if (active) setError(reason instanceof Error ? reason.message : 'Could not load the saved experiment.') })
      .finally(() => { if (active) setBusy(false) })
    return () => { active = false }
  }, [])

  const alternateCreatures = experiment?.alternate.world.creatures ?? []
  const selectedCreature = alternateCreatures.find((creature) => creature.id === selectedCreatureId) ?? alternateCreatures[0]
  useEffect(() => {
    if (!alternateCreatures.some((creature) => creature.id === selectedCreatureId) && alternateCreatures[0]) {
      setSelectedCreatureId(alternateCreatures[0].id)
    }
    if (selectedCreature) setProgramDraft(selectedCreature.programSource)
  }, [experiment?.id, selectedCreatureId, selectedCreature?.programSource])

  const differences = useMemo(() => {
    if (!experiment) return []
    const originals = new Map(experiment.original.world.creatures.map((creature) => [creature.id, creature]))
    return experiment.alternate.world.creatures.flatMap((alternate) => {
      const original = originals.get(alternate.id)
      if (!original) return []
      const dx = alternate.x - original.x, dy = alternate.y - original.y
      const energyChanged = alternate.energy !== original.energy
      const positionChanged = dx !== 0 || dy !== 0
      const ruleChanged = alternate.lastRule !== original.lastRule
      const programChanged = alternate.programSource !== original.programSource
      if (!energyChanged && !positionChanged && !ruleChanged && !programChanged) return []
      return [{ name: alternate.name, energy: alternate.energy - original.energy,
        distance: Math.hypot(dx, dy) * 100, originalRule: original.lastRule,
        alternateRule: alternate.lastRule, programChanged }]
    })
  }, [experiment])

  const perform = async (action: () => Promise<ForkExperiment>) => {
    setBusy(true); setError('')
    try { onExperimentChange(await action()) }
    catch (reason) {
      if (isMissingExperiment(reason)) {
        onExperimentChange(null)
        setError('This experiment expired when the Java session restarted. Save a new moment to continue.')
      } else {
        setError(reason instanceof Error ? reason.message : 'The experiment request failed.')
      }
    }
    finally { setBusy(false) }
  }

  const capture = () => void perform(() => forkStore.capture())
  const applyAlternate = () => {
    if (!experiment || !selectedCreature) return
    void perform(() => forkStore.applyAlternateProgram(experiment.id, selectedCreature.id, programDraft))
  }
  const advance = (branch?: 'original' | 'alternate') => {
    if (!experiment) return
    void perform(() => forkStore.advance(experiment.id, advanceTicks, branch))
  }
  const discard = async () => {
    if (!experiment) return
    setBusy(true); setError('')
    try { await forkStore.discard(experiment.id); onExperimentChange(null) }
    catch (reason) {
      if (isMissingExperiment(reason)) onExperimentChange(null)
      else setError(reason instanceof Error ? reason.message : 'Could not clear the experiment.')
    }
    finally { setBusy(false) }
  }

  const originalWorld = experiment?.original.world ?? world
  const alternateWorld = experiment?.alternate.world ?? null
  const originalTick = experiment?.original.world.tick ?? world?.tick ?? 0
  const alternateTick = experiment?.alternate.world.tick

  return <section className="time-room" aria-label="Fork Time experiment">
    <div className="time-room-heading">
      <div><span className="journal-kicker">A FIELD EXPERIMENT</span><h2>Fork Time <em>Compare two futures from one moment.</em></h2></div>
      <button className="quiet-close" onClick={onClose} aria-label="Close Fork Time">×</button>
    </div>
    {!experiment && <div className="fork-empty-state">
      <p>Save the current Java world, then try a different program in an independent alternate branch.</p>
      <button className="fork-primary" disabled={busy || !world} onClick={capture}>{busy ? 'Saving snapshot…' : 'Save moment & create branches'}</button>
    </div>}
    {experiment && <>
      <div className="time-track" aria-label={`Saved snapshot at tick ${experiment.snapshotTick}`}>
        <span>SNAPSHOT · TICK {formatTick(experiment.snapshotTick)}</span>
        <span>SHA-256 · {shortHash(experiment.snapshotHash)}</span>
        <div className="track-rail"><i className="track-progress"/><i className="track-now"/></div>
        <span className="track-future-label">BRANCHES ADVANCE IN FIXED TICKS · PAUSED BETWEEN ACTIONS</span>
      </div>
      <div className="timeline-worlds">
        <div className="timeline-world-card"><div className="world-card-label"><span>ORIGINAL · {experiment.original.id.toUpperCase()}</span><b>TICK {formatTick(originalTick)}</b></div><WorldMiniature world={originalWorld}/><p>Unchanged unless you advance this branch.</p><code className="fork-hash">{shortHash(experiment.original.stateHash)}</code></div>
        <div className="branch-glyph" aria-hidden="true"><span/><b>same snapshot</b><span/></div>
        <div className="timeline-world-card future-card"><div className="world-card-label"><span>ALTERNATE · {experiment.alternate.id.toUpperCase()}</span><b>TICK {formatTick(alternateTick ?? 0)}</b></div><WorldMiniature world={alternateWorld} placeholder/><p>Independent state with its own program edits.</p><code className="fork-hash">{shortHash(experiment.alternate.stateHash)}</code></div>
      </div>
      <div className="fork-experiment-controls">
        <label>Advance by <select value={advanceTicks} onChange={(event) => setAdvanceTicks(Number(event.target.value))}><option value={1}>1 tick</option><option value={10}>10 ticks</option><option value={50}>50 ticks</option></select></label>
        <button disabled={busy} onClick={() => advance('original')}>Advance original</button>
        <button disabled={busy} onClick={() => advance('alternate')}>Advance alternate</button>
        <button className="fork-primary" disabled={busy} onClick={() => advance()}>{busy ? 'Advancing…' : 'Advance both'}</button>
      </div>
      <div className="fork-rule-editor">
        <label htmlFor="alternate-creature">Change a creature in the alternate</label>
        <select id="alternate-creature" value={selectedCreature?.id ?? ''} onChange={(event) => setSelectedCreatureId(event.target.value)}>
          {alternateCreatures.map((creature) => <option key={creature.id} value={creature.id}>{creature.name}</option>)}
        </select>
        <textarea aria-label="Alternate creature program" spellCheck={false} value={programDraft} onChange={(event) => setProgramDraft(event.target.value)} />
        <button disabled={busy || !selectedCreature} onClick={applyAlternate}>{busy ? 'Working…' : 'Apply to alternate'}</button>
      </div>
      <div className="fork-comparison" aria-live="polite">
        <div><strong>Observed differences</strong><span>{differences.length} creatures differ · {experiment.original.world.food.length} vs {experiment.alternate.world.food.length} snacks</span></div>
        {differences.length === 0 && <p>The branch states currently match in creature position, energy, and latest rule.</p>}
        {differences.map((difference) => <p key={difference.name}><b>{difference.name}</b>{difference.programChanged && <span>Program changed</span>}<span>Energy {difference.energy > 0 ? '+' : ''}{difference.energy.toFixed(2)}%</span><span>Moved {difference.distance.toFixed(2)}% of world</span><span>{difference.originalRule} → {difference.alternateRule}</span></p>)}
      </div>
      <div className="time-room-footer"><div className="time-limit-note"><span className="limit-mark">✳</span><span><strong>Each branch has its own snapshot-derived state.</strong><small>Returning to the terrarium leaves the original world paused at its saved moment.</small></span></div><div><button className="edit-current-world" onClick={onReturn}>Return to field journal</button><button disabled={busy} onClick={() => void discard()}>Clear experiment</button></div></div>
    </>}
    {error && <p className="fork-error" role="alert">{error}</p>}
  </section>
}
