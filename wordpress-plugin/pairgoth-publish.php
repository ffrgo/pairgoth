<?php
/**
 * Plugin Name:       Pairgoth Publish
 * Plugin URI:        https://pairgoth.jeudego.org/
 * Description:       Receives pairings, results and standings pushed by pairgoth and publishes them on the club site.
 * Version:           0.1.0
 * Requires at least: 5.6
 * Requires PHP:      7.4
 * Author:            Pairgoth
 * License:           GPLv2 or later
 * Text Domain:       pairgoth-publish
 * Domain Path:       /languages
 */

/*
 * Trust model — read this before deploying.
 *
 * Pairgoth pushes ready-made HTML fragments (tables plus their own <style> block); this plugin
 * stores them verbatim and echoes them verbatim. The shared secret is the only gate: anyone who
 * knows it can put arbitrary HTML on the site. That is a deliberate trade — sanitizing would
 * strip exactly what makes the payload self-contained. Consequences: serve the site over HTTPS,
 * keep the secret to the tournament laptop, and do not install this on a site whose secret you
 * would not equally trust with an editor account.
 */

if (!defined('ABSPATH')) exit;

const PGP_NS         = 'pairgoth/v1';
const PGP_CPT        = 'pairgoth_tournament';
const PGP_SECRET_OPT = 'pairgoth_publish_secret';
const PGP_CODE_META  = '_pairgoth_code';
const PGP_VERSION    = '0.1.0';

/** Views pairgoth can push. 'pairings' also carries results (same endpoint, richer table). */
function pgp_views() {
    return ['pairings' => __('Pairings & results', 'pairgoth-publish'),
            'standings' => __('Standings', 'pairgoth-publish')];
}

/* ------------------------------------------------------------------ custom post type */

add_action('init', function () {
    load_plugin_textdomain('pairgoth-publish', false, dirname(plugin_basename(__FILE__)) . '/languages');
    register_post_type(PGP_CPT, [
        'labels' => [
            'name'          => __('Tournaments', 'pairgoth-publish'),
            'singular_name' => __('Tournament', 'pairgoth-publish'),
            'menu_name'     => __('Tournaments', 'pairgoth-publish'),
            'search_items'  => __('Search tournaments', 'pairgoth-publish'),
            'not_found'     => __('No tournament published yet', 'pairgoth-publish'),
        ],
        'public'       => true,
        'has_archive'  => true,
        'menu_icon'    => 'dashicons-grid-view',
        'supports'     => ['title', 'editor', 'excerpt', 'thumbnail'],
        'rewrite'      => ['slug' => 'tournaments'],
        'show_in_rest' => false,
        // Created by the webhook, not by hand: no "Add new" in the admin menu.
        'capabilities' => ['create_posts' => 'do_not_allow'],
        'map_meta_cap' => true,
    ]);
});

/** The tournament post for a pairgoth short name, or null. Matched on the stored raw code. */
function pgp_find_tournament($code) {
    $posts = get_posts([
        'post_type'        => PGP_CPT,
        'post_status'      => ['publish', 'draft', 'pending', 'private'],
        'numberposts'      => 1,
        'meta_key'         => PGP_CODE_META,
        'meta_value'       => $code,
        'suppress_filters' => false,
    ]);
    return $posts ? $posts[0] : null;
}

/**
 * The tournament post for a code, created on first push. The title is only ever set at creation
 * (the club renames it to something human; later pushes must not undo that).
 */
function pgp_ensure_tournament($code) {
    $post = pgp_find_tournament($code);
    if ($post) return $post;
    $id = wp_insert_post([
        'post_type'   => PGP_CPT,
        'post_status' => 'publish',
        'post_title'  => $code,
        'post_name'   => sanitize_title($code),
    ], true);
    if (is_wp_error($id)) return null;
    update_post_meta($id, PGP_CODE_META, $code);
    return get_post($id);
}

/* ------------------------------------------------------------------ storage */

function pgp_html_key($view, $round)  { return "_pairgoth_{$view}_{$round}"; }
function pgp_stamp_key($view, $round) { return "_pairgoth_{$view}_{$round}_time"; }

