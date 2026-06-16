# Site Extractor Refactor Plan

Last updated: 2026-06-16

## Goal

Move the extraction/removal pipeline toward a Mercury-style architecture:

- keep generic article extraction as the default;
- allow domain/site profiles to provide stronger content selectors, metadata selectors, removal selectors, and post-processing;
- stop putting site-specific scars into the global removal list;
- keep every migrated rule covered by the existing FeedFlow dump fixtures.

The current dump-driven cleanup loop is useful and should continue, but the selector ownership needs to become explicit.

## Prior Thread Note

The referenced `codex://threads/019ec81f-e095-7a32-98dd-9c8dafb02ec1` thread is readable through the local Codex session store and was used to reconstruct the June 15 dump-fix sequence. The inventory below is cross-checked against:

- `/Users/mg/.codex/sessions/2026/06/14/rollout-2026-06-14T23-53-12-019ec81f-e095-7a32-98dd-9c8dafb02ec1.jsonl`;
- `RemovalPipeline.kt`;
- `MainContentDetector.kt`;
- recent commits;
- `docs/README.md`;
- `docs/fixture-coverage.md`.

The important correction from that prior thread is that the current global removal list does not only contain the latest June 16 sites. It also contains a large June 15 batch from the FeedFlow dump loop: Il Post, MacRumors, Android Central/Future, Axios, TechCrunch, The Verge/Vox, Business Insider, Entrepreneur, Fortune, Blogger, CSS-Tricks, JetBrains Blog, BBC, BuzzFeed, Mashable, Polygon, NASA, Pianetabasket, Citynews/VeneziaToday, WordPress/Jannah/Enfold/Mailchimp, Substack, and 9to5 sites.

## Proposed Architecture

Add a site profile layer alongside the generic detector/removal pipeline.

```kotlin
interface SiteExtractor {
    val id: String
    val domains: Set<String>
    val priority: Int get() = 0

    fun matches(context: SiteExtractionContext): Boolean

    val contentSelectors: List<String> get() = emptyList()
    val preContentRemoveSelectors: List<String> get() = emptyList()
    val postContentRemoveSelectors: List<String> get() = emptyList()

    val titleSelectors: List<String> get() = emptyList()
    val authorSelectors: List<String> get() = emptyList()
    val dateSelectors: List<String> get() = emptyList()

    fun postProcess(content: Element, context: SiteExtractionContext, debug: MutableList<RemovalRecord>) = Unit
}
```

```kotlin
data class SiteExtractionContext(
    val url: String?,
    val host: String?,
    val document: Document,
)
```

Add:

- `SiteExtractorRegistry`: resolves zero or more profiles by host/path.
- `SiteExtractionDebug`: records selected profile id, content selector tried, and site-specific removals.
- `ProfileAwareContentDetector`: tries profile `contentSelectors` before generic scoring when the selector has enough text.
- `ProfileRemovalPipeline`: applies domain-specific selectors only for matching profiles.

## Pipeline Order

1. Parse and standardize HTML.
2. Build `SiteExtractionContext`.
3. Resolve site profiles by host/path.
4. Apply matching profile `preContentRemoveSelectors` to a working document copy.
5. Select content:
   - try profile `contentSelectors` in order;
   - validate with existing word/prose guards;
   - fallback to current generic scorer.
6. Apply generic removal pipeline.
7. Apply matching profile `postContentRemoveSelectors`.
8. Run profile `postProcess`.
9. Deduplicate images, remove cover image, sanitize, convert Markdown.

This means a selector like `.ms-items-widget` can be safe because it only runs for `motorsport.com`, not every site.

## Migration Policy

Keep global only when the rule is genuinely cross-site:

- structural junk: `nav`, `footer`, `form`, `button`, `input`;
- comments containers with generic ids/classes;
- common ad/newsletter/share/related patterns;
- behavioral rules like low-scoring link-heavy blocks;
- hidden-element cleanup.

Move to site profiles when the selector contains:

- site-specific class prefixes: `ms-`, `msnt-`, `duet--`, `w-`, `slice-container-`, etc.;
- exact publisher URLs or domains;
- exact article/dek/title text from one dump;
- branded modules: PhoneArena, Android Authority, Motorsport, Valnet, Future, etc.

