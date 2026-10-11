import { useEffect, useState } from 'react'
import { CircleMarker, MapContainer, Popup, Polyline, TileLayer, useMap } from 'react-leaflet'

function localDateTimeValue() {
  const date = new Date()
  date.setMinutes(date.getMinutes() - date.getTimezoneOffset())
  return date.toISOString().slice(0, 16)
}

function formatDuration(seconds) {
  if (seconds < 60) return `${Math.round(seconds)} sec`
  const minutes = Math.round(seconds / 60)
  if (minutes < 60) return `${minutes} min`
  return `${Math.floor(minutes / 60)} hr ${minutes % 60} min`
}

function formatPreciseDuration(seconds) {
  const minutes = Math.floor(seconds / 60)
  const remainingSeconds = Math.round(seconds % 60)
  if (minutes === 0) return `${remainingSeconds} sec`
  return `${minutes} min ${remainingSeconds} sec`
}

function formatDistance(meters) {
  return `${(meters / 1000).toFixed(1)} km`
}

function FitRoute({ points }) {
  const map = useMap()

  useEffect(() => {
    if (points.length > 1) map.fitBounds(points, { padding: [42, 42], maxZoom: 15 })
  }, [map, points])

  return null
}

function PlaceField({ id, label, value, selectedPlace, onChange, onSelect }) {
  const [suggestions, setSuggestions] = useState([])
  const [searching, setSearching] = useState(false)
  const [searched, setSearched] = useState(false)

  useEffect(() => {
    const query = value.trim()
    if (selectedPlace || query.length < 4 || /^-?\d+(\.\d+)?\s*,\s*-?\d+(\.\d+)?$/.test(query)) {
      setSuggestions([])
      setSearching(false)
      setSearched(false)
      return
    }

    const controller = new AbortController()
    const timeout = setTimeout(async () => {
      setSearching(true)
      try {
        const response = await fetch(`/api/geocode?q=${encodeURIComponent(query)}`, { signal: controller.signal })
        if (!response.ok) throw new Error('Place search is temporarily unavailable.')
        setSuggestions(await response.json())
        setSearched(true)
      } catch (error) {
        if (error.name !== 'AbortError') {
          setSuggestions([])
          setSearched(true)
        }
      } finally {
        if (!controller.signal.aborted) setSearching(false)
      }
    }, 1100)

    return () => {
      clearTimeout(timeout)
      controller.abort()
    }
  }, [value, selectedPlace])

  return (
    <div className="place-field">
      <label htmlFor={id}>{label}</label>
      <input
        autoComplete="off"
        id={id}
        maxLength={160}
        onChange={(event) => {
          setSuggestions([])
          setSearched(false)
          onChange(event.target.value)
        }}
        placeholder="Address or place"
        role="combobox"
        aria-autocomplete="list"
        aria-expanded={suggestions.length > 0}
        value={value}
        required
      />
      {searching && <p className="place-search-status" role="status">Searching places…</p>}
      {!searching && searched && suggestions.length > 0 && (
        <div className="place-suggestions" role="listbox" aria-label={`${label} matches`}>
          {suggestions.map((place) => (
            <button
              aria-selected="false"
              key={`${place.location.latitude},${place.location.longitude}`}
              onClick={() => {
                onChange(place.displayName)
                onSelect(place)
                setSuggestions([])
              }}
              role="option"
              type="button"
            >
              {place.displayName}
            </button>
          ))}
        </div>
      )}
      {!searching && searched && suggestions.length === 0 && !selectedPlace && (
        <p className="place-search-status">No matches. Try a shorter place name with its city.</p>
      )}
      {selectedPlace && <p className="place-search-status place-selected">Place selected from OpenStreetMap</p>}
    </div>
  )
}

