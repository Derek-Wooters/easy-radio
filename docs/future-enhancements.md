# Future Enhancements

Ideas identified during development that aren't being built now, kept here so they
don't get lost.

## Switch podcast search from Apple's iTunes Search API to Podcast Index

Podcast search (`PodcastRepository.search()` / `ItunesSearchApi`) currently calls
Apple's iTunes Search API directly, unmodified. Apple's own relevance ranking can
fail badly on truncated or misspelled queries: searching "dan barreir" (missing the
final "o" of "Barreiro") returns zero relevant results out of 51 total, while
"dan barreiro" returns the correct show as the #1 result. Confirmed this isn't
fixable by re-sorting results on our end -- the correct show isn't present anywhere
in Apple's returned set for the bad query, so there's nothing to re-rank client-side.

**Planned upgrade**: [Podcast Index](https://podcastindex.org) (api.podcastindex.org)
-- free, open, no request cap, built as a community alternative to relying solely on
Apple's catalog. Requires a free API key + secret and HMAC-signed requests (SHA-1 of
key + secret + timestamp as custom headers), which is more integration work than
Apple's key-less GET calls.

**Current blocker**: Podcast Index is not currently accepting signups from free email
providers (Gmail, Outlook, etc.) due to abuse prevention. Needs a non-free-provider
email to register, or for the restriction to lift.

**Alternatives evaluated and rejected**: Listen Notes (150 requests/month free tier)
and Taddy (500 requests/month free tier, no credit card required) both have better
typo-tolerant search than Apple, but their free tiers are too tight for anything
beyond light testing.
