<?php
/*
 * Smoke test for pairgoth-publish, runnable with nothing but a php binary:
 *
 *     php test/smoke.php
 *
 * WordPress is not involved. The functions the plugin calls are faked below over two in-memory
 * arrays, which is enough to exercise everything that is ours: the webhook handlers, the storage
 * round-trip, the view/round fallbacks and the code-rename guard. It proves no integration —
 * that takes an actual install — but it fails loudly when one of those breaks.
 */

define('ABSPATH', __DIR__);

$META = [];   // post id => [meta key => value]
$POSTS = [];  // post id => post object
$HOOKS = [];  // hook name => [callback]
$OPTIONS = ['date_format' => 'Y-m-d', 'time_format' => 'H:i'];
$TRANSIENTS = [];

/* ------------------------------------------------------------------ faked WordPress */

function add_action($hook, $cb = null, ...$rest) { $GLOBALS['HOOKS'][$hook][] = $cb; }
function add_filter($hook, $cb = null, ...$rest) { $GLOBALS['HOOKS'][$hook][] = $cb; }
function add_shortcode($name, $cb) { $GLOBALS['HOOKS']['shortcode_' . $name][] = $cb; }
function register_post_type(...$a) {}
function register_rest_route(...$a) {}
function register_setting(...$a) {}
function add_options_page(...$a) {}
function add_meta_box(...$a) {}
function wp_nonce_field(...$a) {}
function wp_verify_nonce($nonce, $action) { return $nonce === 'nonce_' . $action; }
function current_user_can(...$a) { return true; }
function __($s, $d = null) { return $s; }
function esc_html($s) { return htmlspecialchars($s); }
function esc_html__($s, $d = null) { return htmlspecialchars($s); }
function esc_attr($s) { return htmlspecialchars($s); }
function esc_url($s) { return $s; }
function sanitize_text_field($s) { return trim(strip_tags($s)); }
function sanitize_title($s) { return strtolower($s); }
function sanitize_key($s) { return strtolower(preg_replace('/[^a-z0-9_\-]/i', '', $s)); }
function wp_slash($s) { return addslashes($s); }
function wp_unslash($s) { return stripslashes($s); }
function get_option($k, $d = '') { return $GLOBALS['OPTIONS'][$k] ?? $d; }
function wp_date($f, $t) { return date($f, $t); }
function get_permalink($p) { return 'https://club.example/tournaments/x'; }
function get_the_title($p) { return is_object($p) ? $p->post_title : ''; }
function get_the_ID() { return $GLOBALS['CURRENT_ID'] ?? 0; }
function is_singular(...$a) { return false; }
function in_the_loop() { return false; }
function is_main_query() { return false; }
function remove_query_arg($keys, $url = null) { return 'https://club.example/tournaments/x'; }
function add_query_arg($args, $url) { return $url . '?' . http_build_query($args); }
function set_transient($k, $v, $ttl) { $GLOBALS['TRANSIENTS'][$k] = $v; }
function get_transient($k) { return $GLOBALS['TRANSIENTS'][$k] ?? false; }
function delete_transient($k) { unset($GLOBALS['TRANSIENTS'][$k]); }

function get_post_meta($id, $key = '', $single = false) {
    global $META;
    if ($key === '') return array_map(fn($v) => [$v], $META[$id] ?? []);
    $value = $META[$id][$key] ?? '';
    return $single ? $value : [$value];
}
// WordPress unslashes on the way in; mirroring that is the point of the wp_slash() check below.
function update_post_meta($id, $key, $value) { $GLOBALS['META'][$id][$key] = stripslashes($value); }

function get_posts($args) {
    global $POSTS, $META;
    foreach ($POSTS as $id => $post) {
        if (($META[$id][$args['meta_key']] ?? null) === $args['meta_value']) return [$post];
    }
    return [];
}
function wp_insert_post($data, $err = false) {
    global $POSTS;
    $id = count($POSTS) + 1;
    $POSTS[$id] = (object) array_merge(['ID' => $id], $data);
    return $id;
}
function get_post($id) { return $GLOBALS['POSTS'][$id]; }
function is_wp_error($x) { return $x instanceof WP_Error; }

