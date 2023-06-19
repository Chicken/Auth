void async function() {
    console.log("[PrivateLocation/info] Private location loaded!");

    const PlayerMarkerManager = window.bluemap.playerMarkerManager.constructor;
    const BlueMapApp = window.bluemap.constructor;

    BlueMapApp.prototype.initPlayerMarkerManager = function() {
        if (this.playerMarkerManager){
            this.playerMarkerManager.clear();
            this.playerMarkerManager.dispose()
        }

        const map = this.mapViewer.map;
        if (!map) return;

        this.playerMarkerManager = new PlayerMarkerManager(
            this.mapViewer.markers,
            // map.data.dataUrl + "live/players.json", // Original
            "/addons/privatelocation/players/" + map.data.id, // Changed
            map.data.dataUrl + "assets/playerheads/",
            this.events
        );
        this.playerMarkerManager.setAutoUpdateInterval(0);
        return this.playerMarkerManager.update()
            .then(() => {
                this.playerMarkerManager.setAutoUpdateInterval(1000);
            })
            .catch(e => {
                alert(this.events, e, "warning");
                this.playerMarkerManager.clear();
                this.playerMarkerManager.dispose();
            });
    }

    window.bluemap.initPlayerMarkerManager();
}();
