import { useCallback, useEffect, useState, useSyncExternalStore } from 'react'
import { Inspector } from './components/Inspector'
import { ForkTimePanel } from './components/ForkTimePanel'
import { Terrarium } from './components/Terrarium'
import { engineStore } from './simulation/EngineStore'
import type { ForkExperiment } from './simulation/types'

function useEngineView() {
  return useSyncExternalStore(engineStore.subscribe, engineStore.getSnapshot, engineStore.getSnapshot)
}

function formatTick(tick: number) { return Math.floor(tick).toLocaleString('en-US', { minimumIntegerDigits: 5 }) }

function CreatureJournal({ selectedId, onSelect, onClose }: { selectedId: string; onSelect: (id: string) => void; onClose: () => void }) {
  const { world } = useEngineView()
  const creatures = world?.creatures ?? []
  const creature = creatures.find((item) => item.id === selectedId) ?? creatures[0]
  if (!creature) return null
  return <Inspector creature={creature} creatures={creatures} food={world?.food ?? []} select={onSelect}
    onApplyProgram={(id, source) => engineStore.applyProgram(id, source)} onClose={onClose} />
}

function TickReadout() {
  const { status, world } = useEngineView()
  return <div className="dock-clock" title="Current Java simulation tick">
    <span>JAVA ENGINE</span><i>·</i><strong>{formatTick(world?.tick ?? 0)}</strong><small>TICK</small>
    {status === 'connecting' && <span className="engine-connecting">Connecting</span>}
  </div>
}

function WorldCounts() {
  const { world } = useEngineView()
  if (!world) return null
  return <div className="world-counts"><span>{world.creatures.length} little lives</span><i>·</i><span>{world.food.length} snacks</span></div>
}

export default function App() {
  const [running, setRunning] = useState(true)
  const [speed, setSpeed] = useState(1)
  const [selectedId, setSelectedId] = useState('1')
  const [journalOpen, setJournalOpen] = useState(true)
  const [forkOpen, setForkOpen] = useState(false)
  const [experiment, setExperiment] = useState<ForkExperiment | null>(null)
  const { status, error, world } = useEngineView()

  useEffect(() => { void engineStore.connect() }, [])
  useEffect(() => {
    if (!running || status !== 'ready') return
    let cancelled = false
    let timer = 0
    const schedule = () => {
      timer = window.setTimeout(async () => {
        await engineStore.advance(speed).catch(() => undefined)
        if (!cancelled && engineStore.getSnapshot().status === 'ready') schedule()
      }, 250)
    }
    schedule()
    return () => { cancelled = true; window.clearTimeout(timer) }
  }, [running, speed, status])

  const selectCreature = useCallback((id: string) => {
    setSelectedId(id)
    setJournalOpen(true)
    setForkOpen(false)
  }, [])
  const toggleFork = () => {
    setForkOpen((open) => { if (!open) setRunning(false); return !open })
    setJournalOpen(false)
  }
  const restart = () => { void engineStore.restart().catch(() => undefined); setSelectedId('1'); setJournalOpen(true); setForkOpen(false) }

  return <main className="game-shell">
    <div className="world-backdrop" aria-hidden="true"/>
    <Terrarium selectedId={selectedId} onSelect={selectCreature}/>

    <header className="game-header">
      <a className="brand" href="#" aria-label="Brood home"><span className="brand-mark"><i/><i/><i/></span><span>brood</span></a>
      <div className="world-identity"><span className="identity-spark">✳</span><div><strong>Fernlight</strong><small>A little world, unfolding</small></div></div>
      <div className="header-actions">
        <button className={`header-action ${journalOpen ? 'active' : ''}`} onClick={() => { setJournalOpen((open) => !open); setForkOpen(false) }} aria-expanded={journalOpen}><span>◉</span><b>Field journal</b></button>
        <button className={`header-action fork-action ${forkOpen ? 'active' : ''}`} onClick={toggleFork} aria-expanded={forkOpen}><span>⑂</span><b>Fork Time</b></button>
      </div>
    </header>

    <div className="world-whisper"><span className="whisper-flower">❋</span><span>A small, sun-warmed place<br/>for curious things.</span></div>
    <div className="creature-caption" aria-live="polite"><span className="caption-pip"/><span>Click a little life to follow its path.</span></div>

    <div className={`journal-drawer ${journalOpen ? 'open' : ''}`} aria-hidden={!journalOpen}>
      <CreatureJournal selectedId={selectedId} onSelect={selectCreature} onClose={() => setJournalOpen(false)}/>
    </div>
    {forkOpen && <ForkTimePanel world={world} experiment={experiment}
      onExperimentChange={setExperiment} onClose={() => setForkOpen(false)}
      onReturn={() => { setForkOpen(false); setJournalOpen(true) }} />}

    {status === 'error' && <div className="engine-error" role="alert"><strong>Java simulation unavailable</strong><span>{error || 'Start the Brood server, then reconnect.'}</span><button onClick={() => void engineStore.connect()}>Retry connection</button></div>}
    <WorldCounts/>
    <div className="control-dock">
      <button className={`dock-play ${running ? 'is-running' : ''}`} onClick={() => setRunning((value) => !value)} aria-label={running ? 'Pause simulation' : 'Play simulation'}>{running ? 'Ⅱ' : '▶'}</button>
      <span className="dock-separator"/>
      <div className="dock-speed" aria-label="Simulation speed"><small>PACE</small>{[1, 2, 4].map((value) => <button key={value} onClick={() => setSpeed(value)} className={speed === value ? 'selected' : ''} aria-pressed={speed === value}>{value}<sup>×</sup></button>)}</div>
      <span className="dock-separator"/>
      <TickReadout/>
      <button className={`dock-fork ${forkOpen ? 'selected' : ''}`} onClick={toggleFork} aria-label="Open Fork Time preview" aria-expanded={forkOpen} title="Explore Fork Time"><span>⑂</span><b>Fork</b></button>
      <button className="dock-restart" onClick={restart} aria-label="Restart simulation" title="Start this world again">↺</button>
    </div>
  </main>
}