function App() {
  const [apiStatus, setApiStatus] = useState('checking')
  const [origin, setOrigin] = useState('')
  const [destination, setDestination] = useState('')
  const [originPlace, setOriginPlace] = useState(null)
  const [destinationPlace, setDestinationPlace] = useState(null)
  const [departureTime, setDepartureTime] = useState(localDateTimeValue)
  const [analysis, setAnalysis] = useState(null)
  const [selectedRouteId, setSelectedRouteId] = useState('')
  const [analysisLoading, setAnalysisLoading] = useState(false)
  const [scenarioLoading, setScenarioLoading] = useState(false)
  const [interventionPercent, setInterventionPercent] = useState(15)
  const [scenario, setScenario] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true

    fetch('/api/health')
      .then((response) => {
        if (!response.ok) throw new Error('API unavailable')
        return response.json()
      })
      .then(() => active && setApiStatus('connected'))
      .catch(() => active && setApiStatus('offline'))

    return () => {
      active = false
    }
  }, [])

  const routes = analysis
    ? [analysis.recommendedRoute, ...analysis.alternatives]
    : []
  const selectedRoute = routes.find((route) => route.id === selectedRouteId) ?? routes[0]
  const mapPoints = selectedRoute?.geometry.map((point) => [point.latitude, point.longitude]) ?? []

  async function parseResponse(response) {
    const payload = await response.json().catch(() => ({}))
    if (!response.ok) throw new Error(payload.detail || 'The request could not be completed.')
    return payload
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setAnalysisLoading(true)
    setError('')
    setScenario(null)

    try {
      const result = await parseResponse(await fetch('/api/analysis', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          origin: originPlace ? `${originPlace.location.latitude},${originPlace.location.longitude}` : origin,
          destination: destinationPlace ? `${destinationPlace.location.latitude},${destinationPlace.location.longitude}` : destination,
          departureTime,
        }),
      }))
      setAnalysis(result)
      setSelectedRouteId(result.recommendedRoute.id)
    } catch (requestError) {
      setError(requestError.message)
      setAnalysis(null)
    } finally {
      setAnalysisLoading(false)
    }
  }

  async function handleScenario() {
    setScenarioLoading(true)
    setError('')

    try {
      const result = await parseResponse(await fetch('/api/analysis/scenario', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          origin: originPlace ? `${originPlace.location.latitude},${originPlace.location.longitude}` : origin,
          destination: destinationPlace ? `${destinationPlace.location.latitude},${destinationPlace.location.longitude}` : destination,
          departureTime,
          interventionPercent,
        }),
      }))
      setScenario(result)
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setScenarioLoading(false)
    }
  }

  return (
    <main className="app-shell">
      <header className="topbar">
        <a className="brand" href="/" aria-label="City Bottleneck Explorer home">
          <span className="brand-mark" aria-hidden="true">F</span>
          <span>City Bottleneck Explorer</span>
        </a>
        <div className={`api-status api-status--${apiStatus}`} role="status">
          <span className="status-dot" />
          API {apiStatus === 'checking' ? 'checking' : apiStatus}
        </div>
      </header>

      <section className="workspace" aria-labelledby="page-title">
        <div className="intro">
          <p className="eyebrow">TRAFFIC PLANNING WORKSPACE</p>
          <h1 id="page-title">Find the pressure points.</h1>
          <p className="intro-copy">
            Compare road-network routes and test planning assumptions. Travel times are modeled estimates, not live traffic.
          </p>
        </div>

        <div className="analysis-layout">
          <div className="left-rail">
            <form className="route-panel" onSubmit={handleSubmit}>
              <div className="panel-heading">
                <div>
                  <p className="eyebrow">NEW ANALYSIS</p>
                  <h2>Plan a journey</h2>
                </div>
                <span className="step-number">01</span>
              </div>

              <PlaceField
                id="origin"
                label="Starting point"
                value={origin}
                selectedPlace={originPlace}
                onChange={(value) => {
                  setOrigin(value)
                  setOriginPlace(null)
                }}
                onSelect={setOriginPlace}
              />

              <PlaceField
                id="destination"
                label="Destination"
                value={destination}
                selectedPlace={destinationPlace}
                onChange={(value) => {
                  setDestination(value)
                  setDestinationPlace(null)
                }}
                onSelect={setDestinationPlace}
              />

              <label htmlFor="departure">Departure time</label>
              <input id="departure" name="departure" type="datetime-local" value={departureTime} onChange={(event) => setDepartureTime(event.target.value)} required />

              <button type="submit" disabled={analysisLoading}>
                {analysisLoading ? 'Finding routes…' : 'Analyze route'}
                {!analysisLoading && <span aria-hidden="true">→</span>}
              </button>
              <p className="form-note" aria-live="polite">
                {error || 'Try place names or latitude,longitude coordinates.'}
              </p>
            </form>

            {selectedRoute && (
              <section className="route-options" aria-label="Available route options">
                <div className="section-kicker">
                  <span>ROUTE OPTIONS</span>
                  <span>{routes.length} FOUND</span>
                </div>
                {routes.map((route) => (
                  <button
                    className={`route-choice${route.id === selectedRoute.id ? ' route-choice--selected' : ''}`}
                    key={route.id}
                    onClick={() => {
                      setSelectedRouteId(route.id)
                      setScenario(null)
                    }}
                    type="button"
                  >
                    <span className="route-choice-top">
                      <strong>{route.rank === 1 ? 'Recommended' : `Alternative ${route.rank - 1}`}</strong>
                      <span>{formatDuration(route.estimatedDurationSeconds)}</span>
                    </span>
                    <span className="route-choice-detail">
                      {formatDistance(route.distanceMeters)} · {route.bottlenecks.length} candidates
                    </span>
                  </button>
                ))}
              </section>
            )}
          </div>

          <section className="map-panel" aria-label="Route analysis map">
            <div className="map-toolbar">
              <span className="map-label"><span className="map-pin" /> ROUTE CANVAS</span>
              <span className="map-scale">{selectedRoute ? formatDistance(selectedRoute.distanceMeters) : 'CITY VIEW'}</span>
            </div>
            {selectedRoute ? (
              <MapContainer
                className="leaflet-map"
                center={mapPoints[0]}
                zoom={13}
                scrollWheelZoom
              >
                <TileLayer
                  attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap contributors</a>'
                  url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                />
                <FitRoute points={mapPoints} />
                <Polyline positions={mapPoints} pathOptions={{ color: '#236b4c', weight: 5, opacity: 0.84 }} />
                {selectedRoute.bottlenecks.map((point) => (
                  <CircleMarker
                    center={[point.location.latitude, point.location.longitude]}
                    key={point.id}
                    pathOptions={{ color: '#fff', fillColor: point.severity === 'high' ? '#b94d3c' : '#c18135', weight: 2, fillOpacity: 0.95 }}
                    radius={8}
                  >
                    <Popup>
                      <strong>{point.label}</strong><br />
                      {point.severity} candidate · score {point.pressureScore}/100<br />
                      {point.reason}
                    </Popup>
                  </CircleMarker>
                ))}
              </MapContainer>
            ) : (
              <div className="map-empty">
                <div className="map-glyph" aria-hidden="true">
                  <span className="road road-one" />
                  <span className="road road-two" />
                  <span className="road road-three" />
                  <span className="junction" />
                </div>
                <h2>{analysisLoading ? 'Building your route' : 'Choose two points to begin'}</h2>
                <p>{analysisLoading ? 'Looking up places and comparing available road routes.' : 'Route geometry and candidate pressure points will appear here.'}</p>
              </div>
            )}
            <div className="map-footer">
              <span>{selectedRoute ? `${selectedRoute.bottlenecks.length} CANDIDATE POINTS` : 'NO ROUTE LOADED'}</span>
              <span>ESTIMATES · NOT LIVE TRAFFIC</span>
            </div>
          </section>
        </div>

        {selectedRoute && analysis && (
          <section className="results" aria-live="polite">
            <div className="results-heading">
              <div>
                <p className="eyebrow">MODELED ROUTE REVIEW</p>
                <h2>{analysis.period.label}</h2>
              </div>
              <p className="method-note">{analysis.methodologyNotice}</p>
            </div>

            <div className="metric-row">
              <div className="metric"><span>DISTANCE</span><strong>{formatDistance(selectedRoute.distanceMeters)}</strong></div>
              <div className="metric"><span>ROAD BASELINE</span><strong>{formatDuration(selectedRoute.baselineDurationSeconds)}</strong></div>
              <div className="metric"><span>MODELED AT TIME</span><strong>{formatDuration(selectedRoute.estimatedDurationSeconds)}</strong></div>
              <div className="metric"><span>MODELED DELAY</span><strong>+{formatDuration(selectedRoute.estimatedDelaySeconds)}</strong></div>
            </div>

            <div className="detail-layout">
              <section className="bottleneck-section">
                <div className="section-heading">
                  <div>
                    <p className="eyebrow">INVESTIGATE THESE LOCATIONS</p>
                    <h3>Candidate pressure points</h3>
                  </div>
                  <span className="section-count">{selectedRoute.bottlenecks.length}</span>
                </div>
                {selectedRoute.bottlenecks.length ? (
                  <div className="bottleneck-list">
                    {selectedRoute.bottlenecks.map((point, index) => (
                      <article className="bottleneck-row" key={point.id}>
                        <span className={`pressure-mark pressure-mark--${point.severity}`}>{index + 1}</span>
                        <div className="bottleneck-copy">
                          <strong>{point.label}</strong>
                          <p>{point.reason}</p>
                        </div>
                        <span className="pressure-score">{point.pressureScore}<small>/100</small></span>
                      </article>
                    ))}
                  </div>
                ) : (
                  <p className="empty-note">No candidate points met the model's ranking threshold on this route.</p>
                )}
              </section>

              <section className="scenario-panel">
                <p className="eyebrow">WHAT-IF TEST</p>
                <h3>Test a delay reduction</h3>
                <p className="scenario-copy">Apply a hypothetical reduction to modeled delay at the three highest-ranked points.</p>
                <label htmlFor="intervention">Assumed reduction <strong>{interventionPercent}%</strong></label>
                <input
                  id="intervention"
                  type="range"
                  min="0"
                  max="50"
                  step="5"
                  value={interventionPercent}
                  onChange={(event) => setInterventionPercent(Number(event.target.value))}
                />
                <button className="scenario-button" type="button" onClick={handleScenario} disabled={scenarioLoading}>
                  {scenarioLoading ? 'Calculating…' : 'Compare scenario'}
                  {!scenarioLoading && <span aria-hidden="true">→</span>}
                </button>
                {scenario && (
                  <div className="scenario-result" role="status">
                    <span>MODELED SAVING</span>
                    <strong>{formatDuration(scenario.projectedSecondsSaved)}</strong>
                    <small>{formatPreciseDuration(scenario.baselineDurationSeconds)} → {formatPreciseDuration(scenario.projectedDurationSeconds)}</small>
                  </div>
                )}
                <p className="scenario-disclaimer">Hypothetical arithmetic, not a prediction of an actual road change.</p>
              </section>
            </div>
          </section>
        )}

        <footer className="workspace-footer">
          <span>Built for transparent, evidence-led traffic planning.</span>
          <span>Prototype · estimates are not live traffic data</span>
        </footer>
      </section>
    </main>
  )
}

export default App