interface Slice {
  label: string
  value: number
  color: string
}

// Hand-rolled SVG donut (no charting library). Draws each slice as an arc path.
export default function DonutChart({ data, size = 160 }: { data: Slice[]; size?: number }) {
  const total = data.reduce((s, d) => s + d.value, 0) || 1
  const r = size / 2
  const inner = r * 0.6
  let angle = -Math.PI / 2 // start at top

  const arc = (start: number, end: number) => {
    const large = end - start > Math.PI ? 1 : 0
    const x1 = r + r * Math.cos(start)
    const y1 = r + r * Math.sin(start)
    const x2 = r + r * Math.cos(end)
    const y2 = r + r * Math.sin(end)
    const xi2 = r + inner * Math.cos(end)
    const yi2 = r + inner * Math.sin(end)
    const xi1 = r + inner * Math.cos(start)
    const yi1 = r + inner * Math.sin(start)
    return `M ${x1} ${y1} A ${r} ${r} 0 ${large} 1 ${x2} ${y2} L ${xi2} ${yi2} A ${inner} ${inner} 0 ${large} 0 ${xi1} ${yi1} Z`
  }

  return (
    <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} role="img" aria-label="Donut chart">
      {data.map((d) => {
        const sweep = (d.value / total) * Math.PI * 2
        const start = angle
        const end = angle + sweep
        angle = end
        return <path key={d.label} d={arc(start, end)} fill={d.color} />
      })}
    </svg>
  )
}
