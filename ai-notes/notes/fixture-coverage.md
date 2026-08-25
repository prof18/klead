# Fixture Coverage

Last updated: 2026-08-25

Upstream Defuddle SHA: `9db72600a0cfc568eafb31e85ef68ba16add072e`

## Current Gates

- Total upstream HTML fixtures: 190
- Strict supported Defuddle Markdown snapshots: 176
- Strict supported Defuddle metadata snapshots: 176
- Explicitly excluded math/rendering fixtures: 14
- Focused hidden-content cleaned-HTML fixtures: 2
- Strict whole-corpus Markdown parity: enforced for supported fixtures
- Captured site regression snapshots: 58 (56 recovered captures, one harness fixture, and one new live capture)

## Active Strict Fixtures

- `DefuddleFixtureMarkdownSnapshotTest`: compares supported upstream Defuddle expected Markdown against parser output after minimal Markdown normalization.
- `DefuddleFixtureMarkdownSnapshotTest`: compares supported upstream Defuddle frontmatter fields `title`, `author`, and `site` against parser metadata. The upstream `published` field is intentionally not compared because the public metadata model does not expose it.
## Focused Semantic Fixtures

- `hidden--nodes`
- `hidden--visibility`

## Captured Site Regressions

The original 56 FeedFlow captures are retained as ordinary application-independent regressions, alongside the portable harness fixture and a new live 9to5Mac capture. `SiteRegressionTest` compares both Markdown and cleaned HTML for all 58 cases on every target.

