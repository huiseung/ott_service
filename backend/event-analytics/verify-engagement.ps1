param([string]$Project = 'ott-analytics-stage5-check', [switch]$VerifyOnly)
$ErrorActionPreference = 'Stop'
Set-Location (Resolve-Path "$PSScriptRoot/../..")

function Query([string]$Sql) {
    $output = $Sql | docker compose -p $Project exec -T clickhouse bash -ec 'clickhouse-client --user "$CLICKHOUSE_USER" --password "$CLICKHOUSE_PASSWORD" --multiquery'
    if ($LASTEXITCODE -ne 0) { throw 'ClickHouse query failed' }
    return $output
}
function Check($condition, [string]$message) { if (-not $condition) { throw $message } }
function Event($id, $session, $seq, $type, $prev, $pos, $played, $rate = 1, $viewer = 42, $episode = 101, $next = 102, $content = 10, $duration = 100000, $at = '2026-09-10T09:00:00Z', $from = 0) {
    @{eventId=('00000000-0000-0000-0000-{0:D12}' -f $id);eventVersion=1;eventType=$type;
      occurredAt=$at;userId=$viewer;contentId=$content;videoId=($episode+1000);playbackSessionId=$session;sequence=$seq;
      playbackContext=@{durationMs=$duration;episodeId=$episode;nextEpisodeId=$(if($next -gt 0){$next}else{$null})};
      payload=@{durationMs=$duration;previousPositionMs=$prev;positionMs=$pos;playedMsSincePreviousEvent=$played;playbackRate=$rate;fromPositionMs=$from}
    } | ConvertTo-Json -Depth 4 -Compress
}
if (-not $VerifyOnly) {
    Check ((Query 'SELECT count() FROM analytics.playback_observations') -eq '0') 'Use an empty isolated test project.'
    $rows = @(
        (Event 1 'A' 1 'PLAYBACK_SESSION_STARTED' 0 0 0),
        (Event 2 'A' 2 'HEARTBEAT' 0 60000 60000),
        (Event 3 'A' 3 'SEEK' 60000 90000 0 -from 60000),
        (Event 4 'A' 4 'HEARTBEAT' 90000 100000 10000),
        (Event 5 'A' 5 'SEEK' 100000 0 0 -from 100000),
        (Event 6 'A' 6 'HEARTBEAT' 0 60000 30000 -rate 2),
        (Event 2 'A' 2 'HEARTBEAT' 0 60000 60000),
        (Event 90 'A' 2 'HEARTBEAT' 0 60000 60000),
        (Event 7 'B' 1 'PLAYBACK_SESSION_STARTED' 0 0 0 -episode 102 -next 0 -at '2026-09-10T10:00:00Z'),
        (Event 8 'B' 2 'HEARTBEAT' 0 90000 90000 -episode 102 -next 0 -at '2026-09-10T10:01:30Z'),
        (Event 10 'C' 2 'HEARTBEAT' 0 90000 90000 -viewer 43 -at '2026-09-10T11:01:30Z'),
        (Event 9 'C' 1 'PLAYBACK_SESSION_STARTED' 0 0 0 -viewer 43 -at '2026-09-10T11:00:00Z'),
        (Event 11 'D' 1 'PLAYBACK_SESSION_STARTED' 0 0 0 -content 20 -duration 0),
        (Event 12 'E' 2 'HEARTBEAT' 0 10000 10000 -content 30),
        (Event 13 'F' 1 'PLAYBACK_SESSION_STARTED' 0 0 0 -content 40 -at '2026-09-10T23:59:59Z'),
        (Event 14 'F' 2 'HEARTBEAT' 0 1000 1000 -content 40 -at '2026-09-11T00:00:00Z')
    )
    $rows | docker compose -p $Project exec -T kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server kafka:29092 --topic playback-events
    Check ($LASTEXITCODE -eq 0) 'Kafka publish failed'
}
$deadline = (Get-Date).AddSeconds(90)
do {
    $count = Query 'SELECT count() FROM analytics.playback_observations_current'
    if ($count -eq '14') { break }
    Start-Sleep -Seconds 3
} while ((Get-Date) -lt $deadline)
Check ($count -eq '14') 'Expected 14 normalized unique session sequences'
$daily = Query 'SELECT * FROM analytics.content_daily_engagement WHERE content_id=10 FORMAT JSONEachRow' | ConvertFrom-Json
Check ($daily.observed_sessions -eq 3 -and $daily.completed_sessions -eq 2) 'Completion/session counts'
Check ([Math]::Abs($daily.watch_hours * 3600 - 280) -lt 0.001) 'Watch time must include rewatch, exclude seek, and respect 2x'
Check ([Math]::Abs($daily.completion_rate - 2.0/3) -lt 0.0001) 'Completion rate'
$retention = Query 'SELECT * FROM analytics.content_retention WHERE content_id=10 AND bucket_start_percent IN (60,90) ORDER BY bucket_start_percent FORMAT JSONEachRow'
$buckets = @($retention | ForEach-Object { $_ | ConvertFrom-Json })
Check ($buckets[0].watched_sessions -eq 2 -and $buckets[1].watched_sessions -eq 1) 'Retention must not count skipped spans'
$conversion = Query 'SELECT * FROM analytics.episode_conversion WHERE content_id=10 FORMAT JSONEachRow' | ConvertFrom-Json
Check ($conversion.eligible_viewers -eq 2 -and $conversion.converted_viewers -eq 1 -and $conversion.conversion_rate -eq 0.5) 'Episode conversion'
$unknown = Query 'SELECT completion_rate FROM analytics.content_daily_engagement WHERE content_id=20 FORMAT JSONEachRow' | ConvertFrom-Json
Check ($null -eq $unknown.completion_rate) 'Unknown duration must not complete'
Check ((Query 'SELECT count() FROM analytics.content_retention WHERE content_id IN (20,30)') -eq '0') 'Unknown duration and missing start excluded from retention'
$midnight = Query 'SELECT * FROM analytics.content_daily_engagement WHERE content_id=40 FORMAT JSONEachRow' | ConvertFrom-Json
Check ($midnight.cohort_date -eq '2026-09-10' -and $midnight.average_watch_seconds -eq 1) 'Midnight session cohort'
Write-Output 'PASS: duplicate IDs/sequences, out-of-order delivery, seek, rewatch, 2x, 90% completion, retention, episode conversion, unknown duration, missing start, midnight.'
$daily | ConvertTo-Json -Compress
$conversion | ConvertTo-Json -Compress
