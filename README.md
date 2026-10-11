# City Bottleneck Explorer

A full-stack starter for exploring city traffic bottlenecks. The frontend is a React app and the backend is a Java Spring Boot API.

## Requirements

- Node.js 18 or newer with npm
- Java 17 or newer
- Maven 3.6 or newer

## Run locally

Start the backend in one terminal:

```sh
cd backend
mvn spring-boot:run
```

Start the frontend in another terminal:

```sh
cd frontend
npm install
npm run dev
```

Open the local URL printed by Vite. The frontend proxies `/api` requests to `http://localhost:8080`.

## Backend API

The backend provides:

- `GET /api/health` for service status
- `GET /api/geocode?q=...` for up to five place matches
- `POST /api/analysis` for geocoding, alternative routes, route geometry, modeled pressure points, and scenario suggestions
- `POST /api/analysis/scenario` for a user-selected what-if reduction at the three highest-ranked candidate points
- `GET /api/traffic-model` for model and data-source disclosures

Example analysis request:

```sh
curl -X POST http://localhost:8080/api/analysis \
	-H 'Content-Type: application/json' \
	-d '{"origin":"Eiffel Tower, Paris","destination":"Louvre Museum, Paris","departureTime":"2026-10-07T08:30:00"}'
```

`origin` and `destination` can be place names or `latitude,longitude` coordinates. Example scenario request:

```sh
curl -X POST http://localhost:8080/api/analysis/scenario \
	-H 'Content-Type: application/json' \
	-d '{"origin":"48.8584,2.2945","destination":"48.8606,2.3376","departureTime":"2026-10-07T08:30:00","interventionPercent":20}'
```

## Data And Limitations

Place search uses OpenStreetMap Nominatim and routing uses OSRM's public demo server. No API keys are needed. Nominatim requests are spaced at least one second apart and cached in memory for 15 minutes. These shared public services have usage policies and are not production-grade hosting guarantees.

The routing response contains road-network routes and OSRM's baseline duration, not observed traffic. The backend applies a generic time-of-day multiplier and ranks candidate pressure points using route-step duration and maneuver type. It has no live speeds, traffic counts, queue lengths, incident feed, or city-specific calibration. Scenario savings are hypothetical arithmetic, not a forecast that a real road intervention will achieve that result. The API includes these caveats in `/api/traffic-model` and analysis responses. Departure times are treated as local wall-clock values; the backend does not perform timezone conversion.

The backend is stateless and does not persist searches. Deployment can set `PORT` and `CORS_ALLOWED_ORIGINS`; upstream URLs, timeouts, and the HTTP user agent are configurable in `backend/src/main/resources/application.properties` or through the corresponding environment variables.