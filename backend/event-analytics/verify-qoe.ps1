param([string]$Project = 'ott-analytics-stage7-check', [switch]$VerifyOnly)
$ErrorActionPreference = 'Stop'
Set-Location (Resolve-Path "$PSScriptRoot/../..")
function Query([string]$Sql) {
    $result = $Sql | docker compose -p $Project exec -T clickhouse bash -ec 'clickhouse-client --user "$CLICKHOUSE_USER" --password "$CLICKHOUSE_PASSWORD" --multiquery'
    if ($LASTEXITCODE -ne 0) { throw 'ClickHouse query failed' }
    return $result
}
function Check($Condition, [string]$Message) { if (-not $Condition) { throw $Message } }
function Event($Id, $Session, $Sequence, $Type, $Extra = @{}, $Played = 0, $Content = 10, $At = '2026-09-24T12:00:00Z') {
    $facts = @{qoeVersion=1;positionMs=10000;previousPositionMs=0;playedMsSincePreviousEvent=$Played;durationMs=60000;playbackRate=1}
    foreach ($key in $Extra.Keys) { $facts[$key] = $Extra[$key] }
    @{eventId=('70000000-0000-0000-0000-{0:D12}' -f $Id);eventVersion=1;eventType=$Type;
      occurredAt=$At;userId=42;contentId=$Content;videoId=20;playbackSessionId=$Session;sequence=$Sequence;payload=$facts
    } | ConvertTo-Json -Depth 4 -Compress
}
if (-not $VerifyOnly) {
    Check ((Query 'SELECT count() FROM analytics.qoe_events') -eq '0') 'Use an empty isolated test project.'
    $events = @(
        (Event 1 A 1 PLAYBACK_SESSION_STARTED),
        (Event 2 A 2 PLAY @{startupTimeMs=1000}),
        (Event 3 A 3 HEARTBEAT -Played 10000),
        (Event 4 A 4 BUFFER_STARTED),
        (Event 5 A 5 BUFFER_ENDED @{bufferingDurationMs=2000}),
        (Event 6 A 6 PLAYBACK_ERROR @{source='hls';code='bufferStalledError';fatal=$false}),
        (Event 7 A 7 PLAYBACK_SESSION_ENDED),
        (Event 8 B 1 PLAYBACK_SESSION_STARTED),
        (Event 9 B 2 PLAYBACK_ERROR @{source='media';code='3';fatal=$true}),
        (Event 10 B 3 PLAYBACK_SESSION_ENDED),
        (Event 11 C 1 PLAYBACK_SESSION_STARTED -At '2026-09-24T23:59:59Z'),
        (Event 12 C 2 PLAY @{startupTimeMs=3000} -At '2026-09-25T00:00:02Z'),
        (Event 13 C 3 HEARTBEAT -Played 10000 -At '2026-09-25T00:00:12Z'),
        (Event 14 D 1 PLAYBACK_ERROR @{source='hls';code='networkError';fatal=$true} -Content 20),
        (Event 15 E 1 PLAYBACK_SESSION_STARTED),
        (Event 16 F 1 PLAYBACK_SESSION_STARTED),
        (Event 17 F 2 PLAY @{startupTimeMs=0}),
        (Event 18 F 3 BUFFER_STARTED),
        (Event 3 A 3 HEARTBEAT -Played 10000),
        (Event 90 A 3 HEARTBEAT -Played 10000),
        (Event 91 Legacy 1 PLAYBACK_SESSION_STARTED @{qoeVersion=0}),
        (Event 92 Invalid 1 BUFFER_ENDED @{bufferingDurationMs=-1})
    )
    [array]::Reverse($events)
    $events | docker compose -p $Project exec -T kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server kafka:29092 --topic playback-events
    Check ($LASTEXITCODE -eq 0) 'Kafka publish failed'
}
$deadline = (Get-Date).AddSeconds(90)
do {
    $count = Query 'SELECT count() FROM analytics.qoe_events_current'
    if ($count -eq '18') { break }
    Start-Sleep -Seconds 3
} while ((Get-Date) -lt $deadline)
Check ($count -eq '18') 'Expected 18 unique valid session sequences'
$daily = Query 'SELECT * FROM analytics.content_daily_qoe WHERE content_id=10 FORMAT JSONEachRow' | ConvertFrom-Json
Check ($daily.cohort_date -eq '2026-09-24' -and $daily.started_sessions -eq 5) 'Session start cohort, including midnight'
Check ($daily.startup_samples -eq 3 -and [Math]::Abs($daily.average_startup_ms - 4000.0/3) -lt 0.001) 'Startup includes zero but excludes missing measurements'
Check ($daily.p95_startup_ms -ge 1000 -and $daily.p95_startup_ms -le 3000) 'Startup percentile'
Check ($daily.watch_ms -eq 20000 -and $daily.buffer_ms -eq 2000) 'Deduplicated watch and buffering duration'
Check ([Math]::Abs($daily.rebuffer_ratio - 1.0/11) -lt 0.0001) 'Rebuffer denominator excludes pause and startup'
Check ($daily.buffer_count -eq 2 -and $daily.closed_buffer_count -eq 1 -and $daily.open_buffer_sessions -eq 1) 'Unfinished buffer remains visible'
Check ([Math]::Abs($daily.rebuffer_session_rate - 2.0/3) -lt 0.0001) 'Rebuffer session rate'
Check ($daily.error_sessions -eq 2 -and $daily.error_session_rate -eq 0.4) 'Error session rate'
Check ($daily.fatal_error_sessions -eq 1 -and $daily.fatal_error_session_rate -eq 0.2 -and $daily.failed_before_play_sessions -eq 1) 'Fatal and pre-play failures'
$orphan = Query 'SELECT * FROM analytics.content_daily_qoe WHERE content_id=20 FORMAT JSONEachRow' | ConvertFrom-Json
Check ($orphan.observed_sessions -eq 1 -and $orphan.started_sessions -eq 0 -and $null -eq $orphan.error_session_rate -and $null -eq $orphan.average_startup_ms) 'Orphan events cannot manufacture denominators or zero startup'
Check ((Query 'SELECT sum(error_events) FROM analytics.qoe_error_breakdown') -eq '3') 'Error breakdown deduplicates events'
Write-Output 'PASS: QoE startup, buffering, errors, duplicate IDs/sequences, reversed delivery, midnight, missing start, missing/zero startup and invalid/legacy events.'
$daily | ConvertTo-Json -Compress
