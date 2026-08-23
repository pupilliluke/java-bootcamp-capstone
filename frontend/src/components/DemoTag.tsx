// Visible honesty marker: any screen or panel driven by mockData shows this so
// fabricated data is never mistaken for real, persisted backend data.
export default function DemoTag() {
  return <span className="demo-tag" title="Hardcoded demo data — no backend endpoint exists yet">◇ Demo data</span>
}