Avoid adding new global selectors unless a focused false-positive test proves they are safe across unrelated content.

## Implementation Phases

### Phase 1: Scaffolding

- Add `SiteExtractor`, `SiteExtractionContext`, and `SiteExtractorRegistry`.
- Add empty default registry to `DefuddleOptions` or internal config.
- Add debug fields:
  - `siteExtractorIds`
  - `profileContentSelector`
  - `profileRemovals`
- Keep behavior unchanged at first.

Tests:

- Registry matches by host and ignores unrelated hosts.
- Profile selectors do not run without a match.
- Debug records selected profile.

### Phase 2: Profile Content Selection

- Extend content detection to accept ordered preferred selectors.
- Try preferred selector only if it passes minimum article text/prose guard.
- Keep generic scoring fallback.

Tests:

- A profile selector beats noisy `main`.
- A weak/empty profile selector falls back to generic scoring.
- Existing FeedFlow fixtures still pass.

### Phase 3: Profile Removals

- Add profile-specific removal step after generic removals.
- Reuse `selectSafe` and `RemovalRecord`.
- Tag records with `removeSiteSelectors:<profileId>`.

Tests:

- Site selector runs only for the matching domain.
- Site selector does not run on same markup under `example.com`.
- Existing fixture tests pass.

### Phase 4: Migrate High-Risk Recent Selectors

Start with the most obviously site-specific selectors. These were added during the live FeedFlow dump loop and currently create the highest risk of global false positives:

1. Motorsport.com
2. SI / MinuteMedia
3. PhoneArena
4. Android Authority
5. Rolling Stone
6. PopCulture
7. ScreenRant / Valnet
8. Variety
9. GameSpot
10. GamingOnLinux
11. Axios
12. Business Insider
13. Mashable
14. BBC
15. BuzzFeed

For each migration:

- create a profile object;
- move selectors out of `EXACT_SELECTORS`;
- keep or move the focused unit tests;
- rerun the matching FeedFlow dump regression;
- run full `RemovalPipelineTest` and `FeedFlowReaderDumpRegressionTest`.

### Phase 5: Migrate Older Site Families

Move older committed site/family rules into profiles:

- Il Post.
- MacRumors.
- Future: Android Central and similar Future sites.
- Valnet: Android Police, ScreenRant, Polygon patterns where shared.
- Vox: The Verge/SBNation style.
- Axios.
- Business Insider.
- Citynews/VeneziaToday.
- Pianetabasket.
- WordPress/Jannah/Enfold/Blogger/Mailchimp as family profiles if domain-specific selectors are too broad globally.
- TechCrunch, Ars Technica, Fortune, BuzzFeed, Mashable, NASA.

### Phase 6: Consolidate Generic Behavioral Rules

After site migration, review remaining generic exact selectors and convert some to safer behavioral removals:

- recommendation heading plus card-run removal;
- previous/next article blocks;
- author bio cards;
- comments widgets;
- source/preferred-source prompts;
- adblock/subscription prompts.

The end state should have fewer exact global selectors and more guarded structural rules.

### Phase 7: Docs and Release Surface

- Document custom extractor API in README.
- Add examples for injected profiles:

```kotlin
Defuddle.parseHtml(
    html = html,
    url = url,
    options = DefuddleOptions(
        siteExtractors = listOf(MySiteExtractor),
    ),
)
```

- Decide whether public custom extractors are stable API now or experimental.

## Candidate Site Profiles and Selectors

### MotorsportProfile

Domains:

- `motorsport.com`

Preferred content selectors:

- `.ms-article-content`
- `.ms-article__body`
- `article.ms-page`

Post-content removals:

- `.ms-article-end`
- `.msnt-article-prev-next`
- `.ms-comments-wrapper`
- `.ms-inarticle-widgets`
- `.ms-items-widget`
- `#adblock-content-blocked-tpl`
- `.adblock-content-blocked`
- `h2:matchesOwn((?i)^Photos from .+)`

Fixtures:

- `general--www.motorsport.com-f1-news-im-not-a-machine-isack-hadjar-blasts-red-bull-start-procedure-10830609`

### MinuteMediaSiProfile

Domains:

- `si.com`

Preferred content selectors:

- `.article-content`
- `article#main-article`
- `article`

Post-content removals:

- `[data-testid="google-news-widget"]`
- `[data-mm-recirc]`
- `.voltax-recirculation-widget`
- `div:has(> div > a[data-testtype="author-link"]):has(> div > span:matchesOwn(^\|$))`
- `div:has(> hr):has([data-testtype="author-bio"])`
- `div:has([data-testtype="author-bio"]):has([data-testtype="x-link"])`
- `div:has(> a[href="https://www.si.com/nfl/draft/onsi"]):has(> a[href="https://www.si.com/nfl/draft/onsi/late-round-expert"])`

Fixtures:

- `general--www.si.com-nfl-draft-onsi-late-round-expert-five-sleeper-nfl-draft-picks-already-putting-pressure-on-coaches-to-change-t`

### PhoneArenaProfile

Domains:

- `phonearena.com`

Preferred content selectors:

- `.content-body`
- `article`

Post-content removals:

- `.content-header-widgets`
- `.content-disclaimer`
- `.content-after-content-row`
- `.content-author-byline`
- `.discussions-latest`
- `.phone-links`

Fixtures:

- `general--www.phonearena.com-news-razr-ultra-2025-motorola-deal-700-usd-off-free-earbuds_id181125`

### AndroidAuthorityProfile

Domains:

- `androidauthority.com`

Post-content removals:

- `div:has(> p:matchesOwn((?i)^Affiliate links on Android Authority may earn us a commission))`
- `a[href="https://www.androidauthority.com/mobile/"]`
- `div:matchesOwn((?i)^The Android 17-based update brings critical display, camera, and stability patches\.$)`
- `div:has(> a[href*="AAGooglePrefSource"])`
- `div:has(> a[href*="AAGoogleDiscoverSource"])`
- `div[data-container-type="content"]:has(a[href*="AAGoogleDiscoverSource"])`
- `div[data-container-type="content"]:has(a[href*="AAGooglePreferredSource"])`
- `div:has(> div:matchesOwn((?i)^Follow$))`
- `div[data-container-type="content"]:has(a[href*="android-authority-comment-policy"])`

Fixtures:

- `general--www.androidauthority.com-samsung-galaxy-s26-one-ui-9-beta-3-3677792`

Note:

- Replace exact article-dek text with structural selectors if we see another Android Authority dump with a different dek.

### RollingStoneProfile

Domains:

- `rollingstone.com`

Known removals from docs/tests:

- article header/dek/date chrome;
- `.trending-in-article`;
- in-article trending story recirculation.

Current selectors:

- `.trending-in-article`

Fixtures:

- `general--www.rollingstone.com-music-music-news-madonna-bring-your-love-video-sabrina-carpenter-1235577750`

### PopCultureProfile

Domains:

- `popculture.com`

Known removals:

- Gutenberg video placeholders;
- next article/footer modules;
- category sidebars;
- newsletter signup blocks;
- most-viewed headings.

Current selectors:

- `.wp-block-savage-platform-beehiiv-form`
- `.wp-block-savage-platform-primis-video`
- `.section-heading:matches((?i)^\\s*Most\\s+Viewed\\s*$)`

Fixtures:

- `general--popculture.com-celebrity-news-alf-mom-anne-schedeen-dead-at-77`

### ValnetProfile

Domains/examples:

- `screenrant.com`
- `androidpolice.com`
- likely other Valnet properties if confirmed by fixtures.

Post-content removals:

- `.display-card.article-card`
- `.display-card[data-include-community-rating]`
- `.display-card[data-show-streamrentbuy-links]`
- `div.article-card[data-nosnippet]`
- `.article-options`
- `.article-tags`
- `.follow-container`
- `[data-is-follow-choice-button]`
- `[data-is-followed-choice-button]`
- `.w-article-header-comp`
- `.w-heading-options`
- `.w-sharing-copy`
- `#sharingCopyAlertDiv`
- `.w-article-header-author-img`
- `.article-header-author-img`
- `.w-tag-interaction-popup-menu`
- `.article-header > p`
- `.article-header-title`