function pgp_store($post_id, $view, $round, $html) {
    // update_post_meta unslashes its input; pre-slash so backslashes in the CSS survive.
    update_post_meta($post_id, pgp_html_key($view, $round), wp_slash($html));
    update_post_meta($post_id, pgp_stamp_key($view, $round), time());
    update_post_meta($post_id, '_pairgoth_last_push', time());
}

function pgp_stored($post_id, $view, $round) {
    $html = get_post_meta($post_id, pgp_html_key($view, $round), true);
    return is_string($html) && $html !== '' ? $html : null;
}

/** What has been pushed so far: [ view => [rounds, ascending] ]. */
function pgp_available($post_id) {
    $found = [];
    foreach (array_keys(get_post_meta($post_id)) as $key) {
        if (preg_match('/^_pairgoth_([a-z]+)_(\d+)$/', $key, $m) && isset(pgp_views()[$m[1]])) {
            $found[$m[1]][] = (int) $m[2];
        }
    }
    foreach ($found as &$rounds) { sort($rounds); }
    return $found;
}

/* ------------------------------------------------------------------ webhook endpoints */

add_action('rest_api_init', function () {
    register_rest_route(PGP_NS, '/health', [
        'methods'             => 'GET',
        'callback'            => 'pgp_route_health',
        // Instance-scoped and exposes nothing but status/name/version: pairgoth checks it at startup.
        'permission_callback' => '__return_true',
    ]);
    foreach (array_keys(pgp_views()) as $view) {
        register_rest_route(PGP_NS, "/{$view}/(?P<code>[^/]+)/(?P<round>\d+)", [
            'methods'             => 'POST',
            'callback'            => function (WP_REST_Request $request) use ($view) { return pgp_route_publish($request, $view); },
            'permission_callback' => 'pgp_check_secret',
        ]);
    }
});

function pgp_route_health() {
    return new WP_REST_Response(['status' => true, 'name' => get_bloginfo('name'), 'version' => PGP_VERSION], 200);
}

function pgp_check_secret(WP_REST_Request $request) {
    $expected = (string) get_option(PGP_SECRET_OPT, '');
    if ($expected === '') {
        return new WP_Error('pairgoth_unconfigured',
            __('Pairgoth Publish has no shared secret set', 'pairgoth-publish'), ['status' => 503]);
    }
    $given = (string) $request->get_header('x-pairgoth-secret');
    if (!hash_equals($expected, $given)) {
        return new WP_Error('pairgoth_unauthorized',
            __('invalid X-Pairgoth-Secret', 'pairgoth-publish'), ['status' => 401]);
    }
    return true;
}

function pgp_route_publish(WP_REST_Request $request, $view) {
    $code  = (string) $request['code'];
    $round = (int) $request['round'];
    // Same charset pairgoth enforces on the short name.
    if (!preg_match('/^[-a-zA-Z0-9_.]{1,64}$/', $code)) return pgp_error('invalid tournament code', 400);
    $html = $request->get_body();
    if (trim($html) === '') return pgp_error('empty body', 400);
    $post = pgp_ensure_tournament($code);
    if (!$post) return pgp_error('could not create the tournament page', 500);
    pgp_store($post->ID, $view, $round, $html);
    return new WP_REST_Response(['status' => true, 'url' => get_permalink($post)], 200);
}

/** Pairgoth's error shape. `error` duplicates `message` for pairgoth releases up to 0.26, whose UI
 *  only ever read `error` on a non-2xx body and would show "unknown error" instead of the reason. */
function pgp_error($message, $status) {
    return new WP_REST_Response(['status' => false, 'message' => $message, 'error' => $message], $status);
}

/** Same shape for the errors WordPress itself produces on our routes (auth, 404, bad method). */
add_filter('rest_post_dispatch', function ($result, $server, $request) {
    if (strpos($request->get_route(), '/' . PGP_NS) !== 0) return $result;
    $data = $result instanceof WP_REST_Response ? $result->get_data() : null;
    if (is_array($data) && isset($data['code'], $data['message']) && !isset($data['status'])) {
        $result->set_data(['status' => false, 'message' => $data['message'], 'error' => $data['message']]);
    }
    return $result;
}, 10, 3);

