package com.easyradio.core.model

data class RadioStation(
    val id: String,
    val name: String,
    val streamUrl: String,
    val tagline: String = "",
    val imageUrl: String? = null,
) {
    init {
        require(id.isNotBlank()) { "RadioStation.id must not be blank" }
        require(name.isNotBlank()) { "RadioStation.name must not be blank" }
        require(streamUrl.startsWith("https://")) {
            "RadioStation.streamUrl must be an https URL, was: $streamUrl"
        }
    }

    companion object {
        // Verified via the public Radio-Browser directory (radio-browser.info) against
        // KFAN's own iHeartRadio stream infrastructure (homepage kfan.iheart.com).
        // Used to prove the playback pipeline end-to-end before Phase 2 adds a real directory.
        val KFAN_TEST_STATION = RadioStation(
            id = "kfan-100.3",
            name = "KFAN FM 100.3",
            streamUrl = "https://stream.revma.ihrhls.com/zc1209",
            tagline = "Audio Home For Minnesota Sports",
            // Verified against the Radio-Browser entry for this exact stream url (see
            // CuratedRadioStations doc comment for the verification method).
            imageUrl = "https://i.iheart.com/v3/re/assets.brands/7abeb696904580c44cd523e678ac767c" +
                "?ops=new(),flood(%22white%22),swap(),merge(%22over%22),gravity(%22center%22)," +
                "contain(167,167),quality(80),format(%22png%22)",
        )
    }
}
