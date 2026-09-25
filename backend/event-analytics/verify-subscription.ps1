param([string]$Project = 'ott-subscription-stage6-check')
$ErrorActionPreference = 'Stop'
Set-Location (Resolve-Path "$PSScriptRoot/../..")
function Check($condition, [string]$message) { if (-not $condition) { throw $message } }
function Query([string]$Sql) {
    $result = $Sql | docker compose -p $Project exec -T clickhouse bash -ec 'clickhouse-client --user "$CLICKHOUSE_USER" --password "$CLICKHOUSE_PASSWORD" --multiquery'
    Check ($LASTEXITCODE -eq 0) 'ClickHouse query failed'
    return $result
}
function WaitQuery([string]$Sql, [string]$Expected) {
    $deadline = (Get-Date).AddSeconds(90)
    do {
        $result = Query $Sql
        if ($result -eq $Expected) { return }
        Start-Sleep -Seconds 3
    } while ((Get-Date) -lt $deadline)
    throw "Expected $Expected, got $result"
}
Check (-not (docker ps -aq --filter "label=com.docker.compose.project=$Project")) 'Use a new isolated project.'
$env:LOCAL_TEST_USER_ENABLED = 'true'
$env:LOCAL_TEST_USER_PASSWORD = [Guid]::NewGuid().ToString('N')
$env:LOCAL_SUBSCRIPTION_ACTIVATION_ENABLED = 'true'
$env:ANALYTICS_CHECKPOINT_INTERVAL = '10 s'
$env:ANALYTICS_PARTITIONS = '1'
$env:ANALYTICS_RESTORE_PATH = ''
docker compose -p $Project build user-api
Check ($LASTEXITCODE -eq 0) 'Project API image build failed'
docker compose -p $Project up -d --no-build playback-lb analytics-taskmanager
Check ($LASTEXITCODE -eq 0) 'Compose startup failed'
$base = 'http://localhost:8088/api'
$deadline = (Get-Date).AddMinutes(3)
$login = $null
do {
    try {
        $login = Invoke-RestMethod "$base/auth/login" -Method Post -ContentType 'application/json' -Body (
            @{loginId='ott_test_user';password=$env:LOCAL_TEST_USER_PASSWORD} | ConvertTo-Json -Compress)
        break
    } catch { Start-Sleep -Seconds 5 }
} while ((Get-Date) -lt $deadline)
Check ($null -ne $login) 'Local test user login failed'
$headers = @{Authorization="Bearer $($login.accessToken)"}
function Post([string]$Path, $Body = $null) {
    $parameters = @{Uri="$base$Path";Method='Post';Headers=$headers}
    if ($null -ne $Body) { $parameters.ContentType='application/json'; $parameters.Body=$Body|ConvertTo-Json -Depth 6 -Compress }
    return Invoke-RestMethod @parameters
}
$seed = @'
insert into contents(id,type,status,original_country,original_language,created_at,updated_at)
values(101,'MOVIE','PUBLISHED','KR','ko',now(),now());
insert into content_availabilities(content_id,country_code,available_from,status)
values(101,'KR','2020-01-01','AVAILABLE');
'@
$seed | docker compose -p $Project exec -T mysql bash -ec 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" ott_service'
Check ($LASTEXITCODE -eq 0) 'Content seed failed'
$cta = [Guid]::NewGuid().ToString()
$clickedAt = [DateTimeOffset]::UtcNow.ToString('O')
$request = @{requestId=[Guid]::NewGuid().ToString();contentId=101;ctaEventId=$cta}
$checkout = Post '/user/subscriptions/checkouts' $request
$retry = Post '/user/subscriptions/checkouts' $request
Check ($checkout.checkoutId -eq $retry.checkoutId) 'Checkout retry changed identity'
$path = "/user/subscriptions/local/checkouts/$($checkout.checkoutId)/activate"
$active = Post $path
Check ($active.status -eq 'ACTIVE') 'Activation failed'
$null = Post $path
$unauthorized = Invoke-WebRequest "$base$path" -Method Post -SkipHttpErrorCheck
Check ($unauthorized.StatusCode -in @(401,403)) 'Anonymous activation was accepted'
WaitQuery 'SELECT count() FROM analytics.subscription_events_current' '3'
WaitQuery 'SELECT attributed_content_id FROM analytics.subscription_acquisition_attribution' '0'
# The browser analytics batch may arrive after the business event; it must still resolve by event identity.
$batch = @{events=@(@{eventId=$cta;eventType='SUBSCRIPTION_CTA_CLICK';eventVersion=1;occurredAt=$clickedAt;
    anonymousId='stage6-test';sessionId='stage6-test';producer='user-web';platform='WEB';contentId=101;
    payload=@{surface='home';activationSource='LOCAL_TEST'}})}
$null = Post '/analytics/events/batch' $batch
$null = Post '/analytics/events/batch' $batch
WaitQuery 'SELECT attributed_content_id FROM analytics.subscription_acquisition_attribution' '101'
$funnel = Query 'SELECT * FROM analytics.content_subscription_funnel FORMAT JSONEachRow' | ConvertFrom-Json
Check ($funnel.cta_clicks -eq 1 -and $funnel.checkout_ctas -eq 1 -and $funnel.converted_ctas -eq 1) 'Funnel duplicated or attribution failed'
$null = Post '/user/subscriptions/cancel'
$second = Post '/user/subscriptions/checkouts' @{requestId=[Guid]::NewGuid().ToString()}
$null = Post "/user/subscriptions/local/checkouts/$($second.checkoutId)/activate"
WaitQuery 'SELECT count() FROM analytics.subscription_events_current' '6'
Check ((Query 'SELECT count() FROM analytics.subscription_acquisition_attribution') -eq '1') 'Reactivation counted as new acquisition'
Write-Output 'PASS: authenticated checkout -> DB/Outbox -> Kafka -> Flink -> ClickHouse; duplicate clicks/retries; anonymous rejection; late CTA attribution; reactivation exclusion.'
$funnel | ConvertTo-Json -Compress
# Cleanup is explicit so a failed test leaves its isolated resources available for inspection.
Write-Output "Cleanup: docker compose -p $Project down --volumes"