class WP_Error {
    public $code, $message, $data;
    function __construct($code, $message, $data = []) { $this->code = $code; $this->message = $message; $this->data = $data; }
}
class WP_REST_Response {
    private $data, $status;
    function __construct($data, $status = 200) { $this->data = $data; $this->status = $status; }
    function get_data() { return $this->data; }
    function set_data($d) { $this->data = $d; }
    function get_status() { return $this->status; }
}
class WP_REST_Request implements ArrayAccess {
    public $params, $body, $headers;
    function __construct($params, $body = '', $headers = []) {
        $this->params = $params; $this->body = $body; $this->headers = $headers;
    }
    function get_body() { return $this->body; }
    function get_header($n) { return $this->headers[strtolower($n)] ?? ''; }
    function get_route() { return '/pairgoth/v1/x'; }
    #[\ReturnTypeWillChange] function offsetGet($k) { return $this->params[$k] ?? null; }
    #[\ReturnTypeWillChange] function offsetExists($k) { return isset($this->params[$k]); }
    #[\ReturnTypeWillChange] function offsetSet($k, $v) { $this->params[$k] = $v; }
    #[\ReturnTypeWillChange] function offsetUnset($k) { unset($this->params[$k]); }
}

require __DIR__ . '/../pairgoth-publish.php';

/* ------------------------------------------------------------------ checks */

$ok = 0; $ko = 0;
function check($label, $cond) {
    global $ok, $ko;
    if ($cond) { $ok++; echo "  ok   $label\n"; } else { $ko++; echo "  FAIL $label\n"; }
}
function fire($hook, ...$args) {
    foreach ($GLOBALS['HOOKS'][$hook] ?? [] as $cb) $cb(...$args);
}

// --- authentication
$anonymous = new WP_REST_Request([], '', ['x-pairgoth-secret' => 'guess']);
$res = pgp_check_secret($anonymous);
check('no secret configured → 503, not a silent accept', $res instanceof WP_Error && $res->data['status'] === 503);
$OPTIONS[PGP_SECRET_OPT] = 'right-secret';
$res = pgp_check_secret($anonymous);
check('wrong secret → 401', $res instanceof WP_Error && $res->data['status'] === 401);
check('right secret → true',
    pgp_check_secret(new WP_REST_Request([], '', ['x-pairgoth-secret' => 'right-secret'])) === true);

// --- publishing
// A payload shaped like the real one: @layer style block, a backslash escape in the CSS, data-* cells.
$body = '<style>@layer pairgoth-published { td::after { content: "\201C"; } }</style>' . "\n"
      . '<div class="pairgoth-published"><table><tr><td class="t" data-table="1">A</td>'
      . '<td data-result="w">1-0</td></tr></table></div>';
$pushed = pgp_route_publish(new WP_REST_Request(['code' => '20260712-club', 'round' => '3'], $body), 'pairings');
check('publish answers {status:true}', $pushed->get_data()['status'] === true);

$post = pgp_find_tournament('20260712-club');
check('first push creates the tournament', $post !== null);
check('stored verbatim, backslashes included', pgp_stored($post->ID, 'pairings', 3) === $body);

pgp_route_publish(new WP_REST_Request(['code' => '20260712-club', 'round' => '1'], '<p>pairings r1</p>'), 'pairings');
pgp_route_publish(new WP_REST_Request(['code' => '20260712-club', 'round' => '2'], '<p>standings r2</p>'), 'standings');
check('second push reuses the same page', count($POSTS) === 1);
check('rounds and views indexed', pgp_available($post->ID) === ['pairings' => [1, 3], 'standings' => [2]]);

pgp_route_publish(new WP_REST_Request(['code' => '20260712-club', 'round' => '3'], '<p>corrected</p>'), 'pairings');
check('re-publishing a round overwrites it', pgp_stored($post->ID, 'pairings', 3) === '<p>corrected</p>');