/* ------------------------------------------------------------------ front-end rendering */

/**
 * One published view, preceded by its navigation. $html is echoed verbatim — see the trust
 * model at the top of this file.
 */
function pgp_render($post_id, $view = null, $round = null, $nav = true) {
    $available = pgp_available($post_id);
    if (!$available) {
        return '<p class="pairgoth-empty">' . esc_html__('Nothing published yet.', 'pairgoth-publish') . '</p>';
    }
    $view = $view && isset($available[$view]) ? $view : array_key_first($available);
    $rounds = $available[$view];
    $round = ($round !== null && in_array((int) $round, $rounds, true)) ? (int) $round : end($rounds);
    $html = pgp_stored($post_id, $view, $round);
    if ($html === null) {
        return '<p class="pairgoth-empty">' . esc_html__('Nothing published yet.', 'pairgoth-publish') . '</p>';
    }
    $out = $nav ? pgp_nav($available, $view, $round) : '';
    $stamp = (int) get_post_meta($post_id, pgp_stamp_key($view, $round), true);
    if ($stamp) {
        $out .= '<p class="pairgoth-stamp">' . esc_html(sprintf(
            /* translators: %s: date and time of the last publication */
            __('Published %s', 'pairgoth-publish'),
            wp_date(get_option('date_format') . ' ' . get_option('time_format'), $stamp))) . '</p>';
    }
    // The id is the nav links' scroll target, so a round change lands on the tables
    // even when the block sits at the bottom of a long page.
    return '<div class="pairgoth-publish" id="pairgoth">' . $out . $html . '</div>';
}

/**
 * View tabs + round links, as plain links carrying pgv/pgr on the current URL.
 * A list with a single entry would be a link to the page itself: not rendered.
 */
function pgp_nav($available, $view, $round) {
    $labels = pgp_views();
    $here = remove_query_arg(['pgv', 'pgr']);
    $out = '';
    if (count($available) > 1) {
        $out .= '<ul class="pairgoth-views">';
        foreach ($available as $v => $rounds) {
            $target = add_query_arg(['pgv' => $v, 'pgr' => $v === $view ? $round : end($rounds)], $here);
            $out .= sprintf('<li class="%s"><a href="%s">%s</a></li>',
                $v === $view ? 'current' : '', esc_url($target . '#pairgoth'), esc_html($labels[$v]));
        }
        $out .= '</ul>';
    }
    if (count($available[$view]) > 1) {
        $out .= '<ul class="pairgoth-rounds">';
        foreach ($available[$view] as $r) {
            $target = add_query_arg(['pgv' => $view, 'pgr' => $r], $here);
            $out .= sprintf('<li class="%s"><a href="%s">%s</a></li>',
                $r === $round ? 'current' : '', esc_url($target . '#pairgoth'),
                esc_html(sprintf(/* translators: %d: round number */ __('R%d', 'pairgoth-publish'), $r)));
        }
        $out .= '</ul>';
    }
    return $out === '' ? '' : '<nav class="pairgoth-nav">' . $out . '</nav>';
}

/** [pairgoth code="20260712-club" view="standings" round="3" nav="yes"] */
add_shortcode('pairgoth', function ($atts) {
    $atts = shortcode_atts(['code' => '', 'view' => '', 'round' => '', 'nav' => 'yes'], $atts, 'pairgoth');
    $post = $atts['code'] ? pgp_find_tournament($atts['code']) : null;
    if (!$post) {
        return '<p class="pairgoth-empty">' . esc_html__('Unknown tournament.', 'pairgoth-publish') . '</p>';
    }
    return pgp_render($post->ID,
        $atts['view'] ?: pgp_query_view(),
        $atts['round'] !== '' ? (int) $atts['round'] : pgp_query_round(),
        $atts['nav'] !== 'no');
});