Fixtures:

- `general--screenrant.com-gilmore-girls-leaving-netflix-june-2026`
- `general--www.androidpolice.com-replaced-samsung-home-screen-with-custom-launcher-never-going-back`
- `general--www.androidpolice.com-two-week-android-experiment-changed-how-i-interact-with-social-media`
- `general--www.polygon.com-overwatch-season-3-skins-nyan-cat-cafe-ultra-mythic-battle-pass`

Decision:

- Polygon shares several Valnet-style selectors in the current global list, but it should remain easy to split into `PolygonProfile` if future Polygon fixtures diverge from ScreenRant/Android Police behavior.

### FutureProfile

Domains/examples:

- `androidcentral.com`

Post-content removals:

- `[data-jwp-carousel]`
- `.van_vid_carousel`
- `.jw-carousel`
- `.jwplayer__wrapper`
- `.jwcarousel__hook`
- `.slice-author-bio`
- `.slice-container-authorBio`
- `[id^="slice-container-authorBio"]`
- `.slice-container-newsletterForm`
- `[id^="slice-container-newsletterForm"]`
- `.popular-box`
- `.popular-box-slice`
- `.slice-container-popularBox`
- `[id^="slice-container-popularBox"]`
- `.table__instruction`
- `.inline-gallery__count`
- `.inline-gallery__arrows`
- `.image-cont__expand`

Fixtures:

- `general--www.androidcentral.com-phones-honor-phones-honor-magic-v6-review`
- `general--www.androidcentral.com-phones-samsung-galaxy-galaxy-phones-are-finally-getting-a-feature-android-users-have-wanted-for-y`

### VarietyProfile

Domains:

- `variety.com`

Post-content removals:

- `#article-comments`
- `#comments-loading`
- `#comments-loaded`
- `.o-comments-link`

Fixtures:

- `general--variety.com-2026-film-festivals-cecilia-yip-rebecca-li-manxuan-kering-women-in-motion-shanghai-1236781725`

Some of these comment selectors may remain generic; the profile is useful if a future false positive appears.

### GameSpotProfile

Domains:

- `gamespot.com`

Known removals:

- article right-rail/sidebar commerce widgets;
- "Where to Buy";
- loading placeholders;
- retail commission disclosures.

Current selectors likely involved:

- `.right-rail`
- `.single-sidebar`
- `[aria-label="Article sidebar"]`

Fixtures:

- `general--www.gamespot.com-articles-microsoft-boss-wants-xbox-to-start-pulling-its-weight-financially`

### GamingOnLinuxProfile

Domains:

- `gamingonlinux.com`

Post-content removals:

- `.article_likes`
- `.social-media-comments`
- `.rules-reminder`
- YouTube consent placeholder normalization should remain a generic/media transform only if it is safely host-checked.

Fixtures:

- `general--www.gamingonlinux.com-2026-06-the-big-dino-update-for-dwarf-fortress-announced-for-june-25`

### TechCrunchProfile

Domains:

- `techcrunch.com`

Post-content removals:

- `.wp-block-techcrunch-post-authors-list`
- `.wp-block-techcrunch-event-cta`
- `.rightrail-promo`
- `.latest-in-pattern`

Fixtures:

- `general--techcrunch.com-2026-06-15-spacexs-biggest-ever-ipo-just-grew-to-85-7-billion-raised`

### AxiosProfile

Domains:

- `axios.com`

Known removals from the prior thread:

- timestamp/byline/share chrome;
- source-preference prompts;
- read-next modules;
- empty list artifacts.

Current selectors and rules involved:

- `a[href*="google.com/preferences/source"]`
- source/preferred-source prompt heuristics;
- byline metadata strip heuristics;
- read-next/recommendation module heuristics.

Fixtures:

- `general--www.axios.com-2026-06-14-anthropic-white-house-mythos-fable`

Decision:

- Keep the source-preference URL selector generic only if it stays strongly host/path scoped by URL shape and tests show no false positives. Axios-specific layout selectors should move to this profile.

