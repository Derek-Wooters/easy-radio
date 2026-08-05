package com.easyradio.core.model

/**
 * Hand-picked stations shown before the user searches, and used as a fallback
 * when the remote station directory is unreachable. Every stream URL below was
 * verified against the public Radio-Browser directory (radio-browser.info) --
 * `lastcheckok: 1` at verification time, resolved against each station's own
 * homepage domain.
 */
object CuratedRadioStations {

    val ALL: List<RadioStation> = listOf(
        RadioStation.KFAN_TEST_STATION,
        RadioStation(
            id = "azpm-npr-89.1",
            name = "AZPM NPR 89.1",
            streamUrl = "https://streaming.azpm.org/kuaz128",
            tagline = "NPR News and Music",
        ),
        RadioStation(
            id = "bbc-world-service",
            name = "BBC World Service",
            streamUrl = "https://stream.live.vc.bbcmedia.co.uk/bbc_world_service_east_asia",
            tagline = "News, analysis and information from the BBC",
        ),
    )
}
