package com.easyradio.core.model

/**
 * Hand-picked stations shown before the user searches, and used as a fallback
 * when the remote station directory is unreachable. Every stream URL below was
 * verified against the public Radio-Browser directory (radio-browser.info) --
 * `lastcheckok: 1` at verification time, resolved against each station's own
 * homepage domain. Each `imageUrl` is the `favicon` field from the
 * Radio-Browser entry whose `url_resolved` exactly matches this station's
 * `streamUrl`, so it's a real, current logo rather than a guessed asset.
 */
object CuratedRadioStations {

    val ALL: List<RadioStation> = listOf(
        RadioStation.KFAN_TEST_STATION,
        RadioStation(
            id = "azpm-npr-89.1",
            name = "AZPM NPR 89.1",
            streamUrl = "https://streaming.azpm.org/kuaz128",
            tagline = "NPR News and Music",
            imageUrl = "https://media.azpm.org/master/doc/icons/apple-touch-icon.png",
        ),
        RadioStation(
            id = "bbc-world-service",
            name = "BBC World Service",
            streamUrl = "https://stream.live.vc.bbcmedia.co.uk/bbc_world_service_east_asia",
            tagline = "News, analysis and information from the BBC",
            imageUrl = "https://cdn.vox-cdn.com/thumbor/FOKCcOpam5-0m4VfrxvfzMdDK6I=/10x0:610x400/" +
                "1400x1400/filters:focal(10x0:610x400):format(jpeg)/cdn.vox-cdn.com/assets/958975/" +
                "bbc_world_service.jpeg",
        ),
    )
}