### VoxProfile

Domains/examples:

- `theverge.com`
- maybe `sbnation.com` after fixture confirmation.

Post-content removals:

- `.duet--article--lede`
- `.duet--ledes--standard-lede-bottom`
- `.wp-block-query`
- `aside[data-mrf-recirculation]`
- `aside.hawk-root`

Fixtures:

- `general--www.theverge.com-games-949853-roblox-age-verification-demo-nbc`

### BusinessInsiderProfile

Domains:

- `businessinsider.com`

Post-content removals:

- `[data-component-type="post-byline"]`
- `.post-byline`
- `.byline-wrapper`
- `.byline-author-container`
- `[data-component-type="timestamp"]`
- `.post-video-recirc`
- `[data-component-type="post-video-recirc"]`
- `.back-to-home-container`
- `.back-to-home`

Fixtures:

- `general--www.businessinsider.com-anthropic-white-house-fable-mythos-5-drama-explained-2026-6`

Decision:

- These are publisher layout selectors and should not remain global.

### CitynewsProfile

Domains/examples:

- `veneziatoday.it`

Post-content removals:

- `.l-entry__footer`
- `.l-entry__sidebar`
- `.l-entry--infos-square > .l-entry__header`
- `.l-entry__byline`
- `.l-entry__byline--small`
- `.article__meta`

Fixtures:

- `general--www.veneziatoday.it-cronaca-contratto-scaduto-sciopero-farmacie-comunali`
- `general--www.veneziatoday.it-eventi-estate-insieme-a-vigonovo-programma`

### PianetaBasketProfile

Domains:

- `pianetabasket.com`
- `m.pianetabasket.com`

Known removals from the prior thread:

- body-level site chrome when semantic `role="main"` is available;
- repeated dates;
- section navigation;
- latest-news modules;
- popular lists;
- footer text;
- author-profile boxes;
- mobile opening byline/date/read-count metadata.

Current selectors and rules involved:

- semantic-main selection preference from `MainContentDetector`;
- `.post-author`
- `.byline-box`
- `.entry-meta`
- `.post-meta`
- `.post-meta-infos`
- `.article-meta`
- `.posted-on`
- `.byline`
- latest-news/recommendation module heuristics.

Fixtures:

- `general--www.pianetabasket.com-legabasket-serie-a-virtus-bologna-casting-continua-sekulic-profili-panchina-363560`
- `general--www.pianetabasket.com-euroleague-l-anadolu-efes-conferma-l-uscita-rolands-smits-stagioni-363578`
- `general--m.pianetabasket.com-euroleague-partizan-belgrado-interessato-all-ex-brindisi-venezia-derek-willis-363565`

Decision:

- Semantic-main preference can stay generic, but mobile/template selectors and latest-news blocks should be profile scoped.

### WordPressFamilyProfile

Use this carefully. Some selectors may remain generic, but profile scoping is safer for theme-specific names.

Domains/examples:

- `berlinomagazine.com`
- `ilmitte.com`
- `basketuniverso.it`
- generic WordPress dumps with repeated theme classes.

Post-content removals:

- `.big-preview`
- `.avia-copyright`
- `.entry-footer`
- `.entry-aside`
- `.post-cat-wrap`
- `.post-cat`
- `.post-cats-list`
- `.post-categories`
- `.entry-categories`
- `.cat-links`
- `.category-button`
- `.abh_box`
- `.author-bio-box`
- `.wp-block-mailchimp-mailchimp`
- `.mc_embed_signup`
- `.mailchimp-signup`

Fixtures:

- `general--berlinomagazine.com-2026-berlino-progetto-unico-in-europa-case-e-spazi-per-lesbiche-e-persone-queer-nel-cuore-della-citt`
- `general--www.ilmitte.com-2026-06-riforma-sanita-warken-opposizione-germania`
- `general--www.ilmitte.com-2026-06-svastica-vegana-al-buffet-di-afd`
- `general--www.basketuniverso.it-nba-piu-di-una-semplice-lega-un-viaggio-tra-stori`

### IlPostProfile

Domains:

- `ilpost.it`

Known removals and fixes from the prior thread:

- broad body selection/breadcrumb leakage before the article;
- bottom recommendation sections;
- embedded audio player placeholders;
- trailing article tag lists;
- Markdown link-label flattening around emphasized inline text;
- WordPress-style captioned body images should remain as Markdown images.

Current selectors and rules involved:

- `#audioPlayerArticle`
- `.audio-player`
- `.audioplayer`
- `[data-mp3]`
- `[data-audio-src]`
- trailing tag-list heuristics;
- recommendation module heuristics;
- image-wrapper Markdown handling.

Fixtures:

- `general--www.ilpost.it-2026-06-15-ufc-casa-bianca`
- `general--www.ilpost.it-2026-06-15-lisbona-funicolare-gloria-ferme`
- `general--www.ilpost.it-2026-06-15-cooling-break-mondiali-calcio-pause`
- `general--www.ilpost.it-2026-06-15-marius-borg-hoiby-figlio-principessa-ereditaria-norvegia-condannato-stupro`
- `general--www.ilpost.it-2026-06-15-sorelle-sparite-minturno`

Decision:

- Image-wrapper and Markdown delimiter fixes stay generic. Audio-player placeholders and Il Post-specific trailing chrome should move to this profile unless another publisher fixture proves the selector is generic.

### SubstackProfile

Domains/examples:

- `20percent.berlin`

Post-content removals:

- `#substack-comments`
- `.comments-section`
- `.more-comments`
- `.portable-archive`
- `.portable-archive-list`
- `.portable-archive-empty`
- `[aria-label="Top Posts Footer"]`

Fixtures:

- `general--www.20percent.berlin-p-500-uber-bvg-nius-raves-podcast`
- `general--www.20percent.berlin-p-493-easy-burgeramt-appts-gun-raid`

### BloggerProfile

Domains/examples:

- `android-developers.googleblog.com`

Post-content removals:

- `.copy-tooltip`
- `.copy-tooltiptext`
- `.hidden_message`
- `.postHead`
- `#blog-pager`
- `.blog-pager`
- `.blog-pager-newer-link`
- `.blog-pager-older-link`
- `.top-page`
- `div.separator:matchesOwn((?i)^\\s*posted\\s+by\\s+)`

Fixtures:

- `general--android-developers.googleblog.com-2026-05-apply-android-xr-developer-catalyst`

### JetBrainsBlogProfile

Domains:

- JetBrains/Kotlin blog domain from fixture.

Post-content removals:

- `.article-section .content > a.tag`
- `.article-section .content > h1:first-of-type`
- `.author-post`
- `.content__pagination`
- `.toc-opener`
- `.article-section + .section.light-gray-bg`

Fixtures:

- `general--blog.jetbrains.com-kotlin-2026-05-security-support-policy-for-the-kotlin-standard-library`

### NASAProfile

Domains:

- `science.nasa.gov`

Post-content removals:

- `[class*="credits-and-details"]`
- `[class*="related-articles"]`
- `[class*="topic-cards"]`
- `[class*="about-the-author"]`

Fixtures:

- `general--science.nasa.gov-missions-chandra-nasas-chandra-finds-unexpected-fireworks-in-aftermath-of-stellar-explosions`

### MashableProfile

Domains:

- `mashable.com`

Post-content removals:

- `[aria-label="Author Bio Flyout"]`
- `[role="tooltip"][aria-label*="Author Bio"]`
- `div:matchesOwn((?i)^\s*All products featured here are independently selected)`
- `img[src*="seamless-keep-scrolling"]`
- `img[alt="Mashable Potato"]`
- author-card heuristics for freelance-writer profile boxes and default avatar images.

Known removals from the prior thread:

- breadcrumb/title/dek/byline blocks before article prose;
- top and bottom Mashable author bio blocks;
- keep-scrolling placeholder art;
- author flyouts and footer bios;
- affiliate disclosure and inline newsletter prompts.

Fixtures:

- `general--mashable.com-tech-june-15-aiper-scuba-v3-deal`
- `general--mashable.com-tech-june-12-bose-ultra-open-earbuds-deal`

Decision:

- Generic author-bio heuristics can remain if guarded by prose/image/role checks. Mashable fallback images and author flyout selectors should be profile scoped.

### BBCProfile

Domains:

- `bbc.com`

Post-content removals:

- `[data-component="headline-block"]`
- `[data-component="byline-block"]`
- `img.hide-when-no-script`
- `img[aria-label="image unavailable"]`
- `img[src*="grey-placeholder"]`
- `p:matches((?i)\bdo\s+you\s+have\s+a\s+story\s+suggestion\b)`
- `p:matches((?i)^follow\s+.{1,80}\s+news\s+on\b)`

Known removals from the prior thread:

- duplicated headline/byline blocks;
- no-script grey placeholder images;
- story-suggestion prompts;
- local-news social follow footers.

Fixtures:

- `general--www.bbc.com-news-articles-cnv9367gvp4o`

Decision:

- Placeholder image cleanup may remain generic only if constrained to empty/no-script placeholder images. BBC `data-component` selectors and social prompts should move to this profile.

### BuzzFeedProfile

Domains:

- `buzzfeed.com`

Post-content removals:

- `.postHead`
- `[class*=headline-byline]`
- comment wrappers already covered by generic comments selectors.

Known removals from the prior thread:

- `header.postHead` badge/title/dek/timestamp blocks;
- `headline-byline` author bio blocks;
- trailing comments wrappers.

Fixtures:

- `general--www.buzzfeed.com-morgansloss1-world-cup-tourists-share-thoughts-on-the-usa`

Decision:

- `.postHead` and `headline-byline` are not safe as global selectors and should move to this profile.

### AppleInsiderProfile

Domains:

- `appleinsider.com`

Known removals:

- opening article header chrome;
- hero-caption metadata;
- read-time text;
- rumor-score blocks.

Many of these are behavioral opening-header rules and may remain generic if guarded.

Fixtures:

- `general--appleinsider.com-articles-26-06-15-iphone-18-pro-buyers-should-watch-out-for-a-repeat-problem`

### CSS-TricksProfile

Domains:

- `css-tricks.com`

Known removals from the prior thread:

- mega article headers that duplicate tags, title, avatar, author, and date before article prose.

Current selectors and rules involved:

- `:scope > div:first-child > header`
- opening article header heuristics.

Fixtures:

- `general--css-tricks.com-another-stab-at-the-perfect-css-pie-chart-sans-javascript`

Decision:

- The opening-header heuristic can stay generic when it uses body/prose guards. Any CSS-Tricks-only wrapper selector should move here.

### MacRumorsProfile

Domains:

- `macrumors.com`

Post-content removals:

- `[class*="byline--"]`
- `.comments-link`
- `.linkback`

Fixtures:

- `general--www.macrumors.com-2026-06-15-uk-ban-social-media-under-16s`
- `general--www.macrumors.com-2026-06-15-iphone-18-pro-may-face-same-durability-issues`

### ArsTechnicaProfile

Domains:

- `arstechnica.com`

Post-content removals:

- `.text-settings-dropdown-story`
- `.text-settings`
- `.author-mini-bio`

Fixtures:

- `general--arstechnica.com-security-2026-06-peoplesoft-0-day-affecting-hundreds-of-organizations-steals-gigabytes-of-data`

### FortuneProfile

Domains:

- `fortune.com`

Post-content removals:

- `[data-cy="trending-top-bar"]`
- `[data-cy="article-section-eyebrow"]`
- `[data-cy="article-tag-eyebrow"]`
- `[data-cy="authors-bio-cards"]`
- `[data-cy="author-bio"]`
- `[data-cy="author-see-full-bio"]`
- `[data-component="headline-block"]`
- `[data-component="byline-block"]`

Fixtures:

- `general--fortune.com-2026-06-15-beagle-breeding-farm-wisconsin-protests-closed`

### EntrepreneurProfile

Domains:

- `entrepreneur.com`

Post-content removals:

- `[data-cy="time-rubric"]`
- `[data-cy="byline-author"]`
- `[data-cy="social-share-top"]`
- `[data-cy="social-share-bottom"]`
- `[data-vars-event-name="preferred_source_view"]`
- `[data-cy="preferred-source-top"]`
- `[data-cy="preferred-source-bottom"]`
- `[data-cy="what-to-read-next"]`
- `.classifai-listen-to-post-wrapper`
- `.classifai-post-audio-heading`
- `audio[id^="classifai-post-audio-player"]`

Fixtures:

- `general--www.entrepreneur.com-business-news-hundreds-of-louisiana-teachers-are-getting-50000-bonuses-this-year-heres-why`
- `general--www.entrepreneur.com-business-news-she-turned-celebrity-gossip-into-a-22-billion-company`

### NineToFiveProfile

Domains:

- `9to5google.com`
- `9to5mac.com`
- `9to5linux.com`

Post-content removals:

- `.google-preferred-source-badge`
- `.ad-disclaimer-container`
- `.disclaimer-affiliate`
- `.visitor-promo`
- `#after_disclaimer_placement`
- `.btn-gpsource-bt-article`
- `.top-comment`

Fixtures:

- `general--9to5google.com-2026-06-14-google-ads-tease-next-pixel-drop-with-screen-reactions-and-gemini-omni-video`
- `general--9to5google.com-2026-06-13-the-fitbit-air-made-me-ditch-my-pixel-watch-and-i-couldnt-be-happier`
- `general--9to5mac.com-2026-06-13-airpods-pro-3-drop-to-their-best-price-ever-as-apple-announces-new-ios-27-features`
- `general--9to5mac.com-2026-06-11-iphone-ultra-is-coming-six-new-features-in-apples-top-tier-model`
- `general--9to5linux.com-dietpi-10-5-enables-kms-drm-graphics-system-by-default-for-raspberry-pi-sbcs`

## Selectors to Keep Generic Initially

These are broad enough to stay global for now:

- `nav`
- `footer`
- `form`
- `button`
- `input`
- `select`
- `textarea`
- `[role=navigation]`
- `#comments`
- `#discussion`
- `#article-comments`
- `#comments-loading`
- `#comments-loaded`
- `#viafoura-comments-container`
- `#viafoura-comment-wrapper`
- `.viafoura-twig-component`
- `.viafoura`
- `[data-component-name*=Comments]`
- `[data-component-name*=comments]`
- `[class*=CommentsWrapper]`
- `.ad`
- `.ads`
- `.advertisement`
- `.comments`
- `.comment`
- `.share`
- `.sharing`
- `.related`
- `.related-posts`
- `.toc`
- `.table-of-contents`

These need false-positive audits later:

- `.byline`
- `.entry-meta`
- `.post-meta`
- `.article-meta`
- `.author-bio`
- `.author-box`
- `.author-profile`
- `.newsletter-section`
- `.newsletter-form__wrapper`
- `.right-rail`
- `.single-sidebar`

They are common but not always safe globally.

## Test Strategy

For every migrated profile:

1. Keep existing FeedFlow dump regression unchanged.
2. Add a profile-isolation unit test:
   - same HTML under matching host removes site chrome;
   - same HTML under `example.com` does not run site profile selectors.
3. Run:

```bash
./gradlew test --tests 'dev.defuddle.removal.RemovalPipelineTest' --tests 'dev.defuddle.fixtures.FeedFlowReaderDumpRegressionTest' -q --console=plain
./gradlew test -q --console=plain
./gradlew check -q --console=plain
```

## First Implementation Slice

Recommended first PR/commit sequence:

1. Add profile interfaces and registry with no behavior change.
2. Add `MotorsportProfile` and move only Motorsport selectors.
3. Add `MinuteMediaSiProfile`.
4. Add `PhoneArenaProfile`.
5. Add `AndroidAuthorityProfile`.
6. Review global `EXACT_SELECTORS` count and document before/after.

This gives immediate value because these are the latest and most obviously site-specific selectors.

## Acceptance Criteria

- All current tests pass.
- FeedFlow reader-dump fixture count remains 48 or higher.
- Recent dump regressions still pass after moving selectors out of global list.
- Debug output names the applied profile.
- Site-specific selectors no longer run on unrelated domains.
- New site-specific cleanup work goes into a profile by default.
