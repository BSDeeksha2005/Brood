import { useEffect, useRef } from 'react'
import { engineStore } from '../simulation/EngineStore'
import { TerrariumCanvas } from '../simulation/TerrariumCanvas'
export function Terrarium({ selectedId, onSelect }: { selectedId: string; onSelect: (id: string) => void }) {
  const ref = useRef<HTMLCanvasElement>(null), rendererRef = useRef<TerrariumCanvas | null>(null)
  useEffect(() => {
    if (!ref.current) return
    const renderer = new TerrariumCanvas(ref.current, onSelect)
    rendererRef.current = renderer
    const initialWorld = engineStore.getSnapshot().world
    if (initialWorld) renderer.setWorld(initialWorld)
    const unsubscribe = engineStore.subscribe(() => {
      const world = engineStore.getSnapshot().world
      if (world) renderer.setWorld(world)
    })
    return () => { unsubscribe(); renderer.destroy(); rendererRef.current = null }
  }, [onSelect])
  useEffect(() => { rendererRef.current?.setSelected(selectedId) }, [selectedId])
  return <canvas ref={ref} className="world-canvas" aria-label="Living terrarium. Click a creature to open its field journal." />
}