function pgp_query_view()  { return isset($_GET['pgv']) ? sanitize_key(wp_unslash($_GET['pgv'])) : null; }
function pgp_query_round() { return isset($_GET['pgr']) ? (int) $_GET['pgr'] : null; }

/**
 * The tournament page shows its published content with no shortcode needed. Priority 20 keeps the
 * appended tables clear of wpautop (priority 10).
 */
add_filter('the_content', function ($content) {
    if (!is_singular(PGP_CPT) || !in_the_loop() || !is_main_query()) return $content;
    return $content . pgp_render(get_the_ID(), pgp_query_view(), pgp_query_round());
}, 20);

add_action('wp_enqueue_scripts', function () {
    $post = get_post();
    if (!is_singular(PGP_CPT) && !($post && has_shortcode($post->post_content, 'pairgoth'))) return;
    // Navigation only: the published tables carry their own styles.
    wp_register_style('pairgoth-publish', false, [], PGP_VERSION);
    wp_enqueue_style('pairgoth-publish');
    wp_add_inline_style('pairgoth-publish', '
.pairgoth-nav ul { display: flex; flex-wrap: wrap; list-style: none; margin: 0; padding: 0; }
.pairgoth-nav a { text-decoration: none; }
.pairgoth-views { gap: .25em; border-bottom: 1px solid rgba(128,128,128,.4); }
.pairgoth-views li { margin-bottom: -1px; }
.pairgoth-views a { display: inline-block; padding: .25em .9em; border: 1px solid transparent; border-radius: .4em .4em 0 0; }
.pairgoth-views li.current a { font-weight: bold; border-color: rgba(128,128,128,.4); border-bottom-color: #fff; background: #fff; border-bottom-color: Canvas; background: Canvas; }
.pairgoth-rounds { gap: .6em; margin-top: .5em; }
.pairgoth-rounds li.current a { font-weight: bold; text-decoration: underline; }
.pairgoth-stamp { font-size: .85em; font-style: italic; opacity: .7; }');
});

/* ------------------------------------------------------------------ admin */

add_filter('manage_' . PGP_CPT . '_posts_columns', function ($columns) {
    $columns['pgp_code'] = __('Pairgoth code', 'pairgoth-publish');
    $columns['pgp_last'] = __('Last publication', 'pairgoth-publish');
    return $columns;
});

add_action('manage_' . PGP_CPT . '_posts_custom_column', function ($column, $post_id) {
    if ($column === 'pgp_code') {
        echo '<code>' . esc_html(get_post_meta($post_id, PGP_CODE_META, true)) . '</code>';
    } elseif ($column === 'pgp_last') {
        $stamp = (int) get_post_meta($post_id, '_pairgoth_last_push', true);
        echo $stamp ? esc_html(wp_date(get_option('date_format') . ' ' . get_option('time_format'), $stamp)) : '—';
    }
}, 10, 2);

/**
 * The code is the webhook's routing key, so it must stay editable: renaming a tournament's short
 * name in pairgoth mid-event otherwise strands the rounds already published on the old page.
 */
add_action('add_meta_boxes', function () {
    add_meta_box('pgp-code', __('Pairgoth code', 'pairgoth-publish'), function ($post) {
        wp_nonce_field('pgp_code_' . $post->ID, 'pgp_code_nonce');
        printf('<input type="text" name="pgp_code" class="widefat code" value="%s"/>',
            esc_attr(get_post_meta($post->ID, PGP_CODE_META, true)));
        echo '<p class="description">' .
            esc_html__('The tournament short name pairgoth publishes under. Change it only to follow a rename made in pairgoth.', 'pairgoth-publish') .
            '</p>';
    }, PGP_CPT, 'side');
});

add_action('save_post_' . PGP_CPT, function ($post_id) {
    if (!isset($_POST['pgp_code'], $_POST['pgp_code_nonce'])) return;
    if (!wp_verify_nonce(sanitize_key(wp_unslash($_POST['pgp_code_nonce'])), 'pgp_code_' . $post_id)) return;
    if (!current_user_can('edit_post', $post_id)) return;
    $code = trim(sanitize_text_field(wp_unslash($_POST['pgp_code'])));
    $other = pgp_find_tournament($code);
    if (!preg_match('/^[-a-zA-Z0-9_.]{1,64}$/', $code)) {
        $refused = __('Code refused: only letters, digits, dot, dash and underscore.', 'pairgoth-publish');
    } elseif ($other && $other->ID !== $post_id) {
        // One page per code, or a push would have two candidates to overwrite.
        $refused = sprintf(/* translators: %s: the other tournament's title */
            __('Code refused: already used by “%s”.', 'pairgoth-publish'), get_the_title($other));
    }
    if (isset($refused)) set_transient('pgp_notice_' . $post_id, $refused, 60);
    else update_post_meta($post_id, PGP_CODE_META, $code);
});

add_action('admin_notices', function () {
    $post_id = get_the_ID();
    if (!$post_id || !($notice = get_transient('pgp_notice_' . $post_id))) return;
    delete_transient('pgp_notice_' . $post_id);
    printf('<div class="notice notice-error is-dismissible"><p>%s</p></div>', esc_html($notice));
});

add_action('admin_menu', function () {
    add_options_page(__('Pairgoth Publish', 'pairgoth-publish'), __('Pairgoth Publish', 'pairgoth-publish'),
        'manage_options', 'pairgoth-publish', 'pgp_settings_page');
});

add_action('admin_init', function () {
    register_setting('pairgoth-publish', PGP_SECRET_OPT, ['type' => 'string', 'sanitize_callback' => 'trim']);
});

function pgp_settings_page() {
    if (!current_user_can('manage_options')) return;
    $secret = (string) get_option(PGP_SECRET_OPT, '');
    ?>
    <div class="wrap">
        <h1><?php esc_html_e('Pairgoth Publish', 'pairgoth-publish'); ?></h1>
        <p><?php esc_html_e('Put these two lines in the pairgoth.properties file of the tournament laptop:', 'pairgoth-publish'); ?></p>
        <pre style="padding:1em;background:#fff;border:1px solid #ccd0d4;overflow:auto">webhook.url    = <?php echo esc_html(rest_url(PGP_NS)); ?>
webhook.secret = <?php echo esc_html($secret !== '' ? $secret : '(set a secret below)'); ?></pre>
        <form action="options.php" method="post">
            <?php settings_fields('pairgoth-publish'); ?>
            <table class="form-table" role="presentation">
                <tr>
                    <th scope="row"><label for="pgp-secret"><?php esc_html_e('Shared secret', 'pairgoth-publish'); ?></label></th>
                    <td>
                        <input name="<?php echo esc_attr(PGP_SECRET_OPT); ?>" id="pgp-secret" type="text"
                               class="regular-text code" value="<?php echo esc_attr($secret); ?>"
                               autocomplete="off" spellcheck="false"/>
                        <p class="description">
                            <?php esc_html_e('Any long random string. Anyone holding it can publish HTML on this site, so keep it to the tournament laptop and serve this site over HTTPS.', 'pairgoth-publish'); ?>
                        </p>
                        <?php if ($secret === ''): ?>
                            <p class="description"><code><?php echo esc_html(wp_generate_password(32, false)); ?></code>
                                — <?php esc_html_e('a freshly generated one, if you need it.', 'pairgoth-publish'); ?></p>
                        <?php endif; ?>
                    </td>
                </tr>
            </table>
            <?php submit_button(); ?>
        </form>
        <h2><?php esc_html_e('Displaying a tournament', 'pairgoth-publish'); ?></h2>
        <p><?php esc_html_e('Each tournament gets its own page as soon as pairgoth publishes to it. To embed one in an existing page instead:', 'pairgoth-publish'); ?></p>
        <p><code>[pairgoth code="20260712-club"]</code> —
            <?php esc_html_e('optional attributes: view="pairings|standings", round="3", nav="no".', 'pairgoth-publish'); ?></p>
    </div>
    <?php
}
