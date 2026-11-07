# Pairgoth Publish — a WordPress plugin

Publishes a tournament's pairings, results and standings on a club's WordPress site, as pairgoth
pushes them from the venue.

It implements the publishing half of the [pairgoth webhook specification](../doc/technical.md#pairgoth-webhook-specification):
pairgoth is the client, the website is the server — so the tournament laptop can stay behind any
NAT or firewall.

```
+ /health                     GET    health check, called once at pairgoth startup
+ /pairings/{code}/{round}    POST   pairings, and results for the same round
+ /standings/{code}/{round}   POST   standings as of a round
```

## What it does not do

The webhook's *registration* plane is deliberately left out: `/players/{code}` (pairgoth pulling the
registered roster) and `/presences/{code}/{round}` (a referee's presence override mirrored back).
Those only make sense if the website owns registration — online sign-up, per-round presence, EGD
lookup — which is a different, much larger plugin.

So in pairgoth: **Publish to website** works, **Sync from website** does not (it will report the
plugin's 404). The spec section linked above documents both endpoints for anyone adding them.

## Install

Copy this directory into the site as `wp-content/plugins/pairgoth-publish/`, then activate
*Pairgoth Publish* in the plugins list. No dependency, no build step: the plugin is the single
`pairgoth-publish.php` file (`test/` is for developing it and can be left behind).

## Configure

*Settings → Pairgoth Publish*: set a shared secret (the page offers a generated one), save. The page
then shows the two lines to paste into the tournament laptop's `pairgoth.properties`:

```properties
webhook.url    = https://myclub.example/wp-json/pairgoth/v1
webhook.secret = the-same-secret
```

Take the URL from that settings page rather than typing it: it is already correct whether or not the
site uses pretty permalinks (without them it reads `https://myclub.example/?rest_route=/pairgoth/v1`,
which pairgoth appends to just as happily).

Restart pairgoth. Its startup log should say `webhook at <url> healthy: <site name>`.

## Use

In pairgoth, the *Pairings*, *Results* and *Standings* tabs each have a **Publish to website**
button. Each click ships the round you are looking at and overwrites whatever was there before for
that round.

Each tournament gets its own page under `/tournaments/` on first push, listed in the admin under
**Tournaments**. Rename its title to something human — later pushes never touch it. To embed a
tournament in an existing page instead:

```
[pairgoth code="20260712-club"]
```

Optional attributes: `view="pairings|standings"`, `round="3"`, `nav="no"`.

Visitors switch view and round through `?pgv=` / `?pgr=` links, on the tournament page and around
the shortcode alike.

## Styling

Nothing to do. The pushed HTML carries its own stylesheet inside a CSS `@layer`, and **unlayered
rules win over a layer** — so any rule your theme already has for tables beats the published one
automatically, and the plugin's styles only show through where the theme is silent.

Should you want finer control: everything is scoped under `.pairgoth-published`, each result cell
carries `data-result="? | w | b | = | X | # | 0"` and each row's table-number cell carries
`data-table="<n>"`.

## Testing it

`php test/smoke.php` exercises the plugin's logic — webhook handlers, storage, view/round
navigation, the code-rename guard — against a stub of the WordPress functions it calls, so it needs
nothing but a php binary. It is a smoke test, not an integration test: it proves the logic holds,
not that WordPress is happy.

## Two things to know

**The secret is the only gate, and the HTML is stored and echoed verbatim.** Sanitizing it would
strip exactly what makes the payload self-contained, so this plugin does not try. Whoever holds the
secret can put arbitrary HTML on the site — treat it like an editor password: HTTPS only, laptop
only. This is also why the plugin is not on wordpress.org.

**`{code}` is the tournament's short name, read live at each click.** Rename it in pairgoth's
*Information* tab mid-tournament and the next push lands on a *new* page here, leaving the rounds
already published orphaned on the old one. Pairgoth itself does not mind (it tracks tournaments by
id); the website does. Pick the short name before the first publish and leave it alone.

If it happened anyway, it is recoverable: open the orphaned tournament in the WordPress admin
(*Tournaments → the tournament*) and set its **Pairgoth code** box, in the sidebar, to the new short
name — pushes land there again. Delete the page pairgoth created in between.