// --- rejected input
$bad = pgp_route_publish(new WP_REST_Request(['code' => 'ev/il', 'round' => '1'], 'x'), 'pairings');
check('code outside pairgoth\'s charset → 400', $bad->get_status() === 400 && $bad->get_data()['status'] === false);
check('error body carries message and error alike',
    $bad->get_data()['error'] === $bad->get_data()['message']);
check('empty body → 400',
    pgp_route_publish(new WP_REST_Request(['code' => 'ok', 'round' => '1'], " \n "), 'pairings')->get_status() === 400);
check('nothing was created for a rejected push', count($POSTS) === 1);

// --- rendering
$html = pgp_render($post->ID, null, null);
check('defaults to the first view, latest round', strpos($html, '<p>corrected</p>') !== false);
check('nav offers both views', strpos($html, 'Standings') !== false && strpos($html, 'Pairings') !== false);
check('rendered block carries the scroll anchor', strpos($html, 'id="pairgoth"') !== false);
check('nav links target the anchor', strpos($html, '#pairgoth') !== false);
check('explicit round honoured', strpos(pgp_render($post->ID, 'pairings', 1), '<p>pairings r1</p>') !== false);
check('unknown round falls back to the latest', strpos(pgp_render($post->ID, 'pairings', 99), '<p>corrected</p>') !== false);
check('unknown view falls back to an available one', strpos(pgp_render($post->ID, 'nope', null), '<p>corrected</p>') !== false);
check('a page with nothing published says so', strpos(pgp_render(999, null, null), 'Nothing published') !== false);
check('nav can be suppressed', strpos(pgp_render($post->ID, null, null, false), 'pairgoth-nav') === false);

// --- the code box: recovery path after a short-name rename in pairgoth
$_POST = ['pgp_code' => '20260712-club-v2', 'pgp_code_nonce' => 'nonce_pgp_code_' . $post->ID];
fire('save_post_' . PGP_CPT, $post->ID);
check('code can be re-keyed by hand', get_post_meta($post->ID, PGP_CODE_META, true) === '20260712-club-v2');

pgp_route_publish(new WP_REST_Request(['code' => 'other-club', 'round' => '1'], '<p>x</p>'), 'pairings');
$other = pgp_find_tournament('other-club');
check('single view and single round → no nav at all',
    strpos(pgp_render($other->ID, null, null), 'pairgoth-nav') === false);
pgp_route_publish(new WP_REST_Request(['code' => 'other-club', 'round' => '2'], '<p>y</p>'), 'pairings');
$solo = pgp_render($other->ID, null, null);
check('single view, two rounds → round links but no view tabs',
    strpos($solo, 'pairgoth-rounds') !== false && strpos($solo, 'pairgoth-views') === false);
$_POST = ['pgp_code' => '20260712-club-v2', 'pgp_code_nonce' => 'nonce_pgp_code_' . $other->ID];
fire('save_post_' . PGP_CPT, $other->ID);
check('a code already in use is refused', get_post_meta($other->ID, PGP_CODE_META, true) === 'other-club');
check('and the refusal is reported', strpos((string) get_transient('pgp_notice_' . $other->ID), 'already used') !== false);

$_POST = ['pgp_code' => 'bad code!', 'pgp_code_nonce' => 'nonce_pgp_code_' . $other->ID];
fire('save_post_' . PGP_CPT, $other->ID);
check('an unusable code is refused', get_post_meta($other->ID, PGP_CODE_META, true) === 'other-club');

$_POST = ['pgp_code' => 'forged', 'pgp_code_nonce' => 'wrong'];
fire('save_post_' . PGP_CPT, $other->ID);
check('a bad nonce changes nothing', get_post_meta($other->ID, PGP_CODE_META, true) === 'other-club');

echo "\n$ok passed, $ko failed\n";
exit($ko === 0 ? 0 : 1);
