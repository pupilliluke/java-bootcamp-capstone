interface Bar {
  label: string
  value: number
  color: string
}

// Hand-rolled SVG vertical bar chart (no charting library).
export default function BarChart({ data, height = 160 }: { data: Bar[]; height?: number }) {
  const max = Math.max(...data.map((d) => d.value), 1)
  const barW = 34
  const gap = 26
  const width = data.length * (barW + gap) + gap
  const chartH = height - 24 // leave room for value labels

  return (
    <svg width="100%" height={height} viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Bar chart">
      {data.map((d, i) => {
        const h = (d.value / max) * (chartH - 10)
        const x = gap + i * (barW + gap)
        const y = chartH - h
        return (
          <g key={d.label}>
            <rect x={x} y={y} width={barW} height={h} rx={4} fill={d.color} />
            <text x={x + barW / 2} y={y - 6} textAnchor="middle" fontSize="11" fill="#6b7684">
              {d.value}
            </text>
          </g>
        )
      })}
    </svg>
  )
}
