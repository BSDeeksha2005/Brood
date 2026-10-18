import { useEffect, useRef } from 'react'
import { TerrariumCanvas } from '../simulation/TerrariumCanvas'
import type { WorldSnapshot } from '../simulation/types'
export function Terrarium({ world, selectedId, onSelect }: { world: WorldSnapshot; selectedId: string; onSelect: (id: string) => void }) {
  const ref = useRef<HTMLCanvasElement>(null), rendererRef = useRef<TerrariumCanvas | null>(null)
  useEffect(() => { if (!ref.current) return; const renderer = new TerrariumCanvas(ref.current, onSelect); rendererRef.current = renderer; return () => { renderer.destroy(); rendererRef.current = null } }, [onSelect])
  useEffect(() => { rendererRef.current?.setWorld(world) }, [world])
  useEffect(() => { rendererRef.current?.setSelected(selectedId) }, [selectedId])
  return <canvas ref={ref} className="world-canvas" aria-label="Select a creature in the terrarium" />
}
