import { useCallback, useEffect, useMemo, useState } from 'react'
import { Inspector } from './components/Inspector'
import { Terrarium } from './components/Terrarium'
import { advanceFixture, applyRules, createFixture, updateSavedRules } from './simulation/fixture'
import type { CreatureRule, WorldSnapshot } from './simulation/types'

export default function App() {
  const [running, setRunning] = useState(true)
  const [speed, setSpeed] = useState(1)
  const [world, setWorld] = useState<WorldSnapshot>(createFixture)
  const [selectedId, setSelectedId] = useState('c1')
  const [showForkNote, setShowForkNote] = useState(false)
  useEffect(() => {
    if (!running) return
    const timer = window.setInterval(() => setWorld((current) => advanceFixture(current, speed)), 50)
    return () => window.clearInterval(timer)
  }, [running, speed])
  const selected = useMemo(() => world.creatures.find((creature) => creature.id === selectedId) ?? world.creatures[0], [world.creatures, selectedId])
  const selectCreature = useCallback((id: string) => setSelectedId(id), [])
  const saveRules = useCallback((id: string, rules: CreatureRule[]) => setWorld((current) => updateSavedRules(current, id, rules)), [])
  const activateRules = useCallback((id: string, rules: CreatureRule[]) => setWorld((current) => applyRules(current, id, rules)), [])
  return <main className="app-shell">
    <header className="topbar"><a className="brand" href="#"><span className="brand-mark"><i /><i /><i /></span><span>brood</span></a><div className="top-center"><span className="live-dot" /> A LITTLE WORLD, UNFOLDING</div><div className="top-actions"><span className="save-state"><i /> all tucked in</span><button className="round-button" aria-label="Open settings">☼</button><div className="profile">A</div></div></header>
    <div className="workspace"><section className="world-column"><div className="title-row"><div><div className="breadcrumb">YOUR WORLDS <span>/</span> <b>NO. 01</b></div><h1>Fernlight <span>✳</span></h1><p className="subtitle">A small, sun-warmed place for curious things.</p></div><button className="more-button" aria-label="More world actions">•••</button></div>
      <section className="terrarium-frame"><div className="glass-label"><span className="live-dot" /> <b>FERNLIGHT</b><span>·</span> DAY 04</div><Terrarium world={world} selectedId={selectedId} onSelect={selectCreature} />{world.food.length === 0 && <div className="empty-world">The last snack is gone. Little lives are exploring.</div>}<div className="world-tooltip tip-one">✳ &nbsp; food for curious noses</div><div className="canvas-caption"><span>{world.creatures.length} little lives</span><span>·</span><span>{world.food.length} snacks</span></div></section>
      <div className="playback"><button className={`play-button ${running ? 'playing' : ''}`} onClick={() => setRunning(!running)} aria-label={running ? 'Pause simulation' : 'Play simulation'}>{running ? 'Ⅱ' : '▶'}</button><span className="play-label">{running ? 'WANDERING ALONG' : 'TAKING A BREATHER'}</span><span className="control-divider"/><div className="speed-control"><span>SPEED</span>{[1, 2, 4].map((value) => <button key={value} onClick={() => setSpeed(value)} className={speed === value ? 'active' : ''} aria-pressed={speed === value}>{value}×</button>)}</div><span className="tick-count">DAY 04 <i>·</i> {Math.floor(world.tick).toString().padStart(4, '0')} ticks</span><button className="restart-button" onClick={() => { setWorld(createFixture()); setSelectedId('c1') }} aria-label="Restart simulation">↺</button></div>
      <div className="under-note"><span className="sun-icon">☼</span><span>It’s a good day to just watch.</span><span className="under-line"/><span className="under-detail">THE LIGHT IS SOFT TODAY</span></div>
    </section><section className="side-column"><div className="side-header"><div><div className="breadcrumb">A CLOSER LOOK</div><h2>A little field note</h2></div><span className="leaf-doodle">❋</span></div><Inspector creature={selected} select={selectCreature} onSaveRules={saveRules} onApplyRules={activateRules} /><section className="fork-card"><div className="fork-topline"><span className="fork-icon">⑂</span><span className="coming-soon">ON THE HORIZON</span></div><h3>Fork Time</h3><p>Keep this moment. Wonder about another path.</p><div className="branch-preview" aria-label="Future side-by-side simulation comparison preview"><div><span>THIS MOMENT</span><i /></div><b>↔</b><div><span>ANOTHER PATH</span><i /></div></div><button className="fork-info-button" onClick={() => setShowForkNote(!showForkNote)} aria-expanded={showForkNote}>{showForkNote ? 'Close note' : 'What is Fork Time?'}</button>{showForkNote && <p className="fork-note">Fork Time will let you save a moment and explore a second version of the world beside it. Branching and replay are still being built.</p>}</section><div className="gentle-tip"><span>✦</span><p><b>A gentle thought</b><br/>Every little life has its own pace.<br/>There’s no wrong way to wander.</p></div></section></div>
    <footer className="footer"><span>MADE FOR SMALL CURIOSITIES</span><span>WORLD SEED <b>7F3A · 21C9</b></span><span>STILL GROWING <i>✿</i></span></footer>
  </main>
}
