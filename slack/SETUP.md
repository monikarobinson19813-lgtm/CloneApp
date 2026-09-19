# CloneApp Control Tower Slack App Setup

The current ChatGPT Slack connector is useful for interactive setup, but it cannot independently post every five minutes.

The permanent CloneApp engineering system therefore uses its own Slack app.

## What this enables

- five-minute whole-project heartbeat in #cloneapp-5min-status;
- immediate RED/GREEN CI event posts;
- persistent Socket Mode event intake;
- department-to-department routing;
- agent replies inside Slack threads;
- dynamic CloneApp issue/incident channels.

## Create the app

Use the repository manifest:

slack/cloneapp-control-tower-manifest.yml

It enables:
- bot user;
- Socket Mode;
- app mentions;
- public/private channel message events;
- current minimal routing scopes.

## One-time credentials

After installing the app into the Appwork workspace, obtain:

1. Bot token: xoxb-...
2. App-level Socket Mode token: xapp-...

Never commit either token.

## Immediate five-minute status feed

Create this GitHub Actions repository secret:

SLACK_BOT_TOKEN

Value: the xoxb bot token.

The workflow .github/workflows/slack-5min-status.yml posts to:

#cloneapp-5min-status
Channel ID: C0C35CDV752

The CloneApp Control Tower bot must be invited to that private channel.

Once configured:
- cron requests a whole-project heartbeat every five minutes;
- Android Build / Orchestrator Tests state changes trigger additional immediate posts;
- PR/Issue transitions also trigger status refresh.

GitHub scheduled workflows are not guaranteed to start at the exact second during platform load. Event-driven posts are the primary signal for meaningful changes.

## Persistent two-way agent communication

The always-on Control Tower host later needs:

SLACK_BOT_TOKEN=xoxb-...
SLACK_APP_TOKEN=xapp-...

Those belong in the host secret store/environment.

The Socket Mode router uses the app-level token for low-latency inbound Slack events without requiring a public HTTP endpoint.