- `general--www.ilpost.it-2026-06-15-ufc-casa-bianca`: verifies that broad body selection, breadcrumbs, and bottom recommendation sections do not leak into reader Markdown.
- `general--www.ilpost.it-2026-06-15-lisbona-funicolare-gloria-ferme`: verifies that WordPress-style captioned image wrappers keep body images in Markdown.
- `general--www.ilpost.it-2026-06-15-cooling-break-mondiali-calcio-pause`: verifies that embedded audio player placeholders do not leak into reader Markdown.
- `general--www.ilpost.it-2026-06-15-marius-borg-hoiby-figlio-principessa-ereditaria-norvegia-condannato-stupro`: verifies that trailing article tag lists do not leak into reader Markdown.
- `general--www.ilpost.it-2026-06-15-sorelle-sparite-minturno`: verifies that emphasized link text with boundary whitespace is flattened inside Markdown link labels without losing spacing.
- `general--www.macrumors.com-2026-06-15-uk-ban-social-media-under-16s`: verifies that broad main selection, article tags, comment counts, popular-story modules, and comment modules do not leak into reader Markdown.
- `general--www.macrumors.com-2026-06-15-iphone-18-pro-may-face-same-durability-issues`: verifies that CSS-module byline/date chrome and related-roundup linkback footers do not leak into reader Markdown.
- `general--www.androidcentral.com-phones-honor-phones-honor-magic-v6-review`: verifies that video carousels, affiliate/deal widgets, gallery controls, trailing comment prompts, back-to-top controls, and read-more recirculation modules do not leak into reader Markdown, while no-op-spanned specs tables render as Markdown tables.
- `general--www.androidcentral.com-phones-samsung-galaxy-galaxy-phones-are-finally-getting-a-feature-android-users-have-wanted-for-y`: verifies that Future-style newsletter, author bio, and popular/latest article slice modules do not leak into reader Markdown.
- `general--www.androidpolice.com-replaced-samsung-home-screen-with-custom-launcher-never-going-back`: verifies that Valnet-style author header/bio chrome, inline newsletter signup widgets, article tag links, and follow controls do not leak into reader Markdown.
- `general--www.androidpolice.com-two-week-android-experiment-changed-how-i-interact-with-social-media`: verifies that inline Valnet related article cards do not leak into reader Markdown while the following article section is preserved.
- `general--appleinsider.com-articles-26-06-15-iphone-18-pro-buyers-should-watch-out-for-a-repeat-problem`: verifies that opening article header chrome, hero-caption metadata, read-time text, and rumor-score blocks do not leak before AppleInsider article prose.
- `general--arstechnica.com-security-2026-06-peoplesoft-0-day-affecting-hundreds-of-organizations-steals-gigabytes-of-data`: verifies that Ars Technica opening header/deck/byline/comment-count chrome, text-settings controls, and author mini-bio footers do not leak into reader Markdown.
- `general--www.axios.com-2026-06-14-anthropic-white-house-mythos-fable`: verifies that timestamp/byline/share/source-preference/read-next chrome and empty list artifacts do not leak into reader Markdown while prose wrappers with utility-class clutter keywords are preserved.
- `general--techcrunch.com-2026-06-15-spacexs-biggest-ever-ipo-just-grew-to-85-7-billion-raised`: verifies that TechCrunch article metadata, featured-image credit captions, author cards, event promos, duplicate smart-quote titles, and latest-article recirculation blocks do not leak into reader Markdown.
- `general--www.theverge.com-games-949853-roblox-age-verification-demo-nbc`: verifies that The Verge/Vox lede chrome, package cards, inline author bio modules, and follow-topics prompts do not leak before or after article prose.
- `general--www.businessinsider.com-anthropic-white-house-fable-mythos-5-drama-explained-2026-6`: verifies that Business Insider byline/follow, timestamp, related-video recirculation, and back-to-home chrome do not leak into reader Markdown.
- `general--www.entrepreneur.com-business-news-hundreds-of-louisiana-teachers-are-getting-50000-bonuses-this-year-heres-why`: verifies that byline/editor/date strips, Google preferred-source links, comment controls, and listen-to-post prompts do not leak before article prose.
- `general--www.entrepreneur.com-business-news-she-turned-celebrity-gossip-into-a-22-billion-company`: verifies that orphan separator wrappers and related-content card runs do not leak before or after article prose.
- `general--fortune.com-2026-06-15-beagle-breeding-farm-wisconsin-protests-closed`: verifies that Fortune trending bars, article category eyebrows, author bio cards, and loading-skeleton latest/most-popular recirculation modules do not leak into reader Markdown.
- `general--android-developers.googleblog.com-2026-05-apply-android-xr-developer-catalyst`: verifies that Blogger copy-to-clipboard tooltips, posted-by separators, previous/next post pagers, and orphan trailing dividers do not leak into reader Markdown.
- `general--css-tricks.com-another-stab-at-the-perfect-css-pie-chart-sans-javascript`: verifies that CSS-Tricks mega article headers with duplicated tags, title, avatar, author, and date do not leak before article prose.
- `general--9to5google.com-2026-06-14-google-ads-tease-next-pixel-drop-with-screen-reactions-and-gemini-omni-video`: verifies that 9to5-style related headings, author follow links, preferred-source badges, affiliate disclaimers, and visitor promos do not leak after article prose.
- `general--9to5google.com-2026-06-13-the-fitbit-air-made-me-ditch-my-pixel-watch-and-i-couldnt-be-happier`: verifies that embedded top-comment modules do not leak while adjacent article prose is preserved.
- `general--9to5mac.com-2026-06-13-airpods-pro-3-drop-to-their-best-price-ever-as-apple-announces-new-ios-27-features`: verifies that emphasized affiliate deal link labels are flattened and whitespace-only links do not render as empty Markdown links.
- `general--9to5mac.com-2026-06-11-iphone-ultra-is-coming-six-new-features-in-apples-top-tier-model`: verifies that orphaned trailing commerce headings are removed after product-link lists are stripped.
- `general--9to5mac-com-2026-08-24-openai-restores-5-hour-codex-and-work-limits-for-chatgpt-plus-users`: verifies that a trailing "Worth checking out on Amazon" heading is removed after its affiliate product-link list is stripped.
- `general--9to5linux.com-dietpi-10-5-enables-kms-drm-graphics-system-by-default-for-raspberry-pi-sbcs`: verifies that 9to5Linux share strips, duplicate post thumbnails, and ko-fi donation promos do not leak while the article image metadata remains correct.
- `general--www.veneziatoday.it-cronaca-contratto-scaduto-sciopero-farmacie-comunali`: verifies that Citynews-style mobile app promos, entry footers, story-card recirculation, most-read sidebars, native footer sections, and Outbrain placeholders do not leak into reader Markdown.
- `general--www.veneziatoday.it-eventi-estate-insieme-a-vigonovo-programma`: verifies that Citynews event info-square headers and byline chrome do not leak before event article prose.
- `general--www.pianetabasket.com-legabasket-serie-a-virtus-bologna-casting-continua-sekulic-profili-panchina-363560`: verifies that body-level site chrome, repeated dates, section navigation, latest-news modules, popular lists, and footer text do not leak when a semantic `role="main"` article container is available.
- `general--www.pianetabasket.com-euroleague-l-anadolu-efes-conferma-l-uscita-rolands-smits-stagioni-363578`: verifies that short semantic-main articles still exclude body-level navigation, author-profile boxes, latest-news modules, popular lists, and footer text.
- `general--m.pianetabasket.com-euroleague-partizan-belgrado-interessato-all-ex-brindisi-venezia-derek-willis-363565`: verifies that mobile-template opening byline/date/read-count metadata does not leak into reader Markdown.
- `general--www.basketuniverso.it-nba-piu-di-una-semplice-lega-un-viaggio-tra-stori`: verifies that WordPress category chips and Author Bio Box latest-posts modules do not leak into reader Markdown.
- `general--www.20percent.berlin-p-500-uber-bvg-nius-raves-podcast`: verifies that Substack captioned images with comma-bearing transformed `srcset` URLs render as Markdown images while Substack discussion/footer chrome is excluded.
- `general--www.20percent.berlin-p-493-easy-burgeramt-appts-gun-raid`: verifies that Substack discussion, comments, top-posts archive, and ready-for-more footer modules do not leak into reader Markdown.
- `general--berlinomagazine.com-2026-berlino-progetto-unico-in-europa-case-e-spazi-per-lesbiche-e-persone-queer-nel-cuore-della-citt`: verifies that Enfold/WordPress cover copyright captions and entry metadata strips do not leak before article prose.
- `general--www.ilmitte.com-2026-06-riforma-sanita-warken-opposizione-germania`: verifies that Jannah/TieLabs WordPress category-chip wrappers do not leak before article prose.
- `general--www.ilmitte.com-2026-06-svastica-vegana-al-buffet-di-afd`: verifies that inline WordPress/Mailchimp newsletter blocks do not leak into article prose.
- `general--www.gamespot.com-articles-microsoft-boss-wants-xbox-to-start-pulling-its-weight-financially`: verifies that GameSpot right-rail commerce widgets such as "Where to Buy" do not leak into reader Markdown.
- `general--www.gamingonlinux.com-2026-06-the-big-dino-update-for-dwarf-fortress-announced-for-june-25`: verifies that GamingOnLinux YouTube consent placeholders become safe YouTube links/embeds while article-likes, social-comment, and rules footer chrome do not leak into reader Markdown.
- `general--www.rollingstone.com-music-music-news-madonna-bring-your-love-video-sabrina-carpenter-1235577750`: verifies that Rolling Stone article header/dek/date chrome and in-article trending-story recirculation modules do not leak into reader Markdown while the article body and YouTube link remain.
- `general--popculture.com-celebrity-news-alf-mom-anne-schedeen-dead-at-77`: verifies that PopCulture Gutenberg video placeholders, next-article/footer modules, category sidebars, newsletter signup blocks, and most-viewed template headings do not leak into reader Markdown.
- `general--screenrant.com-gilmore-girls-leaving-netflix-june-2026`: verifies that ScreenRant/Valnet display-card rating widgets and database metadata cards do not leak after article prose.
- `general--variety.com-2026-film-festivals-cecilia-yip-rebecca-li-manxuan-kering-women-in-motion-shanghai-1236781725`: verifies that Variety comment jump links and JavaScript loading placeholders do not leak after article prose.
- `general--www.androidauthority.com-samsung-galaxy-s26-one-ui-9-beta-3-3677792`: verifies that Android Authority affiliate disclosures, opening category/dek/byline/share chrome, Google preferred-source prompts, and comment-policy footers do not leak into reader Markdown.
- `general--www.phonearena.com-news-razr-ultra-2025-motorola-deal-700-usd-off-free-earbuds_id181125`: verifies that PhoneArena comment counters, author/date/disclaimer chrome, Google News follow rows, author bio/latest-post blocks, community discussions, and related-device footer links do not leak into reader Markdown.
- `general--www.si.com-nfl-draft-onsi-late-round-expert-five-sleeper-nfl-draft-picks-already-putting-pressure-on-coaches-to-change-t`: verifies that SI/MinuteMedia preferred-source widgets, recommendation loaders, publish/modified metadata, author bio/follow cards, and breadcrumb footers do not leak after article prose.
- `general--www.motorsport.com-f1-news-im-not-a-machine-isack-hadjar-blasts-red-bull-start-procedure-10830609`: verifies that Motorsport.com share/save, previous article, top comments, more-from recirculation, latest-news, prime-content, and adblock subscription footer modules do not leak after article prose.

## Known Differences

- Non-empty Kotlin Markdown output is normalized to one final newline. Empty content remains an empty string.
- Math rendering/conversion fidelity remains out of scope; source data is preserved where practical.
- Supported upstream Markdown fixtures are expected to match the pinned repo expected output, including the intentional italic image-caption divergence from upstream Defuddle.
- Supported upstream metadata snapshots are expected to match pinned Defuddle `title`, `author`, and `site` frontmatter.

## Top Classified Gaps

- Network-backed extraction is out of scope for this library.
- Math rendering/conversion fidelity is excluded by scope.
- Dynamic browser-rendered content and computed styles are out of scope for static fixture runs.
